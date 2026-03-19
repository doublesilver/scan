import logging
import time
from pathlib import Path

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import FileResponse, Response

from app.config import settings
from app.db.database import get_db
from app.models.schemas import ImageItem, ScanResponse, SearchItem, SearchResponse

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api")


@router.get("/scan/{barcode}", response_model=ScanResponse)
async def scan_barcode(barcode: str) -> ScanResponse:
    """바코드로 상품 정보 + 이미지 조회."""
    start = time.perf_counter()
    db = await get_db()

    # 바코드 → SKU ID
    cursor = await db.execute(
        "SELECT sku_id FROM barcode WHERE barcode = ?", (barcode,)
    )
    row = await cursor.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="barcode not found")

    sku_id = row["sku_id"]

    # 상품 정보
    cursor = await db.execute(
        "SELECT sku_id, product_name, category, brand FROM product WHERE sku_id = ?",
        (sku_id,),
    )
    product = await cursor.fetchone()
    if not product:
        raise HTTPException(status_code=404, detail="barcode not found")

    # 해당 SKU의 모든 바코드
    cursor = await db.execute(
        "SELECT DISTINCT barcode FROM barcode WHERE sku_id = ?", (sku_id,)
    )
    barcodes = [r["barcode"] for r in await cursor.fetchall()]

    # 이미지
    cursor = await db.execute(
        "SELECT file_path, image_type FROM image WHERE sku_id = ?", (sku_id,)
    )
    images = [ImageItem(file_path=r["file_path"], image_type=r["image_type"]) for r in await cursor.fetchall()]

    elapsed = (time.perf_counter() - start) * 1000
    logger.info("scan %s → %s (%.1fms)", barcode, sku_id, elapsed)

    return ScanResponse(
        sku_id=product["sku_id"],
        product_name=product["product_name"],
        category=product["category"],
        brand=product["brand"],
        barcodes=barcodes,
        images=images,
    )


@router.get("/search", response_model=SearchResponse)
async def search_products(
    q: str = Query(..., min_length=1),
    limit: int = Query(20, ge=1, le=100),
) -> SearchResponse:
    """상품명/SKU ID 텍스트 검색."""
    db = await get_db()
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
    # 1. 로컬 캐시 확인
    cache_path = Path(settings.image_cache_dir) / path
    if cache_path.exists() and cache_path.is_file():
        logger.info("image cache hit: %s", path)
        return FileResponse(str(cache_path), media_type=_guess_media_type(path))

    # 2. mock 이미지 확인 (개발용)
    mock_path = Path("data/mock_images") / path
    if mock_path.exists() and mock_path.is_file():
        logger.info("image mock hit: %s", path)
        return FileResponse(str(mock_path), media_type=_guess_media_type(path))

    # 3. WebDAV에서 가져오기 (M4 구현)
    if settings.webdav_base_url:
        image_bytes = await _fetch_from_webdav(path)
        if image_bytes:
            # 캐시에 저장
            cache_path.parent.mkdir(parents=True, exist_ok=True)
            cache_path.write_bytes(image_bytes)
            logger.info("image webdav fetch + cached: %s", path)
            return Response(content=image_bytes, media_type=_guess_media_type(path))

    # 4. 기본 이미지 반환
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
