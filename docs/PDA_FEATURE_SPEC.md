# PDA 앱 기능 명세서

> 버전: v5.1.0 / 기준일: 2026-04-03

---

## 1. 구축 의도

### 배경

물류창고(5층, 층당 70~100평)에서 작업자들이 상품을 찾고 이동하는 과정에서 기존 프로그램(scan13.exe)은 Mini PC에 고정된 USB 스캐너에서만 동작했다. 작업자가 이동 중에 상품을 확인하려면 PC 앞까지 돌아가야 했고, 이미지 확인이 어려워 오인식이 잦았다.

**핵심 목표: 작업자가 현장에서 이동하며 바코드를 스캔하면 즉시 상품 이미지와 위치를 확인할 수 있는 시스템.**

### 설계 원칙

- **기존 시스템 공존**: scan13.exe는 그대로 유지. PDA 앱은 독립 채널로 추가. 데이터 충돌 없음.
- **이미지가 최우선**: "이미지가 제일 눈에 띄어야 한다" — 클라이언트 요구사항. 스캔 결과에서 이미지 최대 표시.
- **오프라인 내성**: 창고 내 WiFi 음영지역 대비. Room DB 캐시로 마지막 조회 데이터 유지.
- **단순한 UX**: 로그인 없음. IP 최초 1회 설정. 서버 자동 발견. 장갑 착용 환경 → 터치 타겟 56dp, 폰트 22sp.
- **확장 대비 설계**: Phase 2(재고 수정, 웹 UI)로 전환 시 서버 API만 붙이면 되는 구조. Android는 Repository 패턴으로 API 교체 용이.

---

## 2. 시스템 아키텍처

```
TC60 PDA (Android App)
    │  WiFi (내부망)
    ▼
Mini PC (FastAPI 서버, NSSM 서비스)
    │  WebDAV
    ▼
NAS (Synology)
├── img/          ← 쿠팡 썸네일
├── real_image/   ← 실사 이미지
├── codepath.xlsx ← 바코드→이미지 매핑
└── sku_download.xlsx ← 쿠팡 SKU 데이터
```

### Android 앱 구조

| 레이어     | 구성                                             |
| ---------- | ------------------------------------------------ |
| UI         | Activity / Dialog (View Binding)                 |
| ViewModel  | ScanViewModel, CellDetailViewModel (Hilt)        |
| Repository | ProductRepository (Hilt Singleton)               |
| Network    | Retrofit2 + OkHttp (RetryInterceptor 3회)        |
| Cache      | Room DB (오프라인 스캔 기록)                     |
| Scanner    | DataWedgeManager (SharedFlow, BroadcastReceiver) |

### 서버 구조

| 레이어  | 구성                                             |
| ------- | ------------------------------------------------ |
| API     | FastAPI Router (`/api`)                          |
| Service | 20개 서비스 모듈 (비즈니스 로직 분리)            |
| DB      | SQLite (aiosqlite, schema v9, 마이그레이션 자동) |
| 이미지  | NAS WebDAV 프록시 + 디스크 캐시                  |
| 동기화  | NAS xlsx 변경 감지 → 자동 파싱 (5분 주기)        |

---

## 3. 화면 구성

### 3-1. 메인 화면 (`MainActivity`)

앱의 중심. 스캔 대기, 결과 표시, 검색, 입출고가 모두 이 화면에서 처리된다.

**상태 3가지**

| 상태      | 표시 요소                                             |
| --------- | ----------------------------------------------------- |
| 스캔 대기 | "PDA 버튼을 눌러 스캔하세요" + 검색창                 |
| 스캔 결과 | 상품 이미지(크게) + 상품명 + SKU ID + 바코드 + 하단바 |
| 검색 결과 | 상품 목록 (RecyclerView)                              |

**스캔 입력 3가지 경로**

1. DataWedge 하드웨어 트리거 → BroadcastReceiver → `scanFlow` SharedFlow
2. 검색창 텍스트 직접 입력 (Enter 또는 검색 버튼)
3. 외부 키스트로크 인식 (키보드 이벤트 `dispatchKeyEvent`, 300ms 타임아웃으로 새 바코드 구분)

**입력 분기 로직** (`performSearch`)

