import logging

from app.models.schemas import ShelfItem

logger = logging.getLogger(__name__)


async def get_shelves(db, floor: int, zone: str) -> list[ShelfItem]:
    rows = await db.execute_fetchall(
        """
        SELECT s.id, s.floor, s.zone, s.shelf_number, s.label,
               sp.file_path AS photo_path
        FROM shelf s
        LEFT JOIN (
            SELECT shelf_id, file_path
            FROM shelf_photo
            WHERE id IN (
                SELECT id FROM shelf_photo p2
                WHERE p2.shelf_id = shelf_photo.shelf_id
                ORDER BY uploaded_at DESC
                LIMIT 1
            )
        ) sp ON sp.shelf_id = s.id
        WHERE s.floor = ? AND s.zone = ?
        ORDER BY s.shelf_number
        """,
        (floor, zone),
    )
    result = []
    for row in rows:
        photo_path = row[5]
        photo_url = f"/api/image/{photo_path}" if photo_path else None
        result.append(ShelfItem(
            id=row[0],
            floor=row[1],
            zone=row[2],
            shelf_number=row[3],
            label=row[4],
            photo_path=photo_path,
            photo_url=photo_url,
        ))
    return result


async def update_shelf_label(db, shelf_id: int, label: str) -> ShelfItem | None:
    await db.execute(
        "UPDATE shelf SET label = ?, updated_at = datetime('now') WHERE id = ?",
        (label, shelf_id),
    )
    await db.commit()
    return await _get_shelf(db, shelf_id)


async def delete_shelf_label(db, shelf_id: int) -> bool:
    await db.execute(
        "UPDATE shelf SET label = NULL, updated_at = datetime('now') WHERE id = ?",
        (shelf_id,),
    )
    await db.commit()
    row = await db.execute_fetchall("SELECT id FROM shelf WHERE id = ?", (shelf_id,))
    return len(row) > 0


async def add_shelf_photo(db, shelf_id: int, file_path: str) -> int:
    cursor = await db.execute(
        "INSERT INTO shelf_photo (shelf_id, file_path) VALUES (?, ?)",
        (shelf_id, file_path),
    )
    await db.commit()
    return cursor.lastrowid


async def delete_shelf_photo(db, photo_id: int) -> str | None:
    rows = await db.execute_fetchall(
        "SELECT file_path FROM shelf_photo WHERE id = ?",
        (photo_id,),
    )
    if not rows:
        return None
    file_path = rows[0][0]
    await db.execute("DELETE FROM shelf_photo WHERE id = ?", (photo_id,))
    await db.commit()
    return file_path


async def _get_shelf(db, shelf_id: int) -> ShelfItem | None:
    rows = await db.execute_fetchall(
        """
        SELECT s.id, s.floor, s.zone, s.shelf_number, s.label,
               sp.file_path AS photo_path
        FROM shelf s
        LEFT JOIN (
            SELECT shelf_id, file_path
            FROM shelf_photo
            WHERE id IN (
                SELECT id FROM shelf_photo p2
                WHERE p2.shelf_id = shelf_photo.shelf_id
                ORDER BY uploaded_at DESC
                LIMIT 1
            )
        ) sp ON sp.shelf_id = s.id
        WHERE s.id = ?
        """,
        (shelf_id,),
    )
    if not rows:
        return None
    row = rows[0]
    photo_path = row[5]
    photo_url = f"/api/image/{photo_path}" if photo_path else None
    return ShelfItem(
        id=row[0],
        floor=row[1],
        zone=row[2],
        shelf_number=row[3],
        label=row[4],
        photo_path=photo_path,
        photo_url=photo_url,
    )
