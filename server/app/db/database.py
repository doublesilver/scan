import asyncio
import logging

import aiosqlite

from app.config import settings
from app.db.schema import MIGRATIONS, SCHEMA_SQL, SCHEMA_VERSION

logger = logging.getLogger(__name__)

_write_db: aiosqlite.Connection | None = None
_read_db: aiosqlite.Connection | None = None
_write_lock = asyncio.Lock()
_read_lock = asyncio.Lock()

write_lock = asyncio.Lock()


async def _init_connection(db: aiosqlite.Connection) -> None:
    db.row_factory = aiosqlite.Row
    await db.execute("PRAGMA journal_mode=WAL")
    await db.execute("PRAGMA foreign_keys=ON")
    await db.execute("PRAGMA busy_timeout=5000")


async def _get_current_version(db: aiosqlite.Connection) -> int:
    cursor = await db.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='db_version'"
    )
    if not await cursor.fetchone():
        return 0
    cursor = await db.execute("SELECT MAX(version) FROM db_version")
    row = await cursor.fetchone()
    return row[0] if row and row[0] else 1


async def _run_migrations(db: aiosqlite.Connection) -> None:
    current = await _get_current_version(db)
    for version in sorted(MIGRATIONS.keys()):
        if version <= current:
            continue
        logger.info("마이그레이션 실행: v%d → v%d", current, version)
        for sql in MIGRATIONS[version]:
            await db.execute(sql)
        await db.execute("INSERT INTO db_version (version) VALUES (?)", (version,))
        await db.commit()
        current = version
    if current < SCHEMA_VERSION:
        await db.execute(
            "INSERT OR IGNORE INTO db_version (version) VALUES (?)",
            (SCHEMA_VERSION,),
        )
        await db.commit()


async def get_db() -> aiosqlite.Connection:
    global _write_db
    if _write_db is None:
        async with _write_lock:
            if _write_db is None:
                db_path = settings.db_path
                db_path.parent.mkdir(parents=True, exist_ok=True)
                _write_db = await aiosqlite.connect(str(db_path))
                await _init_connection(_write_db)
                await _write_db.executescript(SCHEMA_SQL)
                await _write_db.commit()
                await _run_migrations(_write_db)
                await _write_db.execute("PRAGMA optimize")
    return _write_db


async def get_read_db() -> aiosqlite.Connection:
    global _read_db
    if _read_db is None:
        async with _read_lock:
            if _read_db is None:
                db_path = settings.db_path
                _read_db = await aiosqlite.connect(str(db_path))
                await _init_connection(_read_db)
                await _read_db.execute("PRAGMA query_only=ON")
    return _read_db


async def close_db() -> None:
    global _write_db, _read_db
    if _write_db is not None:
        await _write_db.execute("PRAGMA optimize")
        await _write_db.close()
        _write_db = None
    if _read_db is not None:
        await _read_db.close()
        _read_db = None
