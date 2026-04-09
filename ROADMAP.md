# ROADMAP — Phase 1 (MVP)

## M1: 서버 프로젝트 셋업 + DB

- Status: [x] 완료
- 완료 조건:
  - [x] `server/requirements.txt` 존재하고 `pip install -r requirements.txt` 성공
  - [x] `python -m app.main` 실행 시 uvicorn이 에러 없이 기동 (localhost:8000)
  - [x] `curl http://localhost:8000/docs` → Swagger UI 200 응답
  - [x] SQLite DB에 PRODUCT, BARCODE, IMAGE 테이블이 생성됨

## M2: 데이터 이관 (xlsx 파서)

- Status: [x] 완료
- 완료 조건:
  - [x] `codepath.xlsx` 파싱: 바코드 레코드 DB 적재 (23,642건)
  - [x] `sku_download.xlsx` 파싱: SKU 데이터 PRODUCT 테이블 적재 (23,642건)
  - [x] 이미지 경로 변환: `img/xxx.jpg` 상대경로로 DB 저장
  - [x] 파싱 2회 실행 시 중복 없음 (upsert: 추가=0, 갱신=11821)

## M3: REST API 개발

- Status: [x] 완료
- 완료 조건:
  - [x] `/api/scan/8809461170008` → 200 + JSON (sku_id, product_name, barcodes, images)
  - [x] `/api/scan/0000000000000` → 404
  - [x] `/api/search?q=청소기` → 200 + JSON 배열 (20건)
  - [x] `/api/image/img/000000_07da1a9dfd.jpg` → 200 (mock 이미지)
  - [x] 응답 시간: DB 조회 0.9ms (목표 100ms 이내)

## M4: NAS 이미지 프록시 + 캐싱

- Status: [x] 완료
- 완료 조건:
  - [x] `config.py`에 WebDAV base URL 설정 항목 존재
  - [x] `/api/image/img/000000_07da1a9dfd.jpg` → 200 (mock 모드)
  - [x] `/api/image/real_image/20260218_003455.jpg` → 200 (mock 모드)
  - [x] WebDAV 미연결 시 → mock/기본 이미지 반환 (500 에러 아님)
  - [x] 캐시 히트 시 서버 로그 "cache hit" 출력

## M5: xlsx 자동 갱신 (파일 감시)

- Status: [x] 완료
- 완료 조건:
  - [x] 서버 시작 시 "xlsx 파일 감시 시작" 로그 출력 (watchdog)
  - [x] xlsx 파일 감지 시 자동 파싱 실행
  - [x] 갱신 이력 parse_log 테이블에 기록
  - [x] NAS 연결 후 실 데이터로 최종 검증 필요

## M6: Android 앱 — UI + API 연동

- Status: [x] 완료
- 완료 조건:
  - [x] 메인 화면 레이아웃: 검색 + RecyclerView + 상품 이미지(크게) (4개 layout 파일)
  - [x] 상세 화면: ViewPager2 이미지 슬라이드 + 상품 정보 전체
  - [x] Retrofit API 인터페이스: scan, search, image 3개 엔드포인트
  - [x] 서버 IP 설정: SharedPreferences + SettingsActivity + URL 검증
  - [x] ScanResponse Parcelable 적용 (Activity 간 데이터 전달)
  - [x] Snackbar 에러 처리 (재시도 액션 포함)
  - [x] 스캔 대기 화면 + 터치 타겟 56dp + 폰트 확대
  - [x] `./gradlew assembleDebug` 빌드 성공 (BUILD SUCCESSFUL)
  - [x] Stitch "Tactical Command" 디자인 시스템 적용
  - [x] 클라이언트 UI 피드백 반영 (이미지 최대, 바코드 끝5자리 볼드, 스페이스쉴드 삭제, 이미지 토글, 한 화면)
  - [x] Material Design 적용 후 Stitch 디자인으로 교체
  - [x] 하단 네비 제거, FAB 제거, 검색창 통합, 뒤로가기 처리

## M7: Android 앱 — DataWedge 스캐너 연동

