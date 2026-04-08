"""
물류PC BarTender print agent
(scan 프로젝트 — 미니PC FastAPI 서버가 POST로 호출하는 대상)

역할:
- HTTP 요청을 받아 바코드 끝 5자리로 .btw 템플릿을 찾고 BarTender로 인쇄
- 시작 시 TEMPLATE_DIRS 를 한 번 walk 해서 {끝5자리: 전체경로} 인덱스 구성
- 템플릿 못 찾으면 에러 반환 (폴백 금지)
- /health 로 상태 조회 가능 (인쇄 안 트리거)
- /reload 로 템플릿 재인덱싱

설치 위치: C:\\print_agent.py
설정 파일: C:\\print_agent_config.json (없으면 기본값 사용)
로그: C:\\print_agent.log

엔드포인트:
- POST /print    → 실제 인쇄
- GET  /health   → 상태 확인 (안전, 부작용 없음)
- POST /reload   → 템플릿 인덱스 재구성
"""

import json
import logging
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, HTTPServer
from logging.handlers import RotatingFileHandler

# ── 기본 설정 ──────────────────────────────────────────
DEFAULTS = {
    "port": 7777,
    "template_dirs": [
        r"C:\Users\User\Desktop\[02] 양식",
    ],
    "bartender_exe": r"C:\Program Files\Seagull\BarTender 2022\BarTend.exe",
    "default_printer": "TSC TE210 USB001",
    "log_file": r"C:\print_agent.log",
    "temp_btw": r"C:\print_agent_temp.btw",
    "print_timeout_sec": 30,
    # 상품명 검증 — 서버가 product_name 을 같이 보내면 파일명과 키워드 비교
    # overlap 이 아래 임계값 미만이면 인쇄 거부 (지뢰 바코드 방어)
    "name_match_min_overlap": 0.3,
    # 검증 자체를 끄려면 false — product_name 을 무시하고 바코드 끝5자리만 신뢰
    "verify_product_name": True,
}

CONFIG_PATH = r"C:\print_agent_config.json"


def load_config() -> dict:
    cfg = dict(DEFAULTS)
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, encoding="utf-8") as f:
                user = json.load(f)
            cfg.update(user)
        except Exception as e:
            print(f"[WARN] config 로드 실패, 기본값 사용: {e}")
    return cfg


CFG = load_config()

# ── 로깅 ───────────────────────────────────────────────
logger = logging.getLogger("print_agent")
logger.setLevel(logging.INFO)
try:
    handler = RotatingFileHandler(
        CFG["log_file"], maxBytes=2_000_000, backupCount=3, encoding="utf-8"
    )
except Exception:
    handler = logging.StreamHandler(sys.stdout)
handler.setFormatter(
    logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", "%Y-%m-%d %H:%M:%S")
)
logger.addHandler(handler)
logger.addHandler(logging.StreamHandler(sys.stdout))

# ── 템플릿 인덱스 ──────────────────────────────────────
# {끝5자리 문자열: 전체 파일 경로}
_index: dict[str, str] = {}
_index_lock = threading.Lock()
_index_built_at: float = 0.0

SUFFIX_PATTERN = re.compile(r"(\d{5})\.btw$", re.IGNORECASE)


def build_index() -> tuple[int, list[str]]:
    """template_dirs 전체를 walk 해서 끝 5자리 숫자로 끝나는 .btw 파일을 인덱싱."""
    new_index: dict[str, str] = {}
    collisions: list[str] = []
    for base in CFG["template_dirs"]:
        if not os.path.isdir(base):
            logger.warning("템플릿 디렉토리 없음: %s", base)
            continue
        for root, _, files in os.walk(base):
            for name in files:
                m = SUFFIX_PATTERN.search(name)
                if not m:
                    continue
                key = m.group(1)
                full = os.path.join(root, name)
                if key in new_index:
                    collisions.append(f"{key}: {new_index[key]} ↔ {full}")
                    # 충돌 시 첫 발견만 유지 (OS walk 순서 기준)
                    continue
                new_index[key] = full
    global _index, _index_built_at
    with _index_lock:
        _index = new_index
        _index_built_at = time.time()
    logger.info("템플릿 인덱싱 완료: %d개 엔트리, %d개 충돌", len(new_index), len(collisions))
    if collisions:
        for c in collisions[:10]:
            logger.warning("충돌: %s", c)
        if len(collisions) > 10:
            logger.warning("... 외 %d개 충돌 더 있음", len(collisions) - 10)
    return len(new_index), collisions[:10]


