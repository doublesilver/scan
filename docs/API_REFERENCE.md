# API Reference

물류창고 스캐너 FastAPI 서버의 모든 엔드포인트 문서입니다.

기본 URL: `http://100.125.17.60:8000` (Tailscale 내부망)

---

## 바코드 스캔

### GET /api/scan/{barcode}

바코드로 상품 정보를 조회합니다.

**파라미터**

- `barcode` (path): 바코드 문자열

**응답 (200)**

```json
{
  "sku_id": "SKU001",
  "product_name": "삼성 27인치 모니터",
  "category": "전자제품",
  "brand": "Samsung",
  "barcodes": ["8801234567890"],
  "images": [
    {
      "file_path": "img/SKU001.jpg",
      "image_type": "thumbnail"
    },
    {
      "file_path": "real_image/SKU001_real.jpg",
      "image_type": "real"
    }
  ],
  "quantity": 50,
  "coupang_url": "https://www.coupang.com/vp/products/...",
  "location": "A-01-01"
}
```

**에러**

- `404 Not Found`: 바코드 미등록

---

## 상품 검색

### GET /api/search

검색어로 상품을 조회합니다.

**파라미터**

- `q` (query): 검색어 (필수, 최소 1자)
- `limit` (query): 결과 개수 (기본값: 20, 1~100)

**응답 (200)**

```json
{
  "total": 5,
  "items": [
    {
      "sku_id": "SKU001",
      "product_name": "삼성 27인치 모니터",
      "category": "전자제품",
      "brand": "Samsung",
      "barcode": "8801234567890",
      "thumbnail": "img/SKU001.jpg"
    }
  ]
}
```

---

## 재고 조회 및 수정

### GET /api/stock/{sku_id}

특정 SKU의 재고 정보를 조회합니다.

**파라미터**

- `sku_id` (path): SKU ID

**응답 (200)**

```json
{
  "sku_id": "SKU001",
  "quantity": 50,
  "memo": "정상 보관",
  "updated_by": "PDA",
  "updated_at": "2026-04-01T10:30:00Z"
}
```

**에러**

- `404 Not Found`: SKU 미등록

### PATCH /api/stock/{sku_id}

재고 수량을 수정합니다.

**파라미터**

- `sku_id` (path): SKU ID

**요청 Body**

```json
{
  "quantity": 45,
  "memo": "불량품 3개 제거",
  "updated_by": "PDA"
}
```

**응답 (200)**

```json
{
  "sku_id": "SKU001",
  "quantity": 45,
  "memo": "불량품 3개 제거",
  "updated_by": "PDA",
  "updated_at": "2026-04-01T10:35:00Z"
}
```

**에러**

- `404 Not Found`: SKU 미등록

### GET /api/stock/{sku_id}/log

재고 변경 이력을 조회합니다.

**파라미터**

- `sku_id` (path): SKU ID
- `limit` (query): 이력 개수 (기본값: 20, 1~100)

**응답 (200)**

```json
[
  {
    "before_qty": 50,
    "after_qty": 45,
    "memo": "불량품 3개 제거",
    "updated_by": "PDA",
    "created_at": "2026-04-01T10:35:00Z"
  },
  {
    "before_qty": 60,
    "after_qty": 50,
    "memo": "입고",
    "updated_by": "PDA",
    "created_at": "2026-04-01T09:00:00Z"
  }
]
```

---

## 라벨 인쇄

### POST /api/print

바코드 라벨을 프린터로 출력합니다.

**요청 Body**

```json
{
  "barcode": "8801234567890",
  "sku_id": "SKU001",
  "product_name": "삼성 27인치 모니터",
  "quantity": 10
}
```

**응답 (200)**

```json
{
  "status": "ok",
  "message": "10장 출력 완료"
}
```

**에러**

- `500 Internal Server Error`: 프린터 연결 실패

---

## 장바구니

### POST /api/cart

상품을 장바구니에 추가합니다.

**요청 Body**

```json
{
  "barcode": "8801234567890",
  "sku_id": "SKU001",
  "product_name": "삼성 27인치 모니터",
  "quantity": 5
}
```

**응답 (200)**

```json
{
  "status": "ok",
  "message": "장바구니에 추가됨"
}
```

---

## 이미지 조회

### GET /api/image/{path}

NAS에 저장된 이미지를 프록시합니다.

**파라미터**

