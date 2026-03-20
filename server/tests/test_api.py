import pytest
from pathlib import Path


class TestHealthAPI:

    def test_health_check(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"


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


class TestImageAPI:

    def test_image_valid_path(self, client, tmp_path):
        from unittest.mock import patch
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
