import io
import os
import uuid

from fastapi import APIRouter, HTTPException, Request, UploadFile
from PIL import Image

from app.db.database import get_db, get_read_db
from app.db.database import write_lock as _zone_lock
from app.services import warehouse_service as ws

router = APIRouter(prefix="/api")

_PHOTO_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "static", "photos")
_ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
_MAX_FILE_SIZE = 10 * 1024 * 1024


@router.get("/zones")
async def get_zones():
    db = await get_read_db()
    return await ws.get_zones(db)


@router.post("/zones")
async def create_zone(request: Request):
    body = await request.json()
    code = body.get("code")
    name = body.get("name")
    if not code or not name:
        raise HTTPException(status_code=400, detail="code and name are required")
    rows = body.get("rows", 3)
    cols = body.get("cols", 4)
    db = await get_db()
    async with _zone_lock:
        return await ws.create_zone(db, code, name, rows, cols)


@router.patch("/zones/{zone_id}")
async def update_zone(zone_id: int, request: Request):
    body = await request.json()
    kwargs = {}
    for k in ("code", "name", "rows", "cols", "sort_order"):
        if k in body:
            kwargs[k] = body[k]
    db = await get_db()
    async with _zone_lock:
        result = await ws.update_zone(db, zone_id, **kwargs)
    if result is None:
        raise HTTPException(status_code=404, detail="zone not found")
    return result


@router.delete("/zones/{zone_id}")
async def delete_zone(zone_id: int):
    db = await get_db()
    async with _zone_lock:
        found = await ws.delete_zone(db, zone_id)
    if not found:
        raise HTTPException(status_code=404, detail="zone not found")
    return {"status": "ok"}


@router.get("/zones/{zone_id}/cells")
async def get_zone_cells(zone_id: int):
    db = await get_read_db()
    return await ws.get_zone_cells(db, zone_id)


@router.get("/cells/{cell_id}")
async def get_cell_detail(cell_id: int):
    db = await get_read_db()
    result = await ws.get_cell_detail(db, cell_id)
    if result is None:
        raise HTTPException(status_code=404, detail="cell not found")
    return result


@router.patch("/cells/{cell_id}")
async def update_cell(cell_id: int, request: Request):
    body = await request.json()
    db = await get_db()
    async with _zone_lock:
        result = await ws.update_cell(db, cell_id, **body)
    if result is None:
        raise HTTPException(status_code=404, detail="cell not found")
    return result


@router.post("/cells/{cell_id}/levels")
async def add_level(cell_id: int, request: Request):
    body = await request.json()
    label = body.get("label", "")
    db = await get_db()
    async with _zone_lock:
        result = await ws.add_level(db, cell_id, label)
    if result is None:
        raise HTTPException(status_code=404, detail="cell not found")
    return result


@router.delete("/levels/{level_id}")
async def delete_level(level_id: int):
    db = await get_db()
    async with _zone_lock:
        found = await ws.delete_level(db, level_id)
    if not found:
        raise HTTPException(status_code=404, detail="level not found")
    return {"status": "ok"}


@router.post("/levels/{level_id}/products")
async def add_level_product(level_id: int, request: Request):
    body = await request.json()

    db = await get_db()
    async with _zone_lock:
        master_id = await ws.resolve_product_master(
            db,
            barcode=body.get("barcode", ""),
            sku_id=body.get("sku_id", ""),
            product_master_id=body.get("product_master_id"),
        )

        result = await ws.add_level_product(
            db,
            level_id,
            product_master_id=master_id,
            photo=body.get("photo", ""),
            memo=body.get("memo", ""),
        )

    if result is None:
        raise HTTPException(status_code=404, detail="level not found")

    if master_id:
        level_row = await db.execute_fetchall(
            "SELECT cl.cell_id FROM cell_level cl WHERE cl.id = ?", (level_id,)
        )
        if level_row:
            cell_info = await db.execute_fetchall(
                "SELECT wc.row, wc.col, wz.code, wz.cols "
                "FROM warehouse_cell wc JOIN warehouse_zone wz ON wc.zone_id = wz.id "
                "WHERE wc.id = ?",
                (level_row[0][0],),
            )
            if cell_info:
                ci = cell_info[0]
                await ws.sync_product_location(db, master_id, ci[2], ci[0], ci[1], ci[3])

    return result


@router.delete("/level-products/{product_id}")
async def remove_level_product(product_id: int):
    db = await get_db()
    async with _zone_lock:
        found, master_id = await ws.remove_level_product(db, product_id)
    if not found:
        raise HTTPException(status_code=404, detail="product not found")

    if master_id:
        await ws.clear_product_location(db, master_id)

    return {"status": "ok"}


@router.post("/level-products/{product_id}/photo")
async def upload_level_product_photo(product_id: int, file: UploadFile):
    content = await file.read()
    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in _ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"unsupported file type: {ext}")
    if len(content) > _MAX_FILE_SIZE:
        raise HTTPException(status_code=400, detail="file too large (max 10MB)")
    try:
        Image.open(io.BytesIO(content)).verify()
    except Exception:
        raise HTTPException(status_code=400, detail="invalid image file")

    os.makedirs(_PHOTO_DIR, exist_ok=True)
    suffix = uuid.uuid4().hex[:8]
    filename = f"lp_{product_id}_{suffix}{ext}"
    filepath = os.path.join(_PHOTO_DIR, filename)

    db = await get_db()
    async with _zone_lock:
        existing = await db.execute_fetchall(
            "SELECT photo FROM cell_level_product WHERE id = ?", (product_id,)
        )
        if not existing:
            raise HTTPException(status_code=404, detail="product not found")

        old_photo = existing[0][0]
        if old_photo:
            old_path = os.path.join(os.path.dirname(__file__), "..", "..", old_photo.lstrip("/"))
            resolved = os.path.realpath(old_path)
            if resolved.startswith(os.path.realpath(_PHOTO_DIR)) and os.path.exists(resolved):
                os.remove(resolved)

        with open(filepath, "wb") as f:
            f.write(content)

        photo_url = f"/static/photos/{filename}"
        await ws.update_level_product_photo(db, product_id, photo_url)

    return {"status": "ok", "photo": photo_url}


@router.delete("/level-products/{product_id}/photo")
async def delete_level_product_photo(product_id: int):
    db = await get_db()
    async with _zone_lock:
        existing = await db.execute_fetchall(
            "SELECT photo FROM cell_level_product WHERE id = ?", (product_id,)
        )
        if not existing:
            raise HTTPException(status_code=404, detail="product not found")

        photo = existing[0][0]
        if photo:
            filepath = os.path.join(os.path.dirname(__file__), "..", "..", photo.lstrip("/"))
            resolved = os.path.realpath(filepath)
            if resolved.startswith(os.path.realpath(_PHOTO_DIR)) and os.path.exists(resolved):
                os.remove(resolved)

        await ws.update_level_product_photo(db, product_id, "")

    return {"status": "ok"}


@router.get("/product-location/{sku_id}")
async def get_product_location(sku_id: str):
    db = await get_read_db()
    result = await ws.find_product_location(db, sku_id)
    if result is None:
        raise HTTPException(status_code=404, detail="location not found")
    return result