- `path` (path): NAS 이미지 경로 (예: `img/SKU001.jpg`)
- `width` (query): 리사이즈 너비 픽셀 (선택, 1~2000)

**응답 (200)**
이미지 바이너리 데이터

**예시**

```
GET /api/image/img/SKU001.jpg?width=300
```

---

## 박스 조회

### GET /api/box/{qr_code}

QR 코드로 박스 정보를 조회합니다.

**파라미터**

- `qr_code` (path): QR 코드 문자열

**응답 (200)**

```json
{
  "qr_code": "BOX001",
  "box_name": "스피커 믹스팩",
  "product_master_name": "프리미엄 스피커",
  "product_master_image": "img/MASTER001.jpg",
  "location": "B-02-03",
  "members": [
    {
      "sku_id": "SKU101",
      "sku_name": "검은색 스피커",
      "barcode": "8801111111111",
      "location": "B-02-03"
    },
    {
      "sku_id": "SKU102",
      "sku_name": "흰색 스피커",
      "barcode": "8801111111112",
      "location": "B-02-03"
    }
  ],
  "coupang_url": "https://www.coupang.com/vp/products/...",
  "naver_url": null,
  "url_1688": null,
  "flow_url": null
}
```

**에러**

- `404 Not Found`: QR 코드 미등록

---

## 이력 조회

### GET /api/history

작업 이력을 조회합니다.

**파라미터**

- `type` (query): 작업 타입 필터 (선택, 예: `print`, `cart`)
- `limit` (query): 이력 개수 (기본값: 50, 1~200)

**응답 (200)**

```json
[
  {
    "id": 1,
    "action_type": "print",
    "barcode": "8801234567890",
    "sku_id": "SKU001",
    "product_name": "삼성 27인치 모니터",
    "quantity": 10,
    "requested_by": "PDA",
    "created_at": "2026-04-01T10:35:00Z"
  },
  {
    "id": 2,
    "action_type": "cart",
    "barcode": "8801234567890",
    "sku_id": "SKU001",
    "product_name": "삼성 27인치 모니터",
    "quantity": 5,
    "requested_by": "PDA",
    "created_at": "2026-04-01T10:30:00Z"
  }
]
```

---

## 즐겨찾기

### POST /api/favorite

상품을 즐겨찾기에 추가합니다.

**요청 Body**

```json
{
  "sku_id": "SKU001",
  "product_name": "삼성 27인치 모니터",
  "barcode": "8801234567890"
}
```

**응답 (200)**

```json
{
  "status": "ok"
}
```

### DELETE /api/favorite/{sku_id}

즐겨찾기에서 제거합니다.

**파라미터**

- `sku_id` (path): SKU ID

**응답 (200)**

```json
{
  "status": "ok"
}
```

**에러**

- `404 Not Found`: 즐겨찾기 미등록

### GET /api/favorites

즐겨찾기 목록을 조회합니다.

**응답 (200)**

```json
[
  {
    "sku_id": "SKU001",
    "product_name": "삼성 27인치 모니터",
    "barcode": "8801234567890",
    "created_at": "2026-03-31T15:20:00Z"
  }
]
```

---

## 최근 스캔 이력

### GET /api/recent

최근 스캔한 상품 목록을 조회합니다.

**파라미터**

- `limit` (query): 조회 개수 (기본값: 20, 1~100)

**응답 (200)**

```json
[
  {
    "id": 1,
    "barcode": "8801234567890",
    "sku_id": "SKU001",
    "product_name": "삼성 27인치 모니터",
    "scanned_at": "2026-04-01T10:35:00Z"
  }
]
```

---

## 도면 정규화 API

### 구역 (Zone)

#### GET /api/zones

전체 구역 목록을 조회합니다.

**응답 (200)**

```json
[
  {
    "id": 1,
    "code": "A",
    "name": "1층 A존",
    "rows": 3,
    "cols": 9,
    "sort_order": 0,
    "cell_count": 27
  }
]
```

#### POST /api/zones

구역을 추가합니다.

**요청 Body**

```json
{
  "code": "B",
  "name": "1층 B존",
  "rows": 3,
  "cols": 6
}
```

**응답 (200)**

```json
{
  "id": 2,
  "code": "B",
  "name": "1층 B존",
  "rows": 3,
  "cols": 6,
  "sort_order": 1
}
```

**에러**

- `400 Bad Request`: `code` 또는 `name` 누락

#### PATCH /api/zones/{zone_id}

