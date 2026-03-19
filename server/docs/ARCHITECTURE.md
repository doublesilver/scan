# 서버 아키텍처

## 개요

```
TC60 PDA (Android)
    └── WiFi → FastAPI 서버 (Mini PC)
                    ├── SQLite (scanner.db)
                    └── NAS WebDAV (이미지)
```

FastAPI + aiosqlite 기반 비동기 서버. 내부망 전용, 인터넷 불필요.

---

## 모듈 의존 관계

```
config.py
    └── db/database.py
            └── db/schema.py
    └── services/
            ├── codepath_parser.py
            └── sku_parser.py
    └── api/          ← M3에서 추가
    └── main.py
```

- `config.py`: pydantic-settings 기반 환경 설정. `.env` 파일 오버라이드 지원.
- `db/schema.py`: CREATE TABLE DDL 문자열 상수.
- `db/database.py`: aiosqlite 연결 싱글톤, 앱 시작/종료 시 open/close.
- `services/codepath_parser.py`: codepath.xlsx → barcode + image 테이블 적재.
- `services/sku_parser.py`: sku_download.xlsx → product + barcode 테이블 적재.
- `api/`: 라우터 모듈들 (M3 구현 예정).
- `main.py`: FastAPI 앱 생성, CORS 미들웨어, lifespan 훅.

---

## 디렉토리 구조

```
server/
├── app/
│   ├── main.py              # FastAPI 엔트리, lifespan
│   ├── config.py            # Settings (pydantic-settings)
│   ├── db/
│   │   ├── schema.py        # DDL 상수
│   │   └── database.py      # aiosqlite 연결 관리
│   ├── services/
│   │   ├── codepath_parser.py   # codepath.xlsx 파서
│   │   └── sku_parser.py        # sku_download.xlsx 파서
│   └── api/                 # 라우터 (M3)
│       ├── scan.py
│       ├── search.py
│       └── image.py
├── data/
│   ├── scanner.db           # SQLite DB
│   ├── xlsx/                # xlsx 원본 파일 보관
│   └── cache/               # NAS 이미지 캐시 (M4)
├── docs/                    # 이 문서들
├── requirements.txt
└── .env                     # 환경변수 (git 제외)
```

---

## 데이터 흐름

### xlsx → DB 적재 (M2)

```
codepath.xlsx
    → codepath_parser.parse_codepath()
    → product (placeholder) + barcode + image 테이블

sku_download.xlsx
    → sku_parser.parse_sku_download()
    → product (실데이터) + barcode 테이블

적재 순서: codepath 먼저 → sku_download (product placeholder를 실데이터로 갱신)
```

### 바코드 스캔 → 응답 (M3)

```
PDA 스캔
    → GET /api/scan/{barcode}
    → barcode 테이블에서 sku_id 조회
    → product 테이블에서 상품정보 조회
    → image 테이블에서 이미지 경로 조회
    → JSON 응답
```

### 이미지 요청 (M4)

```
PDA 이미지 요청
    → GET /api/image/{path}
    → 로컬 캐시 확인
    → (캐시 미스) NAS WebDAV에서 다운로드 → 캐시 저장
    → 이미지 바이트 응답
```

---

## 설정 우선순위

1. `.env` 파일
2. 환경변수
3. `config.py` 기본값

주요 설정값:

| 항목 | 기본값 | 비고 |
|------|--------|------|
| `host` | `0.0.0.0` | 전체 인터페이스 바인딩 |
| `port` | `8000` | |
| `database_url` | `sqlite+aiosqlite:///data/scanner.db` | |
| `xlsx_watch_dir` | `./data/xlsx` | xlsx 파일 위치 |
| `webdav_base_url` | `` | M4에서 설정 |
