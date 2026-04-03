import logging

logger = logging.getLogger(__name__)


async def get_zones(db) -> list[dict]:
    rows = await db.execute_fetchall(
        "SELECT z.id, z.code, z.name, z.rows, z.cols, z.sort_order, "
        "(SELECT COUNT(*) FROM warehouse_cell WHERE zone_id = z.id) as cell_count "
        "FROM warehouse_zone z ORDER BY z.sort_order"
    )
    return [
        {
            "id": r[0],
            "code": r[1],
            "name": r[2],
            "rows": r[3],
            "cols": r[4],
            "sort_order": r[5],
            "cell_count": r[6],
        }
        for r in rows
    ]


async def create_zone(db, code: str, name: str, rows: int, cols: int) -> dict:
    max_order = await db.execute_fetchall(
        "SELECT COALESCE(MAX(sort_order), -1) FROM warehouse_zone"
    )
    sort_order = max_order[0][0] + 1

    cursor = await db.execute(
        "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES (?, ?, ?, ?, ?)",
        (code, name, rows, cols, sort_order),
    )
    zone_id = cursor.lastrowid

    for r in range(1, rows + 1):
        for c in range(1, cols + 1):
            await db.execute(
                "INSERT OR IGNORE INTO warehouse_cell (zone_id, row, col) VALUES (?, ?, ?)",
                (zone_id, r, c),
            )

    await db.commit()
    return {"id": zone_id, "code": code, "name": name, "rows": rows, "cols": cols, "sort_order": sort_order}


async def update_zone(db, zone_id: int, **kwargs) -> dict | None:
    zone = await db.execute_fetchall(
        "SELECT id, code, name, rows, cols, sort_order FROM warehouse_zone WHERE id = ?",
        (zone_id,),
    )
    if not zone:
        return None

    old_rows, old_cols = zone[0][3], zone[0][4]
    sets = []
    params = []
    for key in ("code", "name", "rows", "cols", "sort_order"):
        if key in kwargs:
            sets.append(f"{key} = ?")
            params.append(kwargs[key])

    if sets:
        sets.append("updated_at = datetime('now')")
        params.append(zone_id)
        await db.execute(
            f"UPDATE warehouse_zone SET {', '.join(sets)} WHERE id = ?", params
        )

    new_rows = kwargs.get("rows", old_rows)
    new_cols = kwargs.get("cols", old_cols)

    if new_rows != old_rows or new_cols != old_cols:
        if new_rows < old_rows:
            await db.execute(
                "DELETE FROM warehouse_cell WHERE zone_id = ? AND row > ?",
                (zone_id, new_rows),
            )
        if new_cols < old_cols:
            await db.execute(
                "DELETE FROM warehouse_cell WHERE zone_id = ? AND col > ?",
                (zone_id, new_cols),
            )
        for r in range(1, new_rows + 1):
            for c in range(1, new_cols + 1):
                await db.execute(
                    "INSERT OR IGNORE INTO warehouse_cell (zone_id, row, col) VALUES (?, ?, ?)",
                    (zone_id, r, c),
                )

    await db.commit()

    updated = await db.execute_fetchall(
        "SELECT id, code, name, rows, cols, sort_order FROM warehouse_zone WHERE id = ?",
        (zone_id,),
    )
    r = updated[0]
    return {"id": r[0], "code": r[1], "name": r[2], "rows": r[3], "cols": r[4], "sort_order": r[5]}


async def delete_zone(db, zone_id: int) -> bool:
    cursor = await db.execute("DELETE FROM warehouse_zone WHERE id = ?", (zone_id,))
    await db.commit()
    return cursor.rowcount > 0


async def get_zone_cells(db, zone_id: int) -> list[dict]:
    cell_rows = await db.execute_fetchall(
        "SELECT id, row, col, label, status, bg_color FROM warehouse_cell "
        "WHERE zone_id = ? ORDER BY row, col",
        (zone_id,),
    )

    result = []
    for cr in cell_rows:
        cell_id, row, col, label, status, bg_color = cr
        levels = await _get_cell_levels(db, cell_id)
        result.append({
            "id": cell_id,
            "row": row,
            "col": col,
            "label": label,
            "status": status,
            "bg_color": bg_color,
            "levels": levels,
        })
    return result


