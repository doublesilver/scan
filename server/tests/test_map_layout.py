import sqlite3

import aiosqlite
import pytest

from app.db.schema import MIGRATIONS, SCHEMA_SQL
from app.services.warehouse_service import save_layout_from_json


async def _open_db(tmp_path):
    db_path = tmp_path / "map_layout.db"
    conn = sqlite3.connect(str(db_path))
    conn.executescript(SCHEMA_SQL)
    for version in sorted(MIGRATIONS.keys()):
        for sql in MIGRATIONS[version]:
            conn.execute(sql)
    conn.commit()
    conn.close()

    db = await aiosqlite.connect(str(db_path))
    db.row_factory = aiosqlite.Row
    return db


@pytest.mark.asyncio
async def test_save_layout_preserves_existing_level_products(tmp_path):
    db = await _open_db(tmp_path)

    await db.execute(
        "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES ('A', '501호', 1, 1, 0)"
    )
    await db.execute(
        "INSERT INTO warehouse_cell (zone_id, row, col, label, status) VALUES (1, 1, 1, 'A-1', 'used')"
    )
    await db.execute(
        "INSERT INTO cell_level (cell_id, level_index, label) VALUES (1, 0, '하단 (1층)')"
    )
    await db.execute("INSERT INTO product_master (name) VALUES ('테스트 상품')")
    await db.execute(
        "INSERT INTO cell_level_product (level_id, product_master_id, photo, memo, sort_order) "
        "VALUES (1, 1, '/static/photos/existing.jpg', '기존 적치', 0)"
    )
    await db.commit()

    layout = {
        "title": "창고 도면",
        "floor": 5,
        "zones": [{"code": "A", "name": "501호", "rows": 1, "cols": 1}],
        "cells": {
            "A-1-1": {
                "label": "A-1",
                "status": "used",
                "bgColor": "",
                "levels": [{"index": 0, "label": "하단 (1층)", "itemLabel": "", "photo": ""}],
            }
        },
    }

    await save_layout_from_json(db, layout)

    rows = await db.execute_fetchall(
        "SELECT clp.product_master_id, clp.photo, clp.memo, clp.sort_order "
        "FROM warehouse_zone wz "
        "JOIN warehouse_cell wc ON wc.zone_id = wz.id "
        "JOIN cell_level cl ON cl.cell_id = wc.id "
        "JOIN cell_level_product clp ON clp.level_id = cl.id "
        "WHERE wz.code = 'A' AND wc.row = 1 AND wc.col = 1 AND cl.level_index = 0"
    )

    assert len(rows) == 1
    assert rows[0][0] == 1
    assert rows[0][1] == "/static/photos/existing.jpg"
    assert rows[0][2] == "기존 적치"
    assert rows[0][3] == 0

    await db.close()


@pytest.mark.asyncio
async def test_save_layout_roundtrip_preserves_linked_product(tmp_path):
    """GET→POST 라운드트립 시 PDA 연결 상품(product_master_id)이 보존되는지 검증."""
    db = await _open_db(tmp_path)

    await db.execute(
        "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES ('A', '501호', 1, 1, 0)"
    )
    await db.execute(
        "INSERT INTO warehouse_cell (zone_id, row, col, label, status) VALUES (1, 1, 1, 'A-1', 'used')"
    )
    await db.execute(
        "INSERT INTO cell_level (cell_id, level_index, label) VALUES (1, 0, '하단 (1층)')"
    )
    await db.execute("INSERT INTO product_master (name) VALUES ('PDA 연결 상품')")
    await db.execute(
        "INSERT INTO cell_level_product (level_id, product_master_id, photo, memo, sort_order) "
        "VALUES (1, 1, '/static/photos/product.jpg', 'PDA 배치', 0)"
    )
    await db.commit()

    layout = {
        "title": "창고 도면",
        "floor": 5,
        "zones": [{"code": "A", "name": "501호", "rows": 1, "cols": 1}],
        "cells": {
            "A-1-1": {
                "label": "A-1",
                "status": "used",
                "bgColor": "",
                "levels": [
                    {
                        "index": 0,
                        "label": "하단 (1층)",
                        "itemLabel": "PDA 연결 상품",
                        "photo": "/static/photos/product.jpg",
                    }
                ],
            }
        },
    }

    result = await save_layout_from_json(db, layout)
    assert result["ok"] is True

    rows = await db.execute_fetchall(
        "SELECT clp.product_master_id, clp.photo, clp.memo "
        "FROM warehouse_zone wz "
        "JOIN warehouse_cell wc ON wc.zone_id = wz.id "
        "JOIN cell_level cl ON cl.cell_id = wc.id "
        "JOIN cell_level_product clp ON clp.level_id = cl.id "
        "WHERE wz.code = 'A' AND wc.row = 1 AND wc.col = 1 AND cl.level_index = 0"
    )

    assert len(rows) == 1
    assert rows[0][0] == 1
    assert rows[0][1] == "/static/photos/product.jpg"
    assert rows[0][2] == "PDA 배치"

    await db.close()


