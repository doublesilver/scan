import logging
import time
from datetime import datetime

from fastapi import APIRouter, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse, Response

from app.db.database import get_db, get_read_db
from app.models.schemas import (
    ScanResponse, SearchResponse, StockResponse, StockUpdate, StockLogItem,
    PrintRequest, CartRequest,
    BoxResponse,
    HistoryItem,
    FavoriteItem, FavoriteRequest,
    RecentScanItem,
    ShelfListResponse, ShelfUpdate,
)
from app.services.product_service import scan_barcode as _scan_barcode
from app.services.print_service import print_label as _print_label
from app.services.cart_service import add_to_cart as _add_to_cart
from app.services.product_service import search_products as _search_products
from app.services.image_service import get_image_data, guess_media_type, upload_to_webdav, delete_from_webdav
from app.services.stock_service import get_stock as _get_stock
from app.services.stock_service import update_stock as _update_stock
from app.services.stock_service import get_stock_log as _get_stock_log
from app.services.status_service import get_status as _get_status
from app.services.box_service import get_box as _get_box
from app.services.history_service import log_action as _log_action
from app.services.history_service import get_history as _get_history
from app.services.favorite_service import add_favorite as _add_favorite
from app.services.favorite_service import remove_favorite as _remove_favorite
from app.services.favorite_service import get_favorites as _get_favorites
from app.services.scan_log_service import log_scan as _log_scan
from app.services.scan_log_service import get_recent_scans as _get_recent_scans
from app.services.url_import_service import import_purchase_urls as _import_purchase_urls
from app.services.shelf_service import (
    get_shelves as _get_shelves,
    update_shelf_label as _update_shelf_label,
    delete_shelf_label as _delete_shelf_label,
    add_shelf_photo as _add_shelf_photo,
    delete_shelf_photo as _delete_shelf_photo,
)

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

    if result.sku_id:
        write_db = await get_db()
        await _log_scan(write_db, barcode, result.sku_id, result.product_name)

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


@router.post("/print")
async def print_label(body: PrintRequest):
    result = _print_label(body.product_name, body.barcode, body.sku_id, body.quantity)
    if result["status"] == "error":
        raise HTTPException(status_code=500, detail=result["message"])
    db = await get_db()
    await _log_action(db, "print", body.barcode, body.sku_id, body.product_name, body.quantity)
    return result


@router.post("/cart")
async def add_to_cart(body: CartRequest):
    result = _add_to_cart(body.barcode, body.sku_id, body.product_name, body.quantity)
    if result["status"] == "error":
        raise HTTPException(status_code=500, detail=result["message"])
    db = await get_db()
    await _log_action(db, "cart", body.barcode, body.sku_id, body.product_name, body.quantity)
    return result


@router.get("/image/{path:path}")
async def get_image(path: str, request: Request, width: int | None = Query(None, ge=1, le=2000)) -> Response:
    http_client = request.app.state.http_client
    image_bytes, file_path, resized_width = await get_image_data(path, width, http_client)

    media_type = guess_media_type(path)
    if file_path:
        return FileResponse(str(file_path), media_type=media_type)
    return Response(content=image_bytes, media_type=media_type)


@router.get("/box/{qr_code}", response_model=BoxResponse)
async def get_box(qr_code: str) -> BoxResponse:
    db = await get_read_db()
    result = await _get_box(db, qr_code)
    if result is None:
        raise HTTPException(status_code=404, detail="box not found")
    return result


@router.get("/history", response_model=list[HistoryItem])
async def get_history(
    type: str | None = Query(None),
    limit: int = Query(50, ge=1, le=200),
) -> list[HistoryItem]:
    db = await get_read_db()
    return await _get_history(db, action_type=type, limit=limit)


@router.post("/favorite")
async def add_favorite(body: FavoriteRequest):
    db = await get_db()
    await _add_favorite(db, body.sku_id, body.product_name, body.barcode)
    return {"status": "ok"}


@router.delete("/favorite/{sku_id}")
async def remove_favorite(sku_id: str):
    db = await get_db()
    found = await _remove_favorite(db, sku_id)
    if not found:
        raise HTTPException(status_code=404, detail="favorite not found")
    return {"status": "ok"}


@router.get("/favorites", response_model=list[FavoriteItem])
async def get_favorites() -> list[FavoriteItem]:
    db = await get_read_db()
    return await _get_favorites(db)


@router.get("/recent", response_model=list[RecentScanItem])
async def get_recent_scans(limit: int = Query(20, ge=1, le=100)) -> list[RecentScanItem]:
    db = await get_read_db()
    return await _get_recent_scans(db, limit)


@router.post("/import/urls")
async def import_urls(file_path: str = Query(...)):
    db = await get_db()
    result = await _import_purchase_urls(db, file_path)
    if result["status"] == "error":
        raise HTTPException(status_code=400, detail=result["message"])
    return result


@router.get("/shelves/{floor}/{zone}", response_model=ShelfListResponse)
async def get_shelves(floor: int, zone: str) -> ShelfListResponse:
    db = await get_read_db()
    shelves = await _get_shelves(db, floor, zone)
    return ShelfListResponse(floor=floor, zone=zone, shelves=shelves)


@router.patch("/shelf/{shelf_id}")
async def update_shelf_label(shelf_id: int, body: ShelfUpdate):
    db = await get_db()
    result = await _update_shelf_label(db, shelf_id, body.label)
    if result is None:
        raise HTTPException(status_code=404, detail="shelf not found")
    return result


@router.delete("/shelf/{shelf_id}/label")
async def delete_shelf_label(shelf_id: int):
    db = await get_db()
    found = await _delete_shelf_label(db, shelf_id)
    if not found:
        raise HTTPException(status_code=404, detail="shelf not found")
    return {"status": "ok"}


@router.post("/shelf/{shelf_id}/photo")
async def upload_shelf_photo(shelf_id: int, file: UploadFile, request: Request):
    from app.config import settings
    db = await get_read_db()
    rows = await db.execute_fetchall(
        "SELECT floor, zone, shelf_number FROM shelf WHERE id = ?", (shelf_id,)
    )
    if not rows:
        raise HTTPException(status_code=404, detail="shelf not found")
    floor, zone, shelf_number = rows[0]

    file_bytes = await file.read()
    timestamp = datetime.utcnow().strftime("%Y%m%d%H%M%S")
    filename = f"{floor}-{zone}-{shelf_number:02d}_{timestamp}.jpg"
    remote_path = f"{settings.shelf_photo_nas_prefix}/{filename}"

    http_client = request.app.state.http_client
    await upload_to_webdav(http_client, file_bytes, remote_path)

    write_db = await get_db()
    photo_id = await _add_shelf_photo(write_db, shelf_id, remote_path)

    return {
        "id": photo_id,
        "shelf_id": shelf_id,
        "file_path": remote_path,
        "photo_url": f"/api/image/{remote_path}",
    }


@router.delete("/shelf/photo/{photo_id}")
async def delete_shelf_photo(photo_id: int, request: Request):
    db = await get_db()
    file_path = await _delete_shelf_photo(db, photo_id)
    if file_path is None:
        raise HTTPException(status_code=404, detail="photo not found")

    http_client = request.app.state.http_client
    await delete_from_webdav(http_client, file_path)

    return {"status": "ok"}