```
입력값
├── EAN-8/13 패턴 → scanBarcode() → GET /api/scan/{barcode}
├── "BOX-" prefix → scanBox() → GET /api/box/{qr_code}
└── 그 외 → searchProducts() → GET /api/search?q=
```

**하단 액션바** (스캔 결과일 때만 표시)

| 버튼       | 동작                                              |
| ---------- | ------------------------------------------------- |
| [입고]     | 도면 셀 선택 다이얼로그 → 층 선택 → POST /inbound |
| [출고]     | 도면 하이라이트 + 피킹 완료 확인 → POST /outbound |
| [장바구니] | POST /cart → 구글시트 행 추가                     |

---

### 3-2. 상품 상세 화면 (`DetailActivity`)

스캔 결과 카드 클릭 시 진입.

- **이미지**: ViewPager2 슬라이드. 썸네일(img/) ↔ 실사(real_image/) 토글.
- **정보**: 상품명(브랜드명 자동 제거), SKU ID, 바코드 목록(끝 5자리 볼드), 위치, 재고 수량.
- 스크롤 없이 한 화면에 모든 정보 표시.

---

### 3-3. 셀 상세 화면 (`CellDetailActivity`)

도면에서 셀 터치 시 진입. 또는 입고 워크플로우에서 셀 선택 후 진입.

- **셀 정보**: 구역명, 행·열, 층(level) 탭 구성.
- **층별 데이터**: 각 층에 배치된 상품 목록 + 사진.
- **사진 업로드**: 카메라 촬영 또는 갤러리 선택 → WebDAV 업로드.
- **상품 편집**: [+ 상품 추가] → 바코드 스캔 또는 검색 → 셀에 매칭. 롱프레스 → 삭제.
- **셀 네비게이션**: ← → 버튼으로 이전/다음 셀 이동 (27셀 연속 작업 지원).
- **빈 셀 자동 편집모드**: 셀에 상품이 없으면 진입 즉시 편집모드.
- **ViewModel**: `CellDetailViewModel`이 상태 관리. Configuration Change(화면 회전) 시 데이터 유실 없음.

---

### 3-4. 도면 다이얼로그 (`WarehouseMapDialog`)

전체 창고 도면을 표시하는 바텀 시트 다이얼로그.

- WebView 기반. 서버 `/admin/map-editor` 또는 `/api/map/layout` 렌더링.
- 셀 터치 → `onCellClick` 콜백 (입고 워크플로우에서 위치 선택에 사용).
- `location` 파라미터 전달 시 해당 셀 자동 하이라이트 (출고 워크플로우에서 위치 안내).

---

### 3-5. 외박스 QR 화면 (`BoxDetailActivity` / `BoxRegisterActivity`)

"BOX-" prefix QR 코드 스캔 시 진입.

**BoxDetailActivity**

- 6블록 그리드 UI (박스 내 위치 구역).
- 각 블록에 상품 매핑. 블록 터치 → 상품 상세 또는 외부 링크(쿠팡).
- 인라인 도면 미니맵 (박스가 창고 어디에 있는지).

**BoxRegisterActivity**

- 미등록 QR 스캔 시 등록 다이얼로그 → 박스명, 위치, 멤버 상품 등록.

---

### 3-6. 설정 화면 (`SettingsActivity`)

- 서버 IP/URL 직접 입력 + URL 형식 검증.
- 서버 자동 발견 (내부 WiFi /24 서브넷 스캔).
- 현재 연결 상태 표시.

---

### 3-7. 스플래시 (`SplashActivity`)

- 앱 시작 시 서버 버전 체크 (`GET /api/app-version`).
- 새 버전 있으면 다운로드 안내 다이얼로그 (`UpdateManager`).

---

## 4. 주요 기능 플로우

### 4-1. 바코드 스캔 → 상품 표시

```
DataWedge 하드웨어 트리거
  → BroadcastReceiver (Intent action: com.scan.warehouse.SCAN)
  → DataWedgeManager.scanFlow (SharedFlow)
  → MainActivity.repeatOnLifecycle(RESUMED) collect
  → performSearch(barcode)
  → ScanViewModel.scanBarcode()
  → ProductRepository.scanBarcode()
  → GET /api/scan/{barcode}
  → showScanResult()
  → scan_log 자동 기록 (서버)
목표 응답시간: 0.3~0.5초
```

### 4-2. 입고