async def get_cell_detail(db, cell_id: int) -> dict | None:
    cr = await db.execute_fetchall(
        "SELECT wc.id, wc.row, wc.col, wc.label, wc.status, wc.bg_color, "
        "wz.id, wz.code, wz.name "
        "FROM warehouse_cell wc JOIN warehouse_zone wz ON wc.zone_id = wz.id "
        "WHERE wc.id = ?",
        (cell_id,),
    )
    if not cr:
        return None
    r = cr[0]
    levels = await _get_cell_levels(db, cell_id)
    return {
        "id": r[0],
        "row": r[1],
        "col": r[2],
        "label": r[3],
        "status": r[4],
        "bg_color": r[5],
        "zone": {"id": r[6], "code": r[7], "name": r[8]},
        "levels": levels,
    }


async def _get_cell_levels(db, cell_id: int) -> list[dict]:
    level_rows = await db.execute_fetchall(
        "SELECT id, level_index, label FROM cell_level WHERE cell_id = ? ORDER BY level_index",
        (cell_id,),
    )
    levels = []
    for lr in level_rows:
        level_id, level_index, level_label = lr
        prod_rows = await db.execute_fetchall(
            "SELECT clp.id, clp.product_master_id, clp.photo, clp.memo, clp.sort_order, "
            "pm.name as master_name "
            "FROM cell_level_product clp "
            "LEFT JOIN product_master pm ON clp.product_master_id = pm.id "
            "WHERE clp.level_id = ? ORDER BY clp.sort_order",
            (level_id,),
        )
        products = [
            {
                "id": pr[0],
                "product_master_id": pr[1],
                "photo": pr[2],
                "memo": pr[3],
                "sort_order": pr[4],
                "master_name": pr[5] or "",
            }
            for pr in prod_rows
        ]
        levels.append({
            "id": level_id,
            "index": level_index,
            "label": level_label,
            "products": products,
        })
    return levels


async def update_cell(db, cell_id: int, **kwargs) -> dict | None:
    existing = await db.execute_fetchall(
        "SELECT id FROM warehouse_cell WHERE id = ?", (cell_id,)
    )
    if not existing:
        return None

    sets = []
    params = []
    for key in ("label", "status", "bg_color"):
        if key in kwargs:
            sets.append(f"{key} = ?")
            params.append(kwargs[key])

    if sets:
        sets.append("updated_at = datetime('now')")
        params.append(cell_id)
        await db.execute(
            f"UPDATE warehouse_cell SET {', '.join(sets)} WHERE id = ?", params
        )
        await db.commit()

    return await get_cell_detail(db, cell_id)


async def add_level(db, cell_id: int, label: str = "") -> dict | None:
    existing = await db.execute_fetchall(
        "SELECT id FROM warehouse_cell WHERE id = ?", (cell_id,)
    )
    if not existing:
        return None

    max_idx = await db.execute_fetchall(
        "SELECT COALESCE(MAX(level_index), -1) FROM cell_level WHERE cell_id = ?",
        (cell_id,),
    )
    next_index = max_idx[0][0] + 1

    cursor = await db.execute(
        "INSERT INTO cell_level (cell_id, level_index, label) VALUES (?, ?, ?)",
        (cell_id, next_index, label),
    )
    await db.commit()
    return {"id": cursor.lastrowid, "index": next_index, "label": label, "products": []}


async def delete_level(db, level_id: int) -> bool:
    cursor = await db.execute("DELETE FROM cell_level WHERE id = ?", (level_id,))
    await db.commit()
    return cursor.rowcount > 0


