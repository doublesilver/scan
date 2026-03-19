# ROADMAP — Phase 1 (MVP)

## M1: 서버 프로젝트 셋업 + DB
- Status: [ ] 미완료
- 완료 조건:
  - [ ] `server/requirements.txt` 존재하고 `pip install -r requirements.txt` 성공
  - [ ] `python -m app.main` 실행 시 uvicorn이 에러 없이 기동 (localhost:8000)
  - [ ] `curl http://localhost:8000/docs` → Swagger UI 200 응답
  - [ ] SQLite DB에 PRODUCT, BARCODE, IMAGE 테이블이 생성됨 (`sqlite3 server/data/scanner.db ".tables"` 로 확인)

## M2: 데이터 이관 (xlsx 파서)
- Status: [ ] 미완료
- 완료 조건:
  - [ ] `codepath.xlsx` 파싱: 바코드 레코드가 DB에 적재됨 (`sqlite3 server/data/scanner.db "SELECT COUNT(*) FROM BARCODE"` ≥ 11821 — 이미지 없는 바코드 포함)
  - [ ] `sku_download.xlsx` 파싱: SKU 데이터가 PRODUCT 테이블에 적재됨 (`sqlite3 server/data/scanner.db "SELECT COUNT(*) FROM PRODUCT"` ≥ 1)
  - [ ] 이미지 경로 변환: `Z:\물류부\scan\img\xxx.jpg` → `img/xxx.jpg` 상대경로로 DB 저장됨 (`sqlite3 server/data/scanner.db "SELECT file_path FROM IMAGE LIMIT 3"` 에서 `Z:\` prefix 없음)
  - [ ] 파싱 스크립트를 2회 연속 실행해도 중복 데이터 없음 (upsert 동작)

## M3: REST API 개발
- Status: [ ] 미완료
- 완료 조건:
  - [ ] `curl http://localhost:8000/api/scan/8809461170008` → 200 + JSON (sku_id, product_name, barcodes, images 포함)
  - [ ] `curl http://localhost:8000/api/scan/0000000000000` → 404 (존재하지 않는 바코드)
  - [ ] `curl "http://localhost:8000/api/search?q=케이스"` → 200 + JSON 배열 (상품명 검색)
  - [ ] `curl http://localhost:8000/api/image/img/000000_07da1a9dfd.jpg` → 이미지 바이너리 응답 또는 NAS 미연결 시 적절한 에러 응답
  - [ ] 응답 시간: DB 조회 API 평균 100ms 이내 (서버 로그 또는 curl 타이밍으로 확인)

## M4: NAS 이미지 프록시 + 캐싱
- Status: [ ] 미완료
- 완료 조건:
  - [ ] `config.py`에 WebDAV base URL 설정 항목 존재
  - [ ] WebDAV 연결 시 `curl http://localhost:8000/api/image/img/000000_07da1a9dfd.jpg` → 이미지 바이너리 200 응답
  - [ ] `curl http://localhost:8000/api/image/real_image/20260218_003455.jpg` → 실사 이미지 200 응답 또는 기본 이미지 반환
  - [ ] WebDAV 미연결 시 → 캐시된 이미지 반환 또는 기본 이미지 반환 (500 에러가 아님)
  - [ ] 동일 이미지 2회 요청 시 2번째가 더 빠름 (캐싱 동작 확인, 서버 로그로 cache hit 확인)

## M5: xlsx 자동 갱신 (파일 감시)
- Status: [ ] 미완료
- 완료 조건:
  - [ ] 서버 실행 중 지정 폴더에 새 xlsx 파일 복사 시 자동 파싱 시작 (서버 로그에 "파일 감지" 메시지)
  - [ ] 갱신 후 `curl http://localhost:8000/api/scan/{새로추가된바코드}` → 200 (신규 데이터 반영)
  - [ ] 기존 SKU 정보 변경 시 업데이트 반영됨 (갱신 전후 응답 비교)
  - [ ] 갱신 이력 로그가 기록됨 (서버 로그에 추가/갱신 건수 출력)

## M6: Android 앱 — UI + API 연동
- Status: [ ] 미완료
- 완료 조건:
  - [ ] `./gradlew assembleDebug` 빌드 성공 (app/build/outputs/apk/debug/*.apk 생성)
  - [ ] 메인 화면 레이아웃: 검색 입력 필드 + 결과 리스트 + 상품 이미지(크게) 존재 (`grep -r "ImageView\|RecyclerView\|EditText" android/app/src/main/res/layout/`)
  - [ ] 상세 화면 레이아웃: 실사 이미지 확대 + 상품 정보 전체 표시
  - [ ] Retrofit API 인터페이스에 scan, search, image 엔드포인트 정의됨 (`grep -r "api/scan\|api/search\|api/image" android/app/src/main/java/`)
  - [ ] 서버 IP 설정 항목 존재 (`grep -r "BASE_URL\|server_ip\|serverUrl" android/app/src/main/`)

## M7: Android 앱 — DataWedge 스캐너 연동
- Status: [ ] 미완료
- 완료 조건:
  - [ ] `DataWedgeManager.kt` 또는 동등한 스캐너 핸들러 파일 존재 (`ls android/app/src/main/java/com/scan/warehouse/scanner/`)
  - [ ] BroadcastReceiver가 `com.scan.warehouse.SCAN` Intent를 수신하는 코드 존재 (`grep -r "com.scan.warehouse.SCAN" android/`)
  - [ ] DataWedge 프로파일 자동 생성 코드 존재 (`grep -r "CREATE_PROFILE\|SET_CONFIG" android/`)
  - [ ] `adb shell am broadcast -a com.scan.warehouse.SCAN --es com.symbol.datawedge.data_string "8809461170008"` 실행 후 앱이 상품 정보를 표시 (에뮬레이터 또는 실기기)

## M8: 통합 테스트 + 서버 자동 시작
- Status: [ ] 미완료
- 완료 조건:
  - [ ] 서버 시작 → xlsx 파싱 → API 응답 전체 플로우가 하나의 스크립트로 테스트 가능 (`python -m pytest server/tests/` 또는 셸 스크립트)
  - [ ] 서버 자동 시작 설정 파일 존재 (systemd service 파일 또는 startup 스크립트)
  - [ ] Android APK 빌드 + 설치 스크립트 존재 (`adb install` 명령 포함)
  - [ ] 전체 응답 시간: 바코드 스캔 → 상품 정보 표시 0.5초 이내 (curl 타이밍으로 서버 측 확인)
