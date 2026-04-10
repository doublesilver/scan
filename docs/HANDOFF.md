# 물류창고 PDA 스캐너 시스템 — 납품 인수인계 문서

최종 수정: 2026년 4월 10일

---

## 1. 납품 범위

### 1.1 소프트웨어

#### Android PDA 앱

- **Live 빌드**: 실제 운영용 앱 (`warehouse-scanner-live-release.apk`)
  - 실제 서버에 연결하여 운영
  - 전체 기능 활성화

- **Demo 빌드**: 테스트/데모용 앱 (`warehouse-scanner-demo-debug.apk`)
  - 서버 연결 불필요
  - 캐시된 샘플 데이터로 테스트 가능

#### FastAPI 서버

- REST API 서버 (Python 3.11 + FastAPI)
- SQLite 데이터베이스 (자동 백업)
- 이미지 프록시 및 캐싱
- 창고 도면 에디터 웹 인터페이스
- 서버 테스트 64개 통과 (v5.3.12)

#### 웹 도면 에디터

- 창고 평면도 관리 UI
- 선반(셀) 정보 수정/편집
- 층별 사진 업로드
- 위치 정보 관리
- 다크모드, 페인트, 다중선택, 접기, 좌표, 툴팁, 자동저장, 통계 (v5.3.12 추가)
- 셀 크기 조절, 텍스트 삽입, 테두리 스타일
- editor-meta 서버 영속 API
- 도면 데이터 보호 (상품 있는 셀 삭제 차단)

#### 문서

- `USER_GUIDE.md`: 창고 직원용 사용 가이드
- `HANDOFF.md`: 이 문서
- `API_SPEC.md`: 개발자용 API 명세
- `DATABASE.md`: 데이터베이스 스키마
- `ARCHITECTURE.md`: 시스템 아키텍처

### 1.2 하드웨어 및 인프라 (클라이언트 소유)

- **PDA**: Samsung Galaxy S20+ 또는 Zebra TC60
  - 내장 바코드 스캔 하드웨어
  - Android 10 이상

- **Mini PC (서버 호스트)**
  - Windows 11 또는 Linux (Ubuntu 22.04+)
  - 최소 사양: CPU 2코어, RAM 4GB, 디스크 100GB 이상
  - 네트워크: 내부 WiFi + Tailscale VPN 지원

- **NAS (Synology)**
  - 이미지 저장소
  - WebDAV 접근
  - 자동 백업 대상

- **프린터**
  - 바코드 라벨 인쇄
  - 네트워크 프린터 권장 (TSC, Zebra 등)

---

## 2. 시스템 구성 및 네트워크 아키텍처

### 2.1 시스템 다이어그램

```
┌──────────────────┐           WiFi           ┌──────────────────────┐
│   PDA (Android)  │ ←────────────────────→   │   Mini PC            │
│ TC60 / S20+      │      REST API            │ (Windows 11)         │
│                  │   http://100.125.17.60   │ FastAPI Server       │
│ Barcode Scanner  │         :8000            │ Port 8000            │
└──────────────────┘                          └──────────────────────┘
                                                        ↓
                                              WebDAV + PROPFIND
                                                        ↓
                                              ┌──────────────────────┐
                                              │   Synology NAS       │
                                              │ chominseven.me       │
                                              │ WebDAV :58890        │
                                              │                      │
                                              │ /물류부/scan/        │
                                              │  - codepath.xlsx     │
                                              │  - sku_download_*.xls│
                                              │  - img/              │
                                              │  - real_image/       │
                                              └──────────────────────┘

기존 시스템 (독립 운영):
┌──────────────────┐                          ┌──────────────────────┐
│  USB 바코드      │ ─────────────────────→   │   scan13.exe         │
│  스캐너          │   local input            │ (PySide6 데스크톱)   │
└──────────────────┘                          └──────────────────────┘
                                                        ↓
                                              RaiDrive (Z: 드라이브)
                                                        ↓
                                              ┌──────────────────────┐
                                              │   Synology NAS       │
                                              │ (별도 마운트)        │
                                              └──────────────────────┘
```

### 2.2 네트워크 정보

#### WiFi 네트워크 (창고 내부)

- **접근점 (AP)**: 창고 내부 무선랜
- **주파수**: 2.4GHz 권장 (5GHz도 가능)
- **보안**: WPA2/WPA3
- **IP 할당**: DHCP 또는 고정 IP

