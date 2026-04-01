import logging

import aiosqlite

logger = logging.getLogger(__name__)

SEEDS: dict[int, list[str]] = {
    4: [
        "UPDATE product SET coupang_url = 'https://www.coupang.com/vp/products/' || sku_id WHERE sku_id IN (SELECT sku_id FROM product LIMIT 5)",
        "UPDATE product SET location = '1층-A-03' WHERE sku_id IN (SELECT sku_id FROM product ORDER BY sku_id LIMIT 1)",
        "UPDATE product SET location = '2층-B-12' WHERE sku_id IN (SELECT sku_id FROM product ORDER BY sku_id LIMIT 1 OFFSET 1)",
        "UPDATE product SET location = '3층-C-07' WHERE sku_id IN (SELECT sku_id FROM product ORDER BY sku_id LIMIT 1 OFFSET 2)",
        "INSERT OR IGNORE INTO outer_box (qr_code, box_name) VALUES ('BOX-001', 'A구역 1번 박스')",
        "INSERT OR IGNORE INTO outer_box (qr_code, box_name) VALUES ('BOX-002', 'B구역 3번 박스')",
        "INSERT OR IGNORE INTO outer_box (qr_code, box_name) VALUES ('BOX-003', 'C구역 7번 박스')",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-001', '8801234567890', 10)",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-001', '8809876543210', 5)",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-002', '8801111111111', 20)",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-002', '8802222222222', 8)",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-003', '8803333333333', 15)",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('print', '8801234567890', 'SKU-001', '샘플 상품 A', 1, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('cart', '8809876543210', 'SKU-002', '샘플 상품 B', 3, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('print', '8801111111111', 'SKU-003', '샘플 상품 C', 2, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('cart', '8802222222222', 'SKU-004', '샘플 상품 D', 5, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('print', '8803333333333', 'SKU-005', '샘플 상품 E', 1, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('cart', '8801234567890', 'SKU-001', '샘플 상품 A', 2, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('print', '8809876543210', 'SKU-002', '샘플 상품 B', 1, 'PDA')",
        "INSERT OR IGNORE INTO favorite (sku_id, product_name, barcode) VALUES ('SKU-001', '샘플 상품 A', '8801234567890')",
        "INSERT OR IGNORE INTO favorite (sku_id, product_name, barcode) VALUES ('SKU-002', '샘플 상품 B', '8809876543210')",
        "INSERT OR IGNORE INTO favorite (sku_id, product_name, barcode) VALUES ('SKU-003', '샘플 상품 C', '8801111111111')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8801234567890', 'SKU-001', '샘플 상품 A')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8809876543210', 'SKU-002', '샘플 상품 B')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8801111111111', 'SKU-003', '샘플 상품 C')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8802222222222', 'SKU-004', '샘플 상품 D')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8803333333333', 'SKU-005', '샘플 상품 E')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8801234567890', 'SKU-001', '샘플 상품 A')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8809876543210', 'SKU-002', '샘플 상품 B')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8801111111111', 'SKU-003', '샘플 상품 C')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8803333333333', 'SKU-005', '샘플 상품 E')",
        "INSERT OR IGNORE INTO scan_log (barcode, sku_id, product_name) VALUES ('8802222222222', 'SKU-004', '샘플 상품 D')",
    ],
    5: [
        "INSERT INTO product_master (id, name, image_url) VALUES (1, '쵸미세븐 무당벌레 청소기', 'https://img3.coupangcdn.com/image/product/image/vendoritem/2016/07/07/3013301785/ca384b18-4c33-4c9f-b10c-1e3d2b79089e.jpg')",
        "INSERT INTO product_master_sku (product_master_id, sku_id, barcode, sku_name, location) VALUES (1, '6947290001', '8809461170008', '무당벌레 청소기 레드 105x70mm', '5층-A-03')",
        "INSERT INTO product_master_sku (product_master_id, sku_id, barcode, sku_name, location) VALUES (1, '6947290002', '8809461170015', '무당벌레 청소기 그린 105x70mm', '5층-B-07')",
        "INSERT INTO product_master_sku (product_master_id, sku_id, barcode, sku_name, location) VALUES (1, '1863997', '8809461170022', '무당벌레 청소기 블루 105x70mm', '5층-C-12')",
        "INSERT INTO product_master_sku (product_master_id, sku_id, barcode, sku_name, location) VALUES (1, '1863998', '8809461170039', '무당벌레 청소기 옐로우 105x70mm', '5층-D-05')",
        "UPDATE outer_box SET product_master_id = 1 WHERE qr_code = 'BOX-2026-0001'",
        "INSERT OR IGNORE INTO outer_box (qr_code, box_name, product_master_id) VALUES ('BOX-2026-0001', '무당벌레 청소기 박스', 1)",
    ],
    7: [
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 1, 'A-01 스트랩 보관')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 2, 'A-02 케이스 보관')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 3, 'A-03 부품 보관')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 4, 'A-04 포장재 보관')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 5, 'A-05 기타')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'B', 1, 'B-01 완제품 A')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'B', 2, 'B-02 완제품 B')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'B', 3, 'B-03 반품')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'C', 1, 'C-01 소형 상품')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'C', 2, 'C-02 중형 상품')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'C', 3, 'C-03 대형 상품')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'C', 4, 'C-04 특대형')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'D', 1, 'D-01 입고 대기')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'D', 2, 'D-02 출고 대기')",
    ],
}


async def run_seeds(db: aiosqlite.Connection) -> None:
    for version in sorted(SEEDS.keys()):
        logger.info("시드 데이터 실행: v%d", version)
        for sql in SEEDS[version]:
            try:
                await db.execute(sql)
            except Exception as e:
                logger.warning("시드 실행 실패 (무시): %s — %s", sql[:60], e)
        await db.commit()
