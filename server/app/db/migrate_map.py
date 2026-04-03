import json
import logging

logger = logging.getLogger(__name__)


async def migrate_json_to_tables(db):
    row = await db.execute_fetchall("SELECT data FROM map_layout WHERE id = 1")
    if not row or not row[0][0]:
        return

    layout = json.loads(row[0][0])

    existing = await db.execute_fetchall("SELECT COUNT(*) FROM warehouse_zone")
    if existing and existing[0][0] > 0:
        return

    zones = layout.get("zones", [])
    if not zones:
        return

    logger.info("map_layout JSON → 정규화 테이블 마이그레이션 시작")

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
            "INSERT OR IGNORE INTO warehouse_cell (zone_id, row, col, label, status, bg_color) VALUES (?, ?, ?, ?, ?, ?)",
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
            sku = lv.get("sku", "")
            photo = lv.get("photo", "")

            if item_label or sku or photo:
                master_id = None
                if sku:
                    pm_row = await db.execute_fetchall(
                        "SELECT pm.id FROM product_master pm "
                        "JOIN product_master_sku pms ON pm.id = pms.product_master_id "
                        "WHERE pms.sku_id = ? LIMIT 1",
                        (sku,),
                    )
                    if pm_row:
                        master_id = pm_row[0][0]

                await db.execute(
                    "INSERT INTO cell_level_product (level_id, product_master_id, photo, memo) VALUES (?, ?, ?, ?)",
                    (level_id, master_id, photo, item_label),
                )

    await db.commit()
    logger.info("map_layout JSON → 정규화 테이블 마이그레이션 완료")
