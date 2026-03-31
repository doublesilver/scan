from app.models.schemas import RecentScanItem


async def log_scan(db, barcode: str, sku_id: str, product_name: str) -> None:
    await db.execute(
        "INSERT INTO scan_log (barcode, sku_id, product_name) VALUES (?, ?, ?)",
        (barcode, sku_id, product_name),
    )
    await db.commit()


async def get_recent_scans(db, limit: int = 20) -> list[RecentScanItem]:
    cursor = await db.execute(
        "SELECT id, barcode, sku_id, product_name, scanned_at "
        "FROM scan_log ORDER BY id DESC LIMIT ?",
        (limit,),
    )
    return [
        RecentScanItem(
            id=r["id"],
            barcode=r["barcode"],
            sku_id=r["sku_id"],
            product_name=r["product_name"],
            scanned_at=r["scanned_at"],
        )
        for r in await cursor.fetchall()
    ]
