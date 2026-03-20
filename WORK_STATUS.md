# 작업 현황 (D+2 마감)

## 완료된 작업

### D+1 (2026-03-19)

- 프로젝트 착수, 기존 scan13.exe 분석 (PySide6 데스크톱 앱, NAS RaiDrive 연동 구조 파악)
- DB 스키마 설계 (PRODUCT, BARCODE, IMAGE 테이블) + FastAPI 서버 셋업
- xlsx 파서 개발 — codepath.xlsx(바코드-이미지 매핑) + sku_download.xlsx(SKU 데이터), 11,821건 적재
- REST API 개발 — /api/scan/{barcode}, /api/search, /api/image 3개 엔드포인트
- NAS 이미지 프록시 + 디스크 캐싱 (WebDAV 경유, mock 모드 지원)
- xlsx 자동 갱신 — watchdog 파일 감시, parse_log 기록
- Android 앱 전체 개발 — UI 레이아웃, Retrofit API 연동, DataWedge 스캐너 연동
- 통합 테스트 스크립트 + systemd 서버 자동 시작 설정

### D+2 (2026-03-20)

- 5인 관점 코드 검수 — Path Traversal 차단, scanFlow 중복 수집 수정, 이미지-바코드 연결 수정, codepath 적재 순서 방어
- 클라이언트 UI 피드백 반영 — 이미지 최대 표시, 바코드 끝5자리 볼드, 스페이스쉴드 자동 삭제, 이미지 토글(썸네일/실사), 한 화면 구성
- Mini PC 서버 배포 — Windows 11, NSSM 서비스 등록, PC 시작 시 자동 실행
- NAS WebDAV 실연결 — chominseven.synology.me:58890, 이미지 11,689장 정상 확인
- 오프라인 대응 — Room DB 캐시, RetryInterceptor 자동 재시도
- 백업/복구 자동화 — 매일 03:00 실행, 7일 보관, 명령어 한 줄 복구
- 재고 수정 API 준비 — stock/stock_log 테이블 추가
- NAS xlsx 자동 동기화 — 1분 주기 변경 감지, 자동 파싱 적재
- 서버 모듈화 — product_service, image_service, stock_service, nas_sync, status_service 분리
- Stitch "Tactical Command" 디자인 적용 (Material Design 적용 후 교체)
- 하단 네비 제거, FAB 제거, 검색창 통합, 뒤로가기 처리
- 에러 메시지 비개발자 친화적 개선 (Toast -> Snackbar + 재시도 액션)
- 서버 모니터링 API (/api/status) 추가
- 브랜드 필터 config화 (스페이스쉴드 등 설정 파일에서 관리)
- demo.html 실데이터 연동
- 기술명세서 작성 (docs/TECHNICAL_SPEC.md)
- progress.html 비개발자용 전면 개편 (mermaid -> HTML/CSS)
- 핸드폰 테스트 16케이스 전부 통과

---

## 현재 프로젝트 상태

| 항목         | 값                                                     |
| ------------ | ------------------------------------------------------ |
| 진행률       | 85% (코드 개발 완료, 확인 대기 5건)                    |
| 마일스톤     | 8 / 8 완료                                             |
| 서버 테스트  | 53개 통과                                              |
| Android 빌드 | 성공 (assembleDebug)                                   |
| Mini PC 서버 | 가동 중 (11,821건 적재, NAS 연결)                      |
| APK          | Mini PC(C:\scanner\app-debug.apk) + GitHub 업로드 완료 |
| 납품 마감    | D+10 (8일 남음)                                        |

---

## 클라이언트 확인 대기 (4건)

1. **실사 이미지 관리 방식** — img/ 폴더에 \_real 접미사 파일(10장)과 real_image/ 폴더(2장)가 혼재. 어느 폴더로 통일할지 확인 필요.
2. **발주하기 URL 데이터** — 상품별 구매 URL이 매칭된 엑셀 데이터 공유 요청함. 수신 후 발주 버튼 연동.
3. **신규 상품 이미지 확보 방법** — 쿠팡 서플라이허브 접근 방법 공유 후 이미지 자동 수집 방법 확인.
4. **재고 수량 입력 상황** — 입고/실사/출고 중 어떤 경우에 수량 입력하는지 확인 후 화면 구성 확정.

---

## 앞으로 할 작업

### 클라이언트 응답 후

- [ ] 실사 이미지 폴더 규칙 확정 -> 이미지 프록시 로직 수정
- [ ] 발주 URL 데이터 수신 -> 파서 추가 + 발주 버튼 활성화
- [ ] 서플라이허브 접근 -> 이미지 자동 수집 방법 확인
- [ ] 재고 수정 화면 구성 확정 -> Android UI 추가

### PDA 도착 후

- [ ] APK 설치 (C:\scanner\app-debug.apk)
- [ ] 서버 URL 설정 (내부 WiFi IP)
- [ ] DataWedge 프로파일 자동 생성 확인
- [ ] 실제 바코드 스캔 테스트
- [ ] 현장 WiFi 환경 테스트
- [ ] 오프라인 모드 동작 확인
- [ ] 스캔 속도 측정 (0.3~0.5초 목표)

### 납품 전

- [ ] Mini PC 서버 재부팅 자동 시작 확인
- [ ] 운영 가이드 최종 검토
- [ ] 클라이언트 최종 확인 -> 잔금 수령

---

## 주요 파일 위치

| 파일                           | 용도                                   |
| ------------------------------ | -------------------------------------- |
| REPORT_D2.md                   | 클라이언트 보고서 (카톡/메일용)        |
| BRIEFING_D2.md                 | 미팅 준비 브리핑                       |
| DEV_NOTES.md                   | 개발 노트 (클라이언트 소통, 기술 분석) |
| ROADMAP.md                     | 마일스톤 체크리스트                    |
| docs/progress.html             | 클라이언트 공유 진행현황 (Vercel)      |
| docs/TECHNICAL_SPEC.md         | 기술명세서                             |
| docs/MINIPC_SETUP_CHECKLIST.md | Mini PC 설치 체크리스트                |
| server/docs/OPERATION_GUIDE.md | 운영 가이드                            |
| server/docs/API_SPEC.md        | API 스펙                               |
| server/docs/DATABASE.md        | DB 스키마 문서                         |

---

## Mini PC 접속 정보

- Tailscale IP: 100.125.17.60
- SSH: `ssh lenovo@100.125.17.60`
- 서버: http://100.125.17.60:8000
- 서비스: ScannerAPI (NSSM)
- 프로젝트 경로: C:\scanner\

---

## NAS 접속

- WebDAV: https://chominseven.synology.me:58890
- 경로: /물류부/scan/
- 계정 정보: .env 파일 참조

---

## 주요 링크

- 진행현황: https://docs-six-woad-11.vercel.app/progress.html
- GitHub: https://github.com/doublesilver/scan
