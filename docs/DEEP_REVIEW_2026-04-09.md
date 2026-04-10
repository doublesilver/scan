# scan 프로젝트 심층 리뷰 보고서

작성일: 2026-04-09

## 1. 범위와 방법

이번 리뷰는 `scan` 저장소 전체를 대상으로 진행했다.

- 서버: `server/app/**`, `server/tests/**`, `server/docs/**`
- 안드로이드: `android/app/src/main/**`
- 운영 스크립트/설정: `scripts/**`, `.claude/**`, `.codex/**`, `docs/**`

검토 방식은 다음과 같다.

- 런타임 진입점과 핵심 서비스 코드 직접 검토
- 서버 테스트/안드로이드 컴파일 시도
- API, 앱 모델, 운영 문서 간 정합성 대조
- 운영/배포 스크립트의 이식성과 재실행 안전성 점검

검증 한계도 있다.

- `cd server && UV_CACHE_DIR=/tmp/uv-cache uv run pytest`는 53개 테스트를 수집했지만 초반에 비정상 종료되어 끝까지 확인하지 못했다.
- 안드로이드 컴파일은 sandbox와 네트워크 제한 때문에 `./gradlew :app:compileDebugKotlin` 완주 검증을 하지 못했다.

그럼에도 아래 항목들은 정적 분석만으로도 재현 가능성이 높은 결함 또는 구조적 리스크다.

## 2. 전체 진단

프로젝트는 기능 확장이 빠르게 진행되면서 다음 문제가 동시에 누적된 상태다.

- 신규 창고/도면 모델과 구형 shelf 모델이 공존하며 일부 경로가 분기되어 있다.
- 자주 쓰는 조회 경로는 비교적 정리되어 있지만, 운영/관리/업로드 경로는 테스트 공백이 크다.
- 서버와 안드로이드 사이의 계약은 주요 화면에서는 맞지만, 일부 관리 화면과 레거시 모델은 응답 스키마와 구현이 느슨하게 연결되어 있다.
- 운영 스크립트와 Codex/Claude 보조 도구는 특정 작성자 로컬 환경에 강하게 결합되어 있다.

즉, 평소 자주 쓰는 스캔/조회는 동작할 수 있지만, 관리 기능과 운영 자동화는 “조용히 깨진 상태”가 섞여 있을 가능성이 높다.

## 3. 즉시 수정이 필요한 결함

### 3.1 P1: `/api/import/urls`는 현재 정상 호출이 불가능함

- 위치: `server/app/api/routes.py`
- 문제 1: `Path`를 import하지 않아 `NameError`가 발생한다.
- 문제 2: 경로 검증은 `xlsx_watch_dir` 기준으로 하고, 실제 서비스 호출은 원본 `file_path`를 넘겨 현재 작업 디렉터리 기준으로 다시 해석한다.
- 영향: URL 일괄 임포트 기능이 실사용에서 실패한다.

관련 파일:

- `server/app/api/routes.py`
- `server/app/services/url_import_service.py`

### 3.2 P1: xlsx 파일 감시 후 자동 파싱이 시작도 못 하고 죽음

- 위치: `server/app/services/file_watcher.py`
- `_handle_new_file(file_path)` 내부에서 `os.path.getsize(path)`를 호출한다.
- 여기서 `path`는 정의되어 있지 않고, 의도된 변수는 `file_path`다.
- 영향: `codepath.xlsx`, `sku_download` 자동 반영 기능이 파일 이벤트 발생 시 바로 예외로 중단된다.

### 3.3 P1: 사진 업로드 검증 코드에 필수 import가 빠져 있음

- 위치:
  - `server/app/api/map_routes.py`
  - `server/app/api/warehouse_routes.py`
- 두 파일 모두 `Image.open(io.BytesIO(...))`를 사용하지만 `io`, `PIL.Image` import가 없다.
- `warehouse_routes.py`는 이 예외를 잡아 400 `invalid image file`로 바꾸기 때문에 원인 파악이 더 어려워진다.
- 영향: 정상 이미지 업로드도 실패할 가능성이 높다.

### 3.4 P1: `/api/status`는 호출 시 내부 예외 가능성이 높음

- 위치: `server/app/services/status_service.py`
- `asyncio.to_thread(...)`를 사용하지만 `asyncio` import가 없다.
- 영향: 운영 상태 조회, 관리자 점검, 대시보드 호출 시 500 발생 가능.

## 4. 기능상 버그 및 회귀 위험

### 4.1 P2: 구역 수정/삭제 UI는 기본 구역 코드에서 사실상 동작하지 않음

- 위치: `android/app/src/main/java/com/scan/warehouse/ui/WarehouseMapDialog.kt`
- `zone.code.toIntOrNull()`로 zone id를 얻으려 한다.
- 현재 기본 zone 코드는 `A`, `B`, `C`라 숫자로 변환되지 않는다.
- 결과적으로 저장/삭제 버튼을 눌러도 서버 호출이 실행되지 않는다.

이 문제는 관리 화면에서 “버튼은 있는데 아무 일도 안 일어나는” 형태로 나타난다.

