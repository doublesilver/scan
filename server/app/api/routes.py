import logging
import time
from pathlib import Path

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import FileResponse, Response

from app.config import settings
from app.db.database import get_db, get_read_db
from app.models.schemas import ImageItem, ScanResponse, SearchItem, SearchResponse

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api")


@router.get("/scan/{barcode}", response_model=ScanResponse)
async def scan_barcode(barcode: str) -> ScanResponse:
    """바코드로 상품 정보 + 이미지 조회."""
    start = time.perf_counter()
    db = await get_read_db()

    cursor = await db.execute(
        "SELECT sku_id FROM barcode WHERE barcode = ?", (barcode,)
    )
    row = await cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="barcode not found")

    sku_id = row["sku_id"]

    if sku_id:
        cursor = await db.execute(
            "SELECT sku_id, product_name, category, brand FROM product WHERE sku_id = ?",
            (sku_id,),
        )
        product = await cursor.fetchone()

        cursor = await db.execute(
            "SELECT DISTINCT barcode FROM barcode WHERE sku_id = ?", (sku_id,)
        )
        barcodes = [r["barcode"] for r in await cursor.fetchall()]
    else:
        product = None
        barcodes = [barcode]

    cursor = await db.execute(
        "SELECT file_path, image_type, sort_order FROM image "
        "WHERE barcode = ? ORDER BY sort_order, id",
        (barcode,),
    )
    images = [
        ImageItem(file_path=r["file_path"], image_type=r["image_type"])
        for r in await cursor.fetchall()
    ]

    if sku_id and not product:
        raise HTTPException(status_code=404, detail="barcode not found")

    elapsed = (time.perf_counter() - start) * 1000
    result_sku = product["sku_id"] if product else barcode
    logger.info("scan %s → %s (%.1fms)", barcode, result_sku, elapsed)

    return ScanResponse(
        sku_id=product["sku_id"] if product else "",
        product_name=product["product_name"] if product else "",
        category=product["category"] if product else "",
        brand=product["brand"] if product else "",
        barcodes=barcodes,
        images=images,
    )


@router.get("/search", response_model=SearchResponse)
async def search_products(
    q: str = Query(..., min_length=1),
    limit: int = Query(20, ge=1, le=100),
) -> SearchResponse:
    """상품명/SKU ID 텍스트 검색 (FTS5 우선, 폴백 LIKE)."""
    db = await get_read_db()

    try:
        cursor = await db.execute(
            "SELECT p.sku_id, p.product_name, p.category, p.brand "
            "FROM product_fts f JOIN product p ON f.sku_id = p.sku_id "
            "WHERE product_fts MATCH ? LIMIT ?",
            (q, limit),
        )
        rows = await cursor.fetchall()
    except Exception:
        pattern = f"%{q}%"
        cursor = await db.execute(
            "SELECT sku_id, product_name, category, brand FROM product "
            "WHERE product_name LIKE ? OR sku_id LIKE ? LIMIT ?",
            (pattern, pattern, limit),
        )
        rows = await cursor.fetchall()

    items = [
        SearchItem(
            sku_id=r["sku_id"],
            product_name=r["product_name"],
            category=r["category"],
            brand=r["brand"],
        )
        for r in rows
    ]

    return SearchResponse(total=len(items), items=items)


@router.get("/image/{path:path}")
async def get_image(path: str) -> Response:
    """이미지 프록시 — 캐시 → mock → 기본 이미지 순으로 반환."""
    cache_path = Path(settings.image_cache_dir) / path
    if cache_path.exists() and cache_path.is_file():
        logger.info("image cache hit: %s", path)
        return FileResponse(str(cache_path), media_type=_guess_media_type(path))

    mock_path = Path("data/mock_images") / path
    if mock_path.exists() and mock_path.is_file():
        logger.info("image mock hit: %s", path)
        return FileResponse(str(mock_path), media_type=_guess_media_type(path))

    if settings.webdav_base_url:
        image_bytes = await _fetch_from_webdav(path)
        if image_bytes:
            cache_path.parent.mkdir(parents=True, exist_ok=True)
            cache_path.write_bytes(image_bytes)
            logger.info("image webdav fetch + cached: %s", path)
            return Response(content=image_bytes, media_type=_guess_media_type(path))

    default_path = Path("data/mock_images/default.png")
    if default_path.exists():
        return FileResponse(str(default_path), media_type="image/png")

    raise HTTPException(status_code=404, detail="image not found")


async def _fetch_from_webdav(path: str) -> bytes | None:
    """WebDAV에서 이미지 다운로드. 실패 시 None 반환."""
    import httpx

    url = f"{settings.webdav_base_url.rstrip('/')}/{path}"
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            auth = None
            if settings.webdav_username:
                auth = (settings.webdav_username, settings.webdav_password)
            resp = await client.get(url, auth=auth)
            if resp.status_code == 200:
                return resp.content
            logger.warning("webdav %s → %d", url, resp.status_code)
    except Exception as e:
        logger.warning("webdav 연결 실패: %s — %s", url, e)
    return None


def _guess_media_type(path: str) -> str:
    ext = Path(path).suffix.lower()
    return {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".gif": "image/gif", ".webp": "image/webp"}.get(ext, "application/octet-stream")
