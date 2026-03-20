#!/bin/bash
set -euo pipefail

BACKUP_FILE="$1"
RESTORE_TARGET="$2"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "백업 파일이 존재하지 않습니다: $BACKUP_FILE"
    exit 1
fi

if [ -f "$RESTORE_TARGET" ]; then
    cp "$RESTORE_TARGET" "${RESTORE_TARGET}.bak"
fi

cp "$BACKUP_FILE" "$RESTORE_TARGET"
echo "복구 완료: $BACKUP_FILE -> $RESTORE_TARGET"
