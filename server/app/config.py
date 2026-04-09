from pathlib import Path

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # 서버
    version: str = "1.0.0"
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

    # URL 임포트
    url_import_file: str = "./data/xlsx/purchase_urls.xlsx"

    # NAS 동기화
    nas_sync_interval: int = 60

    # 이미지 캐시 (M4)
    image_cache_dir: str = "./data/cache"
    image_cache_max_size_mb: int = 500

    # 브랜드 필터
    brand_filter: list[str] = ["스페이스쉴드"]

    # 라벨 프린터
    printer_name: str = ""
    label_width_mm: int = 60
    label_height_mm: int = 40
    label_gap_mm: int = 3
    label_density: int = 8
    print_agent_url: str = ""

    # 구글시트 장바구니
    gsheet_credentials: str = "./credentials.json"
    gsheet_url: str = ""

    # 선반 사진 NAS 경로
    shelf_photo_nas_prefix: str = "shelf_photos"

    # API Key 인증
    api_key: str = ""

    # CORS
    cors_origins: list[str] = ["http://localhost", "http://127.0.0.1"]

    model_config = {"env_file": ".env", "env_file_encoding": "latin-1"}

    @property
    def db_path(self) -> Path:
        """sqlite:///data/scanner.db -> data/scanner.db"""
        raw = self.database_url.split("///")[-1]
        return Path(raw)


settings = Settings()