구역 정보를 수정합니다. 행/열 변경 시 셀이 자동으로 추가/삭제됩니다.

**파라미터**

- `zone_id` (path): 구역 ID

**요청 Body** (수정할 필드만 포함)

```json
{
  "name": "2층 A존",
  "rows": 4,
  "cols": 9
}
```

**응답 (200)**

```json
{
  "id": 1,
  "code": "A",
  "name": "2층 A존",
  "rows": 4,
  "cols": 9,
  "sort_order": 0
}
```

**에러**

- `404 Not Found`: 구역 미존재

#### DELETE /api/zones/{zone_id}

구역과 하위 셀/층/상품을 모두 삭제합니다.

**파라미터**

- `zone_id` (path): 구역 ID

**응답 (200)**

```json
{
  "status": "ok"
}
```

**에러**

- `404 Not Found`: 구역 미존재

---

### 셀 (Cell)

#### GET /api/zones/{zone_id}/cells

구역 내 전체 셀 목록을 조회합니다. 각 셀의 층과 상품 정보를 포함합니다.

**파라미터**

- `zone_id` (path): 구역 ID

**응답 (200)**

```json
[
  {
    "id": 1,
    "row": 1,
    "col": 1,
    "label": "A-1",
    "status": "occupied",
    "bg_color": "",
    "levels": [
      {
        "id": 1,
        "index": 0,
        "label": "1층",
        "products": [
          {
            "id": 1,
            "product_master_id": 5,
            "photo": "/static/photos/lp_1_abc12345.jpg",
            "memo": "",
            "sort_order": 0,
            "master_name": "삼성 27인치 모니터"
          }
        ]
      }
    ]
  }
]
```

#### GET /api/cells/{cell_id}

셀 상세 정보를 조회합니다. 소속 구역 정보를 포함합니다.

**파라미터**

- `cell_id` (path): 셀 ID

**응답 (200)**

```json
{
  "id": 1,
  "row": 1,
  "col": 1,
  "label": "A-1",
  "status": "occupied",
  "bg_color": "",
  "zone": {
    "id": 1,
    "code": "A",
    "name": "1층 A존"
  },
  "levels": [
    {
      "id": 1,
      "index": 0,
      "label": "1층",
      "products": []
    }
  ]
}
```

**에러**

- `404 Not Found`: 셀 미존재

#### PATCH /api/cells/{cell_id}

셀 정보를 수정합니다.

**파라미터**

- `cell_id` (path): 셀 ID

**요청 Body** (수정할 필드만 포함, `label` / `status` / `bg_color`)

```json
{
  "label": "A-1",
  "status": "occupied",
  "bg_color": "#FF6B35"
}
```

**응답 (200)**

셀 상세 응답과 동일 (GET /api/cells/{cell_id} 참고)

**에러**

- `404 Not Found`: 셀 미존재

---

### 층 (Level)

#### POST /api/cells/{cell_id}/levels

셀에 층을 추가합니다.

**파라미터**

- `cell_id` (path): 셀 ID

**요청 Body**

```json
{
  "label": "2층"
}
```

**응답 (200)**

```json
{
  "id": 3,
  "index": 1,
  "label": "2층",
  "products": []
}
```

**에러**

- `404 Not Found`: 셀 미존재

#### DELETE /api/levels/{level_id}

층과 해당 층의 상품을 모두 삭제합니다.

**파라미터**

- `level_id` (path): 층 ID

**응답 (200)**

```json
{
  "status": "ok"
}
```

**에러**

- `404 Not Found`: 층 미존재

---

### 층별 상품 (Level Product)

#### POST /api/levels/{level_id}/products

층에 상품을 등록합니다. 등록 시 `product.location`이 자동으로 동기화됩니다.

**파라미터**

- `level_id` (path): 층 ID

**요청 Body** (`barcode`, `sku_id`, `product_master_id` 중 하나로 상품 지정)

```json
{
  "sku_id": "SKU001",
  "memo": "정상 보관"
}
```

**응답 (200)**

```json
{
  "id": 10,
  "product_master_id": 5,
  "photo": "",
  "memo": "정상 보관",
  "sort_order": 0,
  "master_name": "삼성 27인치 모니터"
}
```

**에러**

- `404 Not Found`: 층 미존재

#### DELETE /api/level-products/{id}

층에서 상품을 제거합니다. 다른 셀에도 없으면 `product.location`을 NULL로 초기화합니다.

