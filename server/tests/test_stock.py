import pytest


class TestStockAPI:

    def test_get_stock_empty(self, client):
        resp = client.get("/api/stock/SKU001")
        assert resp.status_code == 200
        data = resp.json()
        assert data["sku_id"] == "SKU001"
        assert data["quantity"] == 0

    def test_update_stock(self, client):
        resp = client.patch("/api/stock/SKU001", json={"quantity": 100})
        assert resp.status_code == 200
        data = resp.json()
        assert data["sku_id"] == "SKU001"
        assert data["quantity"] == 100

        resp = client.get("/api/stock/SKU001")
        assert resp.status_code == 200
        assert resp.json()["quantity"] == 100

    def test_update_stock_log(self, client):
        client.patch("/api/stock/SKU001", json={"quantity": 100})

        resp = client.get("/api/stock/SKU001/log")
        assert resp.status_code == 200
        logs = resp.json()
        assert len(logs) == 1
        assert logs[0]["before_qty"] == 0
        assert logs[0]["after_qty"] == 100

    def test_update_stock_twice(self, client):
        client.patch("/api/stock/SKU001", json={"quantity": 100})
        client.patch("/api/stock/SKU001", json={"quantity": 50, "memo": "재확인"})

        resp = client.get("/api/stock/SKU001/log")
        assert resp.status_code == 200
        logs = resp.json()
        assert len(logs) == 2
        assert logs[0]["before_qty"] == 100
        assert logs[0]["after_qty"] == 50
        assert logs[1]["before_qty"] == 0
        assert logs[1]["after_qty"] == 100

    def test_scan_includes_stock(self, client):
        client.patch("/api/stock/SKU001", json={"quantity": 77})

        resp = client.get("/api/scan/8801234567890")
        assert resp.status_code == 200
        data = resp.json()
        assert data["quantity"] == 77

    def test_scan_without_stock(self, client):
        resp = client.get("/api/scan/8801234567890")
        assert resp.status_code == 200
        data = resp.json()
        assert data["quantity"] is None

    def test_update_stock_nonexistent_sku(self, client):
        resp = client.patch("/api/stock/NONEXIST", json={"quantity": 10})
        assert resp.status_code == 404
