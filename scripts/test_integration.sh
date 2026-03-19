#!/bin/bash
# 통합 테스트 — 서버 시작 → 파싱 → API 호출
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SERVER_DIR="$PROJECT_DIR/server"

echo "=== 통합 테스트 시작 ==="

# 1. 기존 DB 삭제 (클린 테스트)
rm -f "$SERVER_DIR/data/scanner.db"
echo "[1/5] 클린 DB 준비"

# 2. 데이터 파싱
cd "$SERVER_DIR"
python3 -m app.services.parse_cli
echo "[2/5] 데이터 파싱 완료"

# 3. 서버 시작 (백그라운드)
python3 -m uvicorn app.main:app --host 127.0.0.1 --port 8000 &
SERVER_PID=$!
sleep 2
echo "[3/5] 서버 시작 (PID: $SERVER_PID)"

# 4. API 테스트
echo "[4/5] API 테스트"

# health
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/health)
[ "$STATUS" = "200" ] && echo "  /health → 200 OK" || { echo "  /health FAIL ($STATUS)"; kill $SERVER_PID; exit 1; }

# scan (존재하는 바코드)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/api/scan/8809461170008)
[ "$STATUS" = "200" ] && echo "  /api/scan/8809461170008 → 200 OK" || { echo "  /api/scan FAIL ($STATUS)"; kill $SERVER_PID; exit 1; }

# scan (존재하지 않는 바코드)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/api/scan/0000000000000)
[ "$STATUS" = "404" ] && echo "  /api/scan/0000000000000 → 404 OK" || { echo "  /api/scan 404 FAIL ($STATUS)"; kill $SERVER_PID; exit 1; }

# search
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8000/api/search?q=청소기")
[ "$STATUS" = "200" ] && echo "  /api/search?q=청소기 → 200 OK" || { echo "  /api/search FAIL ($STATUS)"; kill $SERVER_PID; exit 1; }

# image (mock)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/api/image/img/000000_07da1a9dfd.jpg)
[ "$STATUS" = "200" ] && echo "  /api/image → 200 OK" || echo "  /api/image → $STATUS (mock 이미지 없으면 404 허용)"

# 응답 시간 측정
TIME=$(curl -s -o /dev/null -w "%{time_total}" http://localhost:8000/api/scan/8809461170008)
echo "  scan 응답 시간: ${TIME}s"

# 5. 서버 종료
kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null
echo "[5/5] 서버 종료"

echo ""
echo "=== 통합 테스트 완료 ==="
