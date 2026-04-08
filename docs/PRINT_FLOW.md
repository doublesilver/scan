# 인쇄 버튼 작동 방식

PDA 앱에서 상품 라벨(바코드)을 물리 프린터로 출력하는 전체 흐름을 정리한 문서입니다.

**장비 구성:**

- **미니PC** — FastAPI 서버 상시 가동, SQLite DB 보관
- **물류PC** — 라벨 프린터 연결, BarTender + print agent 상시 가동

---

## 1. 인쇄 버튼 위치

**메인 화면에는 없습니다.** 상품 상세 화면(`DetailActivity`) 상단에 있어요.

진입 경로:

1. 홍길동이 메인에서 바코드를 스캔하거나 상품명으로 검색
2. 결과 카드를 탭 → 상품 상세 화면(`DetailActivity`) 진입
3. 상단의 **"인쇄"** 버튼(`btnBarPrint`) 탭

---

## 2. 앱 쪽 흐름

**파일:** `android/app/src/main/java/com/scan/warehouse/ui/DetailActivity.kt`

1. **`btnBarPrint` 탭** → 수량 입력 다이얼로그 표시 (기본값 1, 유효 범위 1~100)
2. 사용자가 수량 입력 후 **"인쇄"** 탭
3. `printLabel(data, qty)` 호출
4. 내부적으로 `repository.printLabel(barcode, skuId, productName, quantity)` 실행
5. Retrofit이 **`POST /api/print`** 요청을 서버로 전송
6. 서버 응답의 메시지를 Toast로 표시 (예: `"3장 인쇄 완료"`)

버튼은 요청 중 비활성 상태로 전환되고, 완료·실패 시 다시 활성화됩니다.

---

## 3. 서버 쪽 처리

**파일:** `server/app/services/print_service.py`
**엔드포인트:** `POST /api/print` (`server/app/api/routes.py`)

핵심 함수: `print_label(product_name, barcode, sku_id, quantity)`

이 함수는 환경·설정에 따라 다음 세 가지 경로 중 하나로 처리합니다.

### 경로 A. BarTender print agent 경유 (현재 운영 방식)

**조건:** `settings.print_agent_url`이 설정돼 있음 (미니PC → 물류PC 주소)

**동작:**

1. 미니PC의 FastAPI가 물류PC의 agent URL로 `POST`: `{ "barcode": ..., "quantity": ..., "printer_name": ... }`
2. 물류PC의 BarTender agent가 **바코드 끝 5자리**로 대응하는 `.btw` 템플릿 파일을 자동 매칭
3. 템플릿에 바코드·수량 주입 후 물류PC에 연결된 프린터로 출력
4. agent의 응답을 FastAPI가 받아 앱에 그대로 반환

**장점:**

- 복잡한 라벨 디자인(로고, 한글 레이아웃, 다중 폰트 등) 깔끔하게 출력
- 템플릿 수정을 BarTender 쪽에서만 하면 되고 코드 변경 불필요

**단점:**

- 물류PC에 BarTender print agent 서비스가 항상 돌고 있어야 함
- 미니PC ↔ 물류PC 네트워크가 살아있어야 함

**관련 커밋:** `73cbb13` (프린터: BarTender print agent 연동 — 바코드 끝 5자리로 btw 템플릿 자동 매칭)

### 경로 B. pywin32로 TSC 프린터 직접 호출 (fallback)

**조건:** `print_agent_url` 비어있음 + Windows 환경

**동작:**

1. `generate_tspl()` 함수가 TSPL(TSC 프린터 제어 언어) 명령 문자열 생성
2. `win32print.EnumPrinters()`로 등록된 프린터 목록을 조회, 이름에 "TSC"가 포함된 프린터를 자동 선택
3. 명시적 `printer_name` 설정이 있으면 그걸 우선 사용
4. `StartDocPrinter → WritePrinter → EndDocPrinter`로 RAW 바이너리 전송
5. 성공·실패 딕셔너리 반환

**생성되는 TSPL 예시:**

```
SIZE 50 mm, 30 mm
GAP 2 mm, 0 mm
DENSITY 8
SPEED 4
DIRECTION 1
CODEPAGE UTF-8
CLS
TEXT 20,20,"K",0,24,24,"상품명"
BARCODE 20,70,"128",80,1,0,2,4,"8809461170008"
TEXT 20,180,"3",0,1,1,"SKU: ABC123"
PRINT 1,1
```

### 경로 C. dry run (비Windows 환경)

**조건:** `print_agent_url` 비어있음 + macOS·Linux 환경

**동작:** 실제 인쇄 없이 `{"status": "dry_run", "message": "Windows 환경에서만 인쇄 가능"}` 반환. 개발·테스트 환경용.

---

## 4. 관련 설정 (`server/app/config.py`)

