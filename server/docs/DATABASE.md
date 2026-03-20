# 데이터베이스 설계

## 개요

- SQLite (aiosqlite)
- 파일 위치: `data/scanner.db`
- 스키마 정의: `app/db/schema.py`

---

## ERD (텍스트)

```
product (sku_id PK)
    └── 1:N → barcode (sku_id FK, NULL 허용)
                └── 1:N → image (barcode 참조)
독립 → parse_log (관계 없음, 적재 이력)
```

---

## 테이블 정의

### product

| 컬럼         | 타입 | 제약                             | 설명             |
| ------------ | ---- | -------------------------------- | ---------------- |
| sku_id       | TEXT | PRIMARY KEY                      | 쿠팡 SKU ID      |
| product_name | TEXT | NOT NULL DEFAULT ''              | 상품명           |
| category     | TEXT | NOT NULL DEFAULT ''              | 카테고리         |
| brand        | TEXT | NOT NULL DEFAULT ''              | 브랜드/제조사    |
| extra        | TEXT | NOT NULL DEFAULT '{}'            | 기타 필드 (JSON) |
| created_at   | TEXT | NOT NULL DEFAULT datetime('now') |                  |
| updated_at   | TEXT | NOT NULL DEFAULT datetime('now') |                  |

- `extra`: moq, weight 등 비정형 옵션 컬럼을 JSON으로 수용.
- codepath.xlsx만 적재된 경우 `product_name = ''`인 placeholder 행 존재. sku_download 적재 시 실데이터로 갱신.

### barcode

| 컬럼         | 타입    | 제약                              | 설명          |
| ------------ | ------- | --------------------------------- | ------------- |
| id           | INTEGER | PRIMARY KEY AUTOINCREMENT         |               |
| barcode      | TEXT    | NOT NULL UNIQUE                   | EAN-13 바코드 |
| sku_id       | TEXT    | FK → product (ON DELETE SET NULL) |               |
| barcode_type | TEXT    | NOT NULL DEFAULT 'EAN-13'         | 바코드 유형   |
| created_at   | TEXT    | NOT NULL DEFAULT datetime('now')  |               |
| updated_at   | TEXT    | NOT NULL DEFAULT datetime('now')  |               |

- UNIQUE(barcode): 동일 바코드 중복 등록 불가.
- sku_id는 NULL 허용 (codepath에서 SKU 미매칭 상태로 생성 가능).
- 상품당 복수 바코드 허용 (동일 sku_id에 여러 barcode 행).
- 인덱스: `idx_barcode_barcode`, `idx_barcode_sku_id`

### image

| 컬럼       | 타입    | 제약                             | 설명                         |
| ---------- | ------- | -------------------------------- | ---------------------------- |
| id         | INTEGER | PRIMARY KEY AUTOINCREMENT        |                              |
| barcode    | TEXT    | NOT NULL                         | 바코드 (barcode 테이블 참조) |
| file_path  | TEXT    | NOT NULL                         | 상대경로 (img/xxx.jpg)       |
| image_type | TEXT    | NOT NULL DEFAULT 'thumbnail'     | thumbnail / real             |
| sort_order | INTEGER | NOT NULL DEFAULT 0               | 정렬 순서                    |
| created_at | TEXT    | NOT NULL DEFAULT datetime('now') |                              |
| updated_at | TEXT    | NOT NULL DEFAULT datetime('now') |                              |

- UNIQUE (barcode, file_path): 동일 바코드+경로 중복 저장 불가.
- `image_type`: 경로에 `real_image` 포함 시 `real`, 그 외 `thumbnail`.
- 인덱스: `idx_image_barcode`

### parse_log

| 컬럼          | 타입    | 설명                             |
| ------------- | ------- | -------------------------------- |
| id            | INTEGER | PRIMARY KEY AUTOINCREMENT        |
| file_name     | TEXT    | 파싱한 파일 경로                 |
| file_type     | TEXT    | `codepath` / `sku_download`      |
| record_count  | INTEGER | 총 처리 행 수                    |
| added_count   | INTEGER | 신규 추가 행 수                  |
| updated_count | INTEGER | 갱신 행 수                       |
| skipped_count | INTEGER | 스킵 행 수 (빈 행 등)            |
| error_count   | INTEGER | 에러 행 수                       |
| errors        | TEXT    | 에러 상세 목록 (JSON, 최대 50건) |
| duration_ms   | INTEGER | 파싱 소요 시간 (ms)              |
| parsed_at     | TEXT    | 파싱 시각                        |

---

## 설계 의도

- **정규화**: product 1 : barcode N, barcode 1 : image N. 복수 바코드/이미지 자연스럽게 수용.
- **barcode 중심 설계**: codepath.xlsx에는 SKU ID가 없으므로 barcode 테이블에 sku_id=NULL 상태로 생성. image 테이블은 barcode 기준으로 연결. sku_download 적재 시 barcode.sku_id가 갱신되어 product와 연결.
- **extra JSON**: 쿠팡 xlsx 컬럼 변경에 유연하게 대응. 자주 조회되는 필드는 향후 정식 컬럼으로 승격.

---

## created_at / updated_at 전략

- SQLite `DEFAULT (datetime('now'))`: INSERT 시 자동 설정.
- UPDATE 시 `updated_at = datetime('now')`를 SQL에 명시적으로 포함 (트리거 미사용).
- 형식: ISO 8601 문자열 (`2026-03-19 12:00:00`), UTC.

---

## Phase 2 확장 포인트

구현 예정 테이블 설계 (현재 미구현).

### inventory (재고)

```sql
CREATE TABLE inventory (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    sku_id      TEXT NOT NULL REFERENCES product(sku_id),
    location_id INTEGER REFERENCES location(id),
    quantity    INTEGER NOT NULL DEFAULT 0,
    memo        TEXT NOT NULL DEFAULT '',
    scanned_at  TEXT NOT NULL DEFAULT (datetime('now')),
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_inventory_sku_id ON inventory(sku_id);
CREATE INDEX idx_inventory_location_id ON inventory(location_id);
```

### location (로케이션/구역)

```sql
CREATE TABLE location (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT NOT NULL UNIQUE,   -- 예: A-01-03
    description TEXT NOT NULL DEFAULT '',
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
```

### scan_log (스캔 이력)

```sql
CREATE TABLE scan_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    barcode     TEXT NOT NULL,
    sku_id      TEXT,
    action      TEXT NOT NULL DEFAULT 'lookup',  -- lookup / inventory_in / inventory_out
    operator    TEXT NOT NULL DEFAULT '',
    scanned_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_scan_log_scanned_at ON scan_log(scanned_at);
```

---

## 마이그레이션 전략

- **현재 (M1~M2)**: `CREATE TABLE IF NOT EXISTS`. 스키마 변경 시 DB 파일 삭제 후 재적재.
- **향후 (Phase 2)**: alembic 도입 검토. `alembic init`, `alembic revision --autogenerate`, `alembic upgrade head` 흐름.
