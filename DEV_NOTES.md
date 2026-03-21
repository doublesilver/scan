# 개발 착수 노트 (2026-03-19)

## 프로젝트 상태

- 착수금 150만원 입금 완료, 공식 착수
- PDA 실기기: 대표님 중국 출장 중, 귀국 후 택배 발송 예정
- 기기 없이 할 수 있는 작업부터 착수 지시받음

## 핵심 전제: 기존 프로그램과 공존

**scan13.exe(기존)를 대체하는 것이 아니라, PDA 앱을 추가하는 것.**
Mini PC에서 USB 스캐너 + scan13.exe는 그대로 유지하고, PDA 앱이 별도로 동작한다.

```
Mini PC
├── scan13.exe (기존 유지, USB 스캐너, RaiDrive Z: 드라이브)
└── 우리 FastAPI 서버 (신규 추가, WebDAV 직접 접근)
         ↑
    PDA (우리 앱) --WiFi-->

NAS (단일 데이터 소스)
├── codepath.xlsx ← 둘 다 읽기 전용
├── sku_download.xlsx ← 둘 다 읽기 전용
├── img/ ← 둘 다 읽기 전용
└── real_image/ ← 둘 다 읽기 전용
```

- NAS xlsx/이미지가 유일한 데이터 소스, 양쪽 모두 읽기 전용
- DB 충돌 없음: scan13.exe는 자체 로직, 우리 서버는 별도 SQLite
- 포트 충돌: scan13.exe가 네트워크 서버가 아니므로 8000번 포트 사용 가능
- RaiDrive(Z:)와 WebDAV 직접 접근은 독립적이므로 간섭 없음

### 완성형 로드맵 (내부 참고용, 클라이언트에 언급 금지)

- **Phase 1 (현재)**: 읽기 전용. scan13 공존, 충돌 없음.
- **Phase 2 (재고 수정 요청 시)**: 쓰기 추가되면 scan13과 데이터 불일치 발생. PC 웹 UI 구축 + scan13 교체가 필수. → 추가 견적.
- **설계 원칙**: Phase 2 전환을 위해 서버 API를 모듈화해서 개발. 웹 UI 붙이기만 하면 되는 구조 유지.

---

## 1. 클라이언트 커뮤니케이션 주의사항

### 반드시 지킬 것

- **기획은 클라이언트가 주도** — 임의 기능 추가/제안 금지
- **AI 생성 느낌의 장문/과잉 설명 금지** — "별로입니다"라고 직접 말씀하심
- **기존 프로그램 비하 금지** — "초보적"이라 표현했다가 지적받음
- Phase 2, 3은 클라이언트가 직접 삭제 요청 — Phase 1만 구현

### 클라이언트가 중요하게 본 것

- "말이 필요 없게 만들어주신다면 당연히 합니다" → 결과물로 보여줘야 함
- 구체적이고 실무적인 제안서를 원함 (4가지 질문에 조목조목 대응)
- 지속 업데이트 가능한 구조 요구

---

## 2. 기존 프로그램 분석 (scan13.exe)

### 확인된 구성

- **PySide6 데스크톱 앱** (Python 3.14, PyArmor 난독화)
- Mini PC에서 실행, USB 바코드 스캐너 연결
- **RaiDrive**로 NAS를 Z: 드라이브로 마운트 (WebDAV)

### UI 구성 (스크린샷 기반)

```
┌────────────────────────────────────────────────┐
│ codepath.xlsx 경로: Z:\물류부\scan\codepath.xlsx [찾기]  │  상품명
│ sku_download.xlsx 경로: Z:/물류부/scan/coupang... [찾기] │  SKU ID
│ 바코드 스캐너: [드롭다운 선택]         [새로고침]  │
│                                                │
│ 로그:                                          │
│ sku_download 헤더 인식 실패: SKU ID/상품명/    │
│ 바코드 열을 찾지 못했습니다                     │
├──────────┬─────────────┬──────────┤
│ 바코드 목록  │  상품 이미지(큰)  │ 실사(NO IMAGE) │
│ 8800307648347│              │              │
│ 8800307648354│  [게임 컨트롤러]  │              │
│ 8800307648323│              │              │
│ ...          │              │              │
│ [선택바코드]  │              │              │
│          [검색]│              │              │
└──────────┴─────────────┴──────────┘
```

### 기존 프로그램의 문제점 (로그에서 확인)

