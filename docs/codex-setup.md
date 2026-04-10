# Codex Setup

이 프로젝트에서 Codex를 안정적으로 쓰기 위한 최소 세팅.

## 포함 내용

- 프로젝트 전용 스킬: `scan-workflow`
- 프로젝트 trust 등록: `~/.codex/config.toml`
- SQLite MCP 등록: `scan-sqlite`

## 설치

홈 디렉터리의 `~/.codex`를 수정해야 하므로 아래 스크립트를 실행한다.

```bash
bash scripts/setup_codex_scan.sh
```

## 권장 실행

```bash
codex -C "$(pwd)" --sandbox workspace-write -a on-request
```

## 구성 의도

- 작업 규칙은 저장소의 `CLAUDE.md`, `AGENTS.md`를 기준으로 유지
- Codex 스킬은 이 프로젝트에서 반복되는 탐색 순서와 금지 경로만 추가
- DB는 파일 직접 열람보다 `scan-sqlite` MCP 우선
