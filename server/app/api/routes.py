import logging
import time

from fastapi import APIRouter, HTTPException, Query, Request
from fastapi.responses import FileResponse, Response

from app.db.database import get_db, get_read_db
from app.models.schemas import ScanResponse, SearchResponse
from app.services.product_service import scan_barcode as _scan_barcode
from app.services.product_service import search_products as _search_products
from app.services.image_service import get_image_data, guess_media_type

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api")


@router.get("/scan/{barcode}", response_model=ScanResponse)
async def scan_barcode(barcode: str) -> ScanResponse:
    start = time.perf_counter()
    db = await get_read_db()

    result = await _scan_barcode(db, barcode)
    if result is None:
        raise HTTPException(status_code=404, detail="barcode not found")

    elapsed = (time.perf_counter() - start) * 1000
    logger.info("scan %s → %s (%.1fms)", barcode, result.sku_id or barcode, elapsed)
    return result


@router.get("/search", response_model=SearchResponse)
async def search_products(
    q: str = Query(..., min_length=1),
    limit: int = Query(20, ge=1, le=100),
) -> SearchResponse:
    db = await get_read_db()
    items = await _search_products(db, q, limit)
    return SearchResponse(total=len(items), items=items)


@router.get("/image/{path:path}")
async def get_image(path: str, request: Request, width: int | None = None) -> Response:
    http_client = request.app.state.http_client
    image_bytes, file_path, resized_width = await get_image_data(path, width, http_client)

    media_type = guess_media_type(path)
    if file_path and resized_width is None:
        return FileResponse(str(file_path), media_type=media_type)
    if file_path and resized_width is not None:
        return FileResponse(str(file_path), media_type=media_type)
    return Response(content=image_bytes, media_type=media_type)
