r"""codepath.xlsx 파서 — 바코드 → 이미지 경로 매핑.

파일 형식: 헤더 없음, 2컬럼 고정
  A: 바코드 (EAN-13)
  B: Windows 절대경로 (Z:\물류부\scan\img\xxx.jpg)
"""

import json
import logging
import re
from pathlib import PurePosixPath, PureWindowsPath

import openpyxl

logger = logging.getLogger(__name__)

# Z:\물류부\scan\ 이후의 상대경로만 추출
_PATH_PREFIX_RE = re.compile(r"^.*?\\scan\\", re.IGNORECASE)


def _to_relative_path(windows_path: str) -> str:
    """Windows 절대경로 → 상대경로 (img/xxx.jpg)."""
    cleaned = _PATH_PREFIX_RE.sub("", windows_path)
    return str(PurePosixPath(PureWindowsPath(cleaned)))


async def parse_codepath(db, file_path: str) -> dict:
    """codepath.xlsx를 파싱하여 BARCODE + IMAGE 테이블에 적재."""
    stats = {"added": 0, "updated": 0, "skipped": 0, "errors": 0, "error_details": []}

    wb = openpyxl.load_workbook(file_path, read_only=True, data_only=True)
    ws = wb.active

    for row_idx, row in enumerate(ws.iter_rows(values_only=True), start=1):
        try:
            barcode = str(row[0]).strip() if row[0] else None
            raw_path = str(row[1]).strip() if len(row) > 1 and row[1] else None

            if not barcode:
                stats["skipped"] += 1
                continue

            # PRODUCT가 없으면 placeholder 생성 (sku_download에서 나중에 갱신)
            sku_id = barcode  # codepath에는 SKU ID가 없으므로 바코드를 임시 키로 사용
            await db.execute(
                "INSERT INTO product (sku_id, product_name) VALUES (?, ?) "
                "ON CONFLICT(sku_id) DO NOTHING",
                (sku_id, ""),
            )

            # BARCODE upsert
            cursor = await db.execute(
                "SELECT id FROM barcode WHERE barcode = ? AND sku_id = ?",
                (barcode, sku_id),
            )
            existing = await cursor.fetchone()
            if existing:
                stats["updated"] += 1
            else:
                await db.execute(
                    "INSERT INTO barcode (barcode, sku_id) VALUES (?, ?) "
                    "ON CONFLICT(barcode, sku_id) DO UPDATE SET updated_at = datetime('now')",
                    (barcode, sku_id),
                )
                stats["added"] += 1

            # IMAGE upsert (경로가 있는 경우만)
            if raw_path and raw_path.lower() != "none":
                relative_path = _to_relative_path(raw_path)
                image_type = "real" if "real_image" in relative_path else "thumbnail"
                await db.execute(
                    "INSERT INTO image (sku_id, file_path, image_type) VALUES (?, ?, ?) "
                    "ON CONFLICT(sku_id, file_path) DO UPDATE SET "
                    "image_type = excluded.image_type, updated_at = datetime('now')",
                    (sku_id, relative_path, image_type),
                )

        except Exception as e:
            stats["errors"] += 1
            stats["error_details"].append(f"Row {row_idx}: {e}")
            if stats["errors"] <= 10:
                logger.warning("codepath row %d 파싱 실패: %s", row_idx, e)

    wb.close()
    await db.commit()

    # parse_log 기록
    await db.execute(
        "INSERT INTO parse_log (file_name, file_type, added_count, updated_count, "
        "skipped_count, error_count, errors) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (
            str(file_path),
            "codepath",
            stats["added"],
            stats["updated"],
            stats["skipped"],
            stats["errors"],
            json.dumps(stats["error_details"][:50], ensure_ascii=False),
        ),
    )
    await db.commit()

    logger.info(
        "codepath 파싱 완료: 추가=%d, 갱신=%d, 스킵=%d, 에러=%d",
        stats["added"], stats["updated"], stats["skipped"], stats["errors"],
    )
    return stats
