async def log_print_attempt(
    db,
    *,
    barcode: str,
    sku_id: str,
    product_name: str,
    quantity: int,
    status: str,
    via: str,
    http_status: int | None,
    elapsed_ms: int | None,
    message: str,
    raw_response: str,
) -> None:
    await db.execute(
        "INSERT INTO print_log (barcode, sku_id, product_name, quantity, status, via, http_status, elapsed_ms, message, raw_response) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            barcode,
            sku_id,
            product_name,
            quantity,
            status,
            via,
            http_status,
            elapsed_ms,
            message,
            raw_response[:4000],
        ),
    )
    await db.commit()