| 항목              | 용도                                                         | 기본값 |
| ----------------- | ------------------------------------------------------------ | ------ |
| `print_agent_url` | BarTender agent URL. 비어있으면 경로 B(pywin32) fallback     | 없음   |
| `printer_name`    | 명시적 프린터 이름. 비어있으면 "TSC" 포함된 프린터 자동 탐색 | 없음   |
| `label_width_mm`  | 라벨 너비                                                    | 50     |
| `label_height_mm` | 라벨 높이                                                    | 30     |
| `label_gap_mm`    | 라벨 간격                                                    | 2      |
| `label_density`   | 인쇄 농도                                                    | 8      |

---

## 5. 현재 운영 중인 방식

**물류PC(Windows)** 에서 BarTender print agent 서비스가 상시 동작하고, `.btw` 템플릿 파일들이 준비돼 있어서 **경로 A**로 인쇄가 이루어집니다. 미니PC의 FastAPI가 `print_agent_url` 로 물류PC agent를 호출합니다. 바코드 끝 5자리 자동 매칭은 `73cbb13` 커밋에서 연결됐습니다.

---

## 6. 문제 해결

### "인쇄 요청 실패" Toast가 뜸

1. 물류PC에서 BarTender print agent 서비스 상태 확인
2. 미니PC의 `settings.print_agent_url` 이 올바른 물류PC 주소인지 확인
3. 미니PC → 물류PC 네트워크(핑/방화벽) 확인
4. 미니PC 서버 로그에서 `물류PC agent` 문구 검색
5. 미니PC DB `print_log` 테이블에서 최근 실패 레코드 조회 — `status=error` 인 행의 `message` 와 `raw_response` 확인

### "TSC 프린터를 찾을 수 없습니다"

경로 B(pywin32) 사용 중인데 Windows 제어판에 TSC 프린터가 등록되지 않은 상태. TSC 드라이버 설치 후 재시도.

### 인쇄는 됐는데 내용이 잘못 출력됨

`.btw` 템플릿 파일명 규칙 확인:

- 파일명이 바코드 **끝 5자리**와 일치해야 함
- 예: 바코드 `8809461170008` → `70008.btw` 파일이 있어야 매칭

### 인쇄 속도가 느림

`settings.label_density` 를 조정하거나 `SPEED` 값을 TSPL 생성 코드에서 높여볼 수 있음 (단, 밀도가 높으면 인쇄 품질 저하).

---

## 7. 관련 파일 목록

| 분류              | 파일                                                                           |
| ----------------- | ------------------------------------------------------------------------------ |
| 앱 UI             | `android/app/src/main/java/com/scan/warehouse/ui/DetailActivity.kt`            |
| 앱 레이아웃       | `android/app/src/main/res/layout/activity_detail.xml`                          |
| 앱 Repository     | `android/app/src/main/java/com/scan/warehouse/repository/ProductRepository.kt` |
| 앱 API 인터페이스 | `android/app/src/main/java/com/scan/warehouse/network/ApiService.kt`           |
| 서버 서비스       | `server/app/services/print_service.py`                                         |
| 서버 인쇄 로그    | `server/app/services/print_log_service.py`                                     |
| 서버 엔드포인트   | `server/app/api/routes.py` (`POST /api/print`)                                 |
| 서버 설정         | `server/app/config.py`                                                         |
| DB 스키마         | `server/app/db/schema.py` (`print_log` 테이블, v10)                            |

---

## 8. 인쇄 이력 기록 (print_log)

모든 인쇄 시도는 성공·실패 상관없이 `print_log` 테이블에 기록됩니다.

**저장 항목:**

| 컬럼         | 설명                                                   |
| ------------ | ------------------------------------------------------ |
| id           | PK                                                     |
| created_at   | 시도 시각                                              |
| barcode      | 바코드                                                 |
| sku_id       | SKU                                                    |
| product_name | 상품명                                                 |
| quantity     | 수량                                                   |
| status       | `ok` / `error` / `dry_run`                             |
| via          | `agent` / `pywin32` / `dry_run`                        |
| http_status  | agent 경로일 때 응답 HTTP 코드                         |
| elapsed_ms   | 전체 소요 시간                                         |
| message      | 작업자 친화 메시지 (Toast에 그대로 표시됨)             |
| raw_response | agent 원본 응답/설치 프린터 목록 등 진단용 원본 텍스트 |

**사용 예 — 최근 실패 조회:**

```sql
SELECT created_at, barcode, message, raw_response
FROM print_log
WHERE status = 'error'
ORDER BY created_at DESC
LIMIT 20;
```

나중에 통합 어드민 웹화면이 만들어지면 이 테이블을 그대로 조회 대상으로 씁니다.

---

## 9. 관련 커밋

- `73cbb13` — 프린터: BarTender print agent 연동, 바코드 끝 5자리로 btw 템플릿 자동 매칭
- `f11e329` — v5.2.0: 바코드 스캔 오류 수정, 라벨 인쇄 개선
