# D+1 작업 프롬프트

아래 내용을 Claude CLI에 그대로 붙여넣으세요.

---

CLAUDE.md, ROADMAP.md, DEV_NOTES.md를 읽고 M1(서버 셋업+DB) + M2(xlsx 파서)를 완료해줘.

## 작업 원칙

### 구조/설계 — 보수적
- Phase 2(재고 입력, 로케이션), Phase 3 이후 기능 확장을 고려한 DB 스키마 설계
- 테이블 추가/컬럼 추가가 기존 구조를 깨지 않도록 정규화
- config는 환경변수 + .env 파일 이원화, 하드코딩 금지
- 서버 디렉토리 구조는 CLAUDE.md의 Directory Structure를 정확히 따를 것

### 문서화 — 철저하게
- `server/docs/` 디렉토리에 아래 문서 작성:
  - `ARCHITECTURE.md` — 서버 아키텍처 개요, 모듈 간 의존 관계, 데이터 흐름
  - `DATABASE.md` — 전체 ERD, 테이블 정의(컬럼/타입/제약조건/인덱스), 설계 의도, Phase 2 확장 포인트
  - `PARSER_SPEC.md` — xlsx 파서 상세 스펙 (codepath/sku_download 각각의 파싱 규칙, 헤더 매칭 전략, 에러 처리 정책, upsert 동작 정의)
  - `API_SPEC.md` — API 엔드포인트 목록, 요청/응답 스키마, 에러 코드 정의 (M3에서 구현하지만 설계는 지금)

### DB 스키마 설계 시 필수 고려
- PRODUCT: sku_id(PK), product_name, category, brand, 확장 필드 대비 json 컬럼 또는 별도 테이블
- BARCODE: id(PK), barcode(UNIQUE INDEX), sku_id(FK) — 상품당 복수 바코드
- IMAGE: id(PK), sku_id(FK), file_path, image_type(thumbnail/real) — 이미지 종류 구분
- 향후 확장: INVENTORY(재고), LOCATION(로케이션), SCAN_LOG(스캔 이력) 테이블은 지금 만들지 않되 DATABASE.md에 설계만 기술
- created_at, updated_at 타임스탬프 모든 테이블에 포함
- 마이그레이션 전략: 초기 버전은 앱 시작 시 CREATE IF NOT EXISTS, 향후 alembic 전환 가능하도록 스키마를 별도 파일로 분리

### xlsx 파서 구현 시 필수 고려
- sku_download.xlsx는 inlineStr 타입 → openpyxl read_only로 읽히지 않음 → lxml 또는 openpyxl data_only=False로 파싱
- codepath.xlsx는 헤더 없음, 2컬럼 고정 (바코드, Windows 절대경로)
- 경로 변환: `Z:\물류부\scan\img\xxx.jpg` → `img/xxx.jpg` (상대경로)
- sku_download 헤더 매칭: 컬럼명 유사도 기반 자동 매칭 구현 (쿠팡이 헤더를 바꿔도 대응)
- upsert: 동일 바코드/SKU ID 존재 시 업데이트, 없으면 신규 삽입
- 파싱 결과 로그: 추가/갱신/스킵 건수 출력
- 에러 처리: 개별 행 실패 시 해당 행만 스킵하고 계속 진행, 실패 행 로그 기록

### 코딩 컨벤션
- CLAUDE.md의 Python 컨벤션 준수 (snake_case, type hint, async/await, Pydantic)
- 모든 함수에 type hint 필수
- Pydantic model로 DB 스키마와 별도의 request/response 모델 정의
- 설정값은 pydantic-settings의 BaseSettings 사용

## 완료 후

1. ROADMAP.md에서 M1, M2 완료 조건 체크리스트를 하나씩 검증하고 통과한 항목은 `[x]`로 변경
2. M1, M2의 Status를 `[x] 완료`로 변경
3. `docs/progress.html` 업데이트:
   - 히어로 카드: 마일스톤 완료 수, 진행률 반영
   - M1, M2 뱃지를 `badge-done`으로 변경, M3를 `badge-progress`로 변경
   - 체크리스트 항목에 `checked` 클래스 추가
   - 프로그레스 바 width 반영
   - 일일 작업 로그에 D+1 작업 내역 업데이트 (실제 구현한 내용 기반)
   - 다음 단계를 M3으로 변경
4. git commit + push (버셀 자동 배포)
