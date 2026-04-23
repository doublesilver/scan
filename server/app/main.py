import logging
import os
from contextlib import asynccontextmanager

import httpx
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api.coupang_routes import router as coupang_router
from app.api.map_routes import router as map_router
from app.api.qr_routes import router as qr_router
from app.api.routes import router
from app.api.shelf_routes import router as shelf_router
from app.api.stats_routes import router as stats_router
from app.api.warehouse_routes import router as warehouse_router
from app.api.ws_routes import router as ws_router
from app.config import settings
from app.db.database import close_db, get_db
from app.db.migrate_map import migrate_json_to_tables
from app.middleware.auth import ApiKeyMiddleware
from app.services.file_watcher import start_watcher, stop_watcher
from app.services.nas_sync import NasSyncService

# from app.services.scheduler import start_scheduler, stop_scheduler  # 납품 시연용 비활성화 (추후 승인 후 복구)
from app.services.status_service import record_start_time

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    record_start_time()
    logger.info("서버 시작 — DB 초기화")
    write_db = await get_db()
    logger.info("DB 준비 완료: %s", settings.db_path)
    await migrate_json_to_tables(write_db)
    app.state.http_client = httpx.AsyncClient(timeout=30.0)
    start_watcher()
    nas_sync = NasSyncService(app.state.http_client)
    app.state.nas_sync = nas_sync
    await nas_sync.start()
    # await start_scheduler(app)  # 납품 시연용 비활성화
    yield
    # await stop_scheduler()  # 납품 시연용 비활성화
    await nas_sync.stop()
    stop_watcher()
    await app.state.http_client.aclose()
    logger.info("서버 종료 — DB 연결 해제")
    await close_db()


app = FastAPI(
    title="물류창고 스캐너 API",
    description="PDA 바코드 스캔 → 상품 정보 + 이미지 조회",
    version=settings.version,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(ApiKeyMiddleware)


app.include_router(router)
app.include_router(map_router)
app.include_router(shelf_router)
app.include_router(warehouse_router)
app.include_router(qr_router)
app.include_router(coupang_router)
app.include_router(stats_router)
app.include_router(ws_router)

_apk_dir = os.path.join(os.path.dirname(__file__), "..", "apk")
os.makedirs(_apk_dir, exist_ok=True)
if os.path.exists(_apk_dir):
    app.mount("/apk", StaticFiles(directory=_apk_dir), name="apk")

_static_dir = os.path.join(os.path.dirname(__file__), "..", "static")
if os.path.exists(_static_dir):
    app.mount("/static", StaticFiles(directory=_static_dir), name="static")


_admin_dir = os.path.join(_static_dir, "admin")
if os.path.exists(os.path.join(_admin_dir, "assets")):
    app.mount(
        "/admin/assets",
        StaticFiles(directory=os.path.join(_admin_dir, "assets")),
        name="admin-assets",
    )


@app.get("/admin/map-editor", include_in_schema=False)
async def map_editor():
    return FileResponse(os.path.join(_static_dir, "map-editor.html"))


@app.get("/admin", include_in_schema=False)
@app.get("/admin/{rest:path}", include_in_schema=False)
async def admin_spa(rest: str = ""):
    # 정적 파일 요청은 직접 서빙
    if rest:
        file_path = os.path.join(_admin_dir, rest)
        if os.path.isfile(file_path):
            return FileResponse(file_path)
    return FileResponse(os.path.join(_admin_dir, "index.html"))


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=os.getenv("DEBUG", "false").lower() == "true",
    )
