import logging
import warnings

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.config import settings

logger = logging.getLogger(__name__)


class ApiKeyMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        if not settings.api_key:
            if request.method in {"POST", "PATCH", "DELETE", "PUT"}:
                path = request.url.path
                _WRITE_WHITELIST = (
                    "/static",
                    "/apk",
                    "/health",
                    "/admin/map-editor",
                    "/api/app-version",
                )
                if not any(path.startswith(p) for p in _WRITE_WHITELIST):
                    warnings.warn(
                        f"api_key 미설정 상태에서 쓰기 요청: {request.method} {path}",
                        stacklevel=2,
                    )
                    logger.warning(
                        "api_key 미설정 — 쓰기 요청 허용 중: %s %s", request.method, path
                    )
            return await call_next(request)

        path = request.url.path
        if path.startswith(
            ("/static", "/apk", "/docs", "/openapi.json", "/health", "/admin/map-editor")
        ):
            return await call_next(request)

        key = request.headers.get("X-API-Key") or request.query_params.get("api_key")
        if key != settings.api_key:
            return JSONResponse(status_code=401, content={"detail": "invalid api key"})

        return await call_next(request)
