from app.models.schemas import FavoriteItem


async def add_favorite(db, sku_id: str, product_name: str, barcode: str | None) -> None:
    await db.execute(
        "INSERT OR REPLACE INTO favorite (sku_id, product_name, barcode) VALUES (?, ?, ?)",
        (sku_id, product_name, barcode),
    )
    await db.commit()


async def remove_favorite(db, sku_id: str) -> bool:
    cursor = await db.execute("DELETE FROM favorite WHERE sku_id = ?", (sku_id,))
    await db.commit()
    return cursor.rowcount > 0


async def get_favorites(db) -> list[FavoriteItem]:
    cursor = await db.execute(
        "SELECT sku_id, product_name, barcode, created_at FROM favorite ORDER BY created_at DESC"
    )
    return [
        FavoriteItem(
            sku_id=r["sku_id"],
            product_name=r["product_name"],
            barcode=r["barcode"],
            created_at=r["created_at"],
        )
        for r in await cursor.fetchall()
    ]
