import logging
import time

from fastapi import APIRouter, HTTPException, Query, Request
from fastapi.responses import FileResponse, Response

from app.db.database import get_db, get_read_db
from app.models.schemas import ScanResponse, SearchResponse, StockResponse, StockUpdate, StockLogItem
from app.services.product_service import scan_barcode as _scan_barcode
from app.services.product_service import search_products as _search_products
from app.services.image_service import get_image_data, guess_media_type
from app.services.stock_service import get_stock as _get_stock
from app.services.stock_service import update_stock as _update_stock
from app.services.stock_service import get_stock_log as _get_stock_log
from app.services.status_service import get_status as _get_status

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


@router.get("/stock/{sku_id}", response_model=StockResponse)
async def get_stock(sku_id: str) -> StockResponse:
    db = await get_read_db()
    result = await _get_stock(db, sku_id)
    if result is None:
        raise HTTPException(status_code=404, detail="SKU not found")
    return result


@router.patch("/stock/{sku_id}", response_model=StockResponse)
async def update_stock(sku_id: str, body: StockUpdate) -> StockResponse:
    db = await get_db()
    result = await _update_stock(db, sku_id, body)
    if result is None:
        raise HTTPException(status_code=404, detail="SKU not found")
    return result


@router.get("/stock/{sku_id}/log", response_model=list[StockLogItem])
async def get_stock_log(sku_id: str, limit: int = Query(20, ge=1, le=100)) -> list[StockLogItem]:
    db = await get_read_db()
    return await _get_stock_log(db, sku_id, limit)


@router.get("/status")
async def status():
    db = await get_read_db()
    return await _get_status(db)


@router.get("/image/{path:path}")
async def get_image(path: str, request: Request, width: int | None = Query(None, ge=1, le=2000)) -> Response:
    http_client = request.app.state.http_client
    image_bytes, file_path, resized_width = await get_image_data(path, width, http_client)

    media_type = guess_media_type(path)
    if file_path:
        return FileResponse(str(file_path), media_type=media_type)
    return Response(content=image_bytes, media_type=media_type)
