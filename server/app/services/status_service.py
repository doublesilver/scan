import time
from datetime import datetime
from pathlib import Path

import aiosqlite

from app.config import settings

_server_start_time: float = 0.0


def record_start_time() -> None:
    global _server_start_time
    _server_start_time = time.time()


def _format_uptime() -> str:
    elapsed = int(time.time() - _server_start_time)
    hours, remainder = divmod(elapsed, 3600)
    minutes, _ = divmod(remainder, 60)
    if hours > 0:
        return f"{hours}시간 {minutes}분"
    return f"{minutes}분"


def _dir_size_mb(path: Path) -> float:
    if not path.exists():
        return 0.0
    total = sum(f.stat().st_size for f in path.rglob("*") if f.is_file())
    return round(total / (1024 * 1024), 1)


def _file_count(path: Path) -> int:
    if not path.exists():
        return 0
    return sum(1 for f in path.iterdir() if f.is_file())


async def get_status(db: aiosqlite.Connection) -> dict:
    tables = {
        "products": "SELECT COUNT(*) FROM product",
        "barcodes": "SELECT COUNT(*) FROM barcode",
        "images": "SELECT COUNT(*) FROM image",
    }
    db_counts = {}
    for key, sql in tables.items():
        cursor = await db.execute(sql)
        row = await cursor.fetchone()
        db_counts[key] = row[0] if row else 0

    stock_sql = "SELECT COUNT(*) FROM stock"
    try:
        cursor = await db.execute(stock_sql)
        row = await cursor.fetchone()
        db_counts["stock_entries"] = row[0] if row else 0
    except Exception:
        db_counts["stock_entries"] = 0

    last_parse = None
    try:
        cursor = await db.execute(
            "SELECT file_name, parsed_at, added_count, updated_count "
            "FROM parse_log ORDER BY id DESC LIMIT 1"
        )
        row = await cursor.fetchone()
        if row:
            last_parse = {
                "file": row[0],
                "parsed_at": row[1],
                "added": row[2],
                "updated": row[3],
            }
    except Exception:
        pass

    cache_dir = Path(settings.image_cache_dir)
    backup_dir = Path("data/backups")

    return {
        "server": {
            "uptime": _format_uptime(),
            "version": settings.version,
        },
        "database": db_counts,
        "last_parse": last_parse,
        "nas_sync": {
            "last_check": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "status": "connected" if settings.webdav_base_url else "disconnected",
        },
        "disk": {
            "cache_size_mb": _dir_size_mb(cache_dir),
            "cache_limit_mb": settings.image_cache_max_size_mb,
            "backup_count": _file_count(backup_dir),
        },
    }
