# scan — 에이전트 라우팅 가이드

상세 구조/관례. 메인 컨텍스트(CLAUDE.md)는 짧게 유지하고, 깊은 정보는 여기서 참조.

## 모듈

### `android/` — Kotlin 앱

- Package: `com.scan.warehouse`
- 구조: `ui/`, `viewmodel/`, `repository/`, `model/`, `network/`, `scanner/`
- Min SDK 24 / Target 34, Gradle KTS, View Binding, Retrofit2 + OkHttp, Coil, MVVM
- Scanner: Zebra DataWedge Intent (`com.scan.warehouse.SCAN`)
- 빌드: `./gradlew assembleDebug` / `installDebug`

### `server/` — FastAPI

- Entry: `app/main.py`, Settings: `app/config.py`
- 구조: `api/`, `db/`, `models/`, `services/`
- 핵심 서비스:
  - `product_service.py` — 바코드 조회, 상품 검색 (위치/그룹 포함)
  - `image_service.py` — NAS WebDAV 이미지 프록시 + 캐시
  - `stock_service.py` — 재고 CRUD
  - `nas_sync.py` — NAS xlsx 자동 동기화
  - `status_service.py` — 서버 상태 대시보드
- DB: `data/scanner.db` (SQLite, aiosqlite)

## 코딩 컨벤션

### Kotlin

- 파일: PascalCase / 변수·함수: camelCase / 상수: UPPER_SNAKE_CASE
- View Binding 필수, findViewById 금지
- Coroutine, runBlocking 금지

### Python

- 파일/변수/함수: snake_case / 클래스: PascalCase
- async/await + Pydantic + type hint 필수
- Ruff (line 100, py311) + pytest-asyncio
- 포맷: 저장 시 PostToolUse 훅이 자동 적용 (ruff diff는 별도 커밋 권장)

## 데이터 / 스펙

- 바코드: EAN-13 (880 prefix, 한국), 상품당 복수 가능
- 데이터 소스:
  - `codepath.xlsx` — 바코드 → 이미지 경로
  - `coupangmd00_sku_download_*.xlsx` — 쿠팡 SKU
- 이미지: NAS `img/`(썸네일), `real_image/`(실사)
- 성능 목표: 스캔→표시 0.3~0.5초, 1만 SKU+

## 에이전트 라우팅

| 작업                     | 에이전트                 | 모델                  |
| ------------------------ | ------------------------ | --------------------- |
| 다중 파일 변경/리팩토링  | `executor`               | sonnet (복잡 시 opus) |
| 광범위 코드 탐색         | `Explore`                | sonnet                |
| 디버깅/원인 추적         | `debugger` 또는 `tracer` | sonnet                |
| 코드 리뷰                | `code-reviewer`          | sonnet                |
| 외부 SDK/라이브러리 사용 | `document-specialist`    | sonnet                |
| 기획/요구사항 정리       | `analyst`/`planner`      | opus                  |
| 설계/아키텍처 결정       | `architect`              | opus                  |
| 단순 lookup (파일/심볼)  | 직접 Glob/Grep           | -                     |

## 토큰 절약 원칙

- 큰 파일(.db, .xlsx, .csv, .apk, build/, outputs/, mock_images/)은 `.claude/settings.json` deny로 차단됨
- 광범위 탐색은 반드시 서브에이전트 위임 (메인 컨텍스트 보호)
- 슬래시 커맨드(`/scan-*`) 활용해 명령 재설명 회피
- 빌드/테스트/서버는 `run_in_background=true`로 실행