#### 외부 VPN (원격 지원 시)

- **VPN 서비스**: Tailscale
  - Mini PC 자동 연결
  - 개발자 원격 접근용
  - 보안 터널 제공

#### NAS 접근

- **WebDAV URL**: `https://chominseven.synology.me:58890`
- **인증**: 계정명/비밀번호
- **포트**: 58890 (HTTPS)
- **목적**: xlsx, 이미지 파일 동기화

---

## 3. 계정 및 접속 정보

### 3.1 서버 접근

#### Mini PC 원격 데스크톱 (Windows)

```
주소: 100.125.17.60
사용자명: lenovo
암호: [클라이언트 보유]
포트: 3389 (기본)
```

#### Mini PC SSH (Linux / WSL)

```
주소: 100.125.17.60
사용자명: lenovo
포트: 22
인증: SSH 키 또는 비밀번호
```

### 3.2 서비스 접근

#### PDA 앱에서 서버 URL

```
http://100.125.17.60:8000
```

#### 웹 도면 에디터 (관리자용)

```
http://100.125.17.60:8000/admin/map-editor
```

#### Swagger API 문서 (개발자용)

```
http://100.125.17.60:8000/docs
```

### 3.3 NAS 접근

#### WebDAV 클라이언트

```
호스트: chominseven.synology.me:58890
프로토콜: WebDAV (HTTPS)
계정: [클라이언트 보유]
```

#### 경로

```
/물류부/scan/
```

### 3.4 Google Service Account (데이터 동기화용)

```
이메일: scan-sheets@scan-sheets-api.iam.gserviceaccount.com
프로젝트: scan-sheets-api
용도: Google Sheets에서 SKU 데이터 다운로드
인증: JSON 키 파일 (서버에 저장)
```

---

## 4. APK 배포 및 버전 관리

### 4.1 APK 파일 위치

Mini PC의 서버에서 제공:

```
http://100.125.17.60:8000/apk/

warehouse-scanner-live-release.apk    실제 운영용 최신 버전
warehouse-scanner-demo-debug.apk      테스트용 샘플 버전
```

### 4.2 앱 업데이트 프로세스

#### 자동 업데이트 (앱 실행 시)

1. PDA 앱 시작 시 자동으로 서버에서 최신 버전 확인
2. 새 버전이 있으면 팝업 표시
3. "업데이트" 버튼으로 APK 다운로드 및 설치
4. 앱 자동 재시작

#### 수동 설치

1. Mini PC 또는 웹 브라우저에서 APK 파일 다운로드
2. PDA의 파일 관리자로 APK 실행
3. 설치 확인 및 권한 승인

### 4.3 빌드 및 배포 (IT 담당자/개발자용)

APK 새 버전 생성 시:

```bash
# 프로젝트 디렉토리
cd <scan-저장소-경로>/android

# Live 빌드
./gradlew assembleLiveRelease

# Demo 빌드
./gradlew assembleDemoDebug

# 생성 파일 위치
# app/build/outputs/apk/live/release/
# app/build/outputs/apk/demo/debug/
```

생성된 APK를 서버의 `/apk/` 디렉토리로 복사하면 자동으로 배포된다.

---

## 5. 운영 매뉴얼

### 5.1 서버 시작/중지

#### Windows (NSSM 서비스)

```powershell
# 서비스 시작
nssm start ScannerAPI

# 서비스 중지
nssm stop ScannerAPI

# 서비스 재시작
nssm restart ScannerAPI

# 서비스 상태 확인
nssm status ScannerAPI

# 서비스 로그 확인
Get-Content "C:\Program Files\nssm\ScannerAPI\service.log"
```

#### Linux (systemd)

```bash
# 서비스 시작
sudo systemctl start scanner-api

# 서비스 중지
sudo systemctl stop scanner-api

# 서비스 재시작
sudo systemctl restart scanner-api

# 서비스 상태 확인
sudo systemctl status scanner-api

# 로그 확인
sudo journalctl -u scanner-api -f
```

### 5.2 데이터베이스 관리

#### SQLite 데이터베이스 위치

```
Windows: C:\ScannerAPI\data\scanner.db
Linux:   /opt/scanner-api/data/scanner.db
```

#### 데이터베이스 백업

