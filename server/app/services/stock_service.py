from app.models.schemas import StockLogItem, StockResponse, StockUpdate


async def get_stock(db, sku_id: str) -> StockResponse | None:
    cursor = await db.execute(
        "SELECT sku_id FROM product WHERE sku_id = ?", (sku_id,)
    )
    if not await cursor.fetchone():
        return None

    cursor = await db.execute(
        "SELECT quantity, memo, updated_by, updated_at FROM stock WHERE sku_id = ?",
        (sku_id,),
    )
    row = await cursor.fetchone()
    if row:
        return StockResponse(
            sku_id=sku_id,
            quantity=row["quantity"],
            memo=row["memo"],
            updated_by=row["updated_by"],
            updated_at=row["updated_at"],
        )
    return StockResponse(
        sku_id=sku_id, quantity=0, memo="", updated_by="", updated_at=""
    )


async def update_stock(db, sku_id: str, data: StockUpdate) -> StockResponse | None:
    cursor = await db.execute(
        "SELECT sku_id FROM product WHERE sku_id = ?", (sku_id,)
    )
    if not await cursor.fetchone():
        return None

    await db.execute("BEGIN IMMEDIATE")

    try:
        cursor = await db.execute(
            "SELECT quantity FROM stock WHERE sku_id = ?", (sku_id,)
        )
        row = await cursor.fetchone()
        before_qty = row["quantity"] if row else 0

        await db.execute(
            "INSERT INTO stock (sku_id, quantity, memo, updated_by, updated_at) "
            "VALUES (?, ?, ?, ?, datetime('now')) "
            "ON CONFLICT(sku_id) DO UPDATE SET "
            "quantity=excluded.quantity, memo=excluded.memo, "
            "updated_by=excluded.updated_by, updated_at=excluded.updated_at",
            (sku_id, data.quantity, data.memo, data.updated_by),
        )

        await db.execute(
            "INSERT INTO stock_log (sku_id, before_qty, after_qty, memo, updated_by) "
            "VALUES (?, ?, ?, ?, ?)",
            (sku_id, before_qty, data.quantity, data.memo, data.updated_by),
        )

        await db.commit()
    except Exception:
        await db.execute("ROLLBACK")
        raise

    return await get_stock(db, sku_id)


async def get_stock_log(db, sku_id: str, limit: int = 20) -> list[StockLogItem]:
    cursor = await db.execute(
        "SELECT before_qty, after_qty, memo, updated_by, created_at "
        "FROM stock_log WHERE sku_id = ? ORDER BY id DESC LIMIT ?",
        (sku_id, limit),
    )
    return [
        StockLogItem(
            before_qty=r["before_qty"],
            after_qty=r["after_qty"],
            memo=r["memo"],
            updated_by=r["updated_by"],
            created_at=r["created_at"],
        )
        for r in await cursor.fetchall()
    ]
