import sqlite3
import tempfile
from pathlib import Path
from unittest.mock import patch, AsyncMock, MagicMock

import aiosqlite
import pytest
from fastapi.testclient import TestClient

from app.db.schema import SCHEMA_SQL, MIGRATIONS

SEED_PRODUCTS = [
    ("SKU001", "테스트 상품 A", "가전", "브랜드A"),
    ("SKU002", "테스트 상품 B", "식품", "브랜드B"),
    ("SKU003", "테스트 상품 C", "의류", "브랜드C"),
    ("SKU004", "특별 상품 D", "가전", "브랜드D"),
    ("SKU005", "특별 상품 E", "식품", "브랜드E"),
    ("SKU006", "스페이스쉴드 브랜드필터 상품 F", "가전", "브랜드F"),
]

SEED_BARCODES = [
    ("8801234567890", "SKU001"),
    ("8801234567891", "SKU002"),
    ("8801234567892", "SKU003"),
    ("8801234567893", "SKU004"),
    ("8801234567894", "SKU005"),
    ("8801234567895", "SKU006"),
]

SEED_IMAGES = [
    ("8801234567890", "img/sku001_thumb.jpg", "thumbnail", 0),
    ("8801234567890", "real_image/sku001_real.jpg", "real", 1),
    ("8801234567891", "img/sku002_thumb.jpg", "thumbnail", 0),
]


def _create_test_db(db_path: Path) -> None:
    conn = sqlite3.connect(str(db_path))
    conn.executescript(SCHEMA_SQL)
    conn.execute(
        "CREATE VIRTUAL TABLE IF NOT EXISTS product_fts USING fts5("
        "sku_id, product_name, category, brand, content='product', content_rowid='rowid')"
    )
    for p in SEED_PRODUCTS:
        conn.execute("INSERT INTO product (sku_id, product_name, category, brand) VALUES (?, ?, ?, ?)", p)
    conn.commit()
    for p in SEED_PRODUCTS:
        row = conn.execute("SELECT rowid FROM product WHERE sku_id = ?", (p[0],)).fetchone()
        conn.execute(
            "INSERT INTO product_fts (rowid, sku_id, product_name, category, brand) VALUES (?, ?, ?, ?, ?)",
            (row[0], *p),
        )
    for b in SEED_BARCODES:
        conn.execute("INSERT INTO barcode (barcode, sku_id) VALUES (?, ?)", b)
    for im in SEED_IMAGES:
        conn.execute("INSERT INTO image (barcode, file_path, image_type, sort_order) VALUES (?, ?, ?, ?)", im)
    for version in sorted(MIGRATIONS.keys()):
        for sql in MIGRATIONS[version]:
            conn.execute(sql)
    conn.commit()
    conn.close()


@pytest.fixture()
def client(tmp_path):
    db_path = tmp_path / "test_scanner.db"
    _create_test_db(db_path)

    _db_holder = {}

    async def mock_get_db():
        if "db" not in _db_holder:
            db = await aiosqlite.connect(str(db_path))
            db.row_factory = aiosqlite.Row
            await db.execute("PRAGMA journal_mode=WAL")
            await db.execute("PRAGMA foreign_keys=ON")
            _db_holder["db"] = db
        return _db_holder["db"]

    async def mock_get_read_db():
        return await mock_get_db()

    cache_dir = tmp_path / "cache"
    cache_dir.mkdir()

    mock_images_dir = tmp_path / "data" / "mock_images"
    mock_images_dir.mkdir(parents=True)
    default_img = mock_images_dir / "default.png"
    default_img.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\x00" * 100)

    mock_nas_sync = MagicMock()
    mock_nas_sync.start = AsyncMock()
    mock_nas_sync.stop = AsyncMock()

    with patch("app.api.routes.get_db", mock_get_db), \
         patch("app.api.routes.get_read_db", mock_get_read_db), \
         patch("app.services.file_watcher.start_watcher"), \
         patch("app.services.file_watcher.stop_watcher"), \
         patch("app.main.NasSyncService", return_value=mock_nas_sync), \
         patch("app.db.database.get_db", mock_get_db), \
         patch("app.db.database.get_read_db", mock_get_read_db):

        original_settings_class = type(None)
        from app.config import settings
        orig_image_cache_dir = settings.image_cache_dir
        orig_webdav = settings.webdav_base_url

        settings.image_cache_dir = str(cache_dir)
        settings.webdav_base_url = ""

        from app.main import app
        with TestClient(app, raise_server_exceptions=False) as c:
            yield c

        settings.image_cache_dir = orig_image_cache_dir
        settings.webdav_base_url = orig_webdav