```bash
# 수동 백업
cp scanner.db scanner.db.$(date +%Y%m%d_%H%M%S)

# 자동 백업 (일일 1회, 자정)
# cron/Task Scheduler에서 자동 설정됨
```

#### 데이터베이스 조회 (SQLite CLI)

```bash
# 접속
sqlite3 scanner.db

# 테이블 목록
.tables

# 상품 개수 확인
SELECT COUNT(*) FROM product;

# 바코드 개수 확인
SELECT COUNT(*) FROM barcode;

# 최근 스캔 기록 확인
SELECT * FROM scan_log ORDER BY created_at DESC LIMIT 10;

# 종료
.quit
```

### 5.3 서버 헬스 체크

#### API 상태 확인

```bash
curl http://100.125.17.60:8000/health
# 응답: {"status":"ok"}
```

#### 데이터베이스 동기화 확인

```bash
curl http://100.125.17.60:8000/status
# 응답: {
#   "db_products": 11821,
#   "db_barcodes": 35463,
#   "last_sync": "2026-04-01T15:30:00Z",
#   "cache_images": 2048
# }
```

### 5.4 NAS 마운트 및 데이터 동기화

#### WebDAV 마운트 확인 (Windows)

```powershell
# 마운트 확인
net use

# RaiDrive로 자동 마운트 (기본 설정)
# Z: 드라이브로 NAS 접근 가능
```

#### WebDAV 마운트 (Linux)

```bash
# 마운트 상태 확인
mount | grep webdav

# 수동 마운트 필요시
sudo mount -t davfs https://chominseven.synology.me:58890 /mnt/nas
```

#### 파일 동기화 상태 확인

```bash
# 마지막 동기화 시간 확인
ls -la /opt/scanner-api/data/

# NAS의 codepath.xlsx 확인
curl -u [계정]:[비밀번호] \
  https://chominseven.synology.me:58890/물류부/scan/codepath.xlsx
```

### 5.5 프린터 설정

#### TSC/Zebra 프린터 연결 확인

```bash
# 네트워크 프린터 포트 확인 (일반적으로 9100)
nc -zv printer_ip 9100

# 테스트 인쇄 (curl 사용)
curl -X POST http://100.125.17.60:8000/print \
  -H "Content-Type: application/json" \
  -d '{"barcode":"1234567890123", "quantity":1}'
```

#### 프린터 드라이버 설치

- TSC TM-M50 또는 Zebra ZP-505 권장
- Windows: 제조사 드라이버 설치
- Linux: CUPS 설정

---

## 6. 미완료 항목 및 향후 과제

### 6.1 1688 구매 링크 연동

**현황**: 미완료

**필요 작업**:

1. 클라이언트로부터 1688 구매처 데이터 수집 (Excel 또는 Google Sheets)
2. 데이터 형식 정의 및 데이터베이스 스키마 수정
3. 서버 API 업데이트 (SKU → 1688 URL 매핑)
4. PDA 앱 UI 업데이트 (발주 버튼 활성화)

**예상 일정**: 데이터 확보 후 3~5일

**담당자**: 개발자

### 6.2 네이버/FLOW 외부 링크 연동

**현황**: 미완료

**필요 작업**:

1. 네이버 쇼핑 / FLOW URL 데이터 수집
2. 데이터베이스에 url_naver, url_flow 필드 추가
3. 박스 상세 페이지에 버튼 활성화
4. 링크 테스트

**예상 일정**: 데이터 확보 후 2~3일

**담당자**: 개발자

### 6.3 중국 발주 이미지 (실사 이미지 추가)

**현황**: 미완료

**현황 설명**:

- 현재 NAS에 쿠팡 썸네일(img/) + 한국 실사(real_image/) 이미지만 존재
- 중국 공장 발주용 상품 이미지(중국 옵션) 미수집

**필요 작업**:

1. 클라이언트로부터 중국 발주 상품 이미지 수집
2. NAS의 별도 디렉토리(china_image/ 등)에 저장
3. 데이터베이스 image 테이블에 image_type 필드 추가 (china 타입 추가)
4. PDA 앱에서 이미지 토글 확대 (썸네일 ↔ 실사 ↔ 중국)

**예상 일정**: 이미지 수집 후 3~5일

**담당자**: 개발자

### 6.4 TSC 프린터 드라이버 설치

**현황**: 미완료

