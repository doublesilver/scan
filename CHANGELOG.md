# 변경 이력 (CHANGELOG)

모든 주요 변경사항을 문서화합니다. 형식은 [Keep a Changelog](https://keepachangelog.com/)를 따릅니다.

---

## [5.3.3] - 2026-04-08 (추가 보강)

### 시연 대비 — print_agent 복원 및 2단 방어

- **`server/tools/print_agent/` 신규** — 물류PC 측 BarTender 연동 agent 소스 복원·개선. 이전 세션에서 임시 작성 후 삭제되어 repo에 부재했던 걸 발견, 복원하면서 동시에 구조 개선.
  - **단일 프로세스 구조**: 원본의 2프로세스(agent + watcher + 파일 큐 폴링)를 하나로 통합, BarTender CLI 직접 호출
  - **템플릿 사전 인덱싱**: 시작 시 `template_dirs` walk 해서 `{끝5자리: 경로}` dict 생성 — O(1) 조회
  - **폴백 로직 제거**: 매칭 실패 시 조용히 기본 TSPL 레이아웃 찍던 동작을 제거하고, 명확한 `"Template file not found: XXXXX.btw"` 에러 반환
  - **2단 방어 — 상품명 검증**: `product_name` 필드를 request 에 추가하고 파일명과 키워드 overlap 계산. threshold(0.3) 미만이면 **인쇄 거부** + 상세 에러 반환. 끝 5자리만 우연히 같은 '지뢰 바코드' (예: 쵸미세븐 에펠타워 바코드가 갤럭시 워치 템플릿과 충돌) 방지.
  - **`GET /health`**: 상태·인덱스 개수·BarTender 경로 조회 (부작용 없음, 시연 전 점검용)
  - **`POST /reload`**: 신규 템플릿 추가 후 재시작 없이 재인덱싱
  - **`/`, `/print`, 빈 경로 모두 POST 지원**: 미니PC `.env` 의 URL 이 경로 없이 설정돼도 호환

- **서버 `print_service.py`**: agent 호출 시 `product_name`, `sku_id` 도 JSON body에 포함. `_friendly_agent_error` 에 "상품명 불일치" 패턴 추가.

- **실제 물류PC 배포 완료** (SSH 원격): `C:\print_agent.py` 배치 + `schtasks /sc ONLOGON` 등록으로 다음 로그인 시 자동 기동. 현재 세션에서 즉시 기동 후 `/health` 및 전체 체인 에러 경로 검증 완료 (실제 인쇄 X).

### 시연 대비 — 문서

- **`docs/DEMO_CHECKLIST.md` 신규**: 시연 3시간 전/30분 전/중 단계별 확인 항목, Toast 문구별 현장 대응표, agent·서버 긴급 재시작 스니펫, v5.3.3 배포 절차.

- **`docs/demo_barcodes.md` 신규**:
  - ✅ 시연 안전 TOP 20 — 스페이스쉴드 브랜드, DB 상품명과 템플릿 파일명 100% 키워드 일치 검증
  - ⛔ 지뢰 블랙리스트 87개 — 끝 5자리 충돌로 엉뚱한 라벨 찍힐 위험. 대부분 쵸미세븐 초기 번호대(`70268`~)
  - 📊 전체 매칭 통계 (DB 11,966 × 템플릿 11,396)

---

## [5.3.3] - 2026-04-08

### 관측성 (인쇄)

- **인쇄 실패 시 구체적 원인이 앱에 노출됨**: 기존엔 "인쇄 요청 실패" Toast 한 줄로 끝이라 왜 실패했는지 현장에서 판단 불가. 이제 서버가 돌려준 상세 메시지(예: `"라벨 템플릿 없음 (70008.btw)"`, `"프린터 오프라인 — 전원/케이블 확인"`, `"물류PC 응답 없음 (30초 타임아웃)"`)가 Toast에 그대로 표시됨.
- **`print_log` 테이블 신규 (스키마 v10)**: 모든 `/api/print` 시도(성공·실패 모두)를 미니PC DB에 기록.
  - 컬럼: `id, created_at, barcode, sku_id, product_name, quantity, status, via, http_status, elapsed_ms, message, raw_response`
  - 인덱스: `created_at`, `status`, `barcode`
  - 나중에 만들 통합 어드민 웹화면에서 그대로 조회 대상
- **`print_service.py` 리팩토링**:
  - 경로 A (BarTender agent) 응답을 HTTP 상태 코드와 body status 모두 검사해 에러 판정 정확도 향상
  - `httpx.TimeoutException` 별도 처리 — "물류PC 응답 없음" 메시지
  - `_friendly_agent_error()` 헬퍼로 agent raw 메시지를 작업자 친화 문구로 변환 (template/printer/timeout 패턴 매칭)
  - 경로 B (pywin32), 경로 C (dry_run) 모두 `elapsed_ms`, `via`, `raw_response` 필드를 반환해 로그 일관성 확보
- **`routes.py` `/api/print` 개편**: 성공·실패 모두 `print_log`에 기록. 성공 시에만 기존 `action_log`에 'print' 액션 기록 유지(기존 동작 호환).
- **`DetailActivity.kt`**: `HttpException` 전용 분기 추가. `errorBody` JSON의 `detail` 필드를 파싱해 Toast에 표시. 네트워크 예외는 별도 메시지. Toast 길이 `LONG`으로 변경해 가독성 확보.

### 문서

- **`docs/PRINT_FLOW.md` 개정**:
  - 장비 구성 명시 (미니PC=FastAPI 서버, 물류PC=BarTender+프린터)
  - 경로 A 설명에 미니PC → 물류PC 네트워크 경유 명시
  - `print_log` 테이블 구조·조회 예시 섹션 추가
  - 문제 해결 가이드에 "DB `print_log` 조회" 단계 추가

---

## [5.3.2] - 2026-04-08

### 변경 (UX 개선)

- **설정 화면 저장 버튼** 동작 개선: URL 검증 → 서버 연결 테스트 → 성공 시 자동 저장 + 메인 화면으로 복귀. 실패 시 이전 URL로 롤백 + 에러 메시지 유지. (테스트 버튼은 별개로 유지)
- **메인 하단 네비게이션**을 1개 버튼(배치)으로 축소. 장바구니 버튼 제거.
- **스캔 결과 화면 정리**:
  - 위치 정보 텍스트(`tvLocation`) 제거 — 위치 확인은 상품 상세 화면에서만
  - 스캔 결과 표시 중에는 하단 배치 버튼(`bottomBar`) 숨김 — 상품 확인에 집중
  - 상품 그룹명(`tvProductMasterName`)은 유지 — 위치가 아닌 상품 정보
- **검색 결과 화면에서 도면 숨김**: 이전엔 `layoutMainMap`과 `rvProducts`가 `weight=1`로 공간을 반씩 차지해 검색 목록 뒤로 도면이 노출되던 문제. 검색 결과 표시 중에는 `layoutMainMap` 명시적 `GONE` 처리.
- **`resetToMap()` 헬퍼 함수** 신규: 스캔 결과·검색 결과·장바구니 상태 복원 로직을 한 곳으로 통합해 중복 제거 및 상태 누락 방지.

### 추가

- **상품 배치 화면(`ProductPlacementActivity`) 검색 기능**: 바코드/QR 스캔 외에 상품명 검색으로도 배치 흐름 시작 가능
  - 화면 상단에 검색창 + 검색 버튼 추가
  - 바코드 형식 입력 시 기존 `handleScan` 직접 호출 (스캔과 동일 흐름)
  - 상품명 검색 시 `repository.searchProducts` → 결과 `AlertDialog` → 선택 → `handleScan(선택한 상품 바코드)` 호출로 스캔과 동일한 배치 흐름 진입

### 제거

- 메인 화면 `btnBarCart`(장바구니 버튼) 및 관련 로직(`addToCart`, 클릭 리스너, `isEnabled` 토글) 전체 삭제
- 스캔 결과 카드의 `tvLocation` 위치 표시 TextView 삭제
- `displayLocation` 계산 로직(`productMasterLocation`/`location` fallback) 제거

---

## [5.3.1] - 2026-04-08

### 수정 (코드 리뷰 반영)

- **장바구니 버튼 활성 상태 회귀 수정**: `addToCart` 완료 후 `isEnabled = true` 무조건 복원 → 스캔 결과 없는 상태에서도 활성화되던 버그. `isEnabled = currentScanResult != null`로 교체.
- **가짜 도면 렌더링 제거**: 서버 로드 실패 시 하드코딩된 `fallbackZones`(A/B/C 3구역)로 도면을 그려 잘못된 셀을 탭하면 서버에 존재하지 않는 셀 키로 CellDetailActivity에 진입해 데이터 오염 위험이 있었음. 실패 시 에러 메시지 + 탭 시 재시도 UI로 교체.
- **scanResult null 분기 `layoutMainMap` 복원 누락**: ViewModel이 null을 밀면 메인 도면이 숨겨진 채로 남던 문제. null 분기에서 `layoutMainMap.visibility = VISIBLE` 복원 추가.
- **`checkServerStatus` 경쟁 조건**: `onResume`마다 호출되는 서버 상태 체크가 여러 코루틴을 동시 기동해서 마지막 완료 순서에 따라 UI가 뒤집히는 문제. `serverStatusJob`으로 이전 작업 취소 후 재실행하는 패턴 도입.
- **`btnBarCart` 관리 책임 분산**: `setupHeader`와 `setupBottomNav`에 각각 리스너/상태가 흩어져 있던 것을 `setupBottomNav`로 통합. 비어있는 상태 클릭 시 "스캔한 상품이 없습니다" 토스트 피드백 추가.

### 제거

- **`mapAnimators` 데드 코드**: `ObjectAnimator` 리스트 선언·정리·취소 코드가 남아있었지만 실제 애니메이션 추가 로직은 없었음. 관련 import(`android.animation.*`) 포함 삭제.

### 분석 스크립트 품질 개선

- `server/scripts/analyze_sku_matching.py`:
  - 절대 경로 하드코딩 → `argparse` + 환경 변수 `SKU_CSV` + 상대 경로 기본값
  - 수동 `n//2` 중앙값 → `statistics.median()`
  - 나눗셈 전 0 체크 (`safe_pct` 헬퍼)
  - `single_skus`·`rejection_skus` 비어있을 때 `ZeroDivisionError` 방어
  - `main()` 함수로 감싸고 `sys.exit()` 종료 코드 반환

---

## [5.3.0] - 2026-04-08

### 추가

- **메인 화면 창고 도면 중앙 임베드**: 스캔 대기 상태에서 전체 창고 도면을 즉시 표시. 셀 탭 시 선반 칸 편집 화면(`CellDetailActivity`) 직접 진입
- **상단 서버 상태 텍스트**: 🟢 연결됨(IP 표시) / 🟡 오프라인 / 🔴 연결 실패 — `onResume`마다 갱신
- **하단 2버튼 네비게이션**: [배치][장바구니] 항상 표시. 장바구니는 스캔 결과 있을 때만 활성화(isEnabled 토글)
- **박스 모양 아이콘**(`ic_box.xml`): 배치 버튼 전용, Material `inventory_2` 스타일
- **SKU 매칭 분석 스크립트**(`server/scripts/analyze_sku_matching.py`): `sku_with_category.csv` 15,053행 대상 quotationId 기반 자동 매칭 커버리지 분석. 결과 커버리지 94.0%, product_master 후보 1,610개
- **하이브리드 2단 분할 시뮬레이션**(`server/scripts/simulate_hybrid_split.py`): 카테고리 1차 + 상품명 유형/브랜드 키워드 2차 분할. 대형 혼재 그룹 완전 해소(0개), 최종 커버리지 93.0% 유지, product_master 1,762개. 유형 키워드 사전 보강(스틱캡/이어패드/헤드셋/트리거/장갑/물걸레 등 14개) + 브랜드 키워드 추가(ROMO/오즈모/조이콘/프로콘/플라이디지/보이스캐디 6개) + 공백 전처리로 유형 미매칭 8.6% → 4.7% 감소

### 변경

- 검색창 안내 문구: "상품명 또는 바코드 검색" → "바코드 스캔 또는 상품명 검색"
- 서버 `/api/app-version` 응답 버전 정보 5.2.1 → 5.3.0

### 제거

- 설정 화면의 "상품 배치"·"도면 편집기" 진입 버튼 — 메인 하단 네비와 도면 직접 진입으로 이전
- 메인 화면 최근 스캔 가로 목록·관련 모델(`RecentScanItem`)·레이아웃(`item_recent_scan.xml`)·API 메서드(`getRecentScans`) — 도면 임베드로 대체
- 우하단 FAB 상품 배치 버튼 — 하단 네비 [배치] 버튼으로 이전

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
