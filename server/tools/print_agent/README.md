# print_agent (물류PC용 BarTender HTTP 어댑터)

scan 프로젝트의 **물류PC 측 컴포넌트**. 미니PC FastAPI 서버가 `POST /api/print` 를 받으면 이 agent로 전달해서 실제 라벨을 인쇄한다.

## 아키텍처

```
PDA 앱 ──▶ 미니PC FastAPI (포트 8000) ──▶ 물류PC print_agent (포트 7777) ──▶ BarTender CLI ──▶ TSC 프린터
```

## 이 agent가 하는 일

1. 시작 시 `template_dirs` 를 walk해서 `{끝5자리: 전체경로}` 인덱스 구성
2. `POST /print` 로 바코드를 받으면 인덱스에서 `barcode[-5:]` 로 템플릿 조회
3. 찾으면 BarTender CLI (`BarTend.exe /f=... /p /c=... /PRN=... /x`) 로 인쇄
4. **못 찾으면 에러 반환** (폴백 인쇄 금지 — 이전 버전의 지뢰였음)

## 엔드포인트

| 메서드·경로    | 용도                                               | 인쇄 트리거? |
| -------------- | -------------------------------------------------- | ------------ |
| `GET /health`  | 상태 조회 (인덱스 개수·BarTender 경로·가동시간 등) | ❌ 안전      |
| `POST /print`  | `{barcode, quantity, printer_name?}` 받아 인쇄     | ✅           |
| `POST /reload` | 템플릿 폴더 재인덱싱                               | ❌ 안전      |

### 요청/응답 예

```bash
curl http://100.123.11.122:7777/health
```

```json
{
  "status": "ok",
  "uptime_sec": 120,
  "templates_indexed": 13157,
  "bartender_exists": true,
  "default_printer": "TSC TE210 USB001",
  "template_dirs": ["C:\\Users\\User\\Desktop\\[02] 양식"]
}
```

```bash
curl -X POST http://100.123.11.122:7777/print \
  -H "Content-Type: application/json" \
  -d '{"barcode": "8809461170008", "quantity": 1}'
```

성공:

```json
{
  "status": "ok",
  "message": "1장 인쇄 완료",
  "template": "스틱캡 블루_갤럭시S24 70008.btw",
  "printer": "TSC TE210 USB001"
}
```

실패 (템플릿 없음):

```json
{
  "status": "error",
  "message": "Template file not found: 70008.btw",
  "barcode_tail": "70008",
  "templates_indexed": 13157
}
```

## 설치

1. `C:\\print_agent.py` 에 본 파일 복사
2. (선택) `C:\\print_agent_config.json` 에 설정 override (없으면 코드 상단 `DEFAULTS` 사용)
3. 방화벽 포트 7777 TCP inbound allow — 규칙 이름 `print_agent`
4. 자동 시작 — HKCU Run 레지스트리:
   ```
   reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v PrintAgent /t REG_SZ /d "pythonw.exe C:\print_agent.py" /f
   ```
5. 즉시 기동:
   ```
   powershell -Command "Start-Process pythonw -ArgumentList 'C:\print_agent.py' -WindowStyle Hidden"
   ```

## 운영 팁

- 로그는 `C:\print_agent.log` 에 순환 저장 (2MB × 3개 백업)
- 신규 템플릿 추가 후 → `POST /reload` 호출하면 재인덱싱 (재시작 불필요)
- 끝 5자리가 겹치는 템플릿이 있으면 시작 시 경고 로그에 표시됨
- 템플릿 파일명 끝이 정확히 `\d{5}\.btw` 형식이어야 매칭됨 (예: `...70008.btw`)

## 미니PC 서버와의 계약

미니PC `.env`:

```
PRINT_AGENT_URL=http://100.123.11.122:7777/print
PRINTER_NAME=TSC TE210 USB001
```

미니PC `server/app/services/print_service.py` 가 이 agent의 `/print` 엔드포인트로 POST한다. 서버 쪽 에러 메시지 변환 로직(`_friendly_agent_error`)이 이 agent가 돌려주는 `"Template file not found: XXXXX.btw"` 를 `"라벨 템플릿 없음 (XXXXX.btw)"` 로 변환한다.

## 변경 이력

- **v2** (2026-04-08) — 단일 프로세스, 폴백 금지, 사전 인덱싱, /health·/reload 엔드포인트
- **v1** (2026-04-05) — 원본 2프로세스 구조 (agent + watcher). 폴백이 조용해서 실패 숨기는 문제 있었음.
