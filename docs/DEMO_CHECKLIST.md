# 시연 체크리스트 — scan 앱 인쇄 데모

_v5.3.3 기준, 2026-04-08 작성. 물류창고 현장 클라이언트 시연 전 체크 항목._

## 장비 구성 재확인

| 역할   | 장비              | IP (tailscale)        | 용도                                 |
| ------ | ----------------- | --------------------- | ------------------------------------ |
| 미니PC | `DESKTOP-Q1S0IO7` | `100.125.17.60:8000`  | FastAPI 서버 + SQLite DB             |
| 물류PC | `DESKTOP-0GOM1QR` | `100.123.11.122:7777` | BarTender + print agent + TSC 프린터 |
| PDA    | 현장 기기         | 미니PC 주소 가리킴    | 바코드 스캔, 인쇄 버튼               |

신호 흐름: **PDA → 미니PC → 물류PC → BarTender → TSC 프린터**

---

## 🟢 시연 3시간 전 (집 맥에서 원격 점검, 물류PC 건드리지 않음)

### 1. 양쪽 서버 생존 확인

```bash
# /api/status — 미니PC 서버 살아있는지
curl -m 5 http://100.125.17.60:8000/api/status

# /health — 물류PC agent 살아있고 템플릿 인덱싱 정상인지
curl -m 5 http://100.123.11.122:7777/health
```

**기대값:**

- 미니PC: uptime > 0, nas_sync.status = "connected"
- 물류PC agent: `status=ok`, `templates_indexed` > 11000, `bartender_exists=true`

### 2. 배포 버전 확인

```bash
curl -m 5 http://100.125.17.60:8000/api/app-version
```

