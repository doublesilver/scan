import logging
import threading
from datetime import datetime
from pathlib import Path

import gspread

from app.config import settings

logger = logging.getLogger(__name__)

_client = None
_client_lock = threading.Lock()


def _get_client():
    global _client
    with _client_lock:
        if _client is None:
            cred_path = Path(settings.gsheet_credentials)
            if not cred_path.exists():
                raise FileNotFoundError(f"credentials.json 없음: {cred_path}")
            _client = gspread.service_account(filename=str(cred_path))
        return _client


def add_to_cart(
    barcode: str,
    sku_id: str,
    product_name: str,
    quantity: int,
    requested_by: str = "PDA",
) -> dict:
    if not settings.gsheet_url:
        return {"status": "error", "message": "구글시트 URL 미설정"}

    try:
        gc = _get_client()
        sheet = gc.open_by_url(settings.gsheet_url).sheet1

        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        row = [now, sku_id, product_name, quantity]
        sheet.append_row(row, value_input_option="USER_ENTERED")

        logger.info("장바구니 추가: %s x%d", sku_id, quantity)
        return {"status": "ok", "message": f"{product_name} {quantity}개 장바구니 추가"}
    except FileNotFoundError as e:
        return {"status": "error", "message": str(e)}
    except Exception:
        logger.error("gsheets error", exc_info=True)
        return {"status": "error", "message": "시트 연동 실패"}
