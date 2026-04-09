import io
from pathlib import Path

import pytest
from PIL import Image

from app.config import settings


def _create_test_image(width: int = 800, height: int = 600, fmt: str = "JPEG") -> bytes:
    img = Image.new("RGB", (width, height), color="red")
    buf = io.BytesIO()
    img.save(buf, format=fmt)
    return buf.getvalue()


@pytest.fixture
def setup_cache(client):
    # conftest의 client fixture가 이미 cache_dir 생성 + settings.image_cache_dir 패치를 마쳤다.
    # 여기서는 그 경로만 돌려받아 테스트에서 파일을 배치한다.
    return Path(settings.image_cache_dir)


class TestResizeThumbnail:
    def test_resize_thumbnail(self, client, setup_cache):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        (img_dir / "test.jpg").write_bytes(_create_test_image(800, 600))

        resp = client.get("/api/image/img/test.jpg?width=300")
        assert resp.status_code == 200

        result = Image.open(io.BytesIO(resp.content))
        assert result.width == 300
        assert result.height == 225  # aspect ratio 유지 (600/800*300)

    def test_resize_original(self, client, setup_cache):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        original = _create_test_image(800, 600)
        (img_dir / "test.jpg").write_bytes(original)

        resp = client.get("/api/image/img/test.jpg")
        assert resp.status_code == 200
        assert resp.content == original


class TestResizeCache:
    def test_resize_cache(self, client, setup_cache):
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
    def test_zero_or_negative(self, client, setup_cache, width):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        original = _create_test_image(800, 600)
        (img_dir / "test.jpg").write_bytes(original)

        resp = client.get(f"/api/image/img/test.jpg?width={width}")
        assert resp.status_code == 200
        assert resp.content == original

    def test_too_large(self, client, setup_cache):
        img_dir = setup_cache / "img"
        img_dir.mkdir(parents=True)
        original = _create_test_image(800, 600)
        (img_dir / "test.jpg").write_bytes(original)

        resp = client.get("/api/image/img/test.jpg?width=10000")
        assert resp.status_code == 200
        assert resp.content == original


class TestResizeNonImage:
    def test_non_image_file(self, client, setup_cache):
        txt_dir = setup_cache / "docs"
        txt_dir.mkdir(parents=True)
        (txt_dir / "readme.txt").write_text("not an image")

        resp = client.get("/api/image/docs/readme.txt?width=300")
        assert resp.status_code == 400