def find_template(barcode: str) -> str | None:
    if not barcode or len(barcode) < 5:
        return None
    key = barcode[-5:]
    with _index_lock:
        return _index.get(key)


# ── 상품명 검증 (지뢰 바코드 방어) ─────────────────────
_TOKEN_PATTERN = re.compile(r"[가-힣a-zA-Z]{2,}")


def name_keyword_overlap(product_name: str, template_filename: str) -> float:
    """DB 상품명과 템플릿 파일명의 키워드(2자+ 한글/영문) 겹침 비율.

    끝 5자리가 우연히 같은 '지뢰' 바코드를 잡기 위한 2차 방어선.
    0.0 (완전 불일치) ~ 1.0 (완전 일치).
    """
    toks_product = set(_TOKEN_PATTERN.findall(product_name or ""))
    toks_template = set(_TOKEN_PATTERN.findall(template_filename or ""))
    if not toks_product or not toks_template:
        return 0.0
    common = toks_product & toks_template
    return len(common) / min(len(toks_product), len(toks_template))


# ── 인쇄 실행 ──────────────────────────────────────────
def do_print(template_path: str, barcode: str, quantity: int, printer_name: str) -> dict:
    # BarTender 가 한글 경로·특수문자 파일을 붙잡는 경우가 있어 임시 파일로 복사 후 인쇄
    temp_path = CFG["temp_btw"]
    try:
        shutil.copy2(template_path, temp_path)
    except Exception as e:
        return {
            "status": "error",
            "message": f"템플릿 임시 복사 실패: {e}",
            "template": os.path.basename(template_path),
        }

    cmd = [
        CFG["bartender_exe"],
        f"/f={temp_path}",
        "/p",
        f"/c={quantity}",
        f"/PRN={printer_name}",
        "/x",
    ]

    try:
        r = subprocess.run(
            cmd,
            timeout=CFG["print_timeout_sec"],
            capture_output=True,
            text=True,
            encoding="cp949",
            errors="replace",
        )
    except subprocess.TimeoutExpired:
        return {
            "status": "error",
            "message": f"BarTender 타임아웃 ({CFG['print_timeout_sec']}초)",
            "template": os.path.basename(template_path),
        }
    except FileNotFoundError:
        return {
            "status": "error",
            "message": f"BarTender 실행파일 없음: {CFG['bartender_exe']}",
            "template": os.path.basename(template_path),
        }
    except Exception as e:
        return {
            "status": "error",
            "message": f"BarTender 실행 실패: {e}",
            "template": os.path.basename(template_path),
        }
    finally:
        try:
            if os.path.exists(temp_path):
                os.remove(temp_path)
        except Exception:
            pass

    if r.returncode == 0:
        return {
            "status": "ok",
            "message": f"{quantity}장 인쇄 완료",
            "template": os.path.basename(template_path),
            "printer": printer_name,
        }
    return {
        "status": "error",
        "message": r.stderr.strip() or f"BarTender exit={r.returncode}",
        "template": os.path.basename(template_path),
    }


