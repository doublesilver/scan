import logging
import os
from contextlib import asynccontextmanager

import httpx
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api.routes import router
from app.config import settings
from app.db.database import close_db, get_db
from app.services.file_watcher import start_watcher, stop_watcher
from app.services.nas_sync import NasSyncService
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
    await get_db()
    logger.info("DB 준비 완료: %s", settings.db_path)
    app.state.http_client = httpx.AsyncClient(timeout=30.0)
    start_watcher()
    nas_sync = NasSyncService(app.state.http_client)
    app.state.nas_sync = nas_sync
    await nas_sync.start()
    yield
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


app.include_router(router)

_apk_dir = os.path.join(os.path.dirname(__file__), "..", "apk")
os.makedirs(_apk_dir, exist_ok=True)
if os.path.exists(_apk_dir):
    app.mount("/apk", StaticFiles(directory=_apk_dir), name="apk")

_static_dir = os.path.join(os.path.dirname(__file__), "..", "static")
if os.path.exists(_static_dir):
    app.mount("/static", StaticFiles(directory=_static_dir), name="static")


@app.get("/admin/map-editor", include_in_schema=False)
async def map_editor():
    return FileResponse(os.path.join(_static_dir, "map-editor.html"))


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
