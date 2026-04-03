# 도면 아키텍처 세부 설계서

## 1. DB 스키마 (정규화)

### 새 테이블

```sql
-- 구역
CREATE TABLE warehouse_zone (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    rows INTEGER NOT NULL DEFAULT 3,
    cols INTEGER NOT NULL DEFAULT 4,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_zone_code ON warehouse_zone(code);

-- 셀
CREATE TABLE warehouse_cell (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    zone_id INTEGER NOT NULL REFERENCES warehouse_zone(id) ON DELETE CASCADE,
    row INTEGER NOT NULL,
    col INTEGER NOT NULL,
    label TEXT DEFAULT '',
    status TEXT DEFAULT 'empty',
    bg_color TEXT DEFAULT '',
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now')),
    UNIQUE(zone_id, row, col)
);
CREATE INDEX idx_cell_zone ON warehouse_cell(zone_id);

-- 층
CREATE TABLE cell_level (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cell_id INTEGER NOT NULL REFERENCES warehouse_cell(id) ON DELETE CASCADE,
    level_index INTEGER NOT NULL DEFAULT 0,
    label TEXT DEFAULT '',
    created_at TEXT DEFAULT (datetime('now')),
    UNIQUE(cell_id, level_index)
);
CREATE INDEX idx_level_cell ON cell_level(cell_id);

-- 층별 상품 (한 층에 여러 상품 가능)
CREATE TABLE cell_level_product (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    level_id INTEGER NOT NULL REFERENCES cell_level(id) ON DELETE CASCADE,
    product_master_id INTEGER REFERENCES product_master(id),
    photo TEXT DEFAULT '',
    memo TEXT DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_lp_level ON cell_level_product(level_id);
CREATE INDEX idx_lp_master ON cell_level_product(product_master_id);
```

### product_master 확장 (기존 테이블 활용)
```sql
-- 기존 product_master 테이블에 컬럼 추가
ALTER TABLE product_master ADD COLUMN category TEXT DEFAULT '';
ALTER TABLE product_master ADD COLUMN representative_image TEXT DEFAULT '';
```

### 마이그레이션 전략
1. DDL: 위 CREATE TABLE문을 `schema.py` MIGRATIONS[9]에 추가
2. Python 마이그레이션 함수: `migrate_json_to_tables(db)`
   - `map_layout.data` JSON 읽기
   - zones → `warehouse_zone` INSERT
   - cells → `warehouse_cell` INSERT
   - levels → `cell_level` INSERT
   - levels[].itemLabel/sku → `cell_level_product` INSERT (product_master 매칭)
3. 호환: `GET /api/map-layout`은 새 테이블에서 JSON 조립하여 반환 (웹 에디터 호환)

---

## 2. API 설계

### 구역 (Zone)
| Method | URL | 설명 | Body/Params | 응답 |
|--------|-----|------|-------------|------|
| GET | /api/zones | 전체 구역 목록 | - | `[{id, code, name, rows, cols, cell_count}]` |
| POST | /api/zones | 구역 추가 | `{code, name, rows, cols}` | `{id, code, name, rows, cols}` |
| PATCH | /api/zones/{id} | 구역 수정 | `{name?, rows?, cols?, code?}` | `{id, ...}` |
| DELETE | /api/zones/{id} | 구역 삭제 | - | `{status: "ok"}` |

### 셀 (Cell)
| Method | URL | 설명 | 응답 |
|--------|-----|------|------|
| GET | /api/zones/{zone_id}/cells | 구역 내 셀+층+상품 전체 | `[{id, row, col, label, status, levels: [{index, label, products: [...]}]}]` |
| GET | /api/cells/{cell_id} | 셀 상세 | `{id, zone, row, col, levels: [...]}` |
| PATCH | /api/cells/{cell_id} | 셀 수정 | `{status: "ok"}` |

### 층 (Level)
| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/cells/{cell_id}/levels | 층 추가 `{label}` |
| DELETE | /api/levels/{level_id} | 층 삭제 |

### 층별 상품 (Level Product)
| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/levels/{level_id}/products | 상품 등록 `{barcode or sku_id or product_master_id}` → 자동 매칭 |
| DELETE | /api/level-products/{id} | 상품 제거 |
| POST | /api/level-products/{id}/photo | 사진 업로드 (multipart) |
| DELETE | /api/level-products/{id}/photo | 사진 삭제 |

### 호환 API (웹 에디터용)
| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/map-layout | 새 테이블에서 기존 JSON 형태로 조립하여 반환 |
| POST | /api/map-layout | JSON을 받아 새 테이블에 분해 저장 |

### 위치 검색
| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/product-location/{sku_id} | SKU가 등록된 셀 위치 반환 (cell_level_product → cell → zone) |

---

## 3. PDA UX 흐름

### 3-1. 도면 전체 보기 (WarehouseMapDialog)
```
┌─────────────────────────────┐
│ 5층 창고 도면     [+ 구역]   │
├─────────────────────────────┤
│ 501호 (1구역) [✏]           │
│ ┌──┬──┬──┬──┐               │
│ │1-1│1-2│1-3│1-4│           │
│ ├──┼──┼──┼──┤               │
│ │1-5│1-6│1-7│1-8│           │
│ └──┴──┴──┴──┘               │
│                             │
│ 포장다이 (2구역) [✏]        │
│ ┌──┬──┐                     │
│ │2-1│2-2│                   │
│ └──┴──┘                     │
│                             │
│ 502호 (3구역) [✏]           │
│ ...                         │
└─────────────────────────────┘
```
- [✏] → 구역 이름/행열 수정 다이얼로그
- [+ 구역] → 새 구역 추가
- 셀 클릭 → CellDetailActivity

