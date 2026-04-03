# Deep Interview Spec: 물류창고 도면 아키텍처 v2

## Metadata
- Interview ID: di-warehouse-map-arch
- Rounds: 11
- Final Ambiguity Score: 8%
- Type: brownfield
- Generated: 2026-04-03
- Status: PASSED

## Goal
도면 셀을 상품 위치의 SSOT로 삼고, DB를 정규화 테이블로 분리하여 확장 가능한 구조를 구축한다. 상품(Product Master) 단위로 셀에 등록하며, PDA 앱에서 구조 편집까지 가능하게 한다.

## 데이터 모델 (정규화)

```
product_master (상품)
├── id, name, image, category
└── product (제품/SKU)
    ├── id, master_id, sku_id, barcode, name, location
    └── (기존 product 테이블 확장)

warehouse_zone (구역)
├── id, warehouse_id, code, name, rows, cols, sort_order
└── warehouse_cell (셀)
    ├── id, zone_id, row, col, label, status, bg_color
    └── cell_level (층)
        ├── id, cell_id, index, label
        └── cell_level_product (층별 상품)
            ├── id, level_id, product_master_id, photo, memo
            └── (한 층에 여러 상품 가능)
```

## 핵심 원칙
1. **도면 셀 = SSOT** — 상품 위치는 도면에서만 편집. 저장 시 product.location 자동 동기화.
2. **상품(Master) 단위 등록** — 셀 층에는 상품군을 등록. 하위 SKU는 상품에 귀속.
3. **한 층 = 여러 상품** — 하나의 층에 여러 상품이 배치될 수 있음.
4. **PDA 앱 = 1순위** — 구조 편집(구역/행열) + 셀 내용 편집 모두 PDA에서 가능.
5. **웹 에디터 = 부가** — PDA 앱과 디자인 통일. 초기 세팅 + 관리자 편의.
6. **테이블 정규화** — JSON blob → zone/cell/cell_level/cell_level_product 테이블 분리.
7. **확장 대비** — 창고 추가, 셀 200+, 동시 편집 증가에 대응.

## Constraints
- PDA 기기: Galaxy S20+ / Zebra TC60
- 네트워크: 내부 WiFi
- 사용자: 비IT 창고 직원
- 등록 패턴: 소량 즉시 + 대량 일괄
- 서버: Windows Mini PC, SQLite → 정규화 테이블
- 오버엔지니어링 허용: 속도/오류/버그 없이 견고하게

## Acceptance Criteria
- [ ] DB 마이그레이션: map_layout JSON → zone/cell/cell_level/cell_level_product 테이블
- [ ] product_master 테이블 활용: 상품(master) ↔ 제품(SKU) 계층 구조
- [ ] API: 셀 단위 CRUD (전체 JSON 교체 아닌 개별 셀 조작)
- [ ] PDA: 구역 추가/삭제/이름변경 가능
- [ ] PDA: 구역 행/열 수 변경 가능
- [ ] PDA: 셀 층에 상품(master) 등록 (바코드 스캔 → 상품 자동 매칭)
- [ ] PDA: 한 층에 여러 상품 등록 가능
- [ ] 셀 편집 시 product.location 자동 동기화
- [ ] 상품 스캔 → 도면 하이라이트 (cell_level_product에서 검색)
- [ ] 웹 에디터: 새 API에 맞게 수정, PDA와 디자인/컬러 통일
- [ ] 기존 데이터 마이그레이션 (JSON blob → 정규화 테이블, 데이터 유실 없음)
- [ ] 구조 변경 시 기존 셀 데이터 유실 없음

## Interview Transcript Summary
| Round | Question | Answer | Dimension |
|-------|----------|--------|-----------|
| 1 | 도면 데이터의 주인이 누구? | 직원도 수정 | 목표 |
| 2 | 수정하는 구체적 상황? | 셀 내용만 (→ 후에 구조도 가능으로 수정) | 목표 |
| 3 | 등록 시점? | 상황에 따라 (즉시+일괄) | 제약 |
| 4 | 가장 자주 하는 작업? | 읽기/쓰기/사진 비슷 | 제약 |
| 5 | 읽기/쓰기 분리 필요? | PDA 1순위, 웹은 부가 | 목표 |
| 6 | 위치 기준 데이터? | 도면이 기준 (SSOT) | 성공기준 |
| 7 | 양쪽 유지 vs 도면만? | 양쪽 유지 (빠름) | 성공기준 |
| 8 | 방향 확인 | 구조 변경 가능, PDA에서 구조 편집, 웹 디자인 통일 | 컨텍스트 |
| 9 | 상품 vs 제품 구분? | 상품(master) 단위로 등록 | 목표 |
| 10 | 한 층에 여러 상품? | 한 층 = 여러 상품 | 제약 |
| 11 | DB 구조? | 테이블 정규화 (확장 대비) | 성공기준 |
