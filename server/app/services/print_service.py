import logging
import platform
import time

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


def _tspl_escape(s: str) -> str:
    return s.replace('"', "'").replace("\\", "")


def generate_tspl(product_name: str, barcode: str, sku_id: str, quantity: int) -> str:
    name = _tspl_escape(product_name[:30])
    barcode = _tspl_escape(barcode)
    sku_id = _tspl_escape(sku_id)
    commands = [
        f"SIZE {settings.label_width_mm} mm, {settings.label_height_mm} mm",
        f"GAP {settings.label_gap_mm} mm, 0 mm",
        f"DENSITY {settings.label_density}",
        "SPEED 4",
        "DIRECTION 1",
        "CODEPAGE UTF-8",
        "CLS",
        f'TEXT 20,20,"K",0,24,24,"{name}"',
        f'BARCODE 20,70,"128",80,1,0,2,4,"{barcode}"',
        f'TEXT 20,180,"3",0,1,1,"SKU: {sku_id}"',
        f"PRINT {quantity},1",
    ]
    return "\r\n".join(commands) + "\r\n"


def _friendly_agent_error(message: str, barcode: str) -> str:
    """bartender agent에서 돌아온 에러를 작업자 친화 문구로 바꿔준다."""
    lower = message.lower()
    tail5 = barcode[-5:] if barcode else "?????"
    if "template" in lower or "btw" in lower or "파일" in message:
        return f"라벨 템플릿 없음 ({tail5}.btw)"
    if "printer" in lower and ("offline" in lower or "not found" in lower):
        return "프린터 오프라인 — 전원/케이블 확인"
    if "timeout" in lower:
        return "프린터 응답 없음 — 상태 확인"
    return message


def print_label(product_name: str, barcode: str, sku_id: str, quantity: int) -> dict:
    start = time.perf_counter()

    if settings.print_agent_url:
        via = "agent"
        try:
            resp = httpx.post(
                settings.print_agent_url,
                json={
                    "barcode": barcode,
                    "quantity": quantity,
                    "printer_name": settings.printer_name or "TSC TE210",
                },
                timeout=30,
            )
            elapsed_ms = int((time.perf_counter() - start) * 1000)
            raw_text = resp.text
            try:
                body = resp.json()
            except Exception:
                body = None

            is_http_error = resp.status_code >= 400
            body_status = (body or {}).get("status", "").lower() if isinstance(body, dict) else ""
            is_body_error = body_status in ("error", "fail", "failed")

            if is_http_error or is_body_error:
                raw_msg = (body or {}).get("message") if isinstance(body, dict) else None
                if not raw_msg:
                    raw_msg = f"HTTP {resp.status_code}: {raw_text[:200]}"
                return {
                    "status": "error",
                    "message": _friendly_agent_error(raw_msg, barcode),
                    "via": via,
                    "http_status": resp.status_code,
                    "elapsed_ms": elapsed_ms,
                    "raw_response": raw_text,
                }

            ok_msg = (
                (body or {}).get("message") if isinstance(body, dict) else None
            ) or f"{quantity}장 인쇄 완료"
            return {
                "status": "ok",
                "message": ok_msg,
                "via": via,
                "http_status": resp.status_code,
                "elapsed_ms": elapsed_ms,
                "raw_response": raw_text,
            }
        except httpx.TimeoutException:
            elapsed_ms = int((time.perf_counter() - start) * 1000)
            logger.error("물류PC agent 타임아웃")
            return {
                "status": "error",
                "message": "물류PC 응답 없음 (30초 타임아웃) — 미니PC에서 물류PC 연결 확인",
                "via": via,
                "http_status": None,
                "elapsed_ms": elapsed_ms,
                "raw_response": "",
            }
        except Exception as e:
            elapsed_ms = int((time.perf_counter() - start) * 1000)
            logger.error("물류PC agent 호출 실패: %s", e)
            return {
                "status": "error",
                "message": f"물류PC 연결 실패: {e}",
                "via": via,
                "http_status": None,
                "elapsed_ms": elapsed_ms,
                "raw_response": "",
            }

    if platform.system() != "Windows":
        elapsed_ms = int((time.perf_counter() - start) * 1000)
        logger.warning("프린터 미지원 OS (%s) — dry run", platform.system())
        return {
            "status": "dry_run",
            "message": "Windows 환경에서만 인쇄 가능",
            "via": "dry_run",
            "http_status": None,
            "elapsed_ms": elapsed_ms,
            "raw_response": "",
        }

    via = "pywin32"
    try:
        import win32print
    except ImportError:
        elapsed_ms = int((time.perf_counter() - start) * 1000)
        return {
            "status": "error",
            "message": "pywin32 미설치 — pip install pywin32",
            "via": via,
            "http_status": None,
            "elapsed_ms": elapsed_ms,
            "raw_response": "",
        }

    tspl = generate_tspl(product_name, barcode, sku_id, quantity)
    printer_name = settings.printer_name
    if not printer_name:
        printers = [p[2] for p in win32print.EnumPrinters(win32print.PRINTER_ENUM_LOCAL)]
        tsc = next((n for n in printers if "TSC" in n.upper()), None)
        if tsc:
            printer_name = tsc
        else:
            elapsed_ms = int((time.perf_counter() - start) * 1000)
            logger.error("TSC 프린터 미발견. 설치된 프린터: %s", printers)
            return {
                "status": "error",
                "message": "TSC 프린터를 찾을 수 없습니다",
                "via": via,
                "http_status": None,
                "elapsed_ms": elapsed_ms,
                "raw_response": f"installed={printers}",
            }

    try:
        handle = win32print.OpenPrinter(printer_name)
        try:
            win32print.StartDocPrinter(handle, 1, ("Label", None, "RAW"))
            win32print.StartPagePrinter(handle)
            win32print.WritePrinter(handle, tspl.encode("utf-8"))
            win32print.EndPagePrinter(handle)
            win32print.EndDocPrinter(handle)
        finally:
            win32print.ClosePrinter(handle)

        elapsed_ms = int((time.perf_counter() - start) * 1000)
        logger.info("라벨 인쇄 완료: %s x%d (%s)", barcode, quantity, printer_name)
        return {
            "status": "ok",
            "message": f"{quantity}장 인쇄 완료",
            "via": via,
            "http_status": None,
            "elapsed_ms": elapsed_ms,
            "raw_response": f"printer={printer_name}",
            "printer": printer_name,
        }
    except Exception as e:
        elapsed_ms = int((time.perf_counter() - start) * 1000)
        logger.error("인쇄 실패: %s", e)
        return {
            "status": "error",
            "message": str(e),
            "via": via,
            "http_status": None,
            "elapsed_ms": elapsed_ms,
            "raw_response": "",
        }