@pytest.mark.asyncio
async def test_save_layout_manual_data_without_product_link(tmp_path):
    """product_master_id 없는 수동 데이터는 payload로 덮어쓸 수 있는지 검증."""
    db = await _open_db(tmp_path)

    await db.execute(
        "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES ('A', '501호', 1, 1, 0)"
    )
    await db.execute(
        "INSERT INTO warehouse_cell (zone_id, row, col, label, status) VALUES (1, 1, 1, 'A-1', 'used')"
    )
    await db.execute("INSERT INTO cell_level (cell_id, level_index, label) VALUES (1, 0, '1층')")
    await db.execute(
        "INSERT INTO cell_level_product (level_id, photo, memo, sort_order) "
        "VALUES (1, '/static/photos/manual.jpg', '수동 메모', 0)"
    )
    await db.commit()

    layout = {
        "title": "창고 도면",
        "floor": 5,
        "zones": [{"code": "A", "name": "501호", "rows": 1, "cols": 1}],
        "cells": {
            "A-1-1": {
                "label": "A-1",
                "status": "used",
                "bgColor": "",
                "levels": [{"index": 0, "label": "1층", "itemLabel": "", "photo": ""}],
            }
        },
    }

    result = await save_layout_from_json(db, layout)
    assert result["ok"] is True

    rows = await db.execute_fetchall(
        "SELECT COUNT(*) FROM cell_level_product clp "
        "JOIN cell_level cl ON clp.level_id = cl.id "
        "JOIN warehouse_cell wc ON cl.cell_id = wc.id "
        "JOIN warehouse_zone wz ON wc.zone_id = wz.id "
        "WHERE wz.code = 'A'"
    )
    assert rows[0][0] == 0

    await db.close()


@pytest.mark.asyncio
async def test_save_layout_rejects_deleting_cells_with_products(tmp_path):
    db = await _open_db(tmp_path)

    await db.execute(
        "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES ('A', '501호', 3, 2, 0)"
    )
    await db.execute(
        "INSERT INTO warehouse_cell (zone_id, row, col, label, status) VALUES (1, 3, 1, 'A-5', 'used')"
    )
    await db.execute("INSERT INTO cell_level (cell_id, level_index, label) VALUES (1, 0, '1층')")
    await db.execute("INSERT INTO product_master (name) VALUES ('보호 대상 상품')")
    await db.execute(
        "INSERT INTO cell_level_product (level_id, product_master_id, memo, sort_order) "
        "VALUES (1, 1, '배치됨', 0)"
    )
    await db.commit()

    layout = {
        "title": "축소 도면",
        "floor": 5,
        "zones": [{"code": "A", "name": "501호", "rows": 2, "cols": 2}],
        "cells": {},
    }

    result = await save_layout_from_json(db, layout)

    assert result["ok"] is False
    assert "A-3-1" in result["affected_cells"]

    still = await db.execute_fetchall(
        "SELECT COUNT(*) FROM cell_level_product WHERE product_master_id = 1"
    )
    assert still[0][0] == 1

    await db.close()


