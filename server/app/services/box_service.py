from app.models.schemas import FamilyMember, BoxResponse


async def get_box(db, qr_code: str) -> BoxResponse | None:
    cursor = await db.execute(
        "SELECT qr_code, box_name, product_master_id FROM outer_box WHERE qr_code = ?",
        (qr_code,),
    )
    box = await cursor.fetchone()
    if not box:
        return None

    cursor = await db.execute(
        "SELECT id, name, image_url FROM product_master WHERE id = ?",
        (box["product_master_id"],),
    )
    master = await cursor.fetchone()
    if not master:
        return None

    cursor = await db.execute(
        "SELECT sku_id, sku_name, barcode, location FROM product_master_sku "
        "WHERE product_master_id = ? ORDER BY sku_name",
        (master["id"],),
    )
    rows = await cursor.fetchall()
    members = [FamilyMember(**dict(r)) for r in rows]

    return BoxResponse(
        qr_code=qr_code,
        box_name=box["box_name"],
        product_master_name=master["name"],
        product_master_image=master["image_url"],
        location=members[0].location if members else None,
        members=members,
    )
