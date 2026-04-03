import logging

logger = logging.getLogger(__name__)


async def check_inventory(db, cell_key: str, scanned_barcodes: list[str]) -> dict:
    parts = cell_key.split("-")
    if len(parts) < 3:
        return {"status": "error", "message": "잘못된 셀 키 형식입니다"}
    zone_code, row_num, col_num = parts[0], int(parts[1]), int(parts[2])

    zone_row = await db.execute_fetchall(
        "SELECT id FROM warehouse_zone WHERE code = ?", (zone_code,)
    )
    if not zone_row:
        return {"status": "error", "message": f"구역 {zone_code}을 찾을 수 없습니다"}
    zone_id = zone_row[0][0]

    cell_row = await db.execute_fetchall(
        "SELECT id FROM warehouse_cell WHERE zone_id = ? AND row = ? AND col = ?",
        (zone_id, row_num, col_num),
    )
    if not cell_row:
        return {"status": "error", "message": "셀을 찾을 수 없습니다"}
    cell_id = cell_row[0][0]

    registered_rows = await db.execute_fetchall(
        "SELECT DISTINCT pms.sku_id, pm.name "
        "FROM cell_level_product clp "
        "JOIN cell_level cl ON clp.level_id = cl.id "
        "JOIN product_master pm ON clp.product_master_id = pm.id "
        "JOIN product_master_sku pms ON pm.id = pms.product_master_id "
        "WHERE cl.cell_id = ?",
        (cell_id,),
    )
    registered = [{"sku_id": r[0], "name": r[1]} for r in registered_rows]
    registered_skus = {r[0] for r in registered_rows}

    scanned_skus = set()
    for bc in scanned_barcodes:
        row = await db.execute_fetchall(
            "SELECT sku_id FROM barcode WHERE barcode = ? LIMIT 1", (bc,)
        )
        if row:
            scanned_skus.add(row[0][0])

    missing = [r for r in registered if r["sku_id"] not in scanned_skus]
    extra_skus = scanned_skus - registered_skus
    extra = []
    for sku in extra_skus:
        name_row = await db.execute_fetchall(
            "SELECT product_name FROM product WHERE sku_id = ? LIMIT 1", (sku,)
        )
        extra.append({"sku_id": sku, "name": name_row[0][0] if name_row else sku})

    return {
        "status": "ok",
        "registered": registered,
        "missing": missing,
        "extra": extra,
        "match_count": len(registered_skus & scanned_skus),
        "total_registered": len(registered_skus),
        "total_scanned": len(scanned_skus),
    }