@pytest.mark.asyncio
async def test_save_layout_allows_deleting_empty_cells(tmp_path):
    db = await _open_db(tmp_path)

    await db.execute(
        "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES ('A', '501호', 3, 2, 0)"
    )
    await db.execute(
        "INSERT INTO warehouse_cell (zone_id, row, col, label, status) VALUES (1, 3, 1, 'A-5', 'empty')"
    )
    await db.commit()

    layout = {
        "title": "축소 도면",
        "floor": 5,
        "zones": [{"code": "A", "name": "501호", "rows": 2, "cols": 2}],
        "cells": {},
    }

    result = await save_layout_from_json(db, layout)

    assert result["ok"] is True

    zones = await db.execute_fetchall("SELECT rows, cols FROM warehouse_zone WHERE code = 'A'")
    assert zones[0][0] == 2
    assert zones[0][1] == 2

    await db.close()


@pytest.mark.asyncio
async def test_save_layout_preserves_products_on_zone_resize(tmp_path):
    db = await _open_db(tmp_path)

    await db.execute(
        "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES ('A', '501호', 2, 2, 0)"
    )
    await db.execute(
        "INSERT INTO warehouse_cell (zone_id, row, col, label, status) VALUES (1, 1, 1, 'A-1', 'used')"
    )
    await db.execute("INSERT INTO cell_level (cell_id, level_index, label) VALUES (1, 0, '1층')")
    await db.execute("INSERT INTO product_master (name) VALUES ('보존 상품')")
    await db.execute(
        "INSERT INTO cell_level_product (level_id, product_master_id, photo, memo, sort_order) "
        "VALUES (1, 1, '/static/photos/keep.jpg', '유지 메모', 0)"
    )
    await db.commit()

    layout = {
        "title": "확장 도면",
        "floor": 5,
        "zones": [{"code": "A", "name": "501호", "rows": 3, "cols": 3}],
        "cells": {
            "A-1-1": {
                "label": "A-1",
                "status": "used",
                "bgColor": "",
                "levels": [{"index": 0, "label": "1층", "itemLabel": "", "photo": ""}],
            }
        },
    }

    result = await save_layout_from_json(db, layout)
    assert result["ok"] is True

    rows = await db.execute_fetchall(
        "SELECT clp.product_master_id, clp.photo, clp.memo "
        "FROM warehouse_zone wz "
        "JOIN warehouse_cell wc ON wc.zone_id = wz.id "
        "JOIN cell_level cl ON cl.cell_id = wc.id "
        "JOIN cell_level_product clp ON clp.level_id = cl.id "
        "WHERE wz.code = 'A' AND wc.row = 1 AND wc.col = 1 AND cl.level_index = 0"
    )
    assert len(rows) == 1
    assert rows[0][0] == 1
    assert rows[0][1] == "/static/photos/keep.jpg"
    assert rows[0][2] == "유지 메모"

    await db.close()


@pytest.mark.asyncio
async def test_save_layout_rejects_removing_level_with_products(tmp_path):
    """셀은 유지하지만 상품이 있는 레벨을 제거하는 저장은 차단."""
    db = await _open_db(tmp_path)

    await db.execute(
        "INSERT INTO warehouse_zone (code, name, rows, cols, sort_order) VALUES ('A', '501호', 1, 1, 0)"
    )
    await db.execute(
        "INSERT INTO warehouse_cell (zone_id, row, col, label, status) VALUES (1, 1, 1, 'A-1', 'used')"
    )
    await db.execute("INSERT INTO cell_level (cell_id, level_index, label) VALUES (1, 0, '1층')")
    await db.execute("INSERT INTO product_master (name) VALUES ('레벨 보호 상품')")
    await db.execute(
        "INSERT INTO cell_level_product (level_id, product_master_id, memo, sort_order) "
        "VALUES (1, 1, '배치됨', 0)"
    )
    await db.commit()

    layout = {
        "title": "도면",
        "floor": 5,
        "zones": [{"code": "A", "name": "501호", "rows": 1, "cols": 1}],
        "cells": {
            "A-1-1": {
                "label": "A-1",
                "status": "used",
                "bgColor": "",
                "levels": [],
            }
        },
    }

    result = await save_layout_from_json(db, layout)

    assert result["ok"] is False
    assert any("A-1-1" in c for c in result["affected_cells"])

    still = await db.execute_fetchall(
        "SELECT COUNT(*) FROM cell_level_product WHERE product_master_id = 1"
    )
    assert still[0][0] == 1

    await db.close()
