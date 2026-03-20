# 기술명세서 (Technical Specification)

Warehouse Scanner v1.0 -- 물류창고 바코드 스캐너 시스템

---

## 1. 프로젝트 개요

### 목적

Zebra TC60 PDA에서 바코드를 스캔하면 상품 정보(이미지, SKU, 상품명)를 즉시 표시하는 시스템. 물류창고 현장에서 상품 식별 시간을 단축한다.

### 배경

- 클라이언트가 쿠팡 서플라이허브에서 소품류(폰케이스, 게임 컨트롤러 등)를 취급
- 5층 건물, 층당 70~100평 규모의 물류창고 운영
- 기존에 Mini PC + USB 바코드 스캐너 + scan13.exe(PySide6 데스크톱 앱)로 운영 중
- PDA를 추가하여 현장 어디서든 바코드 조회가 가능하도록 확장

### 기존 시스템(scan13.exe)과의 관계

scan13.exe를 대체하는 것이 아니라, PDA 앱을 **추가**하는 것이다. 두 시스템은 독립적으로 공존한다.

- NAS의 xlsx/이미지가 유일한 데이터 소스이며, 양쪽 모두 **읽기 전용**으로 접근
- scan13.exe는 RaiDrive(Z: 드라이브) 경유, 우리 서버는 WebDAV 직접 접근 -- 간섭 없음
- scan13.exe는 네트워크 서버가 아니므로 8000번 포트 충돌 없음
- 각자 별도 DB 사용 (scan13.exe는 자체 로직, 우리 서버는 SQLite)

### 전체 아키텍처

```
+------------------+     WiFi      +-------------------+     WebDAV     +------------------+
|   TC60 PDA       | -----------> |   Mini PC          | -----------> |   Synology NAS   |
|   (Android App)  |   REST API   |   (FastAPI Server)  |   HTTP(S)    |                  |
|                  | <----------- |   Port 8000         | <----------- |   Port 58890     |
+------------------+              +-------------------+               +------------------+
                                  |   scan13.exe       |   RaiDrive    |                  |
                                  |   (기존 유지)       | -----------> |   Z: 드라이브     |
                                  +-------------------+               +------------------+

NAS 파일 구조:
/물류부/scan/
  +-- codepath.xlsx              바코드 -> 이미지 경로 매핑
  +-- coupangmd00_sku_download_*.xlsx   쿠팡 SKU 데이터
  +-- img/                       쿠팡 썸네일 이미지 (~108KB/장)
  +-- real_image/                실사 이미지 (~2.3MB/장)
```

---

## 2. 기술 스택 선정 사유

### 서버

| 항목            | 채택                     | 대안                | 채택 사유                                                                                                   |
| --------------- | ------------------------ | ------------------- | ----------------------------------------------------------------------------------------------------------- |
| 언어            | Python 3.11              | Node.js, Go         | 쿠팡 xlsx가 inlineStr 형식이라 lxml 직접 파싱 필요. Python이 lxml/openpyxl 생태계 최강                      |
| 프레임워크      | FastAPI                  | Flask, Django       | 비동기 지원, 자동 Swagger 문서, Pydantic 타입 검증. Django는 이 규모에 과도                                 |
| DB              | SQLite (aiosqlite)       | PostgreSQL, MySQL   | Mini PC 1대에서 서버 하나만 돌림. 별도 DB 서버 불필요. 파일 하나로 백업/복구 간편. 11,821건 규모에 충분     |
| 전문검색        | FTS5                     | Elasticsearch, LIKE | ES는 별도 서버 필요(Mini PC 과부하). LIKE는 10만건 확장 시 느림. FTS5는 SQLite 내장이라 추가 설치 없이 빠름 |
| 이미지 프록시   | httpx + 로컬 캐시        | 직접 NAS 마운트     | WebDAV HTTP 접근이 RaiDrive(Z:)와 독립적이라 scan13.exe와 충돌 없음                                         |
| xlsx 파싱       | lxml 직접                | openpyxl            | 쿠팡 xlsx가 inlineStr 타입이라 openpyxl read_only 모드에서 못 읽음. lxml로 XML 직접 파싱                    |
| 헤더 매칭       | SequenceMatcher (유사도) | 고정 인덱스         | 쿠팡 서플라이허브가 컬럼명/순서를 바꿔도 자동 인식. scan13.exe의 "헤더 인식 실패" 문제 해결                 |
| 파일 감시       | watchdog + WebDAV 폴링   | polling만           | 로컬 변경은 watchdog이 즉시 감지, NAS 변경은 1분 주기 PROPFIND로 감지                                       |
| 이미지 리사이징 | Pillow (서버사이드)      | 클라이언트 리사이징 | PDA 5인치 화면에 원본 이미지(2MB) 전송은 낭비. 서버에서 지정 width로 축소하면 네트워크 절감                 |
| 동시성          | WAL + 읽기/쓰기 분리     | 커넥션 풀           | SQLite는 단일 writer. WAL 모드로 읽기/쓰기 동시성 확보. 읽기 전용 커넥션에 query_only=ON                    |