**파라미터**

- `id` (path): 층별 상품 ID

**응답 (200)**

```json
{
  "status": "ok"
}
```

**에러**

- `404 Not Found`: 상품 미존재

#### POST /api/level-products/{id}/photo

층별 상품 사진을 업로드합니다. 기존 사진은 자동 삭제됩니다.

**파라미터**

- `id` (path): 층별 상품 ID
- `file` (form): 이미지 파일 (jpg, jpeg, png, webp, 최대 10MB)

**응답 (200)**

```json
{
  "status": "ok",
  "photo": "/static/photos/lp_10_abc12345.jpg"
}
```

**에러**

- `400 Bad Request`: 파일 형식 오류 또는 크기 초과
- `404 Not Found`: 상품 미존재

#### DELETE /api/level-products/{id}/photo

층별 상품 사진을 삭제합니다.

**파라미터**

- `id` (path): 층별 상품 ID

**응답 (200)**

```json
{
  "status": "ok"
}
```

**에러**

- `404 Not Found`: 상품 미존재

---

### 위치 검색

#### GET /api/product-location/{sku_id}

SKU가 배치된 셀 위치를 조회합니다.

**파라미터**

- `sku_id` (path): SKU ID

**응답 (200)**

```json
{
  "zone_code": "A",
  "zone_name": "1층 A존",
  "row": 1,
  "col": 3,
  "cell_id": 3,
  "location": "A구역 A-3"
}
```

**에러**

- `404 Not Found`: 해당 SKU가 도면에 배치되지 않음

---

## 창고 맵 (선반 배치)

### GET /api/map-layout

저장소 전체 맵 레이아웃을 조회합니다.

**응답 (200)**

```json
{
  "zones": [
    {
      "code": "A",
      "name": "1층 A존",
      "rows": 5,
      "cols": 10
    }
  ],
  "cells": {
    "A-1-1": {
      "label": "A1-1",
      "levels": [
        {
          "label": "1층",
          "photo": "/static/photos/A_1_1_L0_abc123.jpg",
          "itemLabel": "모니터",
          "sku": "SKU001"
        }
      ]
    }
  }
}
```

### POST /api/map-layout

맵 레이아웃을 저장합니다.

**요청 Body**

```json
{
  "zones": [
    {
      "code": "A",
      "name": "1층 A존",
      "rows": 5,
      "cols": 10
    }
  ],
  "cells": {}
}
```

**응답 (200)**

```json
{
  "status": "ok",
  "message": "저장 완료"
}
```

### PATCH /api/map-layout/cell/{cell_key}

특정 셀 정보를 수정합니다.

**파라미터**

- `cell_key` (path): 셀 키 (형식: `A-1-1`)

**요청 Body**

```json
{
  "label": "셀 레이블",
  "levels": [
    {
      "label": "1층",
      "photo": "",
      "itemLabel": "모니터",
      "sku": "SKU001"
    }
  ]
}
```

**응답 (200)**

```json
{
  "status": "ok"
}
```

**에러**

- `400 Bad Request`: 셀 키 형식 오류 (예: `XX-1-1`, `A-0-1`)

### POST /api/map-layout/cell/{cell_key}/photo

셀의 기본 사진을 업로드합니다.

**파라미터**

- `cell_key` (path): 셀 키 (형식: `A-1-1`)
- `file` (form): 이미지 파일 (jpg, jpeg, png, webp, 최대 10MB)

**응답 (200)**

```json
{
  "status": "ok",
  "photo_url": "/static/photos/A_1_1_L0_abc123.jpg"
}
```

**에러**

- `400 Bad Request`: 파일 형식 오류 또는 파일 크기 초과
- `400 Bad Request`: 셀 키 형식 오류

### DELETE /api/map-layout/cell/{cell_key}/photo

셀의 기본 사진을 삭제합니다.

**파라미터**

- `cell_key` (path): 셀 키

**응답 (200)**

```json
{
  "status": "ok"
}
```

### POST /api/map-layout/cell/{cell_key}/level/{level_index}/photo

셀의 특정 레벨 사진을 업로드합니다.

**파라미터**

- `cell_key` (path): 셀 키
- `level_index` (path): 레벨 인덱스 (0부터 시작)
- `file` (form): 이미지 파일

**응답 (200)**

```json
{
  "status": "ok",
  "photo_url": "/static/photos/A_1_1_L1_def456.jpg"
}
```

