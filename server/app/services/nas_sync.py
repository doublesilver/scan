"""NAS WebDAV xlsx 파일 주기적 동기화."""

import asyncio
import logging
import os
import tempfile
from pathlib import Path
from urllib.parse import quote
from xml.etree import ElementTree

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

DAV_NS = "DAV:"
TARGET_PATTERNS = ("codepath", "sku_download", "coupangmd")


def _is_target_file(name: str) -> bool:
    lower = name.lower()
    if not lower.endswith(".xlsx"):
        return False
    return any(p in lower for p in TARGET_PATTERNS)


def _parse_propfind(xml_bytes: bytes) -> list[dict]:
    root = ElementTree.fromstring(xml_bytes)
    results = []
    for response in root.findall(f"{{{DAV_NS}}}response"):
        href_el = response.find(f"{{{DAV_NS}}}href")
        if href_el is None or href_el.text is None:
            continue
        propstat = response.find(f"{{{DAV_NS}}}propstat")
        if propstat is None:
            continue
        prop = propstat.find(f"{{{DAV_NS}}}prop")
        if prop is None:
            continue
        collection = prop.find(f"{{{DAV_NS}}}resourcetype/{{{DAV_NS}}}collection")
        if collection is not None:
            continue
        last_modified_el = prop.find(f"{{{DAV_NS}}}getlastmodified")
        last_modified = last_modified_el.text if last_modified_el is not None else None
        href = href_el.text
        name = href.rstrip("/").rsplit("/", 1)[-1]
        from urllib.parse import unquote
        name = unquote(name)
        results.append({"href": href, "name": name, "last_modified": last_modified})
    return results


def _parse_http_date(date_str: str) -> float:
    from email.utils import parsedate_to_datetime
    return parsedate_to_datetime(date_str).timestamp()


class NasSyncService:
    def __init__(self, http_client: httpx.AsyncClient, interval_seconds: int | None = None):
        self._client = http_client
        self._interval = interval_seconds or settings.nas_sync_interval
        self._task: asyncio.Task | None = None
        self._running = False

    async def start(self) -> None:
        if not settings.webdav_base_url:
            logger.info("NAS 동기화 비활성 — webdav_base_url 미설정")
            return
        self._running = True
        self._task = asyncio.create_task(self._loop())
        logger.info("NAS 동기화 시작 (간격=%d초)", self._interval)

    async def _loop(self) -> None:
        while self._running:
            try:
                await self._check_and_sync()
            except Exception as e:
                logger.error("NAS 동기화 오류: %s", e)
            await asyncio.sleep(self._interval)

    async def _check_and_sync(self) -> None:
        base = settings.webdav_base_url.rstrip("/")
        prefix = settings.webdav_path_prefix.strip("/")
        encoded_prefix = quote(prefix) if prefix else ""
        scan_dir = f"/{encoded_prefix}/scan/" if encoded_prefix else "/scan/"
        url = f"{base}{scan_dir}"

        auth = None
        if settings.webdav_username:
            auth = (settings.webdav_username, settings.webdav_password)

        resp = await self._client.request(
            "PROPFIND",
            url,
            headers={"Depth": "1", "Content-Type": "application/xml"},
            auth=auth,
        )
        if resp.status_code not in (200, 207):
            logger.warning("NAS PROPFIND 실패: %s → %d", url, resp.status_code)
            return

        entries = _parse_propfind(resp.content)
        local_dir = Path(settings.xlsx_watch_dir)
        local_dir.mkdir(parents=True, exist_ok=True)

        for entry in entries:
            if not _is_target_file(entry["name"]):
                continue
            if not entry["last_modified"]:
                continue

            remote_mtime = _parse_http_date(entry["last_modified"])
            local_path = local_dir / entry["name"]

            if local_path.exists():
                local_mtime = local_path.stat().st_mtime
                if remote_mtime <= local_mtime:
                    continue

            logger.info("NAS 다운로드: %s", entry["name"])
            await self._download_file(entry["href"], local_path, auth)

    async def _download_file(
        self, href: str, local_path: Path, auth: tuple[str, str] | None
    ) -> None:
        base = settings.webdav_base_url.rstrip("/")
        if href.startswith("http"):
            url = href
        else:
            from urllib.parse import urlparse
            parsed = urlparse(base)
            url = f"{parsed.scheme}://{parsed.netloc}{href}"

        resp = await self._client.get(url, auth=auth)
        if resp.status_code != 200:
            logger.warning("NAS 다운로드 실패: %s → %d", url, resp.status_code)
            return

        fd, tmp_path = tempfile.mkstemp(
            dir=str(local_path.parent), suffix=".tmp"
        )
        closed = False
        try:
            os.write(fd, resp.content)
            os.close(fd)
            closed = True
            os.replace(tmp_path, str(local_path))
            remote_mtime = resp.headers.get("last-modified")
            if remote_mtime:
                ts = _parse_http_date(remote_mtime)
                os.utime(str(local_path), (ts, ts))
            logger.info("NAS 다운로드 완료: %s", local_path.name)
        except Exception:
            if not closed:
                os.close(fd)
            if os.path.exists(tmp_path):
                os.unlink(tmp_path)
            raise

    async def stop(self) -> None:
        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            self._task = None
        logger.info("NAS 동기화 중지")
