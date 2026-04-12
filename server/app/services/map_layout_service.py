import json
import logging

logger = logging.getLogger(__name__)

DEFAULT_LAYOUT = {
    "title": "5층 창고 도면",
    "floor": 5,
    "zones": [
        {"code": "A", "name": "501호", "rows": 3, "cols": 4},
        {"code": "B", "name": "포장다이", "rows": 3, "cols": 2},
        {"code": "C", "name": "502호", "rows": 3, "cols": 3},
    ],
    "cells": {},
}


async def get_layout(db) -> dict | None:
    cursor = await db.execute("SELECT data FROM map_layout WHERE id = 1")
    row = await cursor.fetchone()
    if row:
        return json.loads(row[0])
    return None


def _normalize_cell_keys(layout: dict) -> bool:
    """A-01-01 → A-1-1 형식으로 통일. 변경 있으면 True 반환."""
    cells = layout.get("cells", {})
    if not cells:
        return False
    changed = False
    for old_key in list(cells.keys()):
        parts = old_key.split("-")
        if len(parts) != 3:
            continue
        try:
            zone, r, c = parts[0], parts[1], parts[2]
            if r.startswith("0") or c.startswith("0"):
                new_key = f"{zone}-{int(r)}-{int(c)}"
                if new_key != old_key:
                    if new_key in cells:
                        cells[new_key] = {**cells[old_key], **cells[new_key]}
                    else:
                        cells[new_key] = cells[old_key]
                    del cells[old_key]
                    changed = True
        except (ValueError, IndexError):
            continue
    return changed


async def get_or_init_layout(db) -> dict:
    layout = await get_layout(db)
    if layout is None or not layout.get("zones"):
        await save_layout_only(db, DEFAULT_LAYOUT)
        return DEFAULT_LAYOUT
    if _normalize_cell_keys(layout):
        await save_layout_only(db, layout)
    return layout


async def save_layout_only(db, layout: dict) -> None:
    data_json = json.dumps(layout, ensure_ascii=False)
    await db.execute(
        "INSERT INTO map_layout (id, data, updated_at) VALUES (1, ?, datetime('now')) "
        "ON CONFLICT(id) DO UPDATE SET data = ?, updated_at = datetime('now')",
        (data_json, data_json),
    )
    await db.commit()
