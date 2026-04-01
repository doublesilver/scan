from datetime import datetime

from fastapi import APIRouter, HTTPException, Request, UploadFile

from app.db.database import get_db, get_read_db
from app.models.schemas import ShelfListResponse, ShelfUpdate
from app.services.image_service import upload_to_webdav, delete_from_webdav
from app.services.map_layout_service import get_or_init_layout as _get_or_init_layout
from app.services.shelf_service import (
    update_shelf_label as _update_shelf_label,
    delete_shelf_label as _delete_shelf_label,
    add_shelf_photo as _add_shelf_photo,
    delete_shelf_photo as _delete_shelf_photo,
)

router = APIRouter(prefix="/api")


@router.get("/shelves/{floor}/{zone}", response_model=ShelfListResponse)
async def get_shelves(floor: int, zone: str) -> ShelfListResponse:
    db = await get_read_db()
    layout = await _get_or_init_layout(db)
    zone_def = next((z for z in layout.get("zones", []) if z["code"] == zone), None)
    if not zone_def:
        return ShelfListResponse(floor=floor, zone=zone, shelves=[])

    from app.models.schemas import ShelfItem as ShelfItemSchema
    shelves = []
    for r in range(1, zone_def["rows"] + 1):
        for c in range(1, zone_def["cols"] + 1):
            cell_key = f"{zone}-{r}-{c}"
            cell_key_padded = f"{zone}-{str(r).zfill(2)}-{str(c).zfill(2)}"
            cells = layout.get("cells", {})
            cell = cells.get(cell_key) or cells.get(cell_key_padded) or {}
            shelf_num = (r - 1) * zone_def["cols"] + c
            levels = cell.get("levels", [])
            photo_url = levels[0].get("photo") if levels else None
            shelves.append(ShelfItemSchema(
                id=shelf_num,
                floor=floor,
                zone=zone,
                shelf_number=shelf_num,
                label=cell.get("label"),
                photo_path=None,
                photo_url=photo_url,
                cell_key=cell_key,
            ))
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
