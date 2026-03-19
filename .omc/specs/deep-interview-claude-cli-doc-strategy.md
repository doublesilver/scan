# Deep Interview Spec: Claude CLI 작업용 문서 체계 설계

## Metadata
- Interview ID: scan-doc-strategy-20260319
- Rounds: 6
- Final Ambiguity Score: 14%
- Type: greenfield
- Generated: 2026-03-19
- Threshold: 20%
- Status: PASSED

## Clarity Breakdown
| Dimension | Score | Weight | Weighted |
|-----------|-------|--------|----------|
| Goal Clarity | 0.95 | 40% | 0.38 |
| Constraint Clarity | 0.80 | 30% | 0.24 |
| Success Criteria | 0.80 | 30% | 0.24 |
| **Total Clarity** | | | **0.86** |
| **Ambiguity** | | | **14%** |

## Goal
CLAUDE.md(스펙/컨벤션/규칙)와 ROADMAP.md(테스트 기반 마일스톤 체크리스트)를 설계하여, Claude CLI가 새 세션에서 문서를 읽고 스스로 다음 작업을 판단하고 진행할 수 있도록 한다. DEV_NOTES.md는 기존 그대로 참조용으로 유지한다.

## Constraints
- CLAUDE.md는 간결하게 유지 (스펙/컨벤션/규칙 + ROADMAP.md 참조 지시만)
- ROADMAP.md에 마일스톤 단위 로드맵 + 테스트 기반 완료 조건 체크리스트
- DEV_NOTES.md는 데이터 분석/클라이언트 요구사항 참조용 (수정 불필요)
- 세부 구현 방법은 Claude가 자율 판단 (파일/함수 단위 태스크는 적지 않음)
- Phase 1 (MVP) 범위만 — 클라이언트 지시 없이 기능 추가 금지

## Non-Goals
- 클라이언트 납품용 운영 가이드 문서 (개발 완료 후 별도 작성)
- 디렉토리별 CLAUDE.md 분리 (단일 CLAUDE.md로 충분)
- 세부 태스크 백로그 (마일스톤 단위만, 나머지는 Claude 판단)

## Acceptance Criteria
- [ ] CLAUDE.md에 "ROADMAP.md를 읽고 미완료 마일스톤부터 작업하라" 지시 추가
- [ ] ROADMAP.md 생성: 마일스톤별 테스트 기반 완료 조건 체크리스트 포함
- [ ] 각 마일스톤 완료 조건이 Claude가 직접 검증 가능한 수준 (curl, pytest, adb 등)
- [ ] 새 Claude CLI 세션에서 CLAUDE.md + ROADMAP.md만 읽고 다음 작업 판단 가능
- [ ] DEV_NOTES.md는 수정 없이 기존 참조용 유지

## Assumptions Exposed & Resolved
| Assumption | Challenge | Resolution |
|------------|-----------|------------|
| 모든 문서를 한 파일에 | CLAUDE.md가 150줄+ → 컨텍스트 비효율 | CLAUDE.md + ROADMAP.md 분리 |
| 세부 태스크 필요 | Claude가 마일스톤만으로 판단 가능? | 가능 — 완료 조건이 구체적이면 세부 구현은 Claude 자율 |
| 납품 문서도 같이 | 지금 필요한가? | 아니오 — 개발 완료 후 별도 작성 |

## Technical Context
- 기존 파일: CLAUDE.md (60줄, 프로젝트 스펙), DEV_NOTES.md (250줄, 착수 노트)
- 프로젝트: Android PDA 앱 (Kotlin) + FastAPI 서버 (Python)
- 데이터: codepath.xlsx (11,821행), coupangmd00_sku_download.xlsx
- 제안서 약속: Phase 1 MVP, D+10, 300만원

## Ontology (Key Entities)

| Entity | Type | Fields | Relationships |
|--------|------|--------|---------------|
| CLAUDE.md | core document | 프로젝트 개요, 테크 스택, 디렉토리 구조, 코딩 컨벤션, 키 스펙, 규칙, ROADMAP 참조 지시 | ROADMAP.md를 참조 |
| ROADMAP.md | core document | 마일스톤 목록, 각 마일스톤별 완료 조건 체크리스트, 진행 상태 | CLAUDE.md에서 참조됨, DEV_NOTES.md 참조 |
| DEV_NOTES.md | supporting document | 클라이언트 요구사항, 데이터 분석, NAS 구조, 통화 내용, 미확인 사항 | ROADMAP.md에서 참조됨 |

## Ontology Convergence

| Round | Entity Count | New | Changed | Stable | Stability Ratio |
|-------|-------------|-----|---------|--------|----------------|
| 1 | 3 | 3 | - | - | N/A |
| 2 | 4 | 1 | 0 | 3 | 75% |
| 3 | 4 | 0 | 0 | 4 | 100% |
| 4 | 3 | 0 | 0 | 3 | 100% (산출물 문서 → CLAUDE.md 흡수) |
| 5 | 3 | 0 | 0 | 3 | 100% |
| 6 | 3 | 0 | 0 | 3 | 100% |

## Interview Transcript
<details>
<summary>Full Q&A (6 rounds)</summary>

### Round 1
**Q:** "문서를 어떻게 작성해둘까"에서 말하는 문서의 목적이 정확히 뭔가요?
**A:** "전체적으로" — Claude 작업 지시서 + 클라이언트 납품 + 프로젝트 관리 모두
**Ambiguity:** 78% (Goal: 0.4, Constraints: 0.1, Criteria: 0.1)

### Round 2
**Q:** Claude가 새 세션에서 자율 판단하길 원하는지, 매번 직접 지시할 건지?
**A:** 자율 판단 (권장)
**Ambiguity:** 61% (Goal: 0.6, Constraints: 0.3, Criteria: 0.2)

### Round 3
**Q:** 문서에 어떤 수준의 정보가 있어야 하는지? 큰 단계 vs 세부 태스크?
**A:** 큰 단계 (마일스톤)
**Ambiguity:** 48% (Goal: 0.75, Constraints: 0.4, Criteria: 0.35)

### Round 4
**Q:** 마일스톤 로드맵을 어디에 넣을까?
**A:** CLAUDE.md에 추가
**Ambiguity:** 35% (Goal: 0.85, Constraints: 0.6, Criteria: 0.45)

### Round 5 (Contrarian Mode)
**Q:** CLAUDE.md 하나에 모든 걸 넣으면 길어지는데, 분리하는 게 낫지 않나?
**A:** 추천 방향 → CLAUDE.md + ROADMAP.md 분리
**Ambiguity:** 27% (Goal: 0.9, Constraints: 0.75, Criteria: 0.5)

### Round 6
**Q:** 마일스톤 완료 조건을 어떤 수준으로 적을까?
**A:** 테스트 기반 수준 (검증 가능한 조건)
**Ambiguity:** 14% (Goal: 0.95, Constraints: 0.8, Criteria: 0.8)

</details>
