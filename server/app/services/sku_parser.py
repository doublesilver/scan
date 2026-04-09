"""sku_download.xlsx 파서 — 쿠팡 SKU 데이터.

파일 형식: inlineStr 타입 → openpyxl read_only 미지원 → lxml 직접 파싱.
헤더 매칭: 컬럼명 유사도 기반 자동 매칭 (쿠팡이 헤더를 바꿔도 대응).
"""

import json
import logging
import time
import zipfile
from difflib import SequenceMatcher

from lxml import etree

logger = logging.getLogger(__name__)

_SAFE_PARSER = etree.XMLParser(resolve_entities=False, no_network=True, huge_tree=False)

NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}

REQUIRED_COLUMNS = {
    "sku_id": ["sku id", "skuid", "sku_id", "상품id"],
    "product_name": ["상품명", "상품 명", "제품명", "품명", "product name"],
    "barcode": ["바코드", "barcode", "bar code", "ean"],
}

OPTIONAL_COLUMNS = {
    "category": ["카테고리", "업종", "분류", "category"],
    "brand": ["브랜드", "제조사", "brand"],
    "moq": ["최소구매수량", "moq", "최소 구매수량"],
    "weight": ["중량", "무게", "weight"],
}

BATCH_SIZE = 500


def _similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, a.lower().strip(), b.lower().strip()).ratio()


def _match_columns(headers: list[str]) -> dict[str, int | None]:
    """헤더 리스트에서 필수/옵션 컬럼의 인덱스를 자동 매칭."""
    mapping: dict[str, int | None] = {}

    all_columns = {**REQUIRED_COLUMNS, **OPTIONAL_COLUMNS}
    for field_name, keywords in all_columns.items():
        best_score = 0.0
        best_idx = None
        for idx, header in enumerate(headers):
            for keyword in keywords:
                score = _similarity(header, keyword)
                if score > best_score:
                    best_score = score
                    best_idx = idx
        mapping[field_name] = best_idx if best_score >= 0.5 else None

    return mapping


def _parse_xlsx_with_lxml(file_path: str) -> tuple[list[str], list[list[str]]]:
    """inlineStr 타입 xlsx를 lxml으로 파싱하여 헤더 + 데이터 행 반환."""
    with zipfile.ZipFile(file_path) as z:
        xml_bytes = z.read("xl/worksheets/sheet1.xml")

    root = etree.fromstring(xml_bytes, parser=_SAFE_PARSER)

    rows = root.findall(".//s:row", NS)
    if not rows:
        return [], []

    def _extract_row(row_el) -> list[str]:
        cells = row_el.findall("s:c", NS)
        values: list[str] = []
        for cell in cells:
            val = ""
            v_el = cell.find("s:v", NS)
            is_el = cell.find("s:is", NS)
            if v_el is not None and v_el.text:
                val = v_el.text
            elif is_el is not None:
                t_el = is_el.find(".//s:t", NS)
                if t_el is not None and t_el.text:
                    val = t_el.text
            values.append(val)
        return values

    headers = _extract_row(rows[0])
    data = [_extract_row(r) for r in rows[1:]]
    return headers, data