**에러**

- `400 Bad Request`: `level_index` < 0

### DELETE /api/map-layout/cell/{cell_key}/level/{level_index}/photo

셀의 특정 레벨 사진을 삭제합니다.

**파라미터**

- `cell_key` (path): 셀 키
- `level_index` (path): 레벨 인덱스

**응답 (200)**

```json
{
  "status": "ok"
}
```

---

## 선반 관리

### GET /api/shelves/{floor}/{zone}

특정 층의 존별 선반 목록을 조회합니다.

**파라미터**

- `floor` (path): 층 번호
- `zone` (path): 존 코드 (예: `A`, `B`)

**응답 (200)**

```json
{
  "floor": 1,
  "zone": "A",
  "shelves": [
    {
      "id": 1,
      "floor": 1,
      "zone": "A",
      "shelf_number": 1,
      "label": "선반-A1",
      "photo_path": null,
      "photo_url": null,
      "cell_key": "A-1-1"
    }
  ]
}
```

### PATCH /api/shelf/{shelf_id}

선반 레이블을 수정합니다.

**파라미터**

- `shelf_id` (path): 선반 ID

**요청 Body**

```json
{
  "label": "새 레이블"
}
```

**응답 (200)**

```json
{
  "id": 1,
  "label": "새 레이블"
}
```

**에러**

- `404 Not Found`: 선반 미존재

### DELETE /api/shelf/{shelf_id}/label

선반 레이블을 삭제합니다.

**파라미터**

- `shelf_id` (path): 선반 ID

**응답 (200)**

```json
{
  "status": "ok"
}
```

**에러**

- `404 Not Found`: 선반 미존재

### POST /api/shelf/{shelf_id}/photo

선반 사진을 업로드합니다.

**파라미터**

- `shelf_id` (path): 선반 ID
- `file` (form): 이미지 파일

**응답 (200)**

```json
{
  "id": 1,
  "shelf_id": 1,
  "file_path": "/nas/shelf_photos/1-A-01_20260401120000.jpg",
  "photo_url": "/api/image//nas/shelf_photos/1-A-01_20260401120000.jpg"
}
```

### DELETE /api/shelf/photo/{photo_id}

선반 사진을 삭제합니다.

**파라미터**

- `photo_id` (path): 사진 ID

**응답 (200)**

```json
{
  "status": "ok"
}
```

**에러**

- `404 Not Found`: 사진 미존재

---

## 상품 위치 수정

### PATCH /api/product/{sku_id}/location

상품의 위치 정보를 수정합니다.

**파라미터**

- `sku_id` (path): SKU ID

**요청 Body**

```json
{
  "location": "A-1-1"
}
```

**응답 (200)**

```json
{
  "status": "ok",
  "location": "A-1-1"
}
```

**에러**

- `404 Not Found`: 상품 미존재

---

## 앱 버전 및 업데이트

### GET /api/app-version

현재 APK 버전 정보 및 다운로드 URL을 조회합니다.

**응답 (200)**

```json
{
  "versionCode": 57,
  "versionName": "4.3.0",
  "downloadUrl": "/apk/app-live-debug.apk",
  "releaseNotes": "최신 버전",
  "forceUpdate": false
}
```

---

## URL 가져오기

### POST /api/import/urls

파일에서 구매 URL을 가져옵니다.

**파라미터**

- `file_path` (query): 파일 경로

**응답 (200)**

```json
{
  "status": "ok",
  "imported": 150
}
```

**에러**

- `400 Bad Request`: 파일 미존재 또는 형식 오류

---

## 서버 상태

### GET /api/status

서버 상태 정보를 조회합니다.

**응답 (200)**

```json
{
  "server_uptime_seconds": 3600,
  "total_products": 10000,
  "total_stock_value": 150000000,
  "database_size_mb": 25.5,
  "last_sync": "2026-04-01T10:35:00Z",
  "sync_status": "success"
}
```

### GET /health

서버 헬스 체크입니다.

**응답 (200)**

```json
{
  "status": "ok"
}
```

---

## 일반 에러 응답

모든 에러는 다음 형식을 따릅니다:

```json
{
  "detail": "에러 메시지"
}
```

**HTTP 상태 코드**

- `400 Bad Request`: 요청 파라미터 오류
- `404 Not Found`: 리소스 미존재
- `500 Internal Server Error`: 서버 오류
