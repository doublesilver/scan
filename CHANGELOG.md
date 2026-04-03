# 변경 이력 (CHANGELOG)

모든 주요 변경사항을 문서화합니다. 형식은 [Keep a Changelog](https://keepachangelog.com/)를 따릅니다.

---

## [5.1.0] - 2026-04-03

### 추가

- **Hilt DI 도입**: `@HiltAndroidApp`, `AppModule`, 모든 Activity `@AndroidEntryPoint`
- **CellDetailViewModel**: 셀 데이터·편집모드·네비게이션 상태 관리, Configuration Change 대응
- **입출고 워크플로우**:
  - 서버: `inbound_service`, `outbound_service`, `inventory_service`
  - API: `POST /inbound`, `POST /outbound`, `POST /inventory-check`
  - 앱 하단바 [입고][출고][장바구니] 3버튼
  - 입고: 스캔 → 도면 셀 선택 → 층 선택 → 자동 등록 + 위치 동기화
  - 출고: 스캔 → 위치 표시 + 도면 하이라이트 → 피킹 완료

### 변경

- `ScanViewModel` → `@HiltViewModel + @Inject constructor`
- `BuildConfig` flavor 분기 → DI Module로 이동

---

## [5.0.0] - 2026-04-03

### 추가

- 도면 DB 정규화: JSON blob → warehouse_zone/cell/cell_level/cell_level_product 4개 테이블
- 14개 새 API 엔드포인트 (zone/cell/level/product CRUD)
- PDA에서 구역 편집 (추가/수정/삭제)
- 셀 상세: [+ 상품 추가] 버튼, 상품 롱프레스 삭제
- 이전/다음 셀 네비게이션 (27셀 연속 작업)
- 빈 셀 진입 시 자동 편집모드
- 배포 자동화 스크립트 (scripts/deploy.sh)

### 변경

- 위치 동기화: 도면 셀 = SSOT, product.location 자동 동기화
- 웹 에디터: PDA 앱과 디자인 통일 (네이비+오렌지)
- 호환 레이어: 기존 GET/POST /api/map-layout 인터페이스 유지

### 수정

- 기존 JSON 데이터 자동 마이그레이션 (데이터 유실 없음)

---

## [4.3.0] - 2026-03-31

### 추가

- **상품-셀 매칭 시스템**: 바코드 스캔 또는 검색으로 선택한 상품을 셀에 자동 매칭
  - `POST /api/cell/{cell_key}/match` 엔드포인트 추가
  - 매칭 후 자동으로 셀의 location 업데이트
  - 중복 매칭 방지 로직
- **셀 상품 관리**: 셀에 상품 추가/제거 API
  - `POST /api/cell/{cell_key}/product` — 셀에 상품 추가
  - `DELETE /api/cell/{cell_key}/product/{barcode}` — 셀에서 상품 제거
- **앱 버전 로깅**: 사용자가 한 작업의 앱 버전 기록

### 변경

- **셀 상세 화면**: 상품-셀 매칭 후 UI 자동 갱신
- **데이터베이스**: CELL_PRODUCT 테이블 추가 (셀별 상품 매핑)

---

## [4.2.5] - 2026-03-28

### 추가

- **외박스 QR 관리 고도화**:
  - 6블록 그리드 UI 리디자인 (각 블록별 터치 영역 개선)
  - 인라인 도면 보기 (그리드 옆에 도면 미니맵)
  - 각 블록별 외부 링크 지원 (쿠팡/타 플랫폼 링크 저장)
- **웹 에디터 모바일 그리드**: map-editor.html의 그리드 조정 기능 모바일 대응

### 변경

- **BoxDetailActivity**: 6블록 그리드를 더 큰 터치 타겟으로 개선
- **도면 편집 UI**: 웹 에디터 모바일 화면 재구성

### 수정

- 외박스 QR 스캔 시 렉처리 개선

---

## [4.2.2] - 2026-03-27

### 추가

- **카메라 권한 처리**: 카메라 권한 동적 요청 (Runtime Permissions)
- **편집 버튼 이동**: 일부 화면의 편집 버튼 위치 조정 (접근성 개선)

### 변경

- **AndroidManifest.xml**: 카메라 권한 추가 및 설정

### 수정

- 카메라 권한 없을 때 앱 크래시 수정

---

## [4.2.0] - 2026-03-25

### 추가

- **CellDetailActivity 카드형 UI**: 셀 상세 화면 레이아웃 전면 개편
  - 각 셀별 사진 카드 (배열 가능)
  - 셀별 상품 목록 카드
  - 업로드 피드백 (진행 상황 표시)

### 변경

- **CellDetailActivity**: 기존 리스트 뷰 → 카드 기반 레이아웃
- **사진 업로드 UI**: 업로드 진행도 표시 추가

---

## [4.1.0] - 2026-03-22

### 추가

- **도면 에디터 (웹)**: `static/map-editor.html`
  - 드래그 앤 드롭으로 셀 위치 편집
  - 셀 라벨 추가/수정/삭제
  - 도면 저장 → 서버 동기화
- **셀 상세 편집**: WarehouseMapDialog를 통한 도면 내 셀 선택
- **사진 업로드 기능**: 셀별 사진 업로드 (NAS WebDAV 저장)
- **사진 삭제 기능**: 업로드된 사진 개별 삭제

### 변경

- **WarehouseMapDialog**: 웹 기반 도면 뷰어로 확장
- **CellDetailActivity**: 사진 및 상품 정보 표시

### 서버

- `/api/map/layout` 엔드포인트 추가 (도면 데이터 CRUD)
- `/admin/map-editor` 엔드포인트 추가 (웹 에디터)
- 사진 업로드 엔드포인트: `POST /api/cell/{cell_key}/photo`
- 사진 삭제 엔드포인트: `DELETE /api/cell/{cell_key}/photo/{photo_id}`

### 보안

- **경로 보호 (Path Traversal)**: 파일 경로 검증 강화
- **바코드 중복 방지**: 동시 스캔 요청 처리 (asyncio Lock)
- **파일 검증**: 업로드 파일 확장자/크기 검증

### 수정

- 도면 저장 시 동시 접근 문제 해결
- 웹 에디터 모바일 화면 렌더링 개선

---

## [4.0.0] - 2026-03-20

### 변경 (Breaking)

- **Material3 → MaterialComponents 마이그레이션**: Google Material 3 의존성 제거 및 Material Components로 통일
  - 라이브러리 호환성 개선
  - 기존 Material Design 레이아웃 유지

### 수정

- **S20+ 크래시 수정**: 화면 크기 관련 레이아웃 계산 오류 해결
  - ConstraintLayout 제약 조건 재정의
  - RecyclerView 높이 계산 개선

---

## [3.7.0] - 2026-03-15

### 추가

- **앱 자동 업데이트**:
  - 앱 실행 시 서버 버전 체크
  - 새 버전 있으면 자동 다운로드 및 설치 유도
  - `UpdateManager.kt` 추가
  - `/api/version` 엔드포인트 (서버 버전 정보 반환)
  - `/apk` 디렉토리 마운트 (APK 파일 호스팅)

### 변경

- **build.gradle.kts**: versionCode/versionName 관리 자동화

---

## [3.6.0] - 2026-03-10

### 추가

- **외박스 QR 스캔**: 별도 화면에서 외박스 QR 코드 스캔
  - `BoxDetailActivity` 추가
  - 6개 블록(박스 위치) 매핑
  - 외부 링크 저장 (쿠팡 등)

### 변경

- **메인 메뉴**: 외박스 스캔 옵션 추가

---

## [3.5.0] - 2026-02-28

### 추가

- **장바구니 기능**:
  - 스캔한 상품을 장바구니에 추가
  - 수량 조정
  - 구글시트 자동 내보내기 (gspread)
  - `POST /api/cart/add` 엔드포인트
  - `POST /api/cart/export` 엔드포인트

### 변경

- **메인 화면**: 장바구니 아이콘 추가

---

## [3.4.0] - 2026-02-25

### 추가

- **라벨 인쇄 기능**:
  - TSC TE10 프린터 연동
  - 바코드 라벨 자동 생성
  - `POST /api/print/label` 엔드포인트
  - Windows 프린터 API 통합

### 변경

- **메인 화면**: 라벨 인쇄 버튼 추가

---

## [3.3.0] - 2026-02-20

### 추가

- **창고 도면 보기**: `WarehouseMapDialog`
  - SVG 기반 도면 표시
  - 셀별 터치 가능
  - 셀 상세 정보 팝업

### 변경

- **메인 메뉴**: 도면 보기 옵션 추가

---

## [3.2.0] - 2026-02-15

### 추가

- **셀 상세 정보**: `CellDetailActivity`
  - 특정 셀의 상품 목록 표시
  - 셀별 사진 (준비 중)
  - 셀 위치 정보

### 변경

- **도면 클릭**: 셀 선택 시 상세 화면으로 이동

---

## [3.1.0] - 2026-02-10

### 추가

- **오프라인 캐시**:
  - Room DB 기반 로컬 저장소
  - 스캔 기록 저장
  - 네트워크 불가 시 캐시 데이터 조회
  - `ProductDao` 및 `WarehouseDatabase` 추가

### 변경

- **네트워크 오류**: 오프라인 캐시에서 조회 시도

---

## [3.0.0] - 2026-02-05

### 추가

- **서버 자동 발견**:
  - PDA 시작 시 내부 WiFi 서브넷 스캔
  - 활성 서버 자동 감지
  - 사용자 수동 설정 필요 없음 (선택사항)

### 변경

- **SettingsActivity**: 자동 발견 옵션 추가

---

## [2.5.0] - 2026-01-30

### 추가

- **데이터 검증 강화**:
  - 바코드 형식 검증 (EAN-13)
  - 바코드 중복 처리
  - 이미지 경로 검증

### 수정

- 잘못된 바코드 스캔 시 오류 메시지 표시

---

## [2.4.0] - 2026-01-25

### 추가

- **Retrofit2 자동 재시도**:
  - 네트워크 오류 시 3회 재시도
  - 지수 백오프 적용
  - `RetryInterceptor` 추가

### 변경

- **OkHttp 인터셉터**: 재시도 로직 통합

---

## [2.3.0] - 2026-01-20

### 추가

- **상품 이미지 슬라이드**: ViewPager2 기반 이미지 전환
- **바코드 정보**: 상세 화면에 바코드 출력
- **바코드 복사**: 터치로 바코드 클립보드 복사

### 변경

- **DetailActivity**: ViewPager2 레이아웃 추가

---

## [2.2.0] - 2026-01-15

### 추가

- **상품 검색**: 상품명 기반 텍스트 검색
  - `GET /api/search?q=검색어` 엔드포인트
  - SQLite FTS5 최적화
  - 검색 결과 20건 제한

### 변경

- **메인 화면**: 검색 탭 추가

---

## [2.1.0] - 2026-01-10

### 추가

- **상품 상세 화면**: `DetailActivity`
  - 바코드로 조회한 상품의 전체 정보 표시
  - 이미지, SKU, 상품명, 바코드 목록

### 변경

- **메인 화면**: 상품 클릭 시 상세 화면으로 이동

---

## [2.0.0] - 2026-01-05

### 추가

- **REST API 서버** (FastAPI):
  - `/api/scan/{barcode}` — 바코드 조회
  - `/api/search?q=검색어` — 상품 검색
  - `/api/image/{path}` — NAS 이미지 프록시
  - SQLite 데이터베이스 연동
  - Swagger UI `/docs`

### 변경

- **데이터 구조**: xlsx 파일 → SQLite 마이그레이션

---

## [1.5.0] - 2026-01-01

### 추가

- **DataWedge 프로필 자동 생성**:
  - 앱 설치 후 자동으로 DataWedge 프로파일 생성
  - Intent action: `com.scan.warehouse.SCAN`
  - EAN-13 형식 지원

### 변경

- **DataWedgeManager**: 자동 프로파일 생성 로직 추가

---

## [1.4.0] - 2025-12-25

### 추가

- **Retrofit2 API 클라이언트**:
  - `ApiService` 인터페이스 정의
  - `RetrofitClient` Singleton 구현
  - 기본 타임아웃 30초

### 변경

- **네트워크 통신**: OkHttp → Retrofit2 통합

---

## [1.3.0] - 2025-12-20

### 추가

- **SettingsActivity**: 서버 IP 설정
  - SharedPreferences 저장
  - URL 검증

### 변경

- **메인 메뉴**: 설정 옵션 추가

---

## [1.2.0] - 2025-12-15

### 추가

- **View Binding**: 모든 Activity에서 사용
- **ViewModel**: ScanViewModel 추가
- **LiveData**: UI 상태 관리

### 변경

- **findViewById 제거**: View Binding으로 대체

---

## [1.1.0] - 2025-12-10

### 추가

- **RecyclerView**: 스캔 결과 목록 표시
- **ProductAdapter**: 상품 목록 어댑터

### 변경

- **메인 화면 레이아웃**: LinearLayout → RecyclerView

---

## [1.0.0] - 2025-12-05

### 추가

- **MVP (Minimum Viable Product)**:
  - 바코드 스캔 (DataWedge Intent)
  - 상품 조회 (API 호출)
  - 상품 이미지 표시
  - 검색 입력 필드
- **UI**: 스캔 대기 화면 + 상품 목록
- **Android Manifest**: 필수 권한 설정 (INTERNET, CAMERA)

---

## 범례

- **[Added]**: 새 기능 추가
- **[Changed]**: 기존 기능 변경
- **[Deprecated]**: 곧 제거될 기능
- **[Removed]**: 제거된 기능
- **[Fixed]**: 버그 수정
- **[Security]**: 보안 취약점 수정

---

## 버전 번호 규칙

- **MAJOR.MINOR.PATCH** 형식 (Semantic Versioning)
- **versionCode**: 지속 증가 (1, 2, 3, ...)
- **versionName**: MAJOR.MINOR.PATCH (1.0.0, 1.1.0, ...)
