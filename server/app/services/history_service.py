from app.models.schemas import HistoryItem


async def log_action(
    db,
    action_type: str,
    barcode: str,
    sku_id: str,
    product_name: str,
    quantity: int,
    requested_by: str = "PDA",
) -> None:
    await db.execute(
        "INSERT INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (action_type, barcode, sku_id, product_name, quantity, requested_by),
    )
    await db.commit()


async def get_history(db, action_type: str | None = None, limit: int = 50) -> list[HistoryItem]:
    if action_type:
        cursor = await db.execute(
            "SELECT id, action_type, barcode, sku_id, product_name, quantity, requested_by, created_at "
            "FROM action_log WHERE action_type = ? ORDER BY created_at DESC LIMIT ?",
            (action_type, limit),
        )
    else:
        cursor = await db.execute(
            "SELECT id, action_type, barcode, sku_id, product_name, quantity, requested_by, created_at "
            "FROM action_log ORDER BY created_at DESC LIMIT ?",
            (limit,),
        )
    return [
        HistoryItem(
            id=r["id"],
            action_type=r["action_type"],
            barcode=r["barcode"],
            sku_id=r["sku_id"],
            product_name=r["product_name"],
            quantity=r["quantity"],
            requested_by=r["requested_by"],
            created_at=r["created_at"],
        )
        for r in await cursor.fetchall()
    ]