- Status: [x] 완료 (실기기 테스트 대기)
- 완료 조건:
  - [x] DataWedgeManager.kt 존재 (SharedFlow 기반)
  - [x] BroadcastReceiver `com.scan.warehouse.SCAN` Intent 수신
  - [x] DataWedge 프로파일 자동 생성 (CREATE_PROFILE + SET_CONFIG)
  - [x] EAN-8/13 바코드 형식 검증 + 300ms debounce
  - [ ] adb broadcast 실기기/에뮬레이터 테스트 (PDA 도착 후)

## M9: 오프라인 캐시 + 서버 자동 발견

- Status: [x] 완료
- 완료 조건:
  - [x] Room DB 기반 로컬 캐시 (스캔 기록 저장)
  - [x] 네트워크 불가 시 캐시 데이터 조회
  - [x] 내부 WiFi 서브넷 자동 스캔 → 활성 서버 감지
  - [x] 앱 자동 업데이트: `/api/version` 체크 → APK 다운로드 유도

## M10: 창고 도면 (웹 에디터 + 셀 관리)

- Status: [x] 완료
- 완료 조건:
  - [x] `static/map-editor.html` — 드래그 앤 드롭 셀 편집
  - [x] WarehouseMapDialog — 도면 뷰어 + 셀 터치
  - [x] CellDetailActivity — 셀별 상품 목록 + 사진
  - [x] 사진 업로드/삭제 (`POST/DELETE /api/cell/{key}/photo`)
  - [x] 상품-셀 매칭 (`POST /api/cell/{key}/match`)

## M11: 외박스 QR + 장바구니 + 라벨 인쇄

- Status: [x] 완료
- 완료 조건:
  - [x] BoxDetailActivity: 6블록 그리드 + 인라인 도면 미니맵
  - [x] 외부 링크 저장 (쿠팡 등)
  - [x] 장바구니: 스캔 상품 추가/수량 조정 + 구글시트 내보내기
  - [x] TSC TE10 라벨 인쇄 (`POST /api/print/label`)

## M12: 도면 DB 정규화 (v5.0)

- Status: [x] 완료
- 완료 조건:
  - [x] JSON blob → warehouse_zone/cell/cell_level/cell_level_product 4테이블 분리
  - [x] 기존 JSON 데이터 자동 마이그레이션 (데이터 유실 없음)
  - [x] 14개 새 API 엔드포인트 (zone/cell/level/product CRUD)
  - [x] PDA 구역 편집 (추가/수정/삭제)
  - [x] 이전/다음 셀 네비게이션 (27셀 연속 작업)
  - [x] 빈 셀 진입 시 자동 편집모드
  - [x] 위치 동기화: 도면 셀 = SSOT, product.location 자동 동기화
  - [x] 호환 레이어: 기존 GET/POST /api/map-layout 유지
  - [x] 배포 자동화 `scripts/deploy.sh`

## M13: Hilt DI (v5.1)

- Status: [x] 완료
- 완료 조건:
  - [x] Hilt DI: WarehouseApp, AppModule, 전 Activity @AndroidEntryPoint
  - [x] ScanViewModel → @HiltViewModel
  - [x] CellDetailViewModel: 셀 데이터·편집모드·네비게이션 상태 관리

## M14: PDA 실기기 E2E 테스트

- Status: [ ] 대기 중
- 완료 조건:
  - [ ] APK v5.3.5 설치 (Mini PC C:\scanner\)
  - [ ] DataWedge 프로파일 자동 생성 확인
  - [ ] 실제 바코드 스캔 테스트 (0.3~0.5초 목표)
  - [ ] 장바구니 + 라벨 인쇄 워크플로우 E2E
  - [ ] 오프라인 모드 동작 확인
  - [ ] 현장 WiFi 환경 테스트

## M8: 통합 테스트 + 서버 자동 시작

- Status: [x] 완료
- 완료 조건:
  - [x] 통합 테스트 스크립트: scripts/test_integration.sh
  - [x] 서버 자동 시작: server/scanner-server.service (systemd)
  - [x] APK 빌드 + 설치: scripts/deploy_apk.sh
  - [x] 서버 응답 시간: 1.6ms (목표 0.5초 이내)
  - [x] Mini PC 배포 완료 (Windows 11, NSSM 서비스, 자동 시작)
  - [x] 자동 백업 설정 (매일 새벽 3시, 7일 보관)
  - [x] 서버 테스트 53개 통과
  - [x] 핸드폰 테스트 16케이스 통과
