"""xlsx 파일 감시 — 지정 폴더에 새 파일 추가 시 자동 파싱."""

import asyncio
import logging
import os
import time
from pathlib import Path

from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer

from app.config import settings

logger = logging.getLogger(__name__)

_observer: Observer | None = None

_DEBOUNCE_SECONDS = 2.0


class XlsxHandler(FileSystemEventHandler):
    def __init__(self, loop: asyncio.AbstractEventLoop) -> None:
        self._loop = loop
        self._last_handled: dict[str, float] = {}

    def _should_handle(self, path: str) -> bool:
        now = time.monotonic()
        if now - self._last_handled.get(path, 0) < _DEBOUNCE_SECONDS:
            return False
        self._last_handled[path] = now
        return True

    def on_created(self, event) -> None:
        if event.is_directory:
            return
        path = event.src_path
        if not path.endswith(".xlsx"):
            return
        if not self._should_handle(path):
            return
        logger.info("파일 감지: %s", path)
        if self._loop and self._loop.is_running():
            asyncio.run_coroutine_threadsafe(_handle_new_file(path), self._loop)

    def on_modified(self, event) -> None:
        if event.is_directory:
            return
        path = event.src_path
        if not path.endswith(".xlsx"):
            return
        if not self._should_handle(path):
            return
        logger.info("파일 변경 감지: %s", path)
        if self._loop and self._loop.is_running():
            asyncio.run_coroutine_threadsafe(_handle_new_file(path), self._loop)


async def _handle_new_file(file_path: str) -> None:
    """새 xlsx 파일을 파싱하여 DB에 적재."""
    from app.db.database import get_db
    from app.services.codepath_parser import parse_codepath
    from app.services.sku_parser import parse_sku_download

    prev_size = -1
    for _ in range(10):
        size = os.path.getsize(file_path)
        if size == prev_size:
            break
        prev_size = size
        await asyncio.sleep(0.5)

    db = await get_db()
    name = Path(file_path).name.lower()

    try:
        if "codepath" in name:
            stats = await parse_codepath(db, file_path)
            logger.info("자동 갱신 (codepath): 추가=%d, 갱신=%d", stats["added"], stats["updated"])
        elif "sku_download" in name or "coupangmd" in name:
            stats = await parse_sku_download(db, file_path)
            logger.info(
                "자동 갱신 (sku_download): 추가=%d, 갱신=%d", stats["added"], stats["updated"]
            )
        else:
            logger.info("무시: 알 수 없는 xlsx 파일 %s", name)
    except Exception as e:
        logger.error("자동 갱신 실패 (%s): %s", file_path, e)


def start_watcher() -> None:
    """xlsx 감시 시작 (백그라운드 스레드)."""
    global _observer
    watch_dir = Path(settings.xlsx_watch_dir)
    watch_dir.mkdir(parents=True, exist_ok=True)

    loop = asyncio.get_running_loop()
    handler = XlsxHandler(loop)
    _observer = Observer()
    _observer.schedule(handler, str(watch_dir), recursive=False)
    _observer.daemon = True
    _observer.start()
    logger.info("xlsx 파일 감시 시작: %s", watch_dir)


def stop_watcher() -> None:
    """xlsx 감시 중지."""
    global _observer
    if _observer:
        _observer.stop()
        _observer.join(timeout=3)
        _observer = None
        logger.info("xlsx 파일 감시 중지")