def print_label(
    barcode: str,
    quantity: int = 1,
    printer_name: str = "",
    product_name: str = "",
) -> dict:
    if not barcode:
        return {"status": "error", "message": "barcode 누락"}
    if quantity < 1 or quantity > 1000:
        return {"status": "error", "message": f"quantity 범위 초과: {quantity}"}
    printer_name = printer_name or CFG["default_printer"]

    template = find_template(barcode)
    if not template:
        tail5 = barcode[-5:]
        logger.warning("템플릿 없음: barcode=%s (끝5자리=%s)", barcode, tail5)
        return {
            "status": "error",
            "message": f"Template file not found: {tail5}.btw",
            "barcode_tail": tail5,
            "templates_indexed": len(_index),
        }

    # os.path.basename 은 Windows 경로(\)를 Unix 환경에서 못 나눠서,
    # 교차 플랫폼으로 안전하게 \ 와 / 둘 다 처리
    template_name = template.replace("\\", "/").rsplit("/", 1)[-1]

    # 2단 방어: product_name 이 들어왔으면 파일명과 키워드 비교
    # 서버가 product_name 을 보내지 않거나 verify_product_name=false 면 스킵
    if product_name and CFG.get("verify_product_name", True):
        overlap = name_keyword_overlap(product_name, template_name)
        threshold = float(CFG.get("name_match_min_overlap", 0.3))
        if overlap < threshold:
            logger.warning(
                "상품명 불일치 인쇄 거부: barcode=%s overlap=%.2f threshold=%.2f product=%r template=%r",
                barcode,
                overlap,
                threshold,
                product_name,
                template_name,
            )
            return {
                "status": "error",
                "message": (
                    f"Product name mismatch: barcode={barcode[-5:]} "
                    f"DB={product_name!r} vs template={template_name!r} "
                    f"(overlap={overlap:.2f} < {threshold})"
                ),
                "barcode_tail": barcode[-5:],
                "db_product_name": product_name,
                "template_filename": template_name,
                "name_overlap": round(overlap, 2),
            }
        logger.info(
            "상품명 검증 통과: overlap=%.2f (%s ↔ %s)",
            overlap,
            product_name[:40],
            template_name[:40],
        )

    logger.info(
        "인쇄: barcode=%s template=%s qty=%d printer=%s",
        barcode,
        template_name,
        quantity,
        printer_name,
    )
    result = do_print(template, barcode, quantity, printer_name)
    logger.info("인쇄 결과: %s", result.get("message"))
    return result


# ── HTTP 서버 ──────────────────────────────────────────
_started_at = time.time()


class Handler(BaseHTTPRequestHandler):
    def _json(self, code: int, data: dict):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        logger.info("http %s - %s", self.address_string(), fmt % args)

    def do_GET(self):
        if self.path == "/health" or self.path == "/":
            self._json(
                200,
                {
                    "status": "ok",
                    "uptime_sec": int(time.time() - _started_at),
                    "templates_indexed": len(_index),
                    "index_built_at": int(_index_built_at),
                    "port": CFG["port"],
                    "bartender_exe": CFG["bartender_exe"],
                    "bartender_exists": os.path.exists(CFG["bartender_exe"]),
                    "default_printer": CFG["default_printer"],
                    "template_dirs": CFG["template_dirs"],
                },
            )
            return
        self._json(404, {"status": "error", "message": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b""
        try:
            data = json.loads(raw) if raw else {}
        except Exception as e:
            self._json(400, {"status": "error", "message": f"invalid json: {e}"})
            return

        if self.path == "/reload":
            count, collisions = build_index()
            self._json(
                200,
                {
                    "status": "ok",
                    "message": f"템플릿 재인덱싱 완료: {count}개",
                    "templates_indexed": count,
                    "collisions": collisions,
                },
            )
            return

        # /print, /, 빈 경로 모두 인쇄로 라우팅
        # (미니PC .env가 경로 없이 호출해도 동작하도록 관용적으로 처리)
        if self.path in ("/print", "/", ""):
            try:
                result = print_label(
                    barcode=str(data.get("barcode", "")),
                    quantity=int(data.get("quantity", 1)),
                    printer_name=str(data.get("printer_name", "")),
                    product_name=str(data.get("product_name", "")),
                )
                code = 200 if result.get("status") == "ok" else 500
                self._json(code, result)
            except Exception as e:
                logger.error("POST /print 예외: %s", traceback.format_exc())
                self._json(500, {"status": "error", "message": str(e)})
            return

        self._json(404, {"status": "error", "message": f"unknown path: {self.path}"})


def main():
    logger.info("=" * 60)
    logger.info("print_agent 시작 — port=%d", CFG["port"])
    logger.info(
        "BarTender: %s (exists=%s)", CFG["bartender_exe"], os.path.exists(CFG["bartender_exe"])
    )
    logger.info("default_printer: %s", CFG["default_printer"])
    logger.info("template_dirs: %s", CFG["template_dirs"])

    count, _ = build_index()
    logger.info("초기 템플릿 인덱스: %d개", count)

    server = HTTPServer(("0.0.0.0", CFG["port"]), Handler)
    logger.info("리스닝 중... http://0.0.0.0:%d", CFG["port"])
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("종료 요청 수신")
    finally:
        server.server_close()
        logger.info("종료")


if __name__ == "__main__":
    main()
