# 물류창고 스캐너 (Phoenix Warehouse Scanner)

PDA(Zebra TC60)에서 바코드를 스캔하면 상품 정보와 이미지를 즉시 조회하는 물류 관리 시스템.

## 핵심 기능

- **바코드 스캔**: EAN-13 바코드 스캔 후 0.3초 이내 상품 조회
- **상품 검색**: 상품명/SKU 텍스트 검색 (FTS5 최적화)
- **상품 상세**: 이미지 슬라이드, 바코드 복사, 구매 링크
- **장바구니**: 구글시트 자동 연동
- **라벨 인쇄**: TSC TE10 라벨 프린터 연동
- **창고 도면**: 웹 기반 도면 편집/저장
- **셀 관리**: 셀별 사진 및 상품 매칭
- **상품-셀 매칭**: 바코드/검색으로 자동 매칭 및 location 관리
- **외박스 QR**: 6블록 그리드로 외박스 스캔 및 관리
- **앱 자동 업데이트**: 서버 버전 체크 후 APK 자동 다운로드
- **오프라인 캐시**: Room DB 기반 로컬 데이터 저장
- **서버 자동 발견**: 내부 WiFi 서브넷 스캔

## 시스템 구성

```
PDA (Zebra TC60 Android)
         ↓ WiFi (내부망)
Mini PC (Windows 11) — FastAPI 서버
         ↓ WebDAV
NAS (Synology) — xlsx 데이터 + 이미지
```

## 기술 스택

### Android (PDA)

- **언어**: Kotlin
- **아키텍처**: MVVM (ViewModel + Repository)
- **UI**: View Binding, Material Components
- **네트워크**: Retrofit2 + OkHttp
- **이미지**: Coil
- **바코드 스캐너**: Zebra DataWedge Intent API
- **데이터베이스**: Room (SQLite)
- **최소 SDK**: 24 / **대상 SDK**: 34

### Server (Mini PC)

- **언어**: Python 3.11+
- **프레임워크**: FastAPI + Uvicorn
- **데이터베이스**: SQLite (aiosqlite)
- **이미지 저장**: NAS WebDAV 프록시 + 캐싱
- **엑셀 파싱**: openpyxl
- **파일 감시**: watchdog (자동 동기화)
- **CORS**: 내부망 전용

## 빠른 시작

### 서버 설치 (Windows Mini PC)

```bash
# Python 3.11+ 설치 확인
python --version

# 저장소 복제
cd C:\Projects\scan\server

# 가상 환경 (선택사항)
python -m venv venv
venv\Scripts\activate

# 의존성 설치
pip install -r requirements.txt

# 서버 실행 (개발)
python -m app.main

# 서버 실행 (프로덕션, NSSM 서비스)
# NSSM 이미 구성됨 → 자동 시작, 모니터링
```

### Android 앱 빌드

```bash
cd android

# 디버그 APK 빌드
./gradlew assembleDebug

# 릴리스 APK 빌드 (서명 필요)
./gradlew assembleRelease

# 에뮬레이터 실행
./gradlew runDebug
```

### 설정

#### Server (`server/app/config.py`)

```python
# 필요한 환경 변수 설정
NAS_WEBDAV_URL=https://chominseven.synology.me:58890/webdav
NAS_USERNAME=your_username
NAS_PASSWORD=your_password
NAS_XLSX_PATH=물류부/scan/
DB_PATH=./data/warehouse.db
```

#### Android

1. **Settings** 화면에서 API 서버 주소 입력
   - 서버 IP: `192.168.x.x:8000`
   - **검증** 버튼으로 연결 테스트
2. **DataWedge** 프로파일 자동 생성 (앱 첫 실행 시)

## API 엔드포인트

### 기본 조회

| 메소드 | 경로                   | 설명                     |
| ------ | ---------------------- | ------------------------ |
| GET    | `/api/scan/{barcode}`  | 바코드로 상품 조회       |
| GET    | `/api/search?q=검색어` | 상품명/SKU로 검색        |
| GET    | `/api/image/{path}`    | NAS 이미지 조회 (프록시) |

### 장바구니 & 체크아웃

| 메소드 | 경로               | 설명                 |
| ------ | ------------------ | -------------------- |
| POST   | `/api/cart/add`    | 장바구니에 상품 추가 |
| GET    | `/api/cart/list`   | 장바구니 목록 조회   |
| POST   | `/api/cart/export` | 구글시트에 내보내기  |

### 라벨 인쇄

| 메소드 | 경로               | 설명               |
| ------ | ------------------ | ------------------ |
| POST   | `/api/print/label` | TSC TE10 라벨 인쇄 |

### 창고 도면

| 메소드 | 경로                | 설명                |
| ------ | ------------------- | ------------------- |
| GET    | `/api/map/layout`   | 도면 레이아웃 조회  |
| POST   | `/api/map/layout`   | 도면 저장           |
| GET    | `/admin/map-editor` | 웹 기반 도면 에디터 |

### 셀 & 상품-셀 매칭

