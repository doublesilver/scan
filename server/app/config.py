from pathlib import Path

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # 서버
    host: str = "0.0.0.0"
    port: int = 8000

    # 데이터베이스
    database_url: str = "sqlite+aiosqlite:///data/scanner.db"

    # NAS WebDAV (M4)
    webdav_base_url: str = ""
    webdav_path_prefix: str = ""
    webdav_username: str = ""
    webdav_password: str = ""

    # xlsx 경로
    xlsx_watch_dir: str = "./data/xlsx"
    codepath_file: str = "codepath.xlsx"
    sku_download_pattern: str = "coupangmd00_sku_download_*.xlsx"

    # NAS 동기화
    nas_sync_interval: int = 60

    # 이미지 캐시 (M4)
    image_cache_dir: str = "./data/cache"
    image_cache_max_size_mb: int = 500

    # 브랜드 필터
    brand_filter: list[str] = ["스페이스쉴드"]

    # CORS
    cors_origins: list[str] = ["*"]

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}

    @property
    def db_path(self) -> Path:
        """sqlite:///data/scanner.db -> data/scanner.db"""
        raw = self.database_url.split("///")[-1]
        return Path(raw)


settings = Settings()