### 4.2 P2: shelf 모델과 map-layout 모델이 병존해 API 의미가 갈라짐

- 위치:
  - `server/app/api/shelf_routes.py`
  - `server/app/services/shelf_service.py`
  - `server/app/services/warehouse_service.py`
  - `server/app/services/map_layout_service.py`
- `/api/shelves/{floor}/{zone}`는 더 이상 `shelf` 테이블을 중심으로 동작하지 않고 `map_layout` JSON을 렌더링한다.
- 반면 `/api/shelf/{shelf_id}/photo`, `/api/shelf/photo/{photo_id}`는 여전히 `shelf`, `shelf_photo` 테이블에 의존한다.
- 앱의 현재 흐름은 `cellKey` 기반 사진 업로드를 사용하고 있어 일부 레거시 shelf API는 사실상 다른 데이터 모델을 보고 있다.
- 영향: 유지보수자가 “shelf API”를 하나의 일관된 모델로 오해하기 쉽고, 향후 수정 시 회귀 가능성이 높다.

### 4.3 P2: 서버와 앱의 창고 상세 모델 계약이 느슨함

- 서버 `warehouse_service.get_cell_detail()`는 다음 형태를 반환한다.
  - `zone`: 중첩 객체
  - `levels[].index`
  - `levels[].products[].master_name`
- 앱 `android/app/src/main/java/com/scan/warehouse/model/Warehouse.kt`는 다음 키를 기대한다.
  - `zone_id`, `zone_code`
  - `level_index`
  - `product_master_name`
- 현재 앱의 주요 화면은 이 API를 적극적으로 쓰지 않아 겉으로 드러나지 않을 수 있지만, 이 계약 불일치는 새로운 관리 화면 추가 시 바로 장애로 이어질 수 있다.

### 4.4 P2: 테스트 코드가 실제 앱 수명주기를 그대로 올리며 격리 원칙을 깨고 있음

- 위치: `server/tests/test_image_resize.py`
- 전역에서 `from app.main import app` 후 `client = TestClient(app)`를 바로 생성한다.
- 이 방식은 `conftest.py`에서 해두는 watcher/NAS/DB patch를 우회한다.
- 영향:
  - 테스트 간 격리 약화
  - 실제 lifespan 초기화 개입
  - 지금 관찰된 pytest 비정상 종료와도 연결될 가능성 있음

## 5. 운영/보안/배포 리스크

### 5.1 P2: Codex 설정 스크립트가 재실행 안전하지 않음

- 위치: `scripts/setup_codex_scan.sh`
- 기존 프로젝트 trust 항목이 있어도 `trust_level`을 `trusted`로 교정하지 않는다.
- `scan-sqlite` MCP가 이미 있으면 현재 clone의 DB 경로인지 확인하지 않는다.
- 영향: 저장소 경로 이동, 재설치, 다중 clone 환경에서 잘못된 설정이 유지된다.

### 5.2 P2: Codex/Claude 명령 및 문서가 특정 로컬 경로에 고정됨

- 위치:
  - `.claude/commands/scan-build.md`
  - `.claude/commands/scan-server.md`
  - `.claude/commands/scan-test.md`
  - `.claude/commands/scan-deploy.md`
  - `.claude/hooks/format-py.sh`
  - `docs/codex-setup.md`
  - `docs/HANDOFF.md`
- `~/Projects/scan` 또는 `/Users/leeeunseok/Projects/scan` 하드코딩이 다수 남아 있다.
- 영향:
  - 다른 팀원 환경에서 명령 실패
  - 다른 위치 clone 시 문서와 실제 사용법 불일치
  - 자동 포맷 훅이 엉뚱한 디렉터리 기준으로 실행될 수 있음

### 5.3 P2: 배포 스크립트가 지나치게 환경 결합적이고 부분 배포 방식이 취약함

- 위치: `scripts/deploy.sh`
- 하드코딩된 서버 IP, 계정, Windows 경로, `sed -i ''` 사용으로 macOS 전제가 강하다.
- 서버 파일 일부만 `scp`로 덮어쓰고 서비스만 재시작한다.
- DB 마이그레이션/정적 파일/의존성/문서 버전 일관성 체크가 없다.
- 영향:
  - 특정 개발자 환경 외 재현 어려움
  - 파일 누락 배포 가능성
  - 앱/서버 버전 불일치 가능성

### 5.4 P3: 앱 API 키 기능은 준비되어 있으나 사용자 설정 경로가 부족함

- 위치:
  - `android/app/src/main/java/com/scan/warehouse/network/RetrofitClient.kt`
  - `android/app/src/main/java/com/scan/warehouse/ui/SettingsActivity.kt`
  - `server/app/middleware/auth.py`
- 서버는 `api_key` 설정 시 인증을 강제한다.
- 앱은 API 키 저장 로직은 갖고 있지만, 설정 화면에서 이를 명시적으로 입력/관리하는 UI가 거의 없다.
- 영향: 서버에서 인증을 켜는 순간 현장 기기 운영 절차가 매끄럽지 않을 수 있다.

