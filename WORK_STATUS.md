# 작업 현황 (v5.1.0 기준, 2026-04-03)

## 현재 프로젝트 상태

| 항목         | 값                                               |
| ------------ | ------------------------------------------------ |
| 버전         | v5.1.0 (versionCode 71)                          |
| 진행률       | 95% (코드 완성, PDA 실기기 테스트 + 재배포 대기) |
| Android 빌드 | v5.0.0 APK 배포 완료 (v5.1.0 빌드 필요)          |
| Mini PC 서버 | v5.0.0 가동 중 (v5.1.0 재배포 필요)              |
| 서버 테스트  | 53개 통과                                        |

---

## 구현 완료 기능 (전체)

### 바코드 스캔 + 상품 조회

- EAN-13 DataWedge Intent 스캔 → 상품 정보 + 이미지 표시
- 300ms debounce, EAN-8/13 형식 검증
- 스캔 대기 화면 ("PDA 버튼을 눌러 스캔하세요")

### 상품 검색

- 상품명 / SKU ID 텍스트 검색 (FTS5 최적화)
- 검색 결과 20건 제한

### 이미지 표시

- NAS WebDAV 프록시 (썸네일 img/ + 실사 real_image/)
- ViewPager2 이미지 슬라이드, 이미지 토글 (썸네일 ↔ 실사)
- 디스크 캐싱

### 창고 도면 (v5.0 DB 정규화)

- warehouse_zone / cell / cell_level / cell_level_product 4개 테이블
- 14개 API 엔드포인트 (zone/cell/level/product CRUD)
- 웹 에디터 (map-editor.html) — 드래그 앤 드롭 셀 편집
- PDA에서 구역 편집 (추가/수정/삭제)
- 이전/다음 셀 네비게이션, 빈 셀 자동 편집모드
- 위치 동기화: 도면 셀 = SSOT, product.location 자동 동기화

### 입출고 워크플로우 (v5.1 신규)

- 앱 하단바: [입고][출고][장바구니]
- 입고: 스캔 → 도면 셀 선택 → 층 선택 → 등록 + 위치 동기화
- 출고: 스캔 → 위치 표시 + 도면 하이라이트 → 피킹 완료
- 재고 실사: `POST /inventory-check`

### 외박스 QR 관리

- BoxDetailActivity: 6블록 그리드 + 인라인 도면 미니맵
- 외부 링크 저장 (쿠팡 등)

### 장바구니

- 스캔 상품 장바구니 추가/수량 조정
- 구글시트 자동 내보내기 (gspread)

### 라벨 인쇄

- TSC TE10 프린터 연동
- 바코드 라벨 자동 생성

### 기타

- 오프라인 캐시: Room DB 기반 로컬 저장소
- 앱 자동 업데이트: 실행 시 서버 버전 체크 → 자동 다운로드 유도
- Hilt DI: AppModule + @HiltViewModel (v5.1)
- CellDetailViewModel: Configuration Change 데이터 유실 방지 (v5.1)
- 서버 자동 발견: 내부 WiFi 서브넷 스캔
- xlsx 자동 동기화: NAS 파일 변경 감지 → 자동 파싱 (5분 주기)
- 자동 백업: 매일 03:00, 7일 보관
- 서버 모니터링 API (`/api/status`)
- 배포 자동화: `scripts/deploy.sh` 원커맨드 배포

---

## 남은 작업

### v5.1.0 배포

- [ ] `./gradlew assembleLiveRelease` 빌드 → APK 생성
- [ ] Mini PC 서버 v5.1.0 재배포 (`scripts/deploy.sh`)
- [ ] Mini PC NSSM 서비스 재시작 확인

### PDA 실기기 테스트

- [ ] APK 설치 (Mini PC C:\scanner\)
- [ ] DataWedge 프로파일 자동 생성 확인
- [ ] 실제 바코드 스캔 테스트 (0.3~0.5초 목표)
- [ ] 입출고 워크플로우 E2E 테스트
- [ ] 오프라인 모드 동작 확인
- [ ] 현장 WiFi 환경 테스트

### 클라이언트 확인 대기

- [ ] 발주하기 URL 데이터 수신 → 파서 추가 + 발주 버튼 활성화
- [ ] 재고 수량 입력 화면 최종 확인 (입출고 워크플로우 반영)

### 납품 전

- [ ] 운영 가이드 최종 검토
- [ ] 클라이언트 최종 확인 → 잔금 수령

---

## 주요 파일 위치

| 파일                           | 용도                                   |
| ------------------------------ | -------------------------------------- |
| CHANGELOG.md                   | 버전별 변경 이력                       |
| ROADMAP.md                     | 마일스톤 체크리스트                    |
| DEV_NOTES.md                   | 개발 노트 (클라이언트 소통, 기술 분석) |
| docs/API_REFERENCE.md          | 전체 API 레퍼런스 (v5.0 기준)          |
| docs/TECHNICAL_SPEC.md         | 기술명세서                             |
| docs/DEPLOY_GUIDE.md           | 배포 가이드                            |
| docs/MINIPC_SETUP_CHECKLIST.md | Mini PC 설치 체크리스트                |
| server/docs/OPERATION_GUIDE.md | 운영 가이드                            |
| scripts/deploy.sh              | 원커맨드 배포 스크립트                 |

---

## 접속 정보

| 항목          | 값                                     |
| ------------- | -------------------------------------- |
| Mini PC SSH   | `ssh lenovo@100.125.17.60` (Tailscale) |
| 서버          | http://100.125.17.60:8000              |
| 서비스        | ScannerAPI (NSSM)                      |
| 프로젝트 경로 | C:\scanner\                            |
| NAS WebDAV    | https://chominseven.synology.me:58890  |
| NAS 경로      | /물류부/scan/                          |

---

## 주요 링크

- 진행현황: https://docs-six-woad-11.vercel.app/progress.html
- GitHub: https://github.com/doublesilver/scan
