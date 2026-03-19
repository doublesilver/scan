"""xlsx 파싱 CLI — codepath + sku_download를 DB에 적재."""

import asyncio
import glob
import logging
import sys
from pathlib import Path

# server/ 디렉토리에서 실행되도록 path 조정
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from app.db.database import get_db, close_db
from app.services.codepath_parser import parse_codepath
from app.services.sku_parser import parse_sku_download

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


async def main() -> None:
    db = await get_db()

    # codepath.xlsx 파싱
    codepath_candidates = [
        "data/xlsx/codepath.xlsx",
        "../kakao_chat_extract/codepath.xlsx",
    ]
    for path in codepath_candidates:
        if Path(path).exists():
            logger.info("codepath 파싱 시작: %s", path)
            stats = await parse_codepath(db, path)
            logger.info("codepath 결과: %s", stats)
            break
    else:
        logger.warning("codepath.xlsx를 찾을 수 없습니다")

    # sku_download.xlsx 파싱
    sku_candidates = [
        *glob.glob("data/xlsx/coupangmd00_sku_download_*.xlsx"),
        *glob.glob("../kakao_chat_extract/coupangmd00_sku_download_*.xlsx"),
    ]
    if sku_candidates:
        path = sku_candidates[0]
        logger.info("sku_download 파싱 시작: %s", path)
        stats = await parse_sku_download(db, path)
        logger.info("sku_download 결과: %s", stats)
    else:
        logger.warning("sku_download.xlsx를 찾을 수 없습니다")

    await close_db()


if __name__ == "__main__":
    asyncio.run(main())
