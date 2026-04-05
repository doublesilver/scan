import logging
import platform

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


def print_label(product_name: str, barcode: str, sku_id: str, quantity: int) -> dict:
    if settings.print_agent_url:
        try:
            resp = httpx.post(
                settings.print_agent_url,
                json={"barcode": barcode, "quantity": quantity, "printer_name": settings.printer_name or "TSC TE210"},
                timeout=30,
            )
            return resp.json()
        except Exception as e:
            logger.error("print agent 호출 실패: %s", e)
            return {"status": "error", "message": str(e)}

    if platform.system() != "Windows":
        logger.warning("프린터 미지원 OS (%s) — dry run", platform.system())
        return {"status": "dry_run", "message": "Windows 환경에서만 인쇄 가능"}

    try:
        import win32print
    except ImportError:
        return {"status": "error", "message": "pywin32 미설치 — pip install pywin32"}

    tspl = generate_tspl(product_name, barcode, sku_id, quantity)
    printer_name = settings.printer_name
    if not printer_name:
        printers = [p[2] for p in win32print.EnumPrinters(win32print.PRINTER_ENUM_LOCAL)]
        tsc = next((n for n in printers if "TSC" in n.upper()), None)
        if tsc:
            printer_name = tsc
        else:
            logger.error("TSC 프린터 미발견. 설치된 프린터: %s", printers)
            return {"status": "error", "message": "TSC 프린터를 찾을 수 없습니다"}

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

        logger.info("라벨 인쇄 완료: %s x%d (%s)", barcode, quantity, printer_name)
        return {"status": "ok", "message": f"{quantity}장 인쇄 완료", "printer": printer_name}
    except Exception as e:
        logger.error("인쇄 실패: %s", e)
        return {"status": "error", "message": str(e)}
