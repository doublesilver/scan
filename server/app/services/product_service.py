import logging

from app.config import settings
from app.models.schemas import ImageItem, ScanResponse, SearchItem

logger = logging.getLogger(__name__)


def _strip_brands(name: str) -> str:
    for brand in settings.brand_filter:
        name = name.replace(brand, "").strip()
    return name


async def scan_barcode(db, barcode: str) -> ScanResponse | None:
    cursor = await db.execute(
        "SELECT sku_id FROM barcode WHERE barcode = ?", (barcode,)
    )
    row = await cursor.fetchone()
    if not row:
        return None

    sku_id = row["sku_id"]

    if sku_id:
        cursor = await db.execute(
            "SELECT sku_id, product_name, category, brand FROM product WHERE sku_id = ?",
            (sku_id,),
        )
        product = await cursor.fetchone()

        cursor = await db.execute(
            "SELECT DISTINCT barcode FROM barcode WHERE sku_id = ?", (sku_id,)
        )
        barcodes = [r["barcode"] for r in await cursor.fetchall()]
    else:
        product = None
        barcodes = [barcode]

    if sku_id:
        cursor = await db.execute(
            "SELECT DISTINCT i.file_path, i.image_type, i.sort_order "
            "FROM image i JOIN barcode b ON b.barcode = i.barcode "
            "WHERE b.sku_id = ? ORDER BY i.sort_order, i.id",
            (sku_id,),
        )
    else:
        cursor = await db.execute(
            "SELECT file_path, image_type, sort_order FROM image "
            "WHERE barcode = ? ORDER BY sort_order, id",
            (barcode,),
        )
    images = [
        ImageItem(file_path=r["file_path"], image_type=r["image_type"])
        for r in await cursor.fetchall()
    ]

    if sku_id and not product:
        return None

    product_name = _strip_brands(product["product_name"] if product else "")

    quantity = None
    if sku_id:
        cursor = await db.execute(
            "SELECT quantity FROM stock WHERE sku_id = ?", (sku_id,)
        )
        stock_row = await cursor.fetchone()
        if stock_row:
            quantity = stock_row["quantity"]

    return ScanResponse(
        sku_id=product["sku_id"] if product else "",
        product_name=product_name,
        category=product["category"] if product else "",
        brand=product["brand"] if product else "",
        barcodes=barcodes,
        images=images,
        quantity=quantity,
    )


async def search_products(db, query: str, limit: int) -> list[SearchItem]:
    try:
        safe_query = '"' + query.replace('"', '""') + '"'
        cursor = await db.execute(
            "SELECT p.sku_id, p.product_name, p.category, p.brand, "
            "(SELECT b.barcode FROM barcode b WHERE b.sku_id = p.sku_id LIMIT 1) as first_barcode "
            "FROM product_fts f JOIN product p ON f.sku_id = p.sku_id "
            "WHERE product_fts MATCH ? LIMIT ?",
            (safe_query, limit),
        )
        rows = await cursor.fetchall()
    except Exception as e:
        logger.warning("FTS5 검색 실패, LIKE 폴백: %s", e)
        pattern = f"%{query}%"
        cursor = await db.execute(
            "SELECT sku_id, product_name, category, brand, "
            "(SELECT b.barcode FROM barcode b WHERE b.sku_id = product.sku_id LIMIT 1) as first_barcode "
            "FROM product "
            "WHERE product_name LIKE ? OR sku_id LIKE ? LIMIT ?",
            (pattern, pattern, limit),
        )
        rows = await cursor.fetchall()

    return [
        SearchItem(
            sku_id=r["sku_id"],
            product_name=_strip_brands(r["product_name"]),
            category=r["category"],
            brand=r["brand"],
            barcode=r["first_barcode"],
        )
        for r in rows
    ]
