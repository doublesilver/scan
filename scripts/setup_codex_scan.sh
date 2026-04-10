#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CODEX_HOME="${CODEX_HOME:-$HOME/.codex}"
CONFIG_FILE="$CODEX_HOME/config.toml"
SKILL_SRC="$ROOT_DIR/.codex/skills/scan-workflow"
SKILL_DST="$CODEX_HOME/skills/scan-workflow"
DB_PATH="$ROOT_DIR/server/data/scanner.db"
PROJECT_HEADER="[projects.\"$ROOT_DIR\"]"

mkdir -p "$CODEX_HOME/skills"
mkdir -p "$(dirname "$CONFIG_FILE")"

if [[ ! -d "$SKILL_SRC" ]]; then
  echo "Missing skill source: $SKILL_SRC" >&2
  exit 1
fi

rm -rf "$SKILL_DST"
mkdir -p "$SKILL_DST"
cp -R "$SKILL_SRC"/. "$SKILL_DST"/

touch "$CONFIG_FILE"

python3 - "$CONFIG_FILE" "$ROOT_DIR" <<'PY'
from pathlib import Path
import re
import sys

config_path = Path(sys.argv[1])
root_dir = sys.argv[2]
header = f'[projects."{root_dir}"]'
text = config_path.read_text(encoding="utf-8") if config_path.exists() else ""

section_re = re.compile(
    rf"(?ms)^(?P<header>\[projects\.\"{re.escape(root_dir)}\"\]\n)(?P<body>.*?)(?=^\[|\Z)"
)
match = section_re.search(text)

if match:
    body = match.group("body")
    if re.search(r'(?m)^trust_level\s*=', body):
        body = re.sub(
            r'(?m)^trust_level\s*=.*$',
            'trust_level = "trusted"',
            body,
            count=1,
        )
    else:
        if body and not body.endswith("\n"):
            body += "\n"
        body += 'trust_level = "trusted"\n'
    text = text[: match.start()] + match.group("header") + body + text[match.end() :]
else:
    if text and not text.endswith("\n"):
        text += "\n"
    text += f'\n{header}\ntrust_level = "trusted"\n'

config_path.write_text(text, encoding="utf-8")
PY

CURRENT_MCP="$(codex mcp get scan-sqlite 2>/dev/null || true)"
if [[ "$CURRENT_MCP" == *"$DB_PATH"* ]]; then
  echo "MCP already configured: scan-sqlite -> $DB_PATH"
else
  if [[ -n "$CURRENT_MCP" ]]; then
    codex mcp remove scan-sqlite >/dev/null 2>&1 || true
  fi
  codex mcp add scan-sqlite -- uvx mcp-server-sqlite --db-path "$DB_PATH"
fi

cat <<EOF
Codex setup complete.

- Skill installed: $SKILL_DST
- Project trust ensured in: $CONFIG_FILE
- MCP ensured: scan-sqlite -> $DB_PATH

Recommended launch:
  codex -C "$ROOT_DIR" --sandbox workspace-write -a on-request
EOF