### Android

| 항목          | 채택               | 대안                           | 채택 사유                                                                                   |
| ------------- | ------------------ | ------------------------------ | ------------------------------------------------------------------------------------------- |
| 언어          | Kotlin             | Java, Flutter, React Native    | Zebra DataWedge Intent API는 네이티브 Android 전용. 크로스 플랫폼은 DataWedge 호환성 리스크 |
| UI            | XML + View Binding | Jetpack Compose                | 2화면 단순 앱에 Compose 학습곡선 대비 이점 적음. View Binding이 안정적이고 빠름             |
| 아키텍처      | MVVM               | MVI, MVP                       | Google 권장 패턴. LiveData + ViewModel + Repository로 관심사 분리. 이 규모에 MVI는 과도     |
| 네트워크      | Retrofit2 + OkHttp | Ktor, Volley                   | Android 사실상 표준. OkHttp Interceptor로 재시도/로깅 편리                                  |
| 이미지 로딩   | Coil               | Glide, Picasso                 | Kotlin 네이티브, 코루틴 기반, 디스크 캐시 내장. Glide 대비 경량                             |
| 오프라인      | Room DB            | SharedPreferences, SQLite 직접 | Google 공식 ORM. 코루틴 지원, 마이그레이션 지원. 11,821건 캐싱에 적합                       |
| 바코드 스캐너 | DataWedge Intent   | Camera 스캐너 (ZXing)          | TC60 PDA 내장 스캐너 활용. 카메라 스캔 대비 속도/정확도 압도적                              |
| 재시도        | OkHttp Interceptor | Kotlin Retry 라이브러리        | OkHttp 레벨에서 처리하면 모든 API에 일괄 적용. 별도 라이브러리 불필요                       |
| 상태 보존     | SavedStateHandle   | onSaveInstanceState            | 프로세스 데스 시 ViewModel 데이터 자동 복원. Jetpack 권장 방식                              |

---

## 3. 화면 설계 사유

### 스플래시 화면 (SplashActivity)

**왜 필요한가**: 서버 연결 확인 후 결과에 따라 분기 처리. 연결 성공 시 메인 화면 진입, 실패 시 Room 캐시가 있으면 오프라인 모드 진입, 캐시도 없으면 재시도/설정 버튼을 표시한다.

**대안 검토**: 스플래시 없이 바로 메인 화면 진입 -- 서버가 안 되면 빈 화면에 에러만 표시되어 사용자가 무엇을 해야 할지 모름. UX 불량.

**구현**: 3초 타임아웃으로 health check. 실패 시 Room DB 캐시 건수를 확인하여 오프라인 모드 자동 전환.

### 메인 화면 (MainActivity)

**검색창 통합**: 입력값이 숫자 8~13자리면 바코드 스캔 API 호출, 그 외는 텍스트 검색 API 호출. 별도 모드 전환 버튼 불필요.

**이미지 최대 표시**: 클라이언트 요구 -- "반드시 이미지가 제일 눈에 띄어야 한다". 스캔 결과 영역에서 이미지를 가장 크게 배치.

**바코드 끝 5자리 볼드**: 클라이언트 요구. EAN-13에서 끝 5자리가 상품 고유번호 부분. `BarcodeUtils.formatBold()`에서 SpannableString으로 처리.

**스페이스쉴드 자동 제거**: 취급 브랜드가 "스페이스쉴드" 하나뿐이라 모든 상품명에 동일 브랜드명이 포함됨. 식별 시 불필요하므로 서버에서 자동 제거. `config.py`의 `brand_filter` 설정으로 관리.

**카드 형태 검색 결과**: RecyclerView + ProductAdapter. 터치 영역 명확, 터치 시 바코드 스캔 API 호출하여 상세 화면 진입.

**스캔 대기 화면**: 스캔 전 "PDA 버튼을 눌러 스캔하세요" 안내 표시. 처음 사용하는 작업자도 혼란 없이 사용 가능.

### 상세 화면 (DetailActivity)