```
스캔 결과 화면 [입고] 버튼
  → WarehouseMapDialog 표시 (도면 셀 선택)
  → 셀 터치 → 층 선택 다이얼로그 (하단/중단/상단)
  → POST /api/inbound { barcode, cell_key, level_index, quantity }
  → inbound_service: 상품 확인 → cell_level_product 등록 → product.location 업데이트
  → Toast "입고 완료: 상품명 → 위치"
```

### 4-3. 출고

```
스캔 결과 화면 [출고] 버튼
  → product.location 있으면 WarehouseMapDialog 해당 셀 하이라이트
  → 피킹 완료 확인 다이얼로그
  → POST /api/outbound { barcode, quantity }
  → outbound_service: 위치 정보 반환 + stock 감소
  → Toast "출고 완료: 상품명 (위치)"
```

### 4-4. 재고 실사

```
POST /api/inventory-check { cell_key, scanned_barcodes[] }
  → 셀에 등록된 상품 목록 vs 실제 스캔 목록 비교
  → 반환: { matched, missing, extra }
```

---

## 5. 서버 API 전체 목록

| 메서드   | 경로                                 | 용도                                   |
| -------- | ------------------------------------ | -------------------------------------- |
| GET      | `/api/scan/{barcode}`                | 바코드 → 상품 정보 조회                |
| GET      | `/api/search`                        | 상품명/SKU 텍스트 검색 (`?q=&limit=`)  |
| GET      | `/api/image/{path}`                  | NAS 이미지 프록시 (`?width=` 리사이즈) |
| GET      | `/api/stock/{sku_id}`                | 재고 수량 조회                         |
| PATCH    | `/api/stock/{sku_id}`                | 재고 수정                              |
| GET      | `/api/stock/{sku_id}/log`            | 재고 변경 이력                         |
| GET      | `/api/status`                        | 서버 상태 대시보드                     |
| GET      | `/api/app-version`                   | 앱 최신 버전 정보                      |
| POST     | `/api/print`                         | TSC TE10 라벨 인쇄                     |
| POST     | `/api/cart`                          | 장바구니 추가 → 구글시트               |
| GET      | `/api/box/{qr_code}`                 | 외박스 조회                            |
| POST     | `/api/box`                           | 외박스 생성                            |
| PATCH    | `/api/box/{qr_code}`                 | 외박스 수정                            |
| POST     | `/api/box/{qr_code}/member`          | 외박스 멤버 추가                       |
| DELETE   | `/api/box/{qr_code}/member/{sku_id}` | 외박스 멤버 제거                       |
| POST     | `/api/inbound`                       | 입고 처리 + 위치 등록                  |
| POST     | `/api/outbound`                      | 출고 처리 + 위치 반환                  |
| POST     | `/api/inventory-check`               | 셀 재고 실사 (예상 vs 실제)            |
| PATCH    | `/api/product/{sku_id}/location`     | 상품 위치 직접 수정                    |
| GET      | `/api/history`                       | 작업 이력 (인쇄/장바구니 등)           |
| POST     | `/api/favorite`                      | 즐겨찾기 추가                          |
| DELETE   | `/api/favorite/{sku_id}`             | 즐겨찾기 제거                          |
| GET      | `/api/favorites`                     | 즐겨찾기 목록                          |
| GET      | `/api/recent`                        | 최근 스캔 목록                         |
| POST     | `/api/import/urls`                   | 발주 URL xlsx 임포트                   |
| GET/POST | `/api/map/layout`                    | 도면 데이터 (호환 레이어)              |
| —        | 도면 CRUD 14개                       | zone/cell/level/product 정규화 API     |

---

## 6. DB 스키마 (핵심 테이블)