## 6. 데이터/모델 구조 관찰

### 6.1 구형 모델과 신형 모델이 동시에 존재함

현재 저장소에는 아래 두 축이 공존한다.

- 구형 축:
  - `shelf`, `shelf_photo`
  - `outer_box`, `outer_box_item`
- 신형 축:
  - `warehouse_zone`, `warehouse_cell`, `cell_level`, `cell_level_product`
  - `product_master`, `product_master_sku`
  - `map_layout`

문제는 “마이그레이션이 끝난 단일 모델”처럼 정리되어 있지 않다는 점이다.

- 일부 API는 신형 모델만 본다.
- 일부는 구형 테이블을 계속 사용한다.
- 일부 UI는 map JSON만 사용한다.

이 구조는 단기적으로는 빠른 기능 추가에 유리했을 수 있지만, 지금은 유지보수 비용이 커졌다.

### 6.2 위치 표현 문자열이 화면마다 다르게 생성됨

예시:

- 서버 `sync_product_location`: `A구역 A-5`
- 안드로이드 `CellDetailViewModel`: `5층-A-5`
- 안드로이드 `BoxRegisterActivity`: `5층-A-5`

앱 쪽 `ParsedLocation.parse()`는 `5층-A-5` 형태를 기준으로 잘 작동한다.
반면 서버 쪽 일부 경로는 `A구역 A-5` 형식을 만든다.

즉, 같은 “위치”가 경로에 따라 다른 포맷으로 저장될 수 있다.

이 문제는 지금 즉시 크래시를 만들지는 않지만, 상세 화면, 외박스 위치 표시, 지도 하이라이트 로직에서 조용한 오표시를 만들 가능성이 있다.

## 7. 테스트 품질 평가

현재 테스트는 기본 조회 흐름에 치우쳐 있다.

상대적으로 잘 덮는 영역:

- `/health`
- `/api/scan`
- `/api/search`
- `/api/stock`
- 이미지 리사이즈 기본 동작

거의 비어 있는 영역:

- `/api/status`
- `/api/import/urls`
- map/warehouse write API
- 사진 업로드 API
- NAS sync / file watcher / parse 자동화
- box/placement 관리 흐름
- 인증(`api_key`) 케이스
- 실제 운영 스크립트와 문서 연계 검증

즉, 현재 테스트 스위트는 “핵심 조회 데모는 동작하는지”는 보여주지만, “운영 가능한 시스템인지”를 보장하지는 못한다.

## 8. 안드로이드 품질 평가

장점:

- 스캔/검색/오프라인 캐시 기본 흐름은 코드 구조가 비교적 단순하다.
- `ProductRepository`로 네트워크 접근이 모여 있어 교체와 mock이 쉽다.
- `ViewModel` 분리가 기본 수준은 되어 있다.

주의점:

- `Room.fallbackToDestructiveMigration()`으로 오프라인 캐시가 스키마 변경 시 즉시 삭제된다.
- 네트워크 예외를 `Exception` 단위로 넓게 삼켜 UI에서 원인 구분이 어렵다.
- 업데이트, 서버 탐지, 키보드 스캔, WebView 편집기가 각자 독립적으로 동작해 장애 분석 시 경로가 길어진다.

## 9. 우선순위별 권장 조치

### 1단계: 즉시 수정

- `Path`, `asyncio`, `io`, `Image`, `path` 오타/누락 import 수정
- `/api/import/urls` 경로 전달 방식 수정
- file watcher 자동 파싱 경로 복구
- 사진 업로드 API 테스트 추가

### 2단계: 구조 정리

- shelf 레거시 모델과 warehouse/map 모델 중 기준 축 결정
- 위치 문자열 포맷 단일화
- warehouse 관련 서버 응답 스키마와 안드로이드 모델 정렬
- `WarehouseMapDialog`의 zone 수정/삭제 로직을 실제 `zone.id` 기반으로 변경

### 3단계: 운영 체계 정리

- `scripts/setup_codex_scan.sh` 재실행 안전성 확보
- `.claude`, `docs`, `hooks`의 절대경로 제거
- `scripts/deploy.sh`를 환경변수/설정 기반으로 재작성
- 테스트에서 실제 운영 수명주기 부작용 제거

## 10. 결론

이 프로젝트는 “핵심 사용자 시나리오를 우선 완성한 뒤 관리 기능과 운영 자동화를 덧붙여 온 코드베이스”의 전형적인 상태다.

가장 중요한 판단은 다음 두 가지다.

- 지금 당장 현장 장애로 이어질 수 있는 문제는 주로 서버의 미검증 관리 경로에 몰려 있다.
- 장기 유지보수 리스크는 데이터 모델 이중화와 운영 도구의 환경 결합에서 커지고 있다.

따라서 다음 액션은 기능 추가보다 먼저 아래 순서를 권장한다.

1. 서버 관리/업로드 경로의 런타임 오류 제거
2. warehouse/map/shelf 데이터 모델 정리
3. 운영 스크립트와 문서의 이식성 확보
4. 테스트 스위트를 조회 중심에서 운영 경로까지 확장