- 기대값: `versionName: "5.3.3"` (이전 시연 실패 전엔 v5.2.1이었음)
- 아직 v5.2.1이면 → [배포 절차](#v533-배포-절차) 수행 필요

### 3. 시연 상품 준비

- 아래 [시연 바코드 리스트](#시연-바코드-리스트)에서 **3~5개** 선택
- 해당 상품의 실물을 챙겨서 현장 지참

### 4. 지뢰 블랙리스트 확인

- 별도 파일 `docs/demo_barcodes.md` 의 ⛔ 블랙리스트 87개는 **절대 시연에 쓰지 말 것**
- 대부분 쵸미세븐 브랜드의 초기 번호대 (`70268`~`70500` 대역)
- v5.3.3 가 자동 차단해주긴 하지만, 앞에서 에러 뜨면 모양 빠짐

### 5. PDA 앱 상태 확인

- PDA 설정 화면 → 서버 주소 `http://100.125.17.60:8000` 인지
- 버전 `5.3.3` 으로 업데이트됐는지 (아니면 PDA 업데이트 먼저)

---

## 🟡 시연 30분 전 (현장 도착 후)

### 6. 물류PC 물리 상태 확인

- TSC 프린터 **전원 ON**, 케이블 연결 정상
- 라벨 용지 잔량 확보, 헤드 잠금 확인
- BarTender 프로그램 아이콘 작업 표시줄에 떠있는지 (상시 가동 중이어야 함)
- 프린터 USB 포트 점검 — 흔들리면 안 됨

### 7. 실제 상품으로 드라이 런 (1장)

- 시연에 쓸 상품 중 하나를 먼저 혼자 테스트:
  1. PDA로 스캔
  2. 상세 화면 → "인쇄" 버튼 → 수량 1 → 확인
  3. 실제 라벨이 **올바르게** 나오는지 눈으로 확인
- 실패하면 [현장 대응](#현장-대응-문제-해결) 섹션으로

### 8. 서버 로그 실시간 보기 (선택)

미니PC 터미널 하나 띄워두고:

```powershell
# Windows PowerShell
Get-Content C:\scanner\server\logs\app.log -Tail 20 -Wait
```

또는 sqlite에서 최근 print_log 조회:

```
SELECT created_at, barcode, status, message FROM print_log ORDER BY id DESC LIMIT 10;
```

---

## 🔴 시연 중 (실패 감지 & 즉시 복구)

### 9. 인쇄 버튼 누른 직후 Toast 문구별 대응

| Toast 문구                             | 원인                          | 즉시 대응                                  |
| -------------------------------------- | ----------------------------- | ------------------------------------------ |
| `"1장 인쇄 완료"` ✅                   | 정상                          | —                                          |
| `"라벨 템플릿 없음 (XXXXX.btw)"`       | `.btw` 파일 미존재            | 그 상품 시연 포기, 다음 상품으로 넘어감    |
| `"상품명과 라벨이 다름"`               | 지뢰 바코드 (끝5자리 충돌)    | 블랙리스트에 있던 상품. 건너뛰고 다음 상품 |
| `"물류PC 응답 없음 (30초 타임아웃)"`   | agent 다운 또는 네트워크 단절 | [agent 재시작](#agent-재시작-긴급) 절차    |
| `"프린터 오프라인 — 전원/케이블 확인"` | TSC 물리 상태                 | 물류PC 가서 전원·케이블 점검               |
| `"인쇄 요청 실패"` (구버전 메시지)     | **서버가 v5.3.3 아님**        | PDA 앱 업데이트 안 된 것. 다른 PDA로 시도  |

### 10. 클라 앞에서 폭탄 터진 경우

- **침착하게** "잠시 확인해 보겠습니다" — 패닉 금지
- 실패한 상품은 넘기고 **다음 상품으로 진행**
- 시연 마무리 후 원인 설명 (v5.3.3 에러 문구로 이미 진단 가능)

---

## 시연 바코드 리스트

별도 파일: **`docs/demo_barcodes.md`**

- ✅ 안전 TOP 20 (스페이스쉴드 브랜드, 100% 키워드 매칭)
- ⛔ 지뢰 블랙리스트 87개 (절대 금지)
- 📊 전체 통계

---

## 현장 대응 (문제 해결)

### agent 재시작 (긴급)

집 맥에서:

```bash
ssh user@100.123.11.122 "taskkill /f /im pythonw.exe 2>&1 & schtasks /run /tn PrintAgent"
# 4초 대기 후 /health 확인
sleep 4
curl -m 5 http://100.123.11.122:7777/health
```

또는 물류PC 직접 조작:

- 작업 관리자 → `pythonw.exe` 종료
- 작업 스케줄러 → `PrintAgent` 태스크 → 실행
- 또는 cmd: `schtasks /run /tn PrintAgent`

### 미니PC 서버 재시작 (불가피한 경우)

⚠️ **업무 중엔 다른 PDA 작업 중단됨. 시연 전만 하기.**

집 맥에서:

```bash
ssh lenovo@100.125.17.60 "taskkill /f /im python.exe & cd /d C:\\scanner\\server && start /b python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"
sleep 3
curl -m 5 http://100.125.17.60:8000/api/status
```

### 전체 체인 한 줄 헬스체크

```bash
# 집 맥에서 한 번에 3단계 확인
echo "= 미니PC ="; curl -s -m 3 http://100.125.17.60:8000/api/app-version
echo "= 물류PC agent ="; curl -s -m 3 http://100.123.11.122:7777/health
echo "= 전체 체인 (지뢰 바코드로 에러 경로 검증) ="
curl -s -m 10 -X POST http://100.125.17.60:8000/api/print \
  -H "Content-Type: application/json" \
  -d '{"barcode":"0000000000000","sku_id":"TEST","product_name":"TEST","quantity":1}'
```

기대값: 마지막 응답이 `{"detail":"Template file not found: 00000.btw"}` 또는 `{"detail":"라벨 템플릿 없음 (00000.btw)"}` (v5.3.3 이후)

---

## v5.3.3 배포 절차

**⚠️ 업무 시간 중엔 금지. 업무 종료 후 또는 시연 당일 아침 일찍.**

### 미니PC 서버 업데이트

집 맥에서:

```bash
# 1. 최신 코드 pull (미니PC 에는 git 이 없으니 rsync 또는 scp 로)
rsync -avz --exclude=data --exclude=.venv \
  /Users/leeeunseok/Projects/scan/server/ \
  lenovo@100.125.17.60:C:/scanner/server/

# 2. 서버 재시작
ssh lenovo@100.125.17.60 "taskkill /f /im python.exe 2>&1 & timeout 2 & cd /d C:\\scanner\\server && start /b python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"

# 3. 버전 확인
sleep 5
curl -s http://100.125.17.60:8000/api/app-version
# → versionName: "5.3.3" 확인
```

### 물류PC agent 업데이트

집 맥에서:

```bash
scp /Users/leeeunseok/Projects/scan/server/tools/print_agent/print_agent.py user@100.123.11.122:C:/print_agent.py
ssh user@100.123.11.122 "taskkill /f /im pythonw.exe 2>&1 & schtasks /run /tn PrintAgent"
sleep 4
curl http://100.123.11.122:7777/health
```

### PDA 앱 업데이트

1. `scan/android/app/build.gradle.kts` versionCode·versionName 확인 (이미 77·5.3.3)
2. `./gradlew assembleLiveDebug` 로 APK 빌드
3. APK 를 미니PC `C:/scanner/apk/app-live-debug.apk` 에 배치
4. PDA 실행 → 앱 자동 업데이트 체크 → 설치

---

## 성공 기준

시연이 성공하려면 이 4개가 다 그린이어야 함:

- [ ] 미니PC `/api/app-version` 이 `5.3.3` 반환
- [ ] 물류PC `/health` 가 `templates_indexed > 11000`, `bartender_exists=true` 반환
- [ ] 전체 체인 테스트 (존재 안 하는 바코드 POST) 가 `"Template file not found"` 반환
- [ ] 시연 상품 3개 이상이 `demo_barcodes.md` 안전 TOP 20 에 포함

---

## 변경 이력

- **2026-04-08** 초안 — 지난 시연 실패(엉뚱한 라벨 출력) 분석 기반 작성. 지뢰 바코드 블랙리스트 + 2단 방어 적용 후 버전.