| 테이블               | 역할                                                 |
| -------------------- | ---------------------------------------------------- |
| `product`            | SKU 마스터 (sku_id, 상품명, 카테고리, 위치, 발주URL) |
| `barcode`            | 바코드 ↔ SKU 매핑 (1 SKU : N 바코드)                 |
| `image`              | 이미지 경로 (thumbnail / real_image)                 |
| `stock`              | 현재 재고 수량                                       |
| `stock_log`          | 재고 변경 이력                                       |
| `outer_box`          | 외박스 QR 등록                                       |
| `outer_box_item`     | 외박스 내 상품 목록                                  |
| `warehouse_zone`     | 창고 구역 (도면 정규화 v5.0)                         |
| `cell`               | 셀 좌표 (zone_id, row, col, label)                   |
| `cell_level`         | 셀 내 층 (level_index, 사진)                         |
| `cell_level_product` | 층별 상품 배치                                       |
| `product_fts`        | FTS5 전문검색 인덱스                                 |
| `action_log`         | 인쇄/장바구니 등 작업 이력                           |
| `scan_log`           | 스캔 기록                                            |
| `favorite`           | 즐겨찾기                                             |
| `parse_log`          | xlsx 파싱 이력                                       |

---

## 7. 향후 확장 가능한 영역

### 7-1. Phase 2: PC 웹 UI + scan13.exe 교체

현재 scan13.exe와 공존 중이지만, 재고 수정 기능이 양쪽에서 동시에 일어나면 충돌이 발생한다. Phase 2 전환 조건은 클라이언트가 PDA 단독으로 재고를 관리하겠다고 결정하는 시점.

- FastAPI 서버에 웹 UI (Jinja2 또는 React SPA) 추가
- 기존 API 그대로 재사용 (서버 코드 변경 최소)
- 현재 `static/map-editor.html`이 웹 UI 출발점

### 7-2. 발주하기 버튼

`product.coupang_url` 컬럼 이미 준비됨. 서버에 `POST /api/import/urls` + `url_import_service.py`도 구현 완료.  
클라이언트로부터 URL 매핑 엑셀 데이터 수신 후 즉시 활성화 가능.

### 7-3. 다중 사용자 + 작업 추적

현재 모든 요청이 `requested_by = 'PDA'`로 단일 기록됨. 사용자 구분이 필요해지면:

- `SettingsActivity`에 작업자 이름 입력 추가
- 모든 API 요청 헤더에 `X-Worker-Id` 포함
- `action_log`, `stock_log` 테이블 구조 변경 없이 `updated_by` 컬럼 활용

### 7-4. 실시간 재고 동기화

현재 Poll 방식 (앱이 조회 시점에만 서버에서 가져옴). 동시 사용자가 늘면:

- FastAPI WebSocket 엔드포인트 추가
- Android에서 `OkHttp WebSocket` 연결
- 입고/출고 발생 시 같은 셀을 보는 다른 PDA에 즉시 반영

### 7-5. 쿠팡 서플라이어 허브 자동 이미지 수집

`scripts/fetch_supplier_hub.js` (브라우저 콘솔 스크립트)와 `scripts/download_images.py`가 이미 구현됨.  
클라이언트 계정으로 정기 실행하면 신규 SKU 이미지 자동 수집 가능.

### 7-6. 바코드 미부착 상품 대응

현재 EAN-13 바코드가 없는 상품은 스캔 불가. 확장 방안:

- 서버에서 SKU별 자체 QR 생성 API 추가 (`GET /api/product/{sku_id}/qr`)
- 라벨 인쇄 기능 (`POST /api/print`)과 연계하여 현장에서 즉시 부착 가능

### 7-7. 층별 재고 집계

`cell_level_product` 테이블 구조상 "셀 X의 2층에 어떤 상품이 몇 개" 집계 쿼리가 가능.  
현재 단순 배치 기록만 하지만, `quantity` 컬럼 추가 시 층별 재고 보고서 생성 가능.

---

## 8. 알려진 제약사항

| 항목            | 내용                                                                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| 네트워크        | 내부 WiFi 전용. 인터넷 불필요. 외부 접근 미지원.                                                                                           |
| 이미지          | NAS WebDAV 연결 끊기면 캐시된 이미지만 표시.                                                                                               |
| 동시 쓰기       | `asyncio.Lock`으로 단일 쓰기 직렬화. 초고속 동시 요청 환경에선 지연 발생 가능.                                                             |
| scan13.exe 공존 | 양쪽이 xlsx를 읽기 전용으로만 접근. 재고 수치는 각자 독립 관리. Phase 2 전까지 재고 데이터 일치 보장 안 됨.                                |
| PDA 배터리      | DataWedge가 백그라운드에서 상시 동작. 장시간 미사용 시 앱이 백그라운드로 내려가면 BroadcastReceiver 미동작 가능 → 앱 포그라운드 유지 권장. |
