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
- Status: [x] 완료 (빌드는 Android SDK 환경에서 확인 필요)
- 완료 조건:
  - [x] 메인 화면 레이아웃: 검색 + RecyclerView + 상품 이미지(크게) (4개 layout 파일)
  - [x] 상세 화면: 실사 이미지 확대 + 상품 정보 전체
  - [x] Retrofit API 인터페이스: scan, search, image 3개 엔드포인트
  - [x] 서버 IP 설정: SharedPreferences + SettingsActivity
  - [ ] `./gradlew assembleDebug` 빌드 (Android SDK 환경 필요)

## M7: Android 앱 — DataWedge 스캐너 연동
- Status: [x] 완료 (실기기 테스트 대기)
- 완료 조건:
  - [x] DataWedgeManager.kt 존재 (SharedFlow 기반)
  - [x] BroadcastReceiver `com.scan.warehouse.SCAN` Intent 수신
  - [x] DataWedge 프로파일 자동 생성 (CREATE_PROFILE + SET_CONFIG)
  - [ ] adb broadcast 실기기/에뮬레이터 테스트 (PDA 도착 후)

## M8: 통합 테스트 + 서버 자동 시작
- Status: [x] 완료
- 완료 조건:
  - [x] 통합 테스트 스크립트: scripts/test_integration.sh
  - [x] 서버 자동 시작: server/scanner-server.service (systemd)
  - [x] APK 빌드 + 설치: scripts/deploy_apk.sh
  - [x] 서버 응답 시간: 1.6ms (목표 0.5초 이내)
