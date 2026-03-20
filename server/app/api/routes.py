import io
import logging
import time
from pathlib import Path

from fastapi import APIRouter, HTTPException, Query, Request
from fastapi.responses import FileResponse, Response
from PIL import Image, UnidentifiedImageError

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

    if sku_id:
        cursor = await db.execute(
            "SELECT DISTINCT i.file_path, i.image_type, i.sort_order "
            "FROM image i JOIN barcode b ON b.barcode = i.barcode "
            "WHERE b.sku_id = ? ORDER BY i.sort_order, i.id",
            (sku_id,),
        )
    else:
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
    except Exception as e:
        logger.warning("FTS5 검색 실패, LIKE 폴백: %s", e)
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
async def get_image(path: str, request: Request, width: int | None = None) -> Response:
    """이미지 프록시 — 캐시 → mock → 기본 이미지 순으로 반환. width 지정 시 리사이즈."""
    cache_base = Path(settings.image_cache_dir).resolve()
    cache_path = (cache_base / path).resolve()
    if not str(cache_path).startswith(str(cache_base)):
        raise HTTPException(status_code=400, detail="invalid path")

    if width and width > 0:
        resized_path = (cache_base / "resized" / str(width) / path).resolve()
        if not str(resized_path).startswith(str(cache_base)):
            raise HTTPException(status_code=400, detail="invalid path")
        if resized_path.exists() and resized_path.is_file():
            logger.info("resized cache hit: %s (w=%d)", path, width)
            return FileResponse(str(resized_path), media_type=_guess_media_type(path))

    image_bytes = None
    source_path = None

    if cache_path.exists() and cache_path.is_file():
        logger.info("image cache hit: %s", path)
        source_path = cache_path
    else:
        mock_base = Path("data/mock_images").resolve()
        mock_path = (mock_base / path).resolve()
        if not str(mock_path).startswith(str(mock_base)):
            raise HTTPException(status_code=400, detail="invalid path")

        if mock_path.exists() and mock_path.is_file():
            logger.info("image mock hit: %s", path)
            source_path = mock_path
        elif settings.webdav_base_url:
            http_client = request.app.state.http_client
            image_bytes = await _fetch_from_webdav(http_client, path)
            if image_bytes:
                cache_path.parent.mkdir(parents=True, exist_ok=True)
                cache_path.write_bytes(image_bytes)
                logger.info("image webdav fetch + cached: %s", path)

    if source_path is None and image_bytes is None:
        default_path = Path("data/mock_images/default.png")
        if default_path.exists():
            return FileResponse(str(default_path), media_type="image/png")
        raise HTTPException(status_code=404, detail="image not found")

    if width and width > 0:
        raw = image_bytes if image_bytes else source_path.read_bytes()
        resized = _resize_image(raw, width, path)
        if resized:
            resized_path = (cache_base / "resized" / str(width) / path).resolve()
            resized_path.parent.mkdir(parents=True, exist_ok=True)
            resized_path.write_bytes(resized)
            logger.info("resized + cached: %s (w=%d)", path, width)
            return Response(content=resized, media_type=_guess_media_type(path))

    if source_path:
        return FileResponse(str(source_path), media_type=_guess_media_type(path))
    return Response(content=image_bytes, media_type=_guess_media_type(path))


async def _fetch_from_webdav(client, path: str) -> bytes | None:
    """WebDAV에서 이미지 다운로드. 실패 시 None 반환."""
    url = f"{settings.webdav_base_url.rstrip('/')}/{path}"
    try:
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


def _resize_image(raw: bytes, width: int, path: str) -> bytes | None:
    try:
        img = Image.open(io.BytesIO(raw))
    except (UnidentifiedImageError, Exception):
        raise HTTPException(status_code=400, detail="not a valid image")

    if width >= img.width:
        return None

    ratio = width / img.width
    new_height = int(img.height * ratio)
    resized = img.resize((width, new_height), Image.LANCZOS)

    buf = io.BytesIO()
    fmt = img.format or "JPEG"
    resized.save(buf, format=fmt)
    return buf.getvalue()


def _guess_media_type(path: str) -> str:
    ext = Path(path).suffix.lower()
    return {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".gif": "image/gif", ".webp": "image/webp"}.get(ext, "application/octet-stream")