### 3-2. 셀 상세 (CellDetailActivity 리뉴얼)
```
┌─────────────────────────────┐
│ ← 1구역 1-3         [편집]  │
├─────────────────────────────┤
│                             │
│ ▼ 상단 (3층)                │
│ ┌───────────────────────┐   │
│ │ 📦 매트 그레인 스트랩   │   │
│ │ [사진]  SKU 3개        │   │
│ ├───────────────────────┤   │
│ │ 📦 듀얼 쉐이드 스트랩   │   │
│ │ [사진]  SKU 5개        │   │
│ └───────────────────────┘   │
│ [+ 상품 추가]               │
│                             │
│ ▼ 중단 (2층)                │
│ ┌───────────────────────┐   │
│ │ 📦 밀레니즈 스트랩      │   │
│ │ [사진]  SKU 2개        │   │
│ └───────────────────────┘   │
│ [+ 상품 추가]               │
│                             │
│ ▼ 하단 (1층)                │
│ (비어있음)                   │
│ [+ 상품 추가]               │
│                             │
├─────────────────────────────┤
│ [도면]  [+ 층 추가]  [편집]  │
└─────────────────────────────┘
```
- [+ 상품 추가] → 바코드 스캔 or 검색 → product_master 매칭
- 상품 카드 클릭 → 사진 교체, 메모, SKU 목록 보기
- 상품 카드 롱프레스 → 삭제

### 3-3. 상품 등록 흐름
```
[+ 상품 추가] 클릭
    ↓
PDA 바코드 스캔 (하드웨어) or [검색] 버튼
    ↓
barcode → product 테이블 조회 → product_master_id 획득
    ↓
cell_level_product INSERT (level_id + product_master_id)
    ↓
product_master의 모든 SKU.location 자동 업데이트
    ↓
UI 갱신 (상품 카드 추가됨)
```

### 3-4. 구역 구조 편집 (PDA)
```
구역 [✏] 클릭 → 다이얼로그:
┌─────────────────────────┐
│ 구역 설정               │
│ 이름: [501호         ]   │
│ 코드: [1             ]   │
│ 행:   [3] [-] [+]       │
│ 열:   [4] [-] [+]       │
│                         │
│ [삭제]     [취소] [저장]  │
└─────────────────────────┘
```
- 행/열 변경 시 기존 셀 데이터 보존 (확장: 새 셀 생성, 축소: 경고 후 삭제)

---

## 4. 위치 동기화 로직

```
cell_level_product에 상품 등록 시:
1. product_master_id로 product_master 조회
2. product_master_sku에서 해당 master의 모든 SKU 조회
3. 각 SKU의 product.location 업데이트:
   - cell → zone 조회
   - cellNum = (cell.row - 1) * zone.cols + cell.col
   - location = "{zone.code}구역 {zone.code}-{cellNum}"
4. product.location UPDATE

cell_level_product에서 상품 제거 시:
1. 해당 product_master의 SKU들의 location을 NULL로 초기화
   (단, 다른 셀에도 같은 master가 등록되어 있으면 유지)

상품 스캔 시 위치 조회:
1. barcode → product.sku_id → product.location (빠른 조회)
2. location으로 도면 하이라이트 (기존 방식 유지)
```

---

## 5. 웹 에디터 수정 방향

- **API 호환**: `GET /api/map-layout` 호환 레이어로 기존 JSON 형태 반환 → 웹 에디터 최소 수정
- **디자인 통일**: 네이비(#040d1b) + 오렌지(#fe6a34) 컬러 적용
- **점진적 전환**: 초기에는 호환 API로 동작, 이후 새 API로 마이그레이션

---

## 6. 구현 우선순위

| 순서 | 항목 | 공수 | 설명 |
|------|------|------|------|
| P0-1 | DB 스키마 + 마이그레이션 | 1일 | 테이블 생성 + JSON→테이블 데이터 변환 |
| P0-2 | 서버 서비스 + API | 1일 | zone/cell/level/product CRUD |
| P0-3 | 호환 API (map-layout) | 0.5일 | 새 테이블 → JSON 조립 (웹 에디터 호환) |
| P1-1 | Android 데이터 모델 수정 | 0.5일 | Kotlin data class + ApiService 수정 |
| P1-2 | CellDetailActivity 리뉴얼 | 1일 | 층별 여러 상품 표시 + 등록/삭제 |
| P1-3 | 도면 다이얼로그 구역 편집 | 0.5일 | PDA에서 구역 추가/수정/삭제 |
| P2-1 | 위치 동기화 개선 | 0.5일 | product_master 기반 일괄 동기화 |
| P2-2 | 웹 에디터 디자인 통일 | 1일 | 컬러/스타일 PDA 앱과 통일 |
| P3 | 일괄 등록 기능 | 1일 | 대량 입고 시 연속 스캔 등록 |

**총 예상: 7일**