**이미지 토글 (썸네일 ↔ 실사)**: 클라이언트 요구 -- "와리가리 타게". 이미지 터치 시 썸네일/실사 전환. 현재 표시 중인 이미지 타입을 칩으로 표시.

**바코드 복사**: 바코드 영역 길게 눌러 클립보드 복사. 다른 앱에서 바코드 활용.

**위치 필드**: 현재 숨김. 위치 데이터 확보 시 활성화 예정.

**발주 버튼**: 현재 숨김. 1688 URL 데이터 확보 시 활성화 예정.

**뒤로가기**: 슬라이드 애니메이션(slide_in_left/slide_out_right)으로 자연스러운 화면 전환.

### 설정 화면 (SettingsActivity)

서버 URL을 1회 설정하면 SharedPreferences에 저장. 연결 테스트 버튼으로 즉시 확인. URL 변경 시 RetrofitClient를 재생성(`apiService = null`).

---

## 4. DB 설계 사유

### 서버 DB (SQLite)

**product 테이블**

- `sku_id`를 PK로 사용. 쿠팡 SKU ID가 고유 식별자 역할.
- `extra` 필드(JSON TEXT)로 최소구매수량, 중량 등 쿠팡 데이터 확장에 대응. 컬럼 추가 없이 새 필드 저장 가능.
- `brand`, `category` 별도 컬럼으로 분리하여 FTS5 검색 대상에 포함.

**barcode 테이블**

- 상품당 복수 바코드 존재 (1:N 관계). 하나의 SKU에 3~5개 바코드가 매핑될 수 있음.
- `barcode` 컬럼에 UNIQUE 제약으로 중복 방지. ON CONFLICT 시 sku_id를 갱신(upsert).
- `sku_id` FK에 ON DELETE SET NULL -- 상품 삭제 시 바코드는 유지하되 연결만 해제.

**image 테이블**

- 바코드 기준 연결. codepath.xlsx가 바코드-이미지 매핑을 제공하므로 바코드 기반.
- `image_type`으로 썸네일(thumbnail)/실사(real) 구분.
- `(barcode, file_path)` 복합 UNIQUE로 동일 바코드에 같은 이미지 중복 적재 방지.
- 조회 시에는 `sku_id` 기준으로 JOIN하여 동일 SKU의 다른 바코드로 스캔해도 이미지 표시.

**stock 테이블 (Phase 2)**

- 재고 수량 관리. `sku_id` PK + FK로 product와 1:1.
- `stock_log` 테이블로 수정 이력 추적. `before_qty`, `after_qty` 기록.
- scan13.exe와 충돌 없는 독립 영역 -- scan13.exe는 재고 데이터를 관리하지 않음.

**product_fts (FTS5 가상 테이블)**

- `sku_id`, `product_name`, `category`, `brand`를 인덱싱.
- INSERT/UPDATE/DELETE 트리거로 product 테이블 변경 시 자동 동기화.
- FTS5 검색 실패 시 LIKE 폴백으로 안정성 확보.

**parse_log 테이블**

- xlsx 파싱 이력 기록. 파일명, 타입, 건수, 소요 시간, 에러 내역.
- 운영 시 "데이터 반영됐나요?" 질문에 즉시 확인 가능.

**마이그레이션**

- `db_version` 테이블 + `MIGRATIONS` dict 방식.
- 서버 시작 시 현재 DB 버전을 확인하고 미적용 마이그레이션을 순서대로 실행.
- 현재 SCHEMA_VERSION = 3 (v2: FTS5 추가, v3: stock/stock_log 추가).

### Android DB (Room)

**cached_products 테이블**

- `barcode`를 PK로 하는 단순 캐시 테이블.
- 서버 응답을 그대로 저장하여 오프라인 시 폴백으로 사용.
- `imageUrls`는 JSON 문자열로 저장 (Room에서 직접 List 저장 불가).

---

## 5. API 설계 사유

모든 엔드포인트는 `/api` prefix 사용. Pydantic 모델로 요청/응답 타입을 정의하며, FastAPI가 자동으로 Swagger 문서를 생성한다.

### GET /api/scan/{barcode}

바코드로 상품 조회. 응답에 해당 SKU의 모든 바코드와 모든 이미지를 포함한다.

**핵심 설계**: barcode 테이블에서 sku_id를 조회한 뒤, 해당 sku_id에 연결된 모든 바코드의 이미지를 JOIN으로 가져온다. 이로써 동일 SKU의 다른 바코드로 스캔해도 이미지가 정상 표시된다.

