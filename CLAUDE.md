# Warehouse Scanner Project (물류창고 스캐너)

## Project Overview
Android PDA(Zebra TC60) 기반 물류창고 바코드 스캐너 앱 + Mini PC API 서버

## Architecture
```
TC60 PDA (Android App) --WiFi--> Mini PC (FastAPI Server) --WebDAV--> NAS (이미지/데이터)
```

## Tech Stack

### Android App (`/android`)
- Language: Kotlin
- UI: XML (View Binding)
- Min SDK: 24 / Target SDK: 34
- Build: Gradle Kotlin DSL
- Scanner: Zebra DataWedge Intent API
- Network: Retrofit2 + OkHttp
- Image: Coil
- Architecture: MVVM (ViewModel + Repository)

### Server API (`/server`)
- Language: Python 3.11+
- Framework: FastAPI + Uvicorn
- DB: SQLite (aiosqlite)
- Excel Parsing: openpyxl
- Image: NAS WebDAV proxy
- CORS: 내부망 전용

## Directory Structure
```
scan/
├── android/                # Android PDA 앱
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/scan/warehouse/
│   │   │   │   ├── ui/          # Activity, Fragment, Adapter
│   │   │   │   ├── viewmodel/   # ViewModel
│   │   │   │   ├── repository/  # Repository
│   │   │   │   ├── model/       # Data class
│   │   │   │   ├── network/     # Retrofit API interface
│   │   │   │   └── scanner/     # DataWedge Intent handler
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle.properties
├── server/                 # FastAPI 서버
│   ├── app/
│   │   ├── main.py         # FastAPI app entry
│   │   ├── api/            # Route handlers
│   │   ├── db/             # DB models, connection
│   │   ├── services/       # Business logic (xlsx parser, image proxy)
│   │   └── config.py       # Settings
│   ├── requirements.txt
│   └── data/               # SQLite DB file
└── CLAUDE.md
```

## Coding Conventions

### Kotlin (Android)
- 파일명: PascalCase (e.g., `ScanActivity.kt`)
- 변수/함수: camelCase
- 상수: UPPER_SNAKE_CASE
- 패키지: `com.scan.warehouse`
- View Binding 필수, findViewById 금지
- Coroutine 사용 (runBlocking 금지)

### Python (Server)
- 파일명: snake_case (e.g., `sku_service.py`)
- 변수/함수: snake_case
- 클래스: PascalCase
- Type hint 필수
- async/await 사용
- Pydantic model로 request/response 정의

## Key Specifications

### Barcode
- Format: EAN-13 (880 prefix, 한국)
- 상품당 복수 바코드 존재 가능
- DataWedge Intent action: `com.scan.warehouse.SCAN`

### Data Sources
- `codepath.xlsx`: 바코드 → 이미지 경로 매핑
- `coupangmd00_sku_download_*.xlsx`: 쿠팡 SKU 데이터 (SKU ID, 상품명, 바코드 등)
- NAS 이미지: `img/` (썸네일), `real_image/` (실사)

### Performance Target
- 바코드 스캔 → 상품 정보 표시: **0.3 ~ 0.5초**
- 1만 SKU 이상 대응

### Network
- 내부 WiFi 전용 (인터넷 불필요)
- API Base URL: 설정 가능하도록 구현

## Rules
- 클라이언트가 기획 주도 — 임의 기능 추가 금지
- Phase 1 (MVP) 범위만 구현: 바코드 스캔 → 조회 → 이미지 표시
- AI 생성 느낌의 장문/과잉 설명 금지 — 실무적, 간결하게
- 기존 시스템 비하 표현 금지

## How to Work
- **ROADMAP.md**를 읽고 첫 번째 미완료(`[ ]`) 마일스톤부터 작업한다
- 마일스톤의 완료 조건을 모두 충족하면 체크(`[x]`)하고 다음 마일스톤으로 넘어간다
- 데이터 파일 구조, 클라이언트 요구사항, NAS 환경은 **DEV_NOTES.md** 참조
- 세부 구현 방법은 자율 판단하되, 위 Coding Conventions과 Rules를 따른다
