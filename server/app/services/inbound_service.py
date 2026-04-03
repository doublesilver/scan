import logging

from app.services.warehouse_service import resolve_product_master, sync_product_location
from app.services.history_service import log_action

logger = logging.getLogger(__name__)


async def process_inbound(db, barcode: str, cell_key: str, level_index: int = 0, quantity: int = 1) -> dict:
    cursor = await db.execute("SELECT sku_id FROM barcode WHERE barcode = ? LIMIT 1", (barcode,))
    row = await cursor.fetchone()
    if not row:
        return {"status": "error", "message": "등록되지 않은 바코드입니다"}
    sku_id = row["sku_id"]

    cursor = await db.execute("SELECT product_name FROM product WHERE sku_id = ? LIMIT 1", (sku_id,))
    prod = await cursor.fetchone()
    product_name = prod["product_name"] if prod else sku_id

    master_id = await resolve_product_master(db, barcode=barcode, sku_id=sku_id)
    if not master_id:
        return {"status": "error", "message": "상품 마스터를 찾을 수 없습니다"}

    parts = cell_key.split("-")
    if len(parts) < 3:
        return {"status": "error", "message": "잘못된 셀 키 형식입니다"}
    zone_code, row_num, col_num = parts[0], int(parts[1]), int(parts[2])

    zone_row = await db.execute_fetchall(
        "SELECT id, cols FROM warehouse_zone WHERE code = ?", (zone_code,)
    )
    if not zone_row:
        return {"status": "error", "message": f"구역 {zone_code}을 찾을 수 없습니다"}
    zone_id = zone_row[0][0]
    zone_cols = zone_row[0][1]

    cell_row = await db.execute_fetchall(
        "SELECT id FROM warehouse_cell WHERE zone_id = ? AND row = ? AND col = ?",
        (zone_id, row_num, col_num),
    )
    if not cell_row:
        return {"status": "error", "message": "셀을 찾을 수 없습니다"}
    cell_id = cell_row[0][0]

    level_row = await db.execute_fetchall(
        "SELECT id FROM cell_level WHERE cell_id = ? AND level_index = ?",
        (cell_id, level_index),
    )
    if not level_row:
        max_idx = await db.execute_fetchall(
            "SELECT COALESCE(MAX(level_index), -1) FROM cell_level WHERE cell_id = ?",
            (cell_id,),
        )
        actual_index = level_index if level_index > max_idx[0][0] else level_index
        cursor = await db.execute(
            "INSERT INTO cell_level (cell_id, level_index, label) VALUES (?, ?, ?)",
            (cell_id, actual_index, f"{actual_index + 1}층"),
        )
        level_id = cursor.lastrowid
    else:
        level_id = level_row[0][0]

    max_order = await db.execute_fetchall(
        "SELECT COALESCE(MAX(sort_order), -1) FROM cell_level_product WHERE level_id = ?",
        (level_id,),
    )
    sort_order = max_order[0][0] + 1

    await db.execute(
        "INSERT INTO cell_level_product (level_id, product_master_id, photo, memo, sort_order) "
        "VALUES (?, ?, '', '', ?)",
        (level_id, master_id, sort_order),
    )

    await sync_product_location(db, master_id, zone_code, row_num, col_num, zone_cols)

    cell_num = (row_num - 1) * zone_cols + col_num
    location = f"{zone_code}구역 {zone_code}-{cell_num}"

    await log_action(db, "inbound", barcode, sku_id, product_name, quantity)

    return {
        "status": "ok",
        "product_name": product_name,
        "location": location,
        "sku_id": sku_id,
    }
