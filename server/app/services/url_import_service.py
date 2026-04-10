import logging
from pathlib import Path

import openpyxl

logger = logging.getLogger(__name__)


async def import_purchase_urls(db, file_path: str) -> dict:
    """Excel 파일(sku_id, url 컬럼)을 파싱해 product.purchase_url 일괄 업데이트"""
    path = Path(file_path)
    if not path.exists():
        return {"status": "error", "message": f"파일 없음: {file_path}"}

    wb = openpyxl.load_workbook(path, read_only=True)
    try:
        ws = wb.active

        updated = 0
        skipped = 0

        for row in ws.iter_rows(min_row=2, values_only=True):
            if not row or len(row) < 2:
                continue
            sku_id = str(row[0]).strip() if row[0] else None
            url = str(row[1]).strip() if row[1] else None

            if not sku_id or not url:
                skipped += 1
                continue

            await db.execute(
                "UPDATE product SET purchase_url = ? WHERE sku_id = ?",
                (url, sku_id),
            )
            updated += 1

        await db.commit()
    finally:
        wb.close()

    logger.info("URL 임포트 완료: %d건 업데이트, %d건 스킵", updated, skipped)
    return {
        "status": "ok",
        "message": f"{updated}건 업데이트, {skipped}건 스킵",
        "updated": updated,
        "skipped": skipped,
    }
