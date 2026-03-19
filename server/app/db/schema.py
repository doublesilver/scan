SCHEMA_SQL = """
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
    barcode         TEXT NOT NULL,
    sku_id          TEXT NOT NULL,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (sku_id) REFERENCES product(sku_id) ON DELETE CASCADE,
    UNIQUE (barcode, sku_id)
);
CREATE INDEX IF NOT EXISTS idx_barcode_barcode ON barcode(barcode);
CREATE INDEX IF NOT EXISTS idx_barcode_sku_id  ON barcode(sku_id);

CREATE TABLE IF NOT EXISTS image (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    sku_id          TEXT NOT NULL,
    file_path       TEXT NOT NULL,
    image_type      TEXT NOT NULL DEFAULT 'thumbnail',
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (sku_id) REFERENCES product(sku_id) ON DELETE CASCADE,
    UNIQUE (sku_id, file_path)
);
CREATE INDEX IF NOT EXISTS idx_image_sku_id ON image(sku_id);

CREATE TABLE IF NOT EXISTS parse_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    file_name       TEXT NOT NULL,
    file_type       TEXT NOT NULL,
    added_count     INTEGER NOT NULL DEFAULT 0,
    updated_count   INTEGER NOT NULL DEFAULT 0,
    skipped_count   INTEGER NOT NULL DEFAULT 0,
    error_count     INTEGER NOT NULL DEFAULT 0,
    errors          TEXT NOT NULL DEFAULT '[]',
    parsed_at       TEXT NOT NULL DEFAULT (datetime('now'))
);
"""