async def add_level_product(
    db, level_id: int, product_master_id: int | None = None, photo: str = "", memo: str = ""
) -> dict | None:
    existing = await db.execute_fetchall(
        "SELECT id FROM cell_level WHERE id = ?", (level_id,)
    )
    if not existing:
        return None

    max_order = await db.execute_fetchall(
        "SELECT COALESCE(MAX(sort_order), -1) FROM cell_level_product WHERE level_id = ?",
        (level_id,),
    )
    sort_order = max_order[0][0] + 1

    cursor = await db.execute(
        "INSERT INTO cell_level_product (level_id, product_master_id, photo, memo, sort_order) "
        "VALUES (?, ?, ?, ?, ?)",
        (level_id, product_master_id, photo, memo, sort_order),
    )
    await db.commit()

    master_name = ""
    if product_master_id:
        pm = await db.execute_fetchall(
            "SELECT name FROM product_master WHERE id = ?", (product_master_id,)
        )
        if pm:
            master_name = pm[0][0]

    return {
        "id": cursor.lastrowid,
        "product_master_id": product_master_id,
        "photo": photo,
        "memo": memo,
        "sort_order": sort_order,
        "master_name": master_name,
    }


async def remove_level_product(db, product_id: int) -> tuple[bool, int | None]:
    row = await db.execute_fetchall(
        "SELECT product_master_id FROM cell_level_product WHERE id = ?", (product_id,)
    )
    master_id = row[0][0] if row else None

    cursor = await db.execute("DELETE FROM cell_level_product WHERE id = ?", (product_id,))
    await db.commit()
    return cursor.rowcount > 0, master_id


async def clear_product_location(db, product_master_id: int):
    still_placed = await db.execute_fetchall(
        "SELECT 1 FROM cell_level_product WHERE product_master_id = ? LIMIT 1",
        (product_master_id,),
    )
    if still_placed:
        return

    await db.execute(
        "UPDATE product SET location = NULL WHERE sku_id IN "
        "(SELECT sku_id FROM product_master_sku WHERE product_master_id = ?)",
        (product_master_id,),
    )
    await db.commit()


async def update_level_product_photo(db, product_id: int, photo: str) -> dict | None:
    existing = await db.execute_fetchall(
        "SELECT id FROM cell_level_product WHERE id = ?", (product_id,)
    )
    if not existing:
        return None

    await db.execute(
        "UPDATE cell_level_product SET photo = ?, updated_at = datetime('now') WHERE id = ?",
        (photo, product_id),
    )
    await db.commit()
    return {"id": product_id, "photo": photo}


async def sync_product_location(db, product_master_id: int, zone_code: str, cell_row: int, cell_col: int, zone_cols: int):
    cell_num = (cell_row - 1) * zone_cols + cell_col
    location = f"{zone_code}구역 {zone_code}-{cell_num}"
    await db.execute(
        "UPDATE product SET location = ? WHERE sku_id IN "
        "(SELECT sku_id FROM product_master_sku WHERE product_master_id = ?)",
        (location, product_master_id),
    )
    await db.commit()


async def find_product_location(db, sku_id: str) -> dict | None:
    rows = await db.execute_fetchall(
        "SELECT wz.code, wz.name, wc.row, wc.col, wc.id as cell_id, wz.cols "
        "FROM cell_level_product clp "
        "JOIN cell_level cl ON clp.level_id = cl.id "
        "JOIN warehouse_cell wc ON cl.cell_id = wc.id "
        "JOIN warehouse_zone wz ON wc.zone_id = wz.id "
        "JOIN product_master_sku pms ON clp.product_master_id = pms.product_master_id "
        "WHERE pms.sku_id = ? LIMIT 1",
        (sku_id,),
    )
    if not rows:
        return None
    r = rows[0]
    cell_num = (r[2] - 1) * r[5] + r[3]
    return {
        "zone_code": r[0],
        "zone_name": r[1],
        "row": r[2],
        "col": r[3],
        "cell_id": r[4],
        "location": f"{r[0]}구역 {r[0]}-{cell_num}",
    }


