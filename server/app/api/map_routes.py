import io
import os
import re
import uuid

from fastapi import APIRouter, HTTPException, Request, UploadFile
from PIL import Image

from app.db.database import get_db, get_read_db
from app.db.database import write_lock as _map_layout_lock
from app.services import warehouse_service as ws
from app.services.map_layout_service import save_layout_only as _save_layout_only

router = APIRouter(prefix="/api")

_CELL_KEY_RE = re.compile(r"^[A-Za-z0-9]-\d{1,2}-\d{1,2}$")
_ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
_MAX_FILE_SIZE = 10 * 1024 * 1024

_PHOTO_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "static", "photos")


def _validate_cell_key(cell_key: str) -> str:
    """검증 + 정규화. A-01-01 과 A-1-1 을 같은 키(A-1-1)로 통일."""
    if not _CELL_KEY_RE.match(cell_key):
        raise HTTPException(status_code=400, detail="invalid cell_key format")
    zone, row, col = cell_key.split("-")
    return f"{zone}-{int(row)}-{int(col)}"


def _validate_upload_file(file: UploadFile, content: bytes) -> None:
    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in _ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"unsupported file type: {ext}")
    if len(content) > _MAX_FILE_SIZE:
        raise HTTPException(status_code=400, detail="file too large (max 10MB)")
    try:
        Image.open(io.BytesIO(content)).verify()
    except Exception:
        raise HTTPException(status_code=400, detail="invalid image file")


async def _find_or_create_cell_and_level(db, cell_key: str, level_index: int) -> tuple[int, int]:
    parts = cell_key.split("-")
    zone_code, row_num, col_num = parts[0], int(parts[1]), int(parts[2])

    zone_row = await db.execute_fetchall(
        "SELECT id FROM warehouse_zone WHERE code = ?", (zone_code,)
    )
    if not zone_row:
        raise HTTPException(status_code=404, detail="zone not found")
    zone_id = zone_row[0][0]

    await db.execute(
        "INSERT OR IGNORE INTO warehouse_cell (zone_id, row, col) VALUES (?, ?, ?)",
        (zone_id, row_num, col_num),
    )
    cell_row = await db.execute_fetchall(
        "SELECT id FROM warehouse_cell WHERE zone_id = ? AND row = ? AND col = ?",
        (zone_id, row_num, col_num),
    )
    cell_id = cell_row[0][0]

    await db.execute(
        "INSERT OR IGNORE INTO cell_level (cell_id, level_index, label) VALUES (?, ?, ?)",
        (cell_id, level_index, f"{level_index + 1}층"),
    )
    level_row = await db.execute_fetchall(
        "SELECT id FROM cell_level WHERE cell_id = ? AND level_index = ?",
        (cell_id, level_index),
    )
    level_id = level_row[0][0]

    return cell_id, level_id


async def _do_upload_photo_to_table(cell_key: str, level_index: int, file: UploadFile, db) -> str:
    content = await file.read()
    _validate_upload_file(file, content)

    os.makedirs(_PHOTO_DIR, exist_ok=True)

    suffix = uuid.uuid4().hex[:8]
    ext = os.path.splitext(file.filename or "")[1].lower() or ".jpg"
    filename = f"{cell_key.replace('-', '_')}_L{level_index}_{suffix}{ext}"
    filepath = os.path.join(_PHOTO_DIR, filename)

    _cell_id, level_id = await _find_or_create_cell_and_level(db, cell_key, level_index)

    prod_rows = await db.execute_fetchall(
        "SELECT id, photo FROM cell_level_product WHERE level_id = ? ORDER BY sort_order LIMIT 1",
        (level_id,),
    )

    if prod_rows:
        product_id = prod_rows[0][0]
        old_photo = prod_rows[0][1]
        if old_photo:
            old_path = os.path.join(os.path.dirname(__file__), "..", "..", old_photo.lstrip("/"))
            resolved = os.path.realpath(old_path)
            if resolved.startswith(os.path.realpath(_PHOTO_DIR)) and os.path.exists(resolved):
                os.remove(resolved)
        photo_url = f"/static/photos/{filename}"
        await db.execute(
            "UPDATE cell_level_product SET photo = ?, updated_at = datetime('now') WHERE id = ?",
            (photo_url, product_id),
        )
    else:
        photo_url = f"/static/photos/{filename}"
        await db.execute(
            "INSERT INTO cell_level_product (level_id, photo, memo) VALUES (?, ?, '')",
            (level_id, photo_url),
        )

    with open(filepath, "wb") as f:
        f.write(content)

    await db.commit()
    return photo_url