**필요 작업**:

1. Mini PC에 TSC TM-M50 (또는 Zebra ZP-505) 드라이버 설치
2. 프린터와 Mini PC 네트워크 연결
3. 프린터 IP 주소 설정 및 포트 확인 (일반적으로 9100)
4. 라벨 인쇄 테스트

**예상 일정**: 현장 방문 시 1~2시간

**담당자**: IT 담당자 또는 개발자 (현장 출장)

### 6.5 전체 상품 위치(location) 초기 구축

**현황**: 미완료 (향후 운영 중 점진적 수집)

**현황 설명**:

- 현재 데이터베이스에 location 필드는 존재하지만 대부분 NULL
- 창고의 11,821개 상품 각각에 위치 정보 입력 필요

**필요 작업**:

1. 현장 작업자가 PDA로 상품 스캔 → 위치 입력 반복
2. 또는 기존 창고 시스템(scan13.exe)에서 위치 데이터 추출
3. CSV로 일괄 import

**예상 일정**: 11,821건 × 30초 ≈ 100시간 (병렬 작업으로 1~2주)

**담당자**: 창고 현장 작업자 (개발자 지원)

---

## 7. 트러블슈팅

### 7.1 PDA가 서버에 연결되지 않음

**증상**: 스플래시 화면에서 "연결 실패" 메시지

**원인 및 해결책**:

1. **WiFi 미연결**
   - 설정 > WiFi에서 창고 네트워크 확인
   - WiFi 신호 강도 확인
   - WiFi 비밀번호 재입력

2. **서버 다운**

   ```bash
   # Mini PC에서 확인
   curl http://localhost:8000/health
   # 응답이 없으면 서버 재시작
   nssm restart ScannerAPI
   ```

3. **방화벽 차단**
   - Windows 방화벽: 8000번 포트 허용 규칙 추가
   - 라우터 포트포워딩 설정 확인

4. **서버 URL 오류**
   - PDA 설정에서 URL 다시 확인
   - 예: http://100.125.17.60:8000 (http:// 포함)

### 7.2 데이터베이스 동기화 오류

**증상**: 새 상품이 앱에 표시되지 않음

**원인 및 해결책**:

1. **NAS 연결 끊김**

   ```bash
   # 마운트 상태 확인
   mount | grep webdav
   # 없으면 수동 마운트
   sudo mount -t davfs https://chominseven.synology.me:58890 /mnt/nas
   ```

2. **파일 동기화 중단**

   ```bash
   # 서버 로그 확인
   tail -100 /opt/scanner-api/logs/sync.log
   # 오류 있으면 개발자에게 보고
   ```

3. **xlsx 파일 형식 오류**
   - NAS의 coupangmd00*sku_download*\*.xlsx 파일 확인
   - 파일이 손상되었으면 다시 업로드

### 7.3 바코드 스캔 오류

**증상**: 스캔 후 "상품을 찾을 수 없습니다" 메시지

**원인 및 해결책**:

1. **바코드 데이터베이스에 없음**
   - codepath.xlsx에 바코드가 등록되어있는지 확인
   - 없으면 클라이언트에게 등록 요청

2. **바코드 형식 오류**
   - 바코드가 손상되었는지 확인 (스크래치, 구겨짐)
   - 다른 바코드로 재시도

3. **앱 캐시 오래됨**
   - PDA 설정 > 앱 > 스캐너 > 저장소 > 캐시 삭제
   - 앱 재시작

### 7.4 라벨 인쇄 실패

**증상**: "인쇄 요청 실패" 메시지

**원인 및 해결책**:

1. **프린터 오프라인**

   ```bash
   # 프린터 연결 확인
   nc -zv printer_ip 9100
   ```

2. **프린터 자동화 스크립트 오류**

   ```bash
   # 서버 인쇄 로그 확인
   tail -50 /opt/scanner-api/logs/print.log
   ```

3. **라벨지 부족**
   - 프린터의 라벨지 재고 확인
   - 라벨지 교체

### 7.5 이미지 로딩 느림

**증상**: 상품 이미지가 오래 걸림

**원인 및 해결책**:

1. **NAS 과부하**
   - NAS 상태 확인
   - 다른 작업 일시 중지

2. **네트워크 대역폭 부족**
   - WiFi 신호 강도 확인
   - 다른 디바이스 WiFi 사용 정지