- `sku_download 헤더 인식 실패: SKU ID/상품명/바코드 열을 찾지 못했습니다`
- 쿠팡에서 다운로드한 xlsx 파일의 헤더가 바뀌면 인식 못하는 문제 존재
- → 우리 제안서에서 "헤더 불일치 대응 (자동 매칭/수동 지정)" 언급한 부분이 이것

---

## 3. NAS 구조 (WebDAV)

스크린샷에서 확인된 실제 경로:

```
WebDAV (Z:) / 물류부 / scan /
├── img/                          # 쿠팡 썸네일 이미지
│   ├── 000000_07da1a9dfd.jpg     (106KB)
│   ├── 000001_07da1a9dfd.jpg     (84KB)
│   ├── 000002_48e381cdc2.jpg     (89KB)
│   └── ... (순번_해시.jpg 형식)
├── real_image/                   # 실사 이미지 (현장 촬영)
│   ├── 20260218_003455.jpg       (날짜_시간.jpg 형식)
│   └── 20260218_005929.jpg
├── 물류부/                       # 기타 물류 데이터
├── codepath.xlsx                 (254KB) — 바코드→이미지 매핑
├── coupangmd00_sku_download_*.xlsx (997KB) — 쿠팡 SKU 데이터
└── scan                          (1KB) — 설정 파일 (ini?)
```

---

## 4. 데이터 파일 분석

### codepath.xlsx

- **헤더 없음**, 바로 데이터 시작
- **11,821행**, 3열 (3열은 비어있음)
- Col 1: 바코드 (EAN-13, 880 prefix) — `8809461170008`
- Col 2: 이미지 경로 (Windows 절대경로) — `Z:\물류부\scan\img\000000_07da1a9dfd.jpg`
- **주의**: 마지막 일부 행은 이미지 경로 없음 (None) → 바코드는 있지만 이미지 없는 상품

```
예시:
8809461170008 → Z:\물류부\scan\img\000000_07da1a9dfd.jpg
8809461170015 → Z:\물류부\scan\img\000001_07da1a9dfd.jpg
8800363711184 → (없음)
```

### coupangmd00*sku_download*\*.xlsx

- **17컬럼, 11,821건** 데이터 존재 (openpyxl은 inlineStr 타입을 못 읽어서 1컬럼으로 보였음)
- **파서 구현 시 lxml로 직접 파싱 필요** (openpyxl의 read_only 모드는 inlineStr 미지원)
- 컬럼: SKU ID, 요청, 상품명, 바코드, 발주가능상태, 담당BM, 발주담당자, 길이/넓이/높이(mm), 최소구매수량, 중량(g), Box바코드, Inner/Box수량, Pallet수량
- **핵심 컬럼**: A=SKU ID, C=상품명, D=바코드
- **헤더 매칭 이슈**: 쿠팡 서플라이어 허브에서 시점마다 컬럼명/순서 변경 가능

### 파서 설계 시 고려

1. codepath.xlsx → 헤더 없음, 고정 2컬럼 (바코드, 경로)
2. sku_download.xlsx → 헤더 있음, 컬럼명 변동 가능 → 유사도 매칭 or 설정 파일 방식
3. 경로 변환: `Z:\물류부\scan\img\xxx.jpg` → 서버 내부 경로로 변환 필요

---

## 5. PDA 하드웨어 (TC60_EEA)

### 확인된 사항 (실기기 스크린샷)

- 모델명: **TC60_EEA** (About phone에서 확인)
- "DuraSpeed" 메뉴 존재 → Zebra 성능 관리 기능
- "Digital Wellbeing & parental controls" 존재 → Android 9+ 확인
- 하단: "application completed" 토스트 메시지

### 개발 환경 결정

- **minSdk 24 유지 가능** (Android 9 이상으로 보임)
- DataWedge Intent 방식 스캐너 연동
- 실기기 없이도 adb broadcast로 테스트 가능

---

## 6. 클라이언트 질문 & 합의사항 (카톡 3/17)

클라이언트가 보낸 4가지 질문:

### Q1. 기존 데이터 이관 범위

- xlsx, CSV, Google Sheets, ODS, JSON 대응 가능 명시함
- 형식 불일치 시 정제 작업 범위 질문 → 자동매칭(방안A) / 수동지정(방안B) 제시

### Q2. 신규 데이터 추가 방식

- NAS 지정 폴더에 xlsx 저장 → 서버 자동 감지 → DB 반영
- 현재 운영 방식과 동일하게 설계

### Q3. 이미지 연결

- codepath.xlsx 매핑 → img/ 폴더 → real_image/ 폴더 → 기본 이미지 (폴백 순서)
- NAS 직접 업로드 시 즉시 반영