```
요청: GET /api/scan/8809461170008
응답 200:
{
  "sku_id": "12345",
  "product_name": "USB-C 충전 케이블 1m",
  "category": "",
  "brand": "",
  "barcodes": ["8809461170008", "8809461170015"],
  "images": [
    {"file_path": "img/000000_07da1a9dfd.jpg", "image_type": "thumbnail"},
    {"file_path": "real_image/20260218_003455.jpg", "image_type": "real"}
  ],
  "quantity": null
}
응답 404: {"detail": "barcode not found"}
```

### GET /api/search

FTS5 전문검색. 실패 시 LIKE 폴백. `min_length=1`로 1글자부터 검색 가능.

```
요청: GET /api/search?q=충전&limit=20
응답 200:
{
  "total": 5,
  "items": [
    {
      "sku_id": "12345",
      "product_name": "USB-C 충전 케이블 1m",
      "category": "",
      "brand": "",
      "barcode": "8809461170008"
    }
  ]
}
```

### GET /api/image/{path}

이미지 프록시. 3단계 폴백 체인으로 동작:

1. 로컬 캐시 확인 (리사이즈 캐시 포함)
2. WebDAV에서 다운로드 후 캐시 저장
3. 기본 이미지(default.png) 반환

`width` 파라미터로 서버사이드 리사이징. Pillow(LANCZOS)로 축소 후 리사이즈 캐시에 저장.

```
요청: GET /api/image/img/000000_07da1a9dfd.jpg?width=300
응답: 이미지 바이너리 (image/jpeg)
```

### GET /api/status

운영 모니터링. DB 건수, NAS 연결 상태, 디스크 사용량, 마지막 파싱 이력을 반환.

```
요청: GET /api/status
응답 200:
{
  "server": {"uptime": "2시간 30분", "version": "1.0.0"},
  "database": {"products": 11821, "barcodes": 23642, "images": 11000, "stock_entries": 0},
  "last_parse": {"file": "codepath.xlsx", "parsed_at": "2026-03-20 10:00:00", "added": 0, "updated": 11821},
  "nas_sync": {"last_check": "2026-03-20 12:00:00", "status": "connected"},
  "disk": {"cache_size_mb": 45.2, "cache_limit_mb": 500, "backup_count": 7}
}
```

### PATCH /api/stock/{sku_id} (Phase 2)

재고 수량 수정. `BEGIN IMMEDIATE` 트랜잭션으로 동시 쓰기 방지. 변경 전후 수량을 stock_log에 자동 기록.

```
요청: PATCH /api/stock/12345
본문: {"quantity": 50, "memo": "입고", "updated_by": "PDA"}
응답 200:
{
  "sku_id": "12345",
  "quantity": 50,
  "memo": "입고",
  "updated_by": "PDA",
  "updated_at": "2026-03-20 12:00:00"
}
```

### GET /api/stock/{sku_id}/log

재고 수정 이력 조회. 최신순 정렬.

---

## 6. 보안 설계

### Path Traversal 방어

이미지 프록시에서 `../../etc/passwd` 같은 경로 공격 방지. `Path.resolve()` 후 `is_relative_to()` 검증.

```python
def validate_path(base: Path, sub_path: str) -> Path:
    resolved = (base / sub_path).resolve()
    if not resolved.is_relative_to(base):
        raise HTTPException(status_code=400, detail="invalid path")
    return resolved
```

### SQL Injection 방어

모든 SQL 쿼리에서 파라미터 바인딩(`?` placeholder) 사용. 문자열 포매팅으로 쿼리를 조립하지 않음.

### CORS

내부 WiFi 전용 시스템이므로 `cors_origins: ["*"]`. 향후 IP 대역 제한 가능 (`config.py`에서 설정).

### Android 네트워크

- `network_security_config.xml`로 cleartext HTTP 허용 (내부 WiFi 전용, HTTPS 인증서 불필요)
- R8 난독화: release 빌드에서 `isMinifyEnabled = true`, `isShrinkResources = true` 활성화

---

## 7. 성능 설계

### 응답 시간

| 구간                     | 목표       | 실측          |
| ------------------------ | ---------- | ------------- |
| 바코드 스캔 -> 상품 표시 | 0.3~0.5초  | DB 조회 1.6ms |
| FTS5 검색                | 100ms 이내 | DB 조회 0.9ms |

### 이미지 최적화

서버사이드 리사이징으로 네트워크 전송량 절감. PDA 5인치 화면에 원본 전송은 낭비.

- 썸네일 원본: ~108KB
- 실사 원본: ~2.3MB
- `width=300` 리사이즈 후: ~11KB

### DB 최적화

