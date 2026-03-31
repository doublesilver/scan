import hashlib
import io
import logging
from pathlib import Path
from urllib.parse import urlparse

from fastapi import HTTPException
from PIL import Image, UnidentifiedImageError

from app.config import settings

ALLOWED_IMAGE_HOSTS = {
    "img1.coupangcdn.com",
    "img2.coupangcdn.com",
    "img3.coupangcdn.com",
    "img4.coupangcdn.com",
    "img5.coupangcdn.com",
    "img6.coupangcdn.com",
    "thumbnail6.coupangcdn.com",
    "thumbnail7.coupangcdn.com",
    "thumbnail8.coupangcdn.com",
    "thumbnail9.coupangcdn.com",
    "thumbnail10.coupangcdn.com",
}

logger = logging.getLogger(__name__)

_cache_save_count = 0


def _check_and_evict(cache_base: Path) -> None:
    global _cache_save_count
    _cache_save_count += 1
    if _cache_save_count % 100 != 0:
        return

    total = sum(f.stat().st_size for f in cache_base.rglob("*") if f.is_file())
    limit = settings.image_cache_max_size_mb * 1024 * 1024
    if total <= limit:
        return

    target = limit - 50 * 1024 * 1024
    files = sorted(
        (f for f in cache_base.rglob("*") if f.is_file()),
        key=lambda f: f.stat().st_mtime,
    )
    for f in files:
        if total <= target:
            break
        try:
            size = f.stat().st_size
            f.unlink()
            total -= size
            logger.info("cache evict: %s", f)
        except Exception as e:
            logger.warning("cache evict 실패: %s — %s", f, e)


def validate_path(base: Path, sub_path: str) -> Path:
    resolved = (base / sub_path).resolve()
    if not resolved.is_relative_to(base):
        raise HTTPException(status_code=400, detail="invalid path")
    return resolved


async def get_image_data(
    path: str, width: int | None, http_client
) -> tuple[bytes | None, Path | None, int | None]:
    if ".." in path:
        raise HTTPException(status_code=400, detail="invalid path")
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
    elif path.startswith("http://") or path.startswith("https://"):
        parsed = urlparse(path)
        if parsed.hostname not in ALLOWED_IMAGE_HOSTS:
            logger.warning("url fetch 차단 (비허용 호스트): %s", path)
            raise HTTPException(status_code=403, detail="host not allowed")
        url_hash = hashlib.sha256(path.encode()).hexdigest()
        ext = Path(parsed.path).suffix or ".jpg"
        cache_path = validate_path(cache_base, f"url_cache/{url_hash}{ext}")
        if cache_path.exists() and cache_path.is_file():
            logger.info("url cache hit: %s", path)
            source_path = cache_path
        else:
            image_bytes = await _fetch_from_url(http_client, path)
            if image_bytes:
                cache_path.parent.mkdir(parents=True, exist_ok=True)
                _check_and_evict(cache_base)
                cache_path.write_bytes(image_bytes)
                logger.info("image url fetch + cached: %s", path)
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
                _check_and_evict(cache_base)
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
            _check_and_evict(cache_base)
            resized_path.write_bytes(resized)
            logger.info("resized + cached: %s (w=%d)", path, width)
            return resized, None, width

    if source_path:
        return None, source_path, None
    return image_bytes, None, None


async def _fetch_from_url(client, url: str) -> bytes | None:
    try:
        resp = await client.get(url)
        if resp.status_code == 200:
            return resp.content
        logger.warning("url fetch %s → %d", url, resp.status_code)
    except Exception as e:
        logger.warning("url fetch 실패: %s — %s", url, e)
    return None


ALLOWED_WEBDAV_PREFIXES = ("img/", "real_image/", "shelf_photos/")


async def _fetch_from_webdav(client, path: str) -> bytes | None:
    if not path.startswith(ALLOWED_WEBDAV_PREFIXES):
        logger.warning("webdav 경로 차단 (비허용 prefix): %s", path)
        return None
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
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="not a valid image")
    except Exception:
        raise HTTPException(status_code=500, detail="image processing error")

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


async def upload_to_webdav(http_client, file_bytes: bytes, remote_path: str) -> bool:
    from urllib.parse import quote
    resized = resize_image(file_bytes, 1920, remote_path)
    data = resized if resized is not None else file_bytes

    prefix = settings.webdav_path_prefix.strip("/")
    encoded_prefix = quote(prefix) if prefix else ""
    base = settings.webdav_base_url.rstrip("/")
    url = f"{base}/{encoded_prefix}/{remote_path}" if encoded_prefix else f"{base}/{remote_path}"

    auth = None
    if settings.webdav_username:
        auth = (settings.webdav_username, settings.webdav_password)

    try:
        resp = await http_client.put(
            url,
            content=data,
            headers={"Content-Type": "image/jpeg"},
            auth=auth,
            timeout=5.0,
        )
        if resp.status_code in (201, 204):
            return True
        if resp.status_code == 409:
            parent = remote_path.rsplit("/", 1)[0] if "/" in remote_path else ""
            if parent:
                parent_url = f"{base}/{encoded_prefix}/{parent}" if encoded_prefix else f"{base}/{parent}"
                await http_client.request("MKCOL", parent_url, auth=auth, timeout=5.0)
            resp = await http_client.put(
                url,
                content=data,
                headers={"Content-Type": "image/jpeg"},
                auth=auth,
                timeout=5.0,
            )
            if resp.status_code in (201, 204):
                return True
        logger.error("webdav upload 실패: %s → %d", url, resp.status_code)
        raise HTTPException(status_code=502, detail=f"NAS upload failed: {resp.status_code}")
    except HTTPException:
        raise
    except Exception as e:
        logger.error("webdav upload 오류: %s — %s", url, e)
        raise HTTPException(status_code=502, detail="NAS upload error")


async def delete_from_webdav(http_client, remote_path: str) -> bool:
    from urllib.parse import quote
    prefix = settings.webdav_path_prefix.strip("/")
    encoded_prefix = quote(prefix) if prefix else ""
    base = settings.webdav_base_url.rstrip("/")
    url = f"{base}/{encoded_prefix}/{remote_path}" if encoded_prefix else f"{base}/{remote_path}"

    auth = None
    if settings.webdav_username:
        auth = (settings.webdav_username, settings.webdav_password)

    try:
        resp = await http_client.delete(url, auth=auth, timeout=5.0)
        if resp.status_code == 204:
            return True
        if resp.status_code == 404:
            return True
        logger.warning("webdav delete %s → %d", url, resp.status_code)
        return False
    except Exception as e:
        logger.warning("webdav delete 오류: %s — %s", url, e)
        return False
