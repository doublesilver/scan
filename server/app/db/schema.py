SCHEMA_VERSION = 13

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
    9: [
        """CREATE TABLE IF NOT EXISTS warehouse_zone (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            code TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            rows INTEGER NOT NULL DEFAULT 3,
            cols INTEGER NOT NULL DEFAULT 4,
            sort_order INTEGER NOT NULL DEFAULT 0,
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now'))
        )""",
        """CREATE TABLE IF NOT EXISTS warehouse_cell (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            zone_id INTEGER NOT NULL REFERENCES warehouse_zone(id) ON DELETE CASCADE,
            row INTEGER NOT NULL,
            col INTEGER NOT NULL,
            label TEXT DEFAULT '',
            status TEXT DEFAULT 'empty',
            bg_color TEXT DEFAULT '',
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now')),
            UNIQUE(zone_id, row, col)
        )""",
        """CREATE TABLE IF NOT EXISTS cell_level (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            cell_id INTEGER NOT NULL REFERENCES warehouse_cell(id) ON DELETE CASCADE,
            level_index INTEGER NOT NULL DEFAULT 0,
            label TEXT DEFAULT '',
            created_at TEXT DEFAULT (datetime('now')),
            UNIQUE(cell_id, level_index)
        )""",
        """CREATE TABLE IF NOT EXISTS cell_level_product (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            level_id INTEGER NOT NULL REFERENCES cell_level(id) ON DELETE CASCADE,
            product_master_id INTEGER REFERENCES product_master(id),
            photo TEXT DEFAULT '',
            memo TEXT DEFAULT '',
            sort_order INTEGER NOT NULL DEFAULT 0,
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now'))
        )""",
        "CREATE INDEX IF NOT EXISTS idx_zone_code ON warehouse_zone(code)",
        "CREATE INDEX IF NOT EXISTS idx_cell_zone ON warehouse_cell(zone_id)",
        "CREATE INDEX IF NOT EXISTS idx_level_cell ON cell_level(cell_id)",
        "CREATE INDEX IF NOT EXISTS idx_lp_level ON cell_level_product(level_id)",
        "CREATE INDEX IF NOT EXISTS idx_lp_master ON cell_level_product(product_master_id)",
    ],
    10: [
        """CREATE TABLE IF NOT EXISTS print_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            barcode TEXT NOT NULL,
            sku_id TEXT NOT NULL DEFAULT '',
            product_name TEXT NOT NULL DEFAULT '',
            quantity INTEGER NOT NULL DEFAULT 1,
            status TEXT NOT NULL,
            via TEXT NOT NULL DEFAULT '',
            http_status INTEGER,
            elapsed_ms INTEGER,
            message TEXT NOT NULL DEFAULT '',
            raw_response TEXT NOT NULL DEFAULT ''
        )""",
        "CREATE INDEX IF NOT EXISTS idx_print_log_created ON print_log(created_at)",
        "CREATE INDEX IF NOT EXISTS idx_print_log_status ON print_log(status)",
        "CREATE INDEX IF NOT EXISTS idx_print_log_barcode ON print_log(barcode)",
    ],
    11: [
        "ALTER TABLE product ADD COLUMN naver_url TEXT DEFAULT NULL",
        "ALTER TABLE product ADD COLUMN url_1688 TEXT DEFAULT NULL",
        "ALTER TABLE product ADD COLUMN flow_url TEXT DEFAULT NULL",
        """CREATE TABLE IF NOT EXISTS cart (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sku_id TEXT NOT NULL,
            barcode TEXT NOT NULL DEFAULT '',
            product_name TEXT NOT NULL DEFAULT '',
            quantity INTEGER NOT NULL DEFAULT 1,
            added_by TEXT NOT NULL DEFAULT 'web',
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now'))
        )""",
        "CREATE INDEX IF NOT EXISTS idx_cart_sku ON cart(sku_id)",
        "CREATE INDEX IF NOT EXISTS idx_cart_date ON cart(created_at)",
    ],
    12: [
        """CREATE TABLE IF NOT EXISTS coupang_fetch_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sku_id TEXT NOT NULL,
            url TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL,
            saved_path TEXT,
            fetched_at TEXT NOT NULL DEFAULT (datetime('now'))
        )""",
        "CREATE INDEX IF NOT EXISTS idx_coupang_log_sku ON coupang_fetch_log(sku_id)",
        "CREATE INDEX IF NOT EXISTS idx_coupang_log_date ON coupang_fetch_log(fetched_at)",
    ],
    13: [
        """CREATE TABLE IF NOT EXISTS product_master_image (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            product_master_id INTEGER NOT NULL REFERENCES product_master(id),
            file_path TEXT NOT NULL,
            image_type TEXT NOT NULL CHECK(image_type IN ('option', 'sourcing')),
            sort_order INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )""",
        "CREATE INDEX IF NOT EXISTS idx_pmi_master ON product_master_image(product_master_id)",
    ],
}