### Q4. (제안서 09장 참조) 비용/일정

- 300만원 / D+10
- 착수 50% / 완료 50%

---

## 7. 통화 내용 요약 (3/13, 36분 50초)

통화 녹취록(EUC-KR 인코딩) 핵심:

- 기존 프로그램: 전 개발자가 만들어준 것, **소스코드 없이 exe만 받음**
- NAS에 이미지와 데이터가 분리 저장 (img/ = 쿠팡 썸네일, real_image/ = 실사)
- 물류부 → img 폴더에 이미지, codepath로 경로 매핑
- **상품당 복수 바코드** 존재 가능 (하나의 상품에 3~5개 바코드)
- **바코드 미부착 상품** 있음 → 현장에서 자체 QR 부착 가능성 언급
- PDA 모델: TC60, EEA 모델, SDK 확인 필요했음
- USB 바코드 스캐너도 사용 중 (Mini PC에 연결)
- 5층 건물, 층당 70~100평, 501호 502호 등
- 상품: 소품류 (폰케이스, 게임 컨트롤러 등) 다양한 카테고리
- 클라이언트 "현실적인 제안서를 원한다, 구체적으로"
- 클라이언트 "기획은 내가 한다, 개발자는 전략을 짜는 것"
- 3~4개월 후 프로그램 활용도 높아지면 추가 개발 가능성 있음

---

## 8. 제안서에 약속한 구현 범위 (Phase 1)

| #   | 기능            | 설명                                                |
| --- | --------------- | --------------------------------------------------- |
| 1   | 바코드 스캔     | PDA 내장 스캐너 EAN-13 인식                         |
| 2   | 상품 정보 표시  | SKU ID, 상품명, 바코드, 카테고리                    |
| 3   | **이미지 표시** | **썸네일 + 실사, 크게 먼저 표시** (클라이언트 강조) |
| 4   | 상품 검색       | 상품명 / SKU ID 텍스트 검색                         |
| 5   | API 서버        | FastAPI REST API                                    |
| 6   | 데이터 이관     | 쿠팡 xlsx 파싱 → DB                                 |
| 7   | 자동 갱신       | NAS에 xlsx 저장 시 자동 반영                        |
| 8   | 서버 구성       | Mini PC 설치, 자동 시작                             |

### UI 약속

- **2화면**: 메인(스캔+검색+리스트) → 상세(이미지 확대+상세 정보)
- 로그인 없음, 서버 IP 최초 1회 설정

### 성능 약속

- 스캔 → 표시: **0.3~0.5초**
- 11,821 SKU → 100,000건까지 확장 여력

### 납품물

- PDA 앱 APK (설치 완료 상태)
- API 서버 (Mini PC 설치, 자동 시작)
- **전체 소스코드** (Android + Server)
- 운영 가이드 문서

---

## 9. 기기 없이 지금 착수 가능한 작업

### 서버 (FastAPI)

1. DB 스키마 설계 (PRODUCT, BARCODE, IMAGE 테이블)
2. xlsx 파서 (codepath.xlsx, sku_download.xlsx)
   - codepath: 헤더 없음, 2컬럼 고정
   - sku_download: 헤더 유동적 → 매칭 로직
3. REST API 엔드포인트
   - `GET /api/scan/{barcode}` → 상품 정보 + 이미지 경로
   - `GET /api/search?q=검색어` → 상품 검색
   - `GET /api/image/{path}` → NAS 이미지 프록시
4. NAS WebDAV 연동 (이미지 프록시, 캐싱)
5. xlsx 파일 감시 (watchdog or polling)

### Android (Kotlin)

1. 프로젝트 셋업 (Gradle, 의존성)
2. UI 레이아웃 (메인 화면, 상세 화면)
3. Retrofit API 클라이언트
4. DataWedge 자동 프로파일 설정 (Intent API)
5. BroadcastReceiver → SharedFlow 스캔 매니저
6. **adb broadcast로 스캐너 시뮬레이션 테스트**

### 우선순위

**서버 먼저** → API 완성 후 Android UI 연동
(PDA 실기기 없어도 서버 API는 완전히 개발/테스트 가능)

---

## 10. 미확인/확인 필요 사항 (기존)

