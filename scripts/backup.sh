#!/bin/bash
set -euo pipefail

DB_PATH="${1:-server/data/scanner.db}"
BACKUP_DIR="${2:-server/data/backups}"
TODAY=$(date +%Y%m%d)
CUTOFF_DATE=$(date -v-6d +%Y%m%d 2>/dev/null || date -d "6 days ago" +%Y%m%d)

mkdir -p "$BACKUP_DIR"
cp "$DB_PATH" "$BACKUP_DIR/scanner_${TODAY}.db"

for f in "$BACKUP_DIR"/scanner_*.db; do
    [ -f "$f" ] || continue
    fname=$(basename "$f")
    file_date=$(echo "$fname" | sed 's/scanner_\([0-9]\{8\}\)\.db/\1/')
    if [ "$file_date" -lt "$CUTOFF_DATE" ] 2>/dev/null; then
        rm "$f"
    fi
done
