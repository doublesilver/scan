import io
from pathlib import Path
from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.config import settings
from app.main import app

client = TestClient(app)


def _create_test_image(width: int = 800, height: int = 600, fmt: str = "JPEG") -> bytes:
    img = Image.new("RGB", (width, height), color="red")
    buf = io.BytesIO()
    img.save(buf, format=fmt)
    return buf.getvalue()


@pytest.fixture(autouse=True)
def setup_cache(tmp_path, monkeypatch):
    cache_dir = tmp_path / "cache"
    cache_dir.mkdir()
    monkeypatch.setattr(settings, "image_cache_dir", str(cache_dir))
    return cache_dir


class TestResizeThumbnail:
    def test_resize_thumbnail(self, setup_cache):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        (img_dir / "test.jpg").write_bytes(_create_test_image(800, 600))

        resp = client.get("/api/image/img/test.jpg?width=300")
        assert resp.status_code == 200

        result = Image.open(io.BytesIO(resp.content))
        assert result.width == 300
        assert result.height == 225  # aspect ratio 유지 (600/800*300)

    def test_resize_original(self, setup_cache):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        original = _create_test_image(800, 600)
        (img_dir / "test.jpg").write_bytes(original)

        resp = client.get("/api/image/img/test.jpg")
        assert resp.status_code == 200
        assert resp.content == original


class TestResizeCache:
    def test_resize_cache(self, setup_cache):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        (img_dir / "test.jpg").write_bytes(_create_test_image(800, 600))

        client.get("/api/image/img/test.jpg?width=300")

        cached = setup_cache / "resized" / "300" / "img" / "test.jpg"
        assert cached.exists()

        resp2 = client.get("/api/image/img/test.jpg?width=300")
        assert resp2.status_code == 200
        result = Image.open(io.BytesIO(resp2.content))
        assert result.width == 300


class TestResizeInvalidWidth:
    @pytest.mark.parametrize("width", [0, -1, -100])
    def test_zero_or_negative(self, setup_cache, width):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        original = _create_test_image(800, 600)
        (img_dir / "test.jpg").write_bytes(original)

        resp = client.get(f"/api/image/img/test.jpg?width={width}")
        assert resp.status_code == 200
        assert resp.content == original

    def test_too_large(self, setup_cache):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        original = _create_test_image(800, 600)
        (img_dir / "test.jpg").write_bytes(original)

        resp = client.get("/api/image/img/test.jpg?width=10000")
        assert resp.status_code == 200
        assert resp.content == original


class TestResizeNonImage:
    def test_non_image_file(self, setup_cache):
        txt_dir = setup_cache / "docs"
        txt_dir.mkdir(parents=True)
        (txt_dir / "readme.txt").write_text("not an image")

        resp = client.get("/api/image/docs/readme.txt?width=300")
        assert resp.status_code == 400