| 항목                                              | 상태                                 | 필요 시점       |
| ------------------------------------------------- | ------------------------------------ | --------------- |
| PDA Android 정확한 버전                           | 스크린샷상 9+ 추정                   | 실기기 도착 후  |
| DataWedge 버전                                    | 미확인                               | 실기기 도착 후  |
| NAS WebDAV 접속 주소/계정                         | AnyDesk로 공유 예정                  | 서버 개발 시    |
| sku_download xlsx 파싱                            | inlineStr 타입 → lxml 직접 파싱 필요 | 파서 개발 시    |
| Mini PC OS/사양                                   | 미확인                               | 서버 배포 시    |
| WiFi AP 환경 (2.4/5GHz)                           | 2.4GHz 권장 (커버리지)               | 현장 설치 시    |
| "발주 담당자" 대신 넣을 "도매처 연락처, URL" 컬럼 | 데이터 소스 미확인                   | 파서 개발 시    |
| 바코드 미부착 상품 대응 방안                      | QR 부착 가능성 언급됨                | Phase 1 범위 밖 |

---

## 11. 클라이언트 UI 피드백 (카톡 3/20, 오후 1:17~1:28)

### 확정 요구사항 (Phase 1 반영)

1. **화면 시선 순서**: 이미지(최대) → 바코드/SKU ID → 상품명. "반드시 이미지가 제일 눈에 띄어야 한다"
2. **바코드 표시**: 끝 5자리를 더 두껍게(볼드) 표시. 예: 88094611**70015**
3. **브랜드명 자동 삭제**: 모든 상품명에 "스페이스쉴드"가 포함되어 있음. 식별 시 불필요하므로 자동 제거
4. **이미지 토글**: 쿠팡 썸네일 터치 → 실사 표시, 다시 터치 → 썸네일. "와리가리 타게"
5. **한 화면**: 스크롤 없이 한 화면에 모든 정보 표시

### 데이터 대기 (URL 공유 후 반영)

6. **"발주하기" 버튼**: 1688 링크 또는 기존 매칭된 URL로 이동. 데이터 공유 대기 중
   - 위챗 메시지 연동은 불가능할 수 있음 (클라이언트도 인지)

### Phase 2 논의 사항

7. **재고 수량 입력**: "수량은 러프하게 입력하고 싶습니다"
   - scan13.exe 공존 시 동시 수정 충돌 문제 설명함
   - 두 가지 방안 제시: (1) PDA만 수정 가능 (충돌 가능성), (2) 미니PC 웹 UI 새로 제작
   - 클라이언트 "반틈 이해한것 같네요" → 추후 재논의
8. **SKU 추가 시 업데이트 방법**: 클라이언트 질문 받음, 현재 NAS xlsx 갱신 → 자동 파싱으로 대응
9. **쿠팡 서플라이허브 접근**: 클라이언트가 추가 설명 예정 (비행기 이륙으로 중단)

### 브랜드 정보 확인

- 취급 브랜드: **스페이스쉴드** (모든 상품명에 포함)
- 소품류: 폰케이스, 게임 컨트롤러 등
- 도매: 1688 (중국) 경유

---

## 12. D+2 전체 코드 검수 결과 (2026-03-20)

5인 전문가 관점(물류 도메인, 백엔드, Android, 기획, 보안/디자인)으로 전체 코드 검수 후 수정 적용.

### 수정된 CRITICAL 이슈

1. **Path Traversal** — 이미지 프록시에서 `../../` 경로로 서버 파일 접근 가능했던 취약점 수정
2. **scanFlow collect 중복** — onResume마다 collector 누적되어 바코드 N번 처리되던 버그 수정 (repeatOnLifecycle)
3. **이미지-바코드 연결** — 동일 SKU의 다른 바코드로 스캔 시 이미지 안 나오던 문제, sku_id 기준 조회로 전환
4. **codepath 적재 순서** — sku_download 먼저 적재 후 codepath 적재 시 sku_id가 NULL로 덮어쓰이던 문제 방어

### 수정된 HIGH 이슈

- DataWedge EAN-8/13 형식 검증 + 300ms debounce
- httpx 클라이언트 싱글턴 (매 요청마다 TCP 재연결 제거)
- N+1 SELECT 제거 (배치 카운트 최적화)
- RetrofitClient thread-safety (@Volatile + synchronized)
- Android cleartext HTTP + network_security_config.xml 추가
- FTS5 폴백 에러 로깅

### UI/UX 개선

- 터치 타겟 48dp → 56dp (장갑 착용 환경)
- 폰트 크기 확대 (본문 16sp, 상품명 22sp)
- 스캔 대기 화면 추가 ("PDA 버튼을 눌러 스캔하세요")
- ViewPager2 이미지 슬라이드 (1장만 → 전체 탐색)
- Toast → Snackbar + 재시도 액션
- ScanResponse Parcelable 적용
- 서버 URL 형식 검증