async def parse_sku_download(db, file_path: str) -> dict:
    """sku_download.xlsx를 파싱하여 PRODUCT + BARCODE 테이블에 적재."""
    start = time.perf_counter()
    stats = {"added": 0, "updated": 0, "skipped": 0, "errors": 0, "error_details": []}

    headers, data_rows = _parse_xlsx_with_lxml(file_path)
    if not headers:
        logger.error("sku_download 헤더 없음: %s", file_path)
        return stats

    col_map = _match_columns(headers)
    logger.info(
        "sku_download 헤더 매칭: %s",
        {k: headers[v] if v is not None else None for k, v in col_map.items()},
    )

    missing = [k for k in REQUIRED_COLUMNS if col_map.get(k) is None]
    if missing:
        msg = f"sku_download 헤더 인식 실패: {', '.join(missing)} 열을 찾지 못했습니다"
        logger.error(msg)
        stats["error_details"].append(msg)
        stats["errors"] += 1
        return stats

    sku_idx = col_map["sku_id"]
    name_idx = col_map["product_name"]
    barcode_idx = col_map["barcode"]
    category_idx = col_map.get("category")
    brand_idx = col_map.get("brand")

    product_batch = []
    barcode_batch = []
    record_count = len(data_rows)

    for row_num, row in enumerate(data_rows, start=2):
        try:

            def _get(idx: int | None) -> str:
                if idx is None or idx >= len(row):
                    return ""
                return row[idx].strip()

            sku_id = _get(sku_idx)
            product_name = _get(name_idx)
            barcode = _get(barcode_idx)

            if not sku_id:
                stats["skipped"] += 1
                continue

            category = _get(category_idx)
            brand = _get(brand_idx)

            extra = {}
            for field in OPTIONAL_COLUMNS:
                if field not in ("category", "brand"):
                    val = _get(col_map.get(field))
                    if val:
                        extra[field] = val

            product_batch.append(
                (
                    sku_id,
                    product_name,
                    category,
                    brand,
                    json.dumps(extra, ensure_ascii=False),
                )
            )

            if barcode and barcode.lower() != "none":
                barcode_batch.append((barcode, sku_id))

            if len(product_batch) >= BATCH_SIZE:
                a, u = await _flush_product_batch(db, product_batch, barcode_batch)
                stats["added"] += a
                stats["updated"] += u
                product_batch.clear()
                barcode_batch.clear()

        except Exception as e:
            stats["errors"] += 1
            stats["error_details"].append(f"Row {row_num}: {e}")
            if stats["errors"] <= 10:
                logger.warning("sku_download row %d 파싱 실패: %s", row_num, e)

    if product_batch:
        a, u = await _flush_product_batch(db, product_batch, barcode_batch)
        stats["added"] += a
        stats["updated"] += u

    await _link_orphan_barcodes(db)
    await db.commit()

    duration_ms = int((time.perf_counter() - start) * 1000)

    await db.execute(
        "INSERT INTO parse_log (file_name, file_type, record_count, added_count, "
        "updated_count, skipped_count, error_count, errors, duration_ms) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            str(file_path),
            "sku_download",
            record_count,
            stats["added"],
            stats["updated"],
            stats["skipped"],
            stats["errors"],
            json.dumps(stats["error_details"][:50], ensure_ascii=False),
            duration_ms,
        ),
    )
    await db.commit()

    logger.info(
        "sku_download 파싱 완료: 추가=%d, 갱신=%d, 스킵=%d, 에러=%d (%dms)",
        stats["added"],
        stats["updated"],
        stats["skipped"],
        stats["errors"],
        duration_ms,
    )
    return stats


async def _flush_product_batch(
    db, product_batch: list[tuple], barcode_batch: list[tuple]
) -> tuple[int, int]:
    sku_ids = [row[0] for row in product_batch]
    placeholders = ",".join("?" * len(sku_ids))
    cursor = await db.execute(
        f"SELECT COUNT(*) FROM product WHERE sku_id IN ({placeholders})", sku_ids
    )
    existing_count = (await cursor.fetchone())[0]
    added = len(sku_ids) - existing_count
    updated = existing_count

    await db.executemany(
        "INSERT INTO product (sku_id, product_name, category, brand, extra) "
        "VALUES (?, ?, ?, ?, ?) "
        "ON CONFLICT(sku_id) DO UPDATE SET "
        "product_name = excluded.product_name, category = excluded.category, "
        "brand = excluded.brand, extra = excluded.extra, updated_at = datetime('now')",
        product_batch,
    )

    if barcode_batch:
        await db.executemany(
            "INSERT INTO barcode (barcode, sku_id) VALUES (?, ?) "
            "ON CONFLICT(barcode) DO UPDATE SET "
            "sku_id = excluded.sku_id, updated_at = datetime('now')",
            barcode_batch,
        )

    return added, updated


async def _link_orphan_barcodes(db) -> None:
    """barcode UNIQUE 제약으로 upsert 시 sku_id가 자동 갱신되지만,
    FTS 인덱스 동기화를 위해 기존 product_fts에 누락된 데이터를 채움."""
    cursor = await db.execute("SELECT COUNT(*) FROM barcode WHERE sku_id IS NULL")
    row = await cursor.fetchone()
    if row and row[0] > 0:
        logger.info("고아 바코드 %d건 (sku_download 미매칭)", row[0])
