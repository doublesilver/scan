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
        cursor = await db.execute(
            "SELECT sku_id FROM product_master_sku WHERE barcode = ? AND sku_id IS NOT NULL",
            (barcode,),
        )
        row = await cursor.fetchone()
        if not row:
            return None

    sku_id = row["sku_id"]

    if not sku_id:
        cursor = await db.execute(
            "SELECT file_path, image_type, sort_order FROM image "
            "WHERE barcode = ? ORDER BY sort_order, id",
            (barcode,),
        )
        images = [
            ImageItem(file_path=r["file_path"], image_type=r["image_type"])
            for r in await cursor.fetchall()
        ]
        return ScanResponse(
            sku_id="", product_name="", category="", brand="",
            barcodes=[barcode], images=images, quantity=None,
        )

    cursor = await db.execute(
        "SELECT p.sku_id, p.product_name, p.category, p.brand, "
        "p.purchase_url, p.location, "
        "s.quantity, "
        "GROUP_CONCAT(DISTINCT b2.barcode) as all_barcodes "
        "FROM product p "
        "LEFT JOIN stock s ON s.sku_id = p.sku_id "
        "LEFT JOIN barcode b2 ON b2.sku_id = p.sku_id "
        "WHERE p.sku_id = ? "
        "GROUP BY p.sku_id",
        (sku_id,),
    )
    product = await cursor.fetchone()
    if not product:
        return None

    pm_id = None
    pm_name = None
    pm_location = None
    cursor = await db.execute(
        "SELECT pms.product_master_id, pms.location as sku_location, pm.name "
        "FROM product_master_sku pms "
        "JOIN product_master pm ON pm.id = pms.product_master_id "
        "WHERE pms.sku_id = ?",
        (sku_id,),
    )
    pm_row = await cursor.fetchone()
    if pm_row:
        pm_id = pm_row["product_master_id"]
        pm_name = pm_row["name"]
        cursor = await db.execute(
            "SELECT wz.code || '-' || wc.label as cell_location "
            "FROM cell_level_product clp "
            "JOIN cell_level cl ON cl.id = clp.level_id "
            "JOIN warehouse_cell wc ON wc.id = cl.cell_id "
            "JOIN warehouse_zone wz ON wz.id = wc.zone_id "
            "WHERE clp.product_master_id = ? "
            "LIMIT 1",
            (pm_id,),
        )
        loc_row = await cursor.fetchone()
        pm_location = loc_row["cell_location"] if loc_row else pm_row["sku_location"]

    cursor = await db.execute(
        "SELECT DISTINCT i.file_path, i.image_type "
        "FROM image i JOIN barcode b ON b.barcode = i.barcode "
        "WHERE b.sku_id = ? ORDER BY i.sort_order, i.id",
        (sku_id,),
    )
    images = [
        ImageItem(file_path=r["file_path"], image_type=r["image_type"])
        for r in await cursor.fetchall()
    ]

    barcodes = product["all_barcodes"].split(",") if product["all_barcodes"] else [barcode]

    return ScanResponse(
        sku_id=product["sku_id"],
        product_name=_strip_brands(product["product_name"]),
        category=product["category"],
        brand=product["brand"],
        barcodes=barcodes,
        images=images,
        quantity=product["quantity"],
        coupang_url=product["purchase_url"],
        location=product["location"],
        product_master_id=pm_id,
        product_master_name=pm_name,
        product_master_location=pm_location,
    )


async def search_products(db, query: str, limit: int) -> list[SearchItem]:
    # 숫자만 입력된 경우 바코드 부분 검색 우선
    if query.isdigit():
        pattern = f"%{query}%"
        cursor = await db.execute(
            "SELECT p.sku_id, p.product_name, p.category, p.brand, "
            "b.barcode as first_barcode, "
            "(SELECT i.file_path FROM image i WHERE i.barcode = b.barcode LIMIT 1) as thumbnail "
            "FROM barcode b JOIN product p ON b.sku_id = p.sku_id "
            "WHERE b.barcode LIKE ? LIMIT ?",
            (pattern, limit),
        )
        rows = await cursor.fetchall()
        if rows:
            return [
                SearchItem(
                    sku_id=r["sku_id"],
                    product_name=_strip_brands(r["product_name"]),
                    category=r["category"],
                    brand=r["brand"],
                    barcode=r["first_barcode"],
                    thumbnail=r["thumbnail"],
                )
                for r in rows
            ]

    rows = []
    try:
        safe_query = '"' + query.replace('"', '""') + '"'
        cursor = await db.execute(
            "SELECT p.sku_id, p.product_name, p.category, p.brand, "
            "(SELECT b.barcode FROM barcode b WHERE b.sku_id = p.sku_id LIMIT 1) as first_barcode, "
            "(SELECT i.file_path FROM image i WHERE i.barcode = "
            "(SELECT b2.barcode FROM barcode b2 WHERE b2.sku_id = p.sku_id LIMIT 1) LIMIT 1) as thumbnail "
            "FROM product_fts f JOIN product p ON f.sku_id = p.sku_id "
            "WHERE product_fts MATCH ? LIMIT ?",
            (safe_query, limit),
        )
        rows = await cursor.fetchall()
    except Exception as e:
        logger.warning("FTS5 검색 실패, LIKE 폴백: %s", e)

    if not rows:
        pattern = f"%{query}%"
        cursor = await db.execute(
            "SELECT sku_id, product_name, category, brand, "
            "(SELECT b.barcode FROM barcode b WHERE b.sku_id = product.sku_id LIMIT 1) as first_barcode, "
            "(SELECT i.file_path FROM image i WHERE i.barcode = "
            "(SELECT b2.barcode FROM barcode b2 WHERE b2.sku_id = product.sku_id LIMIT 1) LIMIT 1) as thumbnail "
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
            thumbnail=r["thumbnail"],
        )
        for r in rows
    ]
