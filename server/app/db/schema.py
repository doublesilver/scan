SCHEMA_VERSION = 8

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS db_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS product (
    sku_id          TEXT PRIMARY KEY,
    product_name    TEXT NOT NULL DEFAULT '',
    category        TEXT NOT NULL DEFAULT '',
    brand           TEXT NOT NULL DEFAULT '',
    extra           TEXT NOT NULL DEFAULT '{}',
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS barcode (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    barcode         TEXT NOT NULL UNIQUE,
    sku_id          TEXT,
    barcode_type    TEXT NOT NULL DEFAULT 'EAN-13',
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (sku_id) REFERENCES product(sku_id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_barcode_barcode ON barcode(barcode);
CREATE INDEX IF NOT EXISTS idx_barcode_sku_id  ON barcode(sku_id);

CREATE TABLE IF NOT EXISTS image (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    barcode         TEXT NOT NULL,
    file_path       TEXT NOT NULL,
    image_type      TEXT NOT NULL DEFAULT 'thumbnail',
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (barcode, file_path)
);
CREATE INDEX IF NOT EXISTS idx_image_barcode ON image(barcode);

CREATE TABLE IF NOT EXISTS parse_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    file_name       TEXT NOT NULL,
    file_type       TEXT NOT NULL,
    record_count    INTEGER NOT NULL DEFAULT 0,
    added_count     INTEGER NOT NULL DEFAULT 0,
    updated_count   INTEGER NOT NULL DEFAULT 0,
    skipped_count   INTEGER NOT NULL DEFAULT 0,
    error_count     INTEGER NOT NULL DEFAULT 0,
    errors          TEXT NOT NULL DEFAULT '[]',
    duration_ms     INTEGER NOT NULL DEFAULT 0,
    parsed_at       TEXT NOT NULL DEFAULT (datetime('now'))
);
"""

MIGRATIONS = {
    2: [
        "CREATE VIRTUAL TABLE IF NOT EXISTS product_fts USING fts5(sku_id, product_name, category, brand, content='product', content_rowid='rowid')",
        """CREATE TRIGGER IF NOT EXISTS product_fts_insert AFTER INSERT ON product BEGIN
            INSERT INTO product_fts(rowid, sku_id, product_name, category, brand)
            VALUES (new.rowid, new.sku_id, new.product_name, new.category, new.brand);
        END""",
        """CREATE TRIGGER IF NOT EXISTS product_fts_update AFTER UPDATE ON product BEGIN
            INSERT INTO product_fts(product_fts, rowid, sku_id, product_name, category, brand)
            VALUES ('delete', old.rowid, old.sku_id, old.product_name, old.category, old.brand);
            INSERT INTO product_fts(rowid, sku_id, product_name, category, brand)
            VALUES (new.rowid, new.sku_id, new.product_name, new.category, new.brand);
        END""",
        """CREATE TRIGGER IF NOT EXISTS product_fts_delete AFTER DELETE ON product BEGIN
            INSERT INTO product_fts(product_fts, rowid, sku_id, product_name, category, brand)
            VALUES ('delete', old.rowid, old.sku_id, old.product_name, old.category, old.brand);
        END""",
    ],
    3: [
        """CREATE TABLE IF NOT EXISTS stock (
            sku_id TEXT PRIMARY KEY,
            quantity INTEGER NOT NULL DEFAULT 0,
            memo TEXT NOT NULL DEFAULT '',
            updated_by TEXT NOT NULL DEFAULT '',
            updated_at TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY (sku_id) REFERENCES product(sku_id) ON DELETE CASCADE
        )""",
        """CREATE TABLE IF NOT EXISTS stock_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sku_id TEXT NOT NULL,
            before_qty INTEGER NOT NULL,
            after_qty INTEGER NOT NULL,
            memo TEXT NOT NULL DEFAULT '',
            updated_by TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY (sku_id) REFERENCES product(sku_id) ON DELETE CASCADE
        )""",
        "CREATE INDEX IF NOT EXISTS idx_stock_log_sku ON stock_log(sku_id)",
        "CREATE INDEX IF NOT EXISTS idx_stock_log_date ON stock_log(created_at)",
    ],
    5: [
        """CREATE TABLE IF NOT EXISTS product_master (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            image_url TEXT,
            created_at TEXT DEFAULT (datetime('now'))
        )""",
        """CREATE TABLE IF NOT EXISTS product_master_sku (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            product_master_id INTEGER NOT NULL REFERENCES product_master(id),
            sku_id TEXT NOT NULL,
            barcode TEXT,
            sku_name TEXT,
            location TEXT,
            UNIQUE(sku_id)
        )""",
        "CREATE INDEX IF NOT EXISTS idx_pms_master ON product_master_sku(product_master_id)",
        "CREATE INDEX IF NOT EXISTS idx_pms_sku ON product_master_sku(sku_id)",
        "ALTER TABLE outer_box ADD COLUMN product_master_id INTEGER REFERENCES product_master(id)",
        # Seed: product_master
        "INSERT INTO product_master (id, name, image_url) VALUES (1, '쵸미세븐 무당벌레 청소기', 'https://img3.coupangcdn.com/image/product/image/vendoritem/2016/07/07/3013301785/ca384b18-4c33-4c9f-b10c-1e3d2b79089e.jpg')",
        # Seed: product_master_sku
        "INSERT INTO product_master_sku (product_master_id, sku_id, barcode, sku_name, location) VALUES (1, '6947290001', '8809461170008', '무당벌레 청소기 레드 105x70mm', '5층-A-03')",
        "INSERT INTO product_master_sku (product_master_id, sku_id, barcode, sku_name, location) VALUES (1, '6947290002', '8809461170015', '무당벌레 청소기 그린 105x70mm', '5층-B-07')",
        "INSERT INTO product_master_sku (product_master_id, sku_id, barcode, sku_name, location) VALUES (1, '1863997', '8809461170022', '무당벌레 청소기 블루 105x70mm', '5층-C-12')",
        "INSERT INTO product_master_sku (product_master_id, sku_id, barcode, sku_name, location) VALUES (1, '1863998', '8809461170039', '무당벌레 청소기 옐로우 105x70mm', '5층-D-05')",
        # Seed: link outer_box to product_master
        "UPDATE outer_box SET product_master_id = 1 WHERE qr_code = 'BOX-2026-0001'",
        # Seed: add BOX-2026-0001 if not exists
        "INSERT OR IGNORE INTO outer_box (qr_code, box_name, product_master_id) VALUES ('BOX-2026-0001', '무당벌레 청소기 박스', 1)",
    ],
    6: [
        # purchase_url 컬럼 추가 후 coupang_url 데이터 복사
        "ALTER TABLE product ADD COLUMN purchase_url TEXT DEFAULT NULL",
        "UPDATE product SET purchase_url = coupang_url WHERE coupang_url IS NOT NULL",
    ],
    7: [
        """CREATE TABLE IF NOT EXISTS shelf (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            floor INTEGER NOT NULL,
            zone TEXT NOT NULL,
            shelf_number INTEGER NOT NULL,
            label TEXT,
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now'))
        )""",
        "CREATE INDEX IF NOT EXISTS idx_shelf_floor_zone ON shelf(floor, zone)",
        """CREATE TABLE IF NOT EXISTS shelf_photo (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            shelf_id INTEGER NOT NULL REFERENCES shelf(id) ON DELETE CASCADE,
            file_path TEXT NOT NULL,
            uploaded_at TEXT DEFAULT (datetime('now'))
        )""",
        "CREATE INDEX IF NOT EXISTS idx_shelf_photo_shelf ON shelf_photo(shelf_id)",
        # Seed: 5층 A구역 5개 선반
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 1, 'A-01 스트랩 보관')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 2, 'A-02 케이스 보관')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 3, 'A-03 부품 보관')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 4, 'A-04 포장재 보관')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'A', 5, 'A-05 기타')",
        # Seed: 5층 B구역 3개 선반
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'B', 1, 'B-01 완제품 A')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'B', 2, 'B-02 완제품 B')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'B', 3, 'B-03 반품')",
        # Seed: 5층 C구역 4개 선반
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'C', 1, 'C-01 소형 상품')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'C', 2, 'C-02 중형 상품')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'C', 3, 'C-03 대형 상품')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'C', 4, 'C-04 특대형')",
        # Seed: 5층 D구역 2개 선반
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'D', 1, 'D-01 입고 대기')",
        "INSERT INTO shelf (floor, zone, shelf_number, label) VALUES (5, 'D', 2, 'D-02 출고 대기')",
    ],
    8: [
        """CREATE TABLE IF NOT EXISTS map_layout (
            id INTEGER PRIMARY KEY DEFAULT 1,
            data TEXT NOT NULL,
            updated_at TEXT DEFAULT (datetime('now'))
        )""",
    ],
    4: [
        "ALTER TABLE product ADD COLUMN coupang_url TEXT DEFAULT NULL",
        "ALTER TABLE product ADD COLUMN location TEXT DEFAULT NULL",
        """CREATE TABLE IF NOT EXISTS outer_box (
            qr_code TEXT PRIMARY KEY,
            box_name TEXT NOT NULL,
            created_at TEXT DEFAULT (datetime('now'))
        )""",
        """CREATE TABLE IF NOT EXISTS outer_box_item (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            qr_code TEXT NOT NULL REFERENCES outer_box(qr_code),
            barcode TEXT NOT NULL,
            quantity INTEGER DEFAULT 1
        )""",
        """CREATE TABLE IF NOT EXISTS action_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            action_type TEXT NOT NULL,
            barcode TEXT NOT NULL,
            sku_id TEXT NOT NULL,
            product_name TEXT NOT NULL,
            quantity INTEGER NOT NULL,
            requested_by TEXT DEFAULT 'PDA',
            created_at TEXT DEFAULT (datetime('now'))
        )""",
        "CREATE INDEX IF NOT EXISTS idx_action_log_type ON action_log(action_type)",
        "CREATE INDEX IF NOT EXISTS idx_action_log_date ON action_log(created_at)",
        """CREATE TABLE IF NOT EXISTS favorite (
            sku_id TEXT PRIMARY KEY,
            product_name TEXT NOT NULL,
            barcode TEXT,
            created_at TEXT DEFAULT (datetime('now'))
        )""",
        """CREATE TABLE IF NOT EXISTS scan_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            barcode TEXT NOT NULL,
            sku_id TEXT NOT NULL,
            product_name TEXT NOT NULL,
            scanned_at TEXT DEFAULT (datetime('now'))
        )""",
        "CREATE INDEX IF NOT EXISTS idx_scan_log_date ON scan_log(scanned_at)",
        # Seed: coupang_url and location
        "UPDATE product SET coupang_url = 'https://www.coupang.com/vp/products/' || sku_id WHERE sku_id IN (SELECT sku_id FROM product LIMIT 5)",
        "UPDATE product SET location = '1층-A-03' WHERE sku_id IN (SELECT sku_id FROM product ORDER BY sku_id LIMIT 1)",
        "UPDATE product SET location = '2층-B-12' WHERE sku_id IN (SELECT sku_id FROM product ORDER BY sku_id LIMIT 1 OFFSET 1)",
        "UPDATE product SET location = '3층-C-07' WHERE sku_id IN (SELECT sku_id FROM product ORDER BY sku_id LIMIT 1 OFFSET 2)",
        # Seed: outer_box
        "INSERT OR IGNORE INTO outer_box (qr_code, box_name) VALUES ('BOX-001', 'A구역 1번 박스')",
        "INSERT OR IGNORE INTO outer_box (qr_code, box_name) VALUES ('BOX-002', 'B구역 3번 박스')",
        "INSERT OR IGNORE INTO outer_box (qr_code, box_name) VALUES ('BOX-003', 'C구역 7번 박스')",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-001', '8801234567890', 10)",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-001', '8809876543210', 5)",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-002', '8801111111111', 20)",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-002', '8802222222222', 8)",
        "INSERT OR IGNORE INTO outer_box_item (qr_code, barcode, quantity) VALUES ('BOX-003', '8803333333333', 15)",
        # Seed: action_log
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('print', '8801234567890', 'SKU-001', '샘플 상품 A', 1, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('cart', '8809876543210', 'SKU-002', '샘플 상품 B', 3, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('print', '8801111111111', 'SKU-003', '샘플 상품 C', 2, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('cart', '8802222222222', 'SKU-004', '샘플 상품 D', 5, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('print', '8803333333333', 'SKU-005', '샘플 상품 E', 1, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('cart', '8801234567890', 'SKU-001', '샘플 상품 A', 2, 'PDA')",
        "INSERT OR IGNORE INTO action_log (action_type, barcode, sku_id, product_name, quantity, requested_by) VALUES ('print', '8809876543210', 'SKU-002', '샘플 상품 B', 1, 'PDA')",
        # Seed: favorite
        "INSERT OR IGNORE INTO favorite (sku_id, product_name, barcode) VALUES ('SKU-001', '샘플 상품 A', '8801234567890')",
        "INSERT OR IGNORE INTO favorite (sku_id, product_name, barcode) VALUES ('SKU-002', '샘플 상품 B', '8809876543210')",
        "INSERT OR IGNORE INTO favorite (sku_id, product_name, barcode) VALUES ('SKU-003', '샘플 상품 C', '8801111111111')",
        # Seed: scan_log
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
}