async def resolve_product_master(db, barcode: str = "", sku_id: str = "", product_master_id: int | None = None) -> int | None:
    if product_master_id:
        row = await db.execute_fetchall(
            "SELECT id FROM product_master WHERE id = ?", (product_master_id,)
        )
        return row[0][0] if row else None

    target_sku = sku_id
    if barcode and not target_sku:
        br = await db.execute_fetchall(
            "SELECT sku_id FROM barcode WHERE barcode = ? LIMIT 1", (barcode,)
        )
        if br:
            target_sku = br[0][0]

    if target_sku:
        pm = await db.execute_fetchall(
            "SELECT product_master_id FROM product_master_sku WHERE sku_id = ? LIMIT 1",
            (target_sku,),
        )
        if pm:
            return pm[0][0]

        pr = await db.execute_fetchall(
            "SELECT product_name FROM product WHERE sku_id = ? LIMIT 1", (target_sku,)
        )
        product_name = pr[0][0] if pr else target_sku

        cursor = await db.execute(
            "INSERT INTO product_master (name) VALUES (?)", (product_name,)
        )
        master_id = cursor.lastrowid
        await db.execute(
            "INSERT OR IGNORE INTO product_master_sku (product_master_id, sku_id, sku_name) VALUES (?, ?, ?)",
            (master_id, target_sku, product_name),
        )
        await db.commit()
        return master_id

    return None


async def get_layout_as_json(db) -> dict:
    zones_rows = await db.execute_fetchall(
        "SELECT id, code, name, rows, cols FROM warehouse_zone ORDER BY sort_order"
    )

    if not zones_rows:
        return {
            "title": "창고 도면",
            "floor": 5,
            "zones": [],
            "cells": {},
        }

    zones = []
    cells = {}

    for zr in zones_rows:
        zone_id, code, name, rows, cols = zr
        zones.append({"code": code, "name": name, "rows": rows, "cols": cols})

        cell_rows = await db.execute_fetchall(
            "SELECT id, row, col, label, status, bg_color FROM warehouse_cell "
            "WHERE zone_id = ? ORDER BY row, col",
            (zone_id,),
        )

        for cr in cell_rows:
            cell_id, row, col, label, status, bg_color = cr
            cell_key = f"{code}-{row}-{col}"

            level_rows = await db.execute_fetchall(
                "SELECT id, level_index, label FROM cell_level "
                "WHERE cell_id = ? ORDER BY level_index",
                (cell_id,),
            )

            levels = []
            for lr in level_rows:
                level_id, level_index, level_label = lr

                prod_rows = await db.execute_fetchall(
                    "SELECT clp.photo, clp.memo, pm.name "
                    "FROM cell_level_product clp "
                    "LEFT JOIN product_master pm ON clp.product_master_id = pm.id "
                    "WHERE clp.level_id = ? ORDER BY clp.sort_order",
                    (level_id,),
                )

                first_prod = prod_rows[0] if prod_rows else (None, None, None)
                levels.append({
                    "index": level_index,
                    "label": level_label,
                    "photo": first_prod[0] or "",
                    "itemLabel": first_prod[2] or first_prod[1] or "",
                    "sku": "",
                })

            cells[cell_key] = {
                "label": label,
                "status": status,
                "bgColor": bg_color,
                "levels": levels,
            }

    return {
        "title": "창고 도면",
        "floor": 5,
        "zones": zones,
        "cells": cells,
    }


async def save_layout_from_json(db, layout: dict):
    await db.execute("DELETE FROM warehouse_zone")

    zones = layout.get("zones", [])
    for i, z in enumerate(zones):
        await db.execute(
            "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES (?, ?, ?, ?, ?)",
            (z["code"], z["name"], z["rows"], z["cols"], i),
        )

    cells = layout.get("cells", {})
    for cell_key, cell_data in cells.items():
        parts = cell_key.split("-")
        if len(parts) < 3:
            continue
        zone_code, row_num, col_num = parts[0], int(parts[1]), int(parts[2])

        zone_row = await db.execute_fetchall(
            "SELECT id FROM warehouse_zone WHERE code = ?", (zone_code,)
        )
        if not zone_row:
            continue
        zone_id = zone_row[0][0]

        await db.execute(
            "INSERT OR IGNORE INTO warehouse_cell (zone_id, row, col, label, status, bg_color) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (
                zone_id,
                row_num,
                col_num,
                cell_data.get("label", ""),
                cell_data.get("status", "empty"),
                cell_data.get("bgColor", ""),
            ),
        )

        cell_row = await db.execute_fetchall(
            "SELECT id FROM warehouse_cell WHERE zone_id = ? AND row = ? AND col = ?",
            (zone_id, row_num, col_num),
        )
        if not cell_row:
            continue
        cell_id = cell_row[0][0]

        levels = cell_data.get("levels", [])
        for lv in levels:
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