### 미해결 (PDA/현장 의존)

- 실기기 DataWedge 테스트 (PDA 도착 후)
- assembleDebug 빌드 검증 (Android SDK 환경)
- NAS WebDAV 실연결 (접속 정보 대기)
- 오프라인 Room 캐시 (Phase 2 검토)

---

## 13. 경로 변환 로직 (중요)

기존 프로그램은 Windows 경로로 이미지를 참조:

```
Z:\물류부\scan\img\000000_07da1a9dfd.jpg
```

우리 서버는 WebDAV로 NAS에 접근하므로 변환 필요:

```
Z:\물류부\scan\img\xxx.jpg
→ {WEBDAV_BASE_URL}/물류부/scan/img/xxx.jpg
```

codepath.xlsx 파싱 시 `Z:\물류부\scan\` prefix를 제거하고 상대 경로만 저장.

---

## 14. D+2 최종 상태 요약 (2026-03-20)

### Mini PC 배포

- OS: Windows 11
- 서비스: NSSM으로 Windows 서비스 등록, PC 시작 시 자동 실행
- 모니터링 API 추가

### NAS WebDAV 실연결

- 주소: chominseven.synology.me:58890
- 이미지: 썸네일(img/, 108KB) + 실사(real_image/, 2.3MB) 정상 표시
- xlsx 자동 동기화: 5분 주기로 NAS 파일 변경 감지, 자동 파싱 적재
- WebDAV 경로 prefix 분리 적용

### 테스트 결과

- 서버 API 테스트: 53개 전부 통과
- 핸드폰 테스트: 16케이스 전부 통과
- 전수검사: Critical 8건 + Warning 30건 수정 완료

### 디자인

- Stitch "Tactical Command" UI 적용
- 클라이언트 피드백 반영: 이미지 최대, 바코드 끝5자리 볼드, 스페이스쉴드 자동 삭제, 이미지 토글, 한 화면
- 하단 네비 제거, FAB 제거, 검색창 통합

### D+2 커밋 이력 (약 20개)

- 전체 코드 검수 + 보안/안정성/UI 개선
- 전수검사 결과 반영 (Critical 8건 + Warning 30건)
- 클라이언트 UI 피드백 반영
- 서버 모듈화 + Mini PC 배포
- NAS WebDAV 실연결 + 경로 prefix 분리
- 재고 수정 API + NAS 자동 동기화 + demo.html 실데이터 연동
- 모니터링 API + 브랜드 설정화
- APK 빌드 파일 추가
- 앱 버그 수정 + 수동 스캔 버튼
- Material Design 적용 -> Stitch 디자인으로 교체
- 하단 네비 제거 + 에러 메시지 개선
- FAB 제거, 검색창 통합, 뒤로가기 처리

---

## 서플라이어 허브 API 수집

### 개요

- 쿠팡 서플라이어 허브(supplier.coupang.com) 내부 API를 활용한 SKU + 이미지 URL 수집
- 브라우저 콘솔 스크립트로 로그인 쿠키 재사용 (크롤링 아님)

### API 엔드포인트

- POST /plan/v1/ticket/sku/listTicketSku
- Request body: { skuId, skuName, barcode, orderingStatus, unit1, unit2, issueStatus, issueType, size, page }
- Response: { body: { content: [...], total: number } }
- 응답 필드: skuId, skuName, barCode, imagePath, orderStatus, issueCount, mdId, mdName, scmId, scmName

### 이미지 URL 포맷

- http://img{N}.coupangcdn.com/image/product/image/vendoritem/{date}/{id}/{uuid}.jpg
- 인증 없이 접근 가능 (공개 CDN)

### 수집 결과 (2026-03-21 기준)

- 총 12,048건 (ACTIVE 10,340 / OBSOLETE 1,059 / INACTIVE 625)
- 바코드 100%, 이미지 URL 100%

### 관련 스크립트

- scripts/fetch_supplier_hub.js — 브라우저 콘솔용 SKU 수집
- scripts/download_images.py — CDN 이미지 일괄 다운로드

### 갱신 방법

1. 서플라이어 허브 로그인 (클라이언트 2차 인증 필요)
2. 브라우저 콘솔에서 fetch_supplier_hub.js 실행
3. 다운로드된 JSON을 프로젝트 루트에 배치
4. download_images.py 실행하여 이미지 다운로드
