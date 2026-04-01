import asyncio
import os
import re
import uuid

from fastapi import APIRouter, HTTPException, Request, UploadFile

from app.db.database import get_db, get_read_db
from app.services.map_layout_service import get_or_init_layout as _get_or_init_layout
from app.services.map_layout_service import save_layout_only as _save_layout_only

router = APIRouter(prefix="/api")

_map_layout_lock = asyncio.Lock()

_CELL_KEY_RE = re.compile(r"^[A-Za-z0-9]-\d{1,2}-\d{1,2}$")
_ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
_MAX_FILE_SIZE = 10 * 1024 * 1024

_PHOTO_DIR = os.path.join(os.path.dirname(__file__), '..', '..', 'static', 'photos')


def _validate_cell_key(cell_key: str) -> None:
    if not _CELL_KEY_RE.match(cell_key):
        raise HTTPException(status_code=400, detail="invalid cell_key format")


def _validate_upload_file(file: UploadFile, content: bytes) -> None:
    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in _ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"unsupported file type: {ext}")
    if len(content) > _MAX_FILE_SIZE:
        raise HTTPException(status_code=400, detail="file too large (max 10MB)")


async def _do_upload_photo(cell_key: str, level_index: int, file: UploadFile, layout: dict) -> str:
    content = await file.read()
    _validate_upload_file(file, content)

    os.makedirs(_PHOTO_DIR, exist_ok=True)

    suffix = uuid.uuid4().hex[:8]
    ext = os.path.splitext(file.filename or "")[1].lower() or ".jpg"
    filename = f"{cell_key.replace('-', '_')}_L{level_index}_{suffix}{ext}"
    filepath = os.path.join(_PHOTO_DIR, filename)

    if "cells" not in layout:
        layout["cells"] = {}
    cell = layout["cells"].get(cell_key, {})
    levels = cell.get("levels", [])

    while len(levels) <= level_index:
        levels.append({"label": f"{len(levels)+1}층", "photo": "", "itemLabel": "", "sku": ""})

    old_photo = levels[level_index].get("photo", "")
    if old_photo:
        old_filepath = os.path.join(os.path.dirname(__file__), '..', '..', old_photo.lstrip('/'))
        if os.path.exists(old_filepath):
            os.remove(old_filepath)

    with open(filepath, 'wb') as f:
        f.write(content)

    photo_url = f"/static/photos/{filename}"
    levels[level_index]["photo"] = photo_url
    cell["levels"] = levels
    layout["cells"][cell_key] = cell

    return photo_url


@router.get("/map-layout")
async def get_map_layout():
    db = await get_read_db()
    return await _get_or_init_layout(db)


@router.post("/map-layout")
async def save_map_layout(request: Request):
    body = await request.json()
    db = await get_db()
    async with _map_layout_lock:
        await _save_layout_only(db, body)
    return {"status": "ok", "message": "저장 완료"}


@router.post("/map-layout/cell/{cell_key}/photo")
async def upload_cell_photo(cell_key: str, file: UploadFile):
    _validate_cell_key(cell_key)
    db = await get_db()
    async with _map_layout_lock:
        layout = await _get_or_init_layout(db)
        photo_url = await _do_upload_photo(cell_key, 0, file, layout)
        await _save_layout_only(db, layout)
    return {"status": "ok", "photo_url": photo_url}


@router.delete("/map-layout/cell/{cell_key}/photo")
async def delete_cell_photo(cell_key: str):
    _validate_cell_key(cell_key)
    db = await get_db()
    async with _map_layout_lock:
        layout = await _get_or_init_layout(db)
        cell = layout.get("cells", {}).get(cell_key)
        if cell is None:
            return {"status": "ok"}

        levels = cell.get("levels", [])
        for level in levels:
            photo = level.get("photo", "")
            if photo:
                filepath = os.path.join(os.path.dirname(__file__), '..', '..', photo.lstrip('/'))
                if os.path.exists(filepath):
                    os.remove(filepath)
                level["photo"] = ""

        cell["levels"] = levels
        layout["cells"][cell_key] = cell
        await _save_layout_only(db, layout)
    return {"status": "ok"}


@router.post("/map-layout/cell/{cell_key}/level/{level_index}/photo")
async def upload_level_photo(cell_key: str, level_index: int, file: UploadFile):
    _validate_cell_key(cell_key)
    if level_index < 0:
        raise HTTPException(status_code=400, detail="level_index must be >= 0")
    db = await get_db()
    async with _map_layout_lock:
        layout = await _get_or_init_layout(db)
        photo_url = await _do_upload_photo(cell_key, level_index, file, layout)
        await _save_layout_only(db, layout)
    return {"status": "ok", "photo_url": photo_url}


@router.delete("/map-layout/cell/{cell_key}/level/{level_index}/photo")
async def delete_level_photo(cell_key: str, level_index: int):
    _validate_cell_key(cell_key)
    if level_index < 0:
        raise HTTPException(status_code=400, detail="level_index must be >= 0")
    db = await get_db()
    async with _map_layout_lock:
        layout = await _get_or_init_layout(db)
        cell = layout.get("cells", {}).get(cell_key)
        if cell is None:
            return {"status": "ok"}

        levels = cell.get("levels", [])
        if level_index < len(levels):
            photo = levels[level_index].get("photo", "")
            if photo:
                filepath = os.path.join(os.path.dirname(__file__), '..', '..', photo.lstrip('/'))
                if os.path.exists(filepath):
                    os.remove(filepath)
                levels[level_index]["photo"] = ""

        cell["levels"] = levels
        layout["cells"][cell_key] = cell
        await _save_layout_only(db, layout)
    return {"status": "ok"}


@router.patch("/map-layout/cell/{cell_key}")
async def update_map_cell(cell_key: str, request: Request):
    _validate_cell_key(cell_key)
    body = await request.json()
    db = await get_db()
    async with _map_layout_lock:
        layout = await _get_or_init_layout(db)
        if "cells" not in layout:
            layout["cells"] = {}
        layout["cells"][cell_key] = {**layout["cells"].get(cell_key, {}), **body}
        await _save_layout_only(db, layout)
    return {"status": "ok"}
