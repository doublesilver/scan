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
    ],
    6: [
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
    ],
    8: [
        """CREATE TABLE IF NOT EXISTS map_layout (
            id INTEGER PRIMARY KEY DEFAULT 1,
            data TEXT NOT NULL,
            updated_at TEXT DEFAULT (datetime('now'))
        )""",
    ],
}
