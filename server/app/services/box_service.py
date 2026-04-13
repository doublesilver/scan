from app.models.schemas import BoxResponse, FamilyMember, ProductMasterImage


async def _get_master_images(db, master_id: int) -> tuple[list[ProductMasterImage], list[ProductMasterImage]]:
    cursor = await db.execute(
        "SELECT id, file_path, image_type, sort_order FROM product_master_image "
        "WHERE product_master_id = ? ORDER BY sort_order, id",
        (master_id,),
    )
    rows = await cursor.fetchall()
    option_images = []
    sourcing_images = []
    for r in rows:
        img = ProductMasterImage(**dict(r))
        if r["image_type"] == "option":
            option_images.append(img)
        else:
            sourcing_images.append(img)
    return option_images, sourcing_images


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

    if master:
        cursor = await db.execute(
            "SELECT sku_id, sku_name, barcode, location FROM product_master_sku "
            "WHERE product_master_id = ? ORDER BY sku_name",
            (master["id"],),
        )
        rows = await cursor.fetchall()
        members = [FamilyMember(**dict(r)) for r in rows]
        option_images, sourcing_images = await _get_master_images(db, master["id"])
        return BoxResponse(
            qr_code=qr_code,
            box_name=box["box_name"],
            product_master_id=master["id"],
            product_master_name=master["name"],
            product_master_image=master["image_url"],
            location=members[0].location if members else None,
            members=members,
            option_images=option_images,
            sourcing_images=sourcing_images,
        )

    return BoxResponse(
        qr_code=qr_code,
        box_name=box["box_name"],
        product_master_name="",
        product_master_image=None,
        location=None,
        members=[],
    )


async def create_box(
    db,
    qr_code: str,
    box_name: str,
    product_master_name: str,
    location: str | None,
    members: list[dict],
) -> BoxResponse:
    cursor = await db.execute(
        "SELECT id FROM product_master WHERE name = ?",
        (product_master_name,),
    )
    row = await cursor.fetchone()
    if row:
        master_id = row["id"]
    else:
        cursor = await db.execute(
            "INSERT INTO product_master (name) VALUES (?)",
            (product_master_name,),
        )
        master_id = cursor.lastrowid

    await db.execute(
        "INSERT INTO outer_box (qr_code, box_name, product_master_id) VALUES (?, ?, ?)",
        (qr_code, box_name, master_id),
    )

    for m in members:
        await db.execute(
            "INSERT OR IGNORE INTO product_master_sku (product_master_id, sku_id, sku_name, barcode, location) VALUES (?, ?, ?, ?, ?)",
            (
                master_id,
                m["sku_id"],
                m.get("sku_name"),
                m.get("barcode"),
                m.get("location") or location,
            ),
        )

    await db.commit()
    return await get_box(db, qr_code)


async def update_box(
    db, qr_code: str, box_name: str | None, product_master_name: str | None, location: str | None
) -> BoxResponse | None:
    cursor = await db.execute(
        "SELECT qr_code, box_name, product_master_id FROM outer_box WHERE qr_code = ?",
        (qr_code,),
    )
    box = await cursor.fetchone()
    if not box:
        return None

    if box_name:
        await db.execute(
            "UPDATE outer_box SET box_name = ? WHERE qr_code = ?",
            (box_name, qr_code),
        )

    if product_master_name:
        cursor = await db.execute(
            "SELECT id FROM product_master WHERE id = ?",
            (box["product_master_id"],),
        )
        if await cursor.fetchone():
            await db.execute(
                "UPDATE product_master SET name = ? WHERE id = ?",
                (product_master_name, box["product_master_id"]),
            )

    if location is not None:
        await db.execute(
            "UPDATE product_master_sku SET location = ? WHERE product_master_id = ?",
            (location, box["product_master_id"]),
        )

    await db.commit()
    return await get_box(db, qr_code)


async def add_member(
    db, qr_code: str, sku_id: str, sku_name: str, barcode: str | None, location: str | None
) -> bool:
    cursor = await db.execute(
        "SELECT product_master_id FROM outer_box WHERE qr_code = ?",
        (qr_code,),
    )
    box = await cursor.fetchone()
    if not box:
        return False

    await db.execute(
        "INSERT OR IGNORE INTO product_master_sku (product_master_id, sku_id, sku_name, barcode, location) VALUES (?, ?, ?, ?, ?)",
        (box["product_master_id"], sku_id, sku_name, barcode, location),
    )
    await db.commit()
    return True


async def remove_member(db, qr_code: str, sku_id: str) -> bool:
    cursor = await db.execute(
        "SELECT product_master_id FROM outer_box WHERE qr_code = ?",
        (qr_code,),
    )
    box = await cursor.fetchone()
    if not box:
        return False

    result = await db.execute(
        "DELETE FROM product_master_sku WHERE product_master_id = ? AND sku_id = ?",
        (box["product_master_id"], sku_id),
    )
    await db.commit()
    return result.rowcount > 0