| 메소드 | 경로                                    | 설명                 |
| ------ | --------------------------------------- | -------------------- |
| GET    | `/api/cell/{cell_key}`                  | 셀 상세 (사진, 상품) |
| POST   | `/api/cell/{cell_key}/photo`            | 셀 사진 업로드       |
| DELETE | `/api/cell/{cell_key}/photo/{photo_id}` | 셀 사진 삭제         |
| POST   | `/api/cell/{cell_key}/match`            | 상품-셀 매칭         |
| POST   | `/api/cell/{cell_key}/product`          | 셀에 상품 추가       |

### 외박스 관리

| 메소드 | 경로                     | 설명                  |
| ------ | ------------------------ | --------------------- |
| GET    | `/api/box/{box_id}`      | 외박스 정보 조회      |
| POST   | `/api/box/{box_id}/qr`   | 외박스 QR 스캔        |
| PUT    | `/api/box/{box_id}/grid` | 6블록 그리드 업데이트 |

### 서버 상태

| 메소드 | 경로          | 설명                |
| ------ | ------------- | ------------------- |
| GET    | `/health`     | 서버 헬스 체크      |
| GET    | `/api/status` | 서버 상태 대시보드  |
| GET    | `/docs`       | Swagger UI API 문서 |

## 데이터 구조

### 데이터 소스 (NAS)

```
물류부/scan/
├── codepath.xlsx          # 바코드 → 이미지 경로 매핑
├── coupangmd00_sku_download_*.xlsx  # 쿠팡 SKU 데이터
├── img/                   # 썸네일 이미지
└── real_image/            # 실사 이미지
```

### 데이터베이스 (SQLite)

```sql
PRODUCT              -- 상품 정보
├── sku_id (PK)
├── product_name
├── primary_barcode
└── ...

BARCODE              -- 상품당 복수 바코드
├── barcode (PK)
├── sku_id (FK)
└── ...

IMAGE                -- 상품 이미지
├── image_id (PK)
├── sku_id (FK)
├── image_path
└── ...

STOCK                -- 재고 정보
├── location         -- 셀 위치 (예: A-1-1)
├── quantity
└── ...

CELL_PRODUCT         -- 셀-상품 매칭
├── cell_key
├── barcode
└── location

BOX_ITEM             -- 외박스 QR 관리
├── box_id
├── grid_position
└── ...

MAP_LAYOUT           -- 도면 레이아웃
├── version
├── layout_data (JSON)
└── ...
```

## 배포

### Mini PC (Windows)

1. **FastAPI 서버를 NSSM 서비스로 등록**

   ```bash
   nssm install ScanServer "python -m app.main"
   nssm set ScanServer AppDirectory "C:\Projects\scan\server"
   nssm start ScanServer
   ```

2. **자동 백업 (Task Scheduler)**
   - 매일 새벽 3시 데이터베이스 백업
   - 7일 보관

3. **방화벽 허용**
   - 포트 8000 (FastAPI)
   - 내부 WiFi 네트워크만 접근 가능

### PDA (Android)

1. **APK 빌드**

   ```bash
   ./gradlew assembleRelease
   ```

2. **서명 (release keystore 필요)**

   ```bash
   jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
     app-release-unsigned.apk alias_name -keystore keystore.jks
   ```

3. **설치**
   ```bash
   adb install app-release.apk
   ```

## 테스트

### 서버 테스트

```bash
cd server

# 모든 테스트 실행
pytest

# 특정 테스트만 실행
pytest tests/test_product_service.py -v
```

### 통합 테스트

```bash
bash scripts/test_integration.sh
```

## 성능 목표

| 항목                    | 목표                          |
| ----------------------- | ----------------------------- |
| 바코드 스캔 → 상품 표시 | 0.3 ~ 0.5초                   |
| 상품 검색 (1만 SKU)     | 0.5초 이내                    |
| API 응답 시간           | 100ms 이내                    |
| 이미지 로딩             | 1초 이내 (캐시 히트 시 300ms) |

## 주의사항

- **기존 시스템 공존**: scan13.exe(기존 USB 스캐너)는 그대로 유지되며, 우리 서버는 별도로 동작
- **읽기 전용**: Phase 1은 읽기 전용. 재고 쓰기는 추후 업그레이드 필요
- **내부망 전용**: 인터넷 연결 없이 WiFi만으로 동작
- **NAS 의존성**: xlsx 데이터 소스가 NAS에만 있으므로 NAS 연결 필수

## 문제 해결

### 서버 실행 실패

```bash
# 1. 포트 8000 확인
netstat -ano | findstr :8000

# 2. Python 경로 확인
where python

# 3. 의존성 재설치
pip install -r requirements.txt --force-reinstall
```

### Android 연결 실패

1. **네트워크 확인**: PDA와 Mini PC가 같은 WiFi 네트워크에 있는가?
2. **API 주소**: Settings에서 서버 IP:8000 설정
3. **방화벽**: Windows 방화벽에서 포트 8000 허용
4. **서버 상태**: `curl http://server-ip:8000/health`

### 바코드 인식 안 됨

1. **DataWedge 프로파일 확인**: Settings > DataWedge Profiles
2. **Intent Action**: `com.scan.warehouse.SCAN`으로 설정되어 있는가?
3. **바코드 형식**: EAN-13 지원 여부 확인

## 라이선스

내부 프로젝트 (스페이스쉴드 - 쵸미세븐)

## 지원

- 개발자: 이은석 (프리랜서)
- 클라이언트: 스페이스쉴드 (쵸미세븐)