@router.get("/map-layout")
async def get_map_layout():
    db = await get_read_db()
    zones = await db.execute_fetchall("SELECT COUNT(*) FROM warehouse_zone")
    if zones and zones[0][0] > 0:
        return await ws.get_layout_as_json(db)
    from app.services.map_layout_service import get_or_init_layout

    return await get_or_init_layout(db)


@router.post("/map-layout")
async def save_map_layout(request: Request):
    body = await request.json()
    db = await get_db()
    async with _map_layout_lock:
        await ws.save_layout_from_json(db, body)
        await _save_layout_only(db, body)
    return {"status": "ok", "message": "저장 완료"}


@router.post("/map-layout/cell/{cell_key}/photo")
async def upload_cell_photo(cell_key: str, file: UploadFile):
    cell_key = _validate_cell_key(cell_key)
    db = await get_db()
    async with _map_layout_lock:
        photo_url = await _do_upload_photo_to_table(cell_key, 0, file, db)
    return {"status": "ok", "photo_url": photo_url}


@router.delete("/map-layout/cell/{cell_key}/photo")
async def delete_cell_photo(cell_key: str):
    cell_key = _validate_cell_key(cell_key)
    db = await get_db()
    async with _map_layout_lock:
        parts = cell_key.split("-")
        zone_code, row_num, col_num = parts[0], int(parts[1]), int(parts[2])

        cell_row = await db.execute_fetchall(
            "SELECT wc.id FROM warehouse_cell wc "
            "JOIN warehouse_zone wz ON wc.zone_id = wz.id "
            "WHERE wz.code = ? AND wc.row = ? AND wc.col = ?",
            (zone_code, row_num, col_num),
        )
        if not cell_row:
            return {"status": "ok"}
        cell_id = cell_row[0][0]

        prod_rows = await db.execute_fetchall(
            "SELECT clp.id, clp.photo FROM cell_level_product clp "
            "JOIN cell_level cl ON clp.level_id = cl.id "
            "WHERE cl.cell_id = ?",
            (cell_id,),
        )
        for pr in prod_rows:
            photo = pr[1]
            if photo:
                filepath = os.path.join(os.path.dirname(__file__), "..", "..", photo.lstrip("/"))
                resolved = os.path.realpath(filepath)
                if resolved.startswith(os.path.realpath(_PHOTO_DIR)) and os.path.exists(resolved):
                    os.remove(resolved)
            await db.execute(
                "UPDATE cell_level_product SET photo = '', updated_at = datetime('now') WHERE id = ?",
                (pr[0],),
            )
        await db.commit()
    return {"status": "ok"}


@router.post("/map-layout/cell/{cell_key}/level/{level_index}/photo")
async def upload_level_photo(cell_key: str, level_index: int, file: UploadFile):
    cell_key = _validate_cell_key(cell_key)
    if level_index < 0:
        raise HTTPException(status_code=400, detail="level_index must be >= 0")
    db = await get_db()
    async with _map_layout_lock:
        photo_url = await _do_upload_photo_to_table(cell_key, level_index, file, db)
    return {"status": "ok", "photo_url": photo_url}


