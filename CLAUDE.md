# scan — 물류창고 바코드 스캐너

Android PDA(Zebra TC60) + FastAPI 서버 + SQLite. NAS는 WebDAV 프록시.

## 디렉토리

- `android/` — Kotlin, Gradle KTS, View Binding, Retrofit2, MVVM
- `server/` — Python 3.11+, FastAPI, aiosqlite, openpyxl
- 자세한 구조·관례·라우팅 → **AGENTS.md**
- 데이터 구조·NAS 환경 → **DEV_NOTES.md**
- 마일스톤 → **ROADMAP.md**

## 작업 방식

1. `ROADMAP.md`의 첫 미완료(`[ ]`) 마일스톤부터 진행
2. 완료 시 `[x]` 체크 후 다음으로 이동
3. 자율 진행, 코드 변경 후 요약 불필요 (diff로 확인)
4. ruff diff는 별도 커밋으로 분리

## 핵심 규칙

- 클라이언트 기획 주도, 임의 기능 추가 금지
- Phase 1 (MVP) 범위: 바코드 스캔 → 조회 → 이미지/위치/그룹 표시
- 성능 목표: 스캔→표시 0.3~0.5초
- 내부 WiFi 전용, EAN-13 (880 prefix)
- AI 생성 느낌의 장문/과잉 설명 금지

## 슬래시 커맨드

`/scan-server` `/scan-build` `/scan-deploy` `/scan-test` `/scan-log`

## 토큰 절약 (자동 적용)

- 큰 파일(.db, .xlsx, .csv, .apk, build/, mock_images/)은 deny 차단
- 광범위 탐색은 `Explore` 에이전트 위임
- 빌드/테스트/서버는 background 실행