3. **캐시 가득 참**
   ```bash
   # 서버 이미지 캐시 정리
   rm -rf /opt/scanner-api/cache/images/*
   # 서버 재시작
   nssm restart ScannerAPI
   ```

### 7.6 앱이 계속 충돌 (크래시)

**증상**: 앱을 실행하면 바로 종료됨

**원인 및 해결책**:

1. **앱 데이터 손상**
   - PDA 설정 > 앱 > 스캐너 > 저장소 > 데이터 삭제
   - 앱 재설치

2. **메모리 부족**
   - PDA 재부팅 (전원 끄고 켜기)
   - 불필요한 앱 삭제

3. **Android 버전 호환성**
   - PDA의 Android 버전이 10 이상인지 확인
   - 필요하면 Android 업데이트

4. **버그**
   - 개발자에게 현상 보고 및 APK 업데이트

---

## 8. 유지보수 일정

### 월별 점검 항목

**매월 1일**:

- 서버 헬스 체크 (database 레코드 수, 응답 시간 등)
- 백업 파일 존재 여부 확인

**매월 15일**:

- NAS 디스크 용량 확인 (여유 20% 이상 유지)
- 이미지 캐시 정리 (30일 이상 미사용 파일 삭제)

**분기별 (3개월)**:

- 데이터베이스 defrag (SQLite VACUUM)
- 서버 보안 업데이트 확인

**반기별 (6개월)**:

- 전체 시스템 성능 리포트 작성
- 향후 확장 계획 검토

---

## 9. 연락처 및 지원

### 개발자 연락처

**이름**: 이은석

**방식**:

- 카톡: [클라이언트 보유]
- 이메일: [클라이언트 보유]
- 긴급: 전화

**지원 범위**:

- 앱 기능 관련 문제
- 서버 오류 및 복구
- 데이터 동기화 문제
- 새로운 기능 요청

**응답 시간**:

- 긴급 (서버 다운): 1시간 이내
- 높음 (데이터 손상): 4시간 이내
- 일반 (기능 문제): 업무일 기준 1일 이내

---

## 10. 라이센스 및 기술 스택 요약

### 오픈소스 라이센스

- **FastAPI**: MIT 라이센스
- **SQLite**: 퍼블릭 도메인
- **Coil**: Apache 2.0 라이센스
- **Retrofit2**: Apache 2.0 라이센스
- **Kotlin Coroutines**: Apache 2.0 라이센스

모든 라이센스는 상업용 사용을 허용합니다.

### 기술 스택 요약

**백엔드**:

- Python 3.11, FastAPI, SQLite, Pillow, lxml

**프론트엔드 (PDA)**:

- Kotlin, Android 10+, MVVM 아키텍처

**웹 (도면 에디터)**:

- HTML5, Vanilla JS, Canvas API

**인프라**:

- Windows 11 / Linux (Ubuntu 22.04+)
- Tailscale VPN, WebDAV, NSSM

---

## 부록: 초기 설정 체크리스트

### 최종 납품 전 확인 사항

- [ ] PDA 앱 Live 빌드 설치 및 테스트
- [ ] 서버 URL 설정 및 연결 확인
- [ ] 바코드 스캔 3개 이상 테스트
- [ ] 텍스트 검색 기능 테스트
- [ ] 라벨 인쇄 테스트
- [ ] 도면 보기 및 셀 편집 테스트
- [ ] 사진 업로드 테스트
- [ ] 오프라인 모드 (서버 미연결 시) 동작 확인
- [ ] 박스 QR 스캔 테스트
- [ ] 앱 업데이트 기능 테스트
- [ ] 데이터베이스 백업 자동화 확인
- [ ] 현장 WiFi 신호 강도 측정 (2.4GHz 기준 -60dBm 이상)
- [ ] 프린터 연결 및 인쇄 테스트
- [ ] 사용자 가이드 배포
- [ ] 운영진 교육 (1회 이상)

### 운영 개시 후 확인 사항

- [ ] 주 1회 서버 상태 모니터링
- [ ] 월 1회 백업 파일 복구 테스트
- [ ] 월 1회 사용자 피드백 수집
- [ ] 분기별 성능 리포트 작성

---

**문서 버전**: 1.1
**앱 버전**: v5.3.12
**마지막 수정**: 2026년 4월 10일
**개발자**: 이은석
