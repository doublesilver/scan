SCHEMA_VERSION = 2

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
}
