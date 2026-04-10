from unittest.mock import patch

import openpyxl


class TestHealthAPI:
    def test_health_check(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"

    def test_status(self, client):
        resp = client.get("/api/status")
        assert resp.status_code == 200
        data = resp.json()
        assert "server" in data
        assert "database" in data
        assert "disk" in data


class TestScanAPI:
    def test_scan_valid_barcode(self, client):
        resp = client.get("/api/scan/8801234567890")
        assert resp.status_code == 200
        data = resp.json()
        assert "product_name" in data
        assert "sku_id" in data
        assert "images" in data
        assert data["sku_id"] == "SKU001"
        assert data["product_name"] == "테스트 상품 A"
        assert len(data["images"]) == 2

    def test_scan_invalid_barcode(self, client):
        resp = client.get("/api/scan/0000000000000")
        assert resp.status_code == 404

    def test_scan_barcode_format(self, client):
        resp = client.get("/api/scan/abc-invalid!")
        assert resp.status_code == 404


class TestSearchAPI:
    def test_search_returns_results(self, client):
        resp = client.get("/api/search", params={"q": "테스트"})
        assert resp.status_code == 200
        data = resp.json()
        assert "items" in data
        assert len(data["items"]) >= 1
        for item in data["items"]:
            assert "barcode" in item

    def test_search_empty_query(self, client):
        resp = client.get("/api/search", params={"q": ""})
        assert resp.status_code == 422

    def test_search_limit(self, client):
        resp = client.get("/api/search", params={"q": "상품", "limit": 2})
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["items"]) <= 2

    def test_search_no_results(self, client):
        resp = client.get("/api/search", params={"q": "존재하지않는키워드xyz"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["items"] == []
        assert data["total"] == 0


class TestBrandFilter:
    def test_brand_filter_applied(self, client):
        resp = client.get("/api/scan/8801234567895")
        assert resp.status_code == 200
        data = resp.json()
        from app.config import settings

        for brand in settings.brand_filter:
            assert brand not in data["product_name"]
        assert data["product_name"] == "브랜드필터 상품 F"


class TestImageAPI:
    def test_image_valid_path(self, client, tmp_path):
        cache_dir = tmp_path / "img_cache"
        cache_dir.mkdir()
        test_img = cache_dir / "test.jpg"
        test_img.write_bytes(b"\xff\xd8\xff\xe0" + b"\x00" * 100)

        with patch("app.config.settings.image_cache_dir", str(cache_dir)):
            resp = client.get("/api/image/test.jpg")
            assert resp.status_code == 200
            assert "image" in resp.headers["content-type"]

    def test_image_path_traversal(self, client):
        resp = client.get("/api/image/sub/../../../etc/passwd")
        assert resp.status_code in (400, 404)

    def test_image_not_found(self, client):
        resp = client.get("/api/image/nonexistent/image.jpg")
        assert resp.status_code in (200, 404)


class TestUrlImportAPI:
    def test_import_urls_from_watch_dir(self, client, tmp_path):
        watch_dir = tmp_path / "xlsx"
        watch_dir.mkdir()
        workbook_path = watch_dir / "purchase_urls.xlsx"

        wb = openpyxl.Workbook()
        ws = wb.active
        ws.append(["sku_id", "url"])
        ws.append(["SKU001", "https://example.com/item"])
        wb.save(workbook_path)
        wb.close()

        with patch("app.config.settings.xlsx_watch_dir", str(watch_dir)):
            resp = client.post("/api/import/urls", params={"file_path": "purchase_urls.xlsx"})

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "ok"
        assert body["updated"] == 1


class TestMapLayoutPatchAPI:
    def test_patch_levels_updates_manual_data(self, client):
        layout = {
            "title": "테스트",
            "floor": 5,
            "zones": [{"code": "A", "name": "501호", "rows": 1, "cols": 1}],
            "cells": {
                "A-1-1": {
                    "label": "A-1",
                    "status": "used",
                    "bgColor": "",
                    "levels": [{"index": 0, "label": "1층", "itemLabel": "초기메모", "photo": ""}],
                }
            },
        }
        resp = client.post("/api/map-layout", json=layout)
        assert resp.status_code == 200

        resp = client.patch(
            "/api/map-layout/cell/A-1-1",
            json={
                "levels": [{"index": 0, "label": "1층 수정", "itemLabel": "수정메모", "photo": ""}]
            },
        )
        assert resp.status_code == 200

        resp = client.get("/api/map-layout")
        assert resp.status_code == 200
        data = resp.json()
        cell = data["cells"].get("A-1-1", {})
        levels = cell.get("levels", [])
        assert len(levels) >= 1
        assert levels[0]["label"] == "1층 수정"
        assert levels[0]["itemLabel"] == "수정메모"

    def test_patch_cell_updates_only_sent_fields(self, client):
        layout = {
            "title": "테스트",
            "floor": 5,
            "zones": [{"code": "B", "name": "포장", "rows": 1, "cols": 1}],
            "cells": {
                "B-1-1": {
                    "label": "원래라벨",
                    "status": "used",
                    "bgColor": "#ff0000",
                    "levels": [],
                }
            },
        }
        resp = client.post("/api/map-layout", json=layout)
        assert resp.status_code == 200

        resp = client.patch("/api/map-layout/cell/B-1-1", json={"label": "새라벨"})
        assert resp.status_code == 200

        resp = client.get("/api/map-layout")
        assert resp.status_code == 200
        data = resp.json()
        cell = data["cells"].get("B-1-1", {})
        assert cell["label"] == "새라벨"
        assert cell["status"] == "used"
        assert cell["bgColor"] == "#ff0000"
