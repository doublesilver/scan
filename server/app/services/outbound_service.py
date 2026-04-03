import logging

from app.services.history_service import log_action
from app.services.warehouse_service import find_product_location

logger = logging.getLogger(__name__)


async def process_outbound(db, barcode: str, quantity: int = 1) -> dict:
    cursor = await db.execute("SELECT sku_id FROM barcode WHERE barcode = ? LIMIT 1", (barcode,))
    row = await cursor.fetchone()
    if not row:
        return {"status": "error", "message": "등록되지 않은 바코드입니다"}
    sku_id = row["sku_id"]

    cursor = await db.execute("SELECT product_name, location FROM product WHERE sku_id = ? LIMIT 1", (sku_id,))
    prod = await cursor.fetchone()
    product_name = prod["product_name"] if prod else sku_id
    location = prod["location"] if prod else None

    loc_info = await find_product_location(db, sku_id)
    cell_key = None
    zone_code = None
    if loc_info:
        location = loc_info["location"]
        cell_key = f"{loc_info['zone_code']}-{loc_info['row']}-{loc_info['col']}"
        zone_code = loc_info["zone_code"]

    await log_action(db, "outbound", barcode, sku_id, product_name, quantity)

    result = {
        "status": "ok",
        "product_name": product_name,
        "location": location or "",
        "sku_id": sku_id,
    }
    if cell_key:
        result["cell_key"] = cell_key
    if zone_code:
        result["zone_code"] = zone_code
    return result