- **WAL 모드**: 읽기/쓰기 동시 실행 가능. 읽기 전용 커넥션에 `PRAGMA query_only = ON`
- **인덱스**: `idx_barcode_barcode`, `idx_barcode_sku_id`, `idx_image_barcode`
- **FTS5**: product_name, sku_id, category, brand 전문검색. 트리거로 자동 동기화
- **배치 처리**: xlsx 파싱 시 500건 단위 upsert. 11,821건 전체 파싱 ~2초

### 네트워크 최적화

- **httpx 싱글턴**: `app.state.http_client`로 TCP 커넥션 재사용. 매 요청마다 재연결하지 않음
- **RetrofitClient 싱글턴**: `@Volatile + synchronized`로 thread-safe. baseUrl 변경 시에만 재생성

### 오프라인 지원

- Room DB에 서버 응답을 캐싱
- 네트워크 실패 시 Room에서 로컬 조회 폴백
- 오프라인 상태를 배너로 표시

### 재시도 전략

OkHttp Interceptor 레벨에서 exponential backoff:

- 1차 재시도: 300ms 대기
- 2차 재시도: 600ms 대기
- 최대 2회 (총 3회 시도)
- 5xx 에러 또는 IOException 시 재시도

---

## 8. 디자인 시스템

### 기반

Stitch "Tactical Command" -- 군사/물류 현장에 적합한 고대비 다크 테마.

### No-Line Rule

구분선(border) 없이 배경색 차이만으로 영역을 분리. 시각적 노이즈를 줄여 정보 인식 속도를 높인다.

### 색상

| 용도                | 값      | 설명              |
| ------------------- | ------- | ----------------- |
| primary             | #040d1b | 배경              |
| secondary-container | #fe6a34 | 강조 (버튼, 액션) |

### 터치 타겟

56dp 이상. 물류창고에서 장갑 착용 상태에서도 정확한 터치가 가능한 크기. Android 권장 48dp보다 8dp 확대.

### 타이포그래피

| 용도   | 크기      | 비고          |
| ------ | --------- | ------------- |
| 본문   | 16sp      | 기본 텍스트   |
| 제목   | 20sp      | 상품명 등     |
| 바코드 | monospace | 끝 5자리 볼드 |

---

## 9. 테스트 전략

### 서버 (pytest)

53개 테스트 케이스:

- API 통합 테스트 (바코드 조회, 검색, 이미지 프록시)
- 이미지 리사이징 테스트
- 재고 CRUD 테스트
- 문서 정합성 테스트
- 백업 테스트

### Android (JUnit)

8개 테스트 케이스:

- RetryInterceptor: 재시도 횟수, 지연 시간, IOException 처리
- CachedProduct: Room 엔티티 -> ScanResponse 매핑 정합성

### 수동 테스트 (핸드폰)

16개 테스트 케이스:

- 바코드 스캔/검색 동작
- 이미지 토글
- 오프라인 모드 전환
- 설정 화면 동작
- 에러 상태 처리

---

## 10. 배포 구성

### Mini PC

- OS: Windows 11
- 서비스 관리: NSSM (Non-Sucking Service Manager)으로 Windows 서비스 등록
- 자동 시작: PC 부팅 시 서비스 자동 실행
- 포트: 8000

### NAS

- 장비: Synology NAS
- 접근 방식: WebDAV (chominseven.synology.me:58890)
- 동기화 주기: 1분 (PROPFIND로 파일 변경 감지, 변경 시 다운로드 후 파싱)

### 백업

- 방식: Windows 작업 스케줄러
- 주기: 매일 03:00
- 보관: 7일분
- 대상: SQLite DB 파일

### APK 배포

- Mini PC에 APK 파일 업로드 (C:\scanner\app-debug.apk)
- PDA에 직접 설치 (Play Store 미사용)

### 서버 설정

`config.py`의 주요 설정 항목:

| 항목                    | 기본값           | 설명                     |
| ----------------------- | ---------------- | ------------------------ |
| host                    | 0.0.0.0          | 바인딩 주소              |
| port                    | 8000             | 서버 포트                |
| webdav_base_url         | (빈 값)          | NAS WebDAV 주소          |
| webdav_path_prefix      | (빈 값)          | WebDAV 경로 prefix       |
| nas_sync_interval       | 60               | NAS 동기화 주기 (초)     |
| image_cache_max_size_mb | 500              | 이미지 캐시 상한 (MB)    |
| brand_filter            | ["스페이스쉴드"] | 상품명에서 제거할 브랜드 |
| cors_origins            | ["*"]            | CORS 허용 origin         |

모든 설정은 `.env` 파일 또는 환경변수로 오버라이드 가능 (pydantic-settings).