@router.delete("/map-layout/cell/{cell_key}/level/{level_index}/photo")
async def delete_level_photo(cell_key: str, level_index: int):
    cell_key = _validate_cell_key(cell_key)
    if level_index < 0:
        raise HTTPException(status_code=400, detail="level_index must be >= 0")
    db = await get_db()
    async with _map_layout_lock:
        parts = cell_key.split("-")
        zone_code, row_num, col_num = parts[0], int(parts[1]), int(parts[2])

        level_row = await db.execute_fetchall(
            "SELECT cl.id FROM cell_level cl "
            "JOIN warehouse_cell wc ON cl.cell_id = wc.id "
            "JOIN warehouse_zone wz ON wc.zone_id = wz.id "
            "WHERE wz.code = ? AND wc.row = ? AND wc.col = ? AND cl.level_index = ?",
            (zone_code, row_num, col_num, level_index),
        )
        if not level_row:
            return {"status": "ok"}
        level_id = level_row[0][0]

        prod_rows = await db.execute_fetchall(
            "SELECT id, photo FROM cell_level_product WHERE level_id = ? ORDER BY sort_order LIMIT 1",
            (level_id,),
        )
        if prod_rows:
            photo = prod_rows[0][1]
            if photo:
                filepath = os.path.join(os.path.dirname(__file__), "..", "..", photo.lstrip("/"))
                resolved = os.path.realpath(filepath)
                if resolved.startswith(os.path.realpath(_PHOTO_DIR)) and os.path.exists(resolved):
                    os.remove(resolved)
            await db.execute(
                "UPDATE cell_level_product SET photo = '', updated_at = datetime('now') WHERE id = ?",
                (prod_rows[0][0],),
            )
            await db.commit()
    return {"status": "ok"}


@router.patch("/map-layout/cell/{cell_key}")
async def update_map_cell(cell_key: str, request: Request):
    cell_key = _validate_cell_key(cell_key)
    body = await request.json()
    db = await get_db()
    async with _map_layout_lock:
        parts = cell_key.split("-")
        zone_code, row_num, col_num = parts[0], int(parts[1]), int(parts[2])

        cell_row = await db.execute_fetchall(
            "SELECT wc.id FROM warehouse_cell wc "
            "JOIN warehouse_zone wz ON wc.zone_id = wz.id "
            "WHERE wz.code = ? AND wc.row = ? AND wc.col = ?",
            (zone_code, row_num, col_num),
        )
        if not cell_row:
            # 셀이 DB에 아직 없으면 lazy-create
            # (grid는 zone.rows × zone.cols 전부 렌더링하는데,
            #  DB에는 일부 cell만 미리 삽입된 상태라 클릭 시 404 나던 문제)
            zone_row = await db.execute_fetchall(
                "SELECT id FROM warehouse_zone WHERE code = ?", (zone_code,)
            )
            if not zone_row:
                raise HTTPException(status_code=404, detail=f"zone not found: {zone_code}")
            zone_id = zone_row[0][0]
            await db.execute(
                "INSERT OR IGNORE INTO warehouse_cell (zone_id, row, col, label, status) "
                "VALUES (?, ?, ?, '', 'empty')",
                (zone_id, row_num, col_num),
            )
            cell_row = await db.execute_fetchall(
                "SELECT id FROM warehouse_cell WHERE zone_id = ? AND row = ? AND col = ?",
                (zone_id, row_num, col_num),
            )
            if not cell_row:
                raise HTTPException(status_code=500, detail="cell creation failed")
        cell_id = cell_row[0][0]

        update_kwargs = {}
        if "label" in body:
            update_kwargs["label"] = body["label"]
        if "status" in body:
            update_kwargs["status"] = body["status"]
        if "bgColor" in body:
            update_kwargs["bg_color"] = body["bgColor"]
        if "bg_color" in body:
            update_kwargs["bg_color"] = body["bg_color"]

        if update_kwargs:
            await ws.update_cell(db, cell_id, **update_kwargs)

        if "levels" in body:
            await db.execute("BEGIN IMMEDIATE")
            try:
                await db.execute("DELETE FROM cell_level WHERE cell_id = ?", (cell_id,))
                for lv in body["levels"]:
                    level_index = lv.get("index", 0)
                    label = lv.get("label", "")
                    await db.execute(
                        "INSERT OR IGNORE INTO cell_level (cell_id, level_index, label) VALUES (?, ?, ?)",
                        (cell_id, level_index, label),
                    )
                    level_row = await db.execute_fetchall(
                        "SELECT id FROM cell_level WHERE cell_id = ? AND level_index = ?",
                        (cell_id, level_index),
                    )
                    if not level_row:
                        continue
                    level_id = level_row[0][0]

                    item_label = lv.get("itemLabel", "")
                    photo = lv.get("photo", "")
                    if item_label or photo:
                        await db.execute(
                            "INSERT INTO cell_level_product (level_id, photo, memo) VALUES (?, ?, ?)",
                            (level_id, photo, item_label),
                        )

                await db.commit()
            except Exception:
                await db.execute("ROLLBACK")
                raise

    return {"status": "ok"}
