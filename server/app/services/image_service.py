import io
import logging
from pathlib import Path

from fastapi import HTTPException
from PIL import Image, UnidentifiedImageError

from app.config import settings

logger = logging.getLogger(__name__)


def validate_path(base: Path, sub_path: str) -> Path:
    resolved = (base / sub_path).resolve()
    if not resolved.is_relative_to(base):
        raise HTTPException(status_code=400, detail="invalid path")
    return resolved


async def get_image_data(
    path: str, width: int | None, http_client
) -> tuple[bytes | None, Path | None, int | None]:
    cache_base = Path(settings.image_cache_dir).resolve()
    cache_path = validate_path(cache_base, path)

    if width and width > 0:
        resized_path = validate_path(cache_base, f"resized/{width}/{path}")
        if resized_path.exists() and resized_path.is_file():
            logger.info("resized cache hit: %s (w=%d)", path, width)
            return None, resized_path, width

    image_bytes = None
    source_path = None

    if cache_path.exists() and cache_path.is_file():
        logger.info("image cache hit: %s", path)
        source_path = cache_path
    else:
        mock_base = Path("data/mock_images").resolve()
        mock_path = validate_path(mock_base, path)

        if mock_path.exists() and mock_path.is_file():
            logger.info("image mock hit: %s", path)
            source_path = mock_path
        elif settings.webdav_base_url:
            image_bytes = await _fetch_from_webdav(http_client, path)
            if image_bytes:
                cache_path.parent.mkdir(parents=True, exist_ok=True)
                cache_path.write_bytes(image_bytes)
                logger.info("image webdav fetch + cached: %s", path)

    if source_path is None and image_bytes is None:
        default_path = Path("data/mock_images/default.png")
        if default_path.exists():
            return None, default_path, None
        raise HTTPException(status_code=404, detail="image not found")

    if width and width > 0:
        raw = image_bytes if image_bytes else source_path.read_bytes()
        resized = resize_image(raw, width, path)
        if resized:
            resized_path = validate_path(cache_base, f"resized/{width}/{path}")
            resized_path.parent.mkdir(parents=True, exist_ok=True)
            resized_path.write_bytes(resized)
            logger.info("resized + cached: %s (w=%d)", path, width)
            return resized, None, width

    if source_path:
        return None, source_path, None
    return image_bytes, None, None


async def _fetch_from_webdav(client, path: str) -> bytes | None:
    from urllib.parse import quote
    prefix = settings.webdav_path_prefix.strip("/")
    encoded_prefix = quote(prefix) if prefix else ""
    base = settings.webdav_base_url.rstrip("/")
    url = f"{base}/{encoded_prefix}/{path}" if encoded_prefix else f"{base}/{path}"
    try:
        auth = None
        if settings.webdav_username:
            auth = (settings.webdav_username, settings.webdav_password)
        resp = await client.get(url, auth=auth)
        if resp.status_code == 200:
            return resp.content
        logger.warning("webdav %s → %d", url, resp.status_code)
    except Exception as e:
        logger.warning("webdav 연결 실패: %s — %s", url, e)
    return None


def resize_image(raw: bytes, width: int, path: str) -> bytes | None:
    try:
        img = Image.open(io.BytesIO(raw))
    except (UnidentifiedImageError, Exception):
        raise HTTPException(status_code=400, detail="not a valid image")

    if width >= img.width:
        return None

    ratio = width / img.width
    new_height = int(img.height * ratio)
    resized = img.resize((width, new_height), Image.LANCZOS)

    buf = io.BytesIO()
    fmt = img.format or "JPEG"
    resized.save(buf, format=fmt)
    return buf.getvalue()


def guess_media_type(path: str) -> str:
    ext = Path(path).suffix.lower()
    return {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".gif": "image/gif",
        ".webp": "image/webp",
    }.get(ext, "application/octet-stream")
