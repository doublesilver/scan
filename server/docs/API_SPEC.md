# API 명세

> 구현 완료.

## 공통

- Base URL: `http://{MINI_PC_IP}:8000`
- Content-Type: `application/json`
- 인코딩: UTF-8
- 내부망 전용

---

## 엔드포인트 목록

| 메서드 | 경로                    | 설명                   |
| ------ | ----------------------- | ---------------------- |
| GET    | /health                 | 서버 상태 확인         |
| GET    | /api/scan/{barcode}     | 바코드로 상품 조회     |
| GET    | /api/search             | 상품명/SKU 텍스트 검색 |
| GET    | /api/stock/{sku_id}     | 재고 조회              |
| PATCH  | /api/stock/{sku_id}     | 재고 수정              |
| GET    | /api/stock/{sku_id}/log | 재고 수정 이력         |
| GET    | /api/image/{path}       | NAS 이미지 프록시      |
| GET    | /api/status             | 서버 상태 대시보드     |

---

## GET /health

서버 동작 확인용.

**응답 200**

```json
{ "status": "ok" }
```

---

## GET /api/scan/{barcode}

EAN-13 바코드로 상품 정보 + 이미지 경로 조회.

**파라미터**

| 위치 | 이름    | 타입   | 필수 | 설명                   |
| ---- | ------- | ------ | ---- | ---------------------- |
| path | barcode | string | Y    | EAN-13 바코드 (13자리) |

**응답 200**

```json
{
  "sku_id": "12345678",
  "product_name": "테스트 상품명",
  "category": "식품",
  "brand": "테스트브랜드",
  "barcodes": ["8801234567890", "8809876543210"],
  "images": [
    { "file_path": "img/8801234567890.jpg", "image_type": "thumbnail" },
    { "file_path": "real_image/8801234567890_1.jpg", "image_type": "real" }
  ],
  "quantity": null,
  "coupang_url": null,
  "location": null,
  "product_master_id": null,
  "product_master_name": null,
  "product_master_location": null
}
```

**응답 404**

```json
{ "detail": "barcode not found" }
```

**성능 목표**: DB 조회 100ms 이내. 전체 응답(PDA 수신까지) 0.3~0.5초.

---

## GET /api/search

상품명 또는 SKU ID 부분 일치 검색.

**파라미터**

| 위치  | 이름  | 타입    | 필수 | 기본값 | 설명                 |
| ----- | ----- | ------- | ---- | ------ | -------------------- |
| query | q     | string  | Y    | -      | 검색어 (최소 1자)    |
| query | limit | integer | N    | 20     | 최대 결과 수 (1~100) |

**응답 200**

```json
{
  "total": 3,
  "items": [
    {
      "sku_id": "12345678",
      "product_name": "테스트 상품명",
      "category": "식품",
      "brand": "테스트브랜드",
      "barcode": "8801234567890",
      "thumbnail": null
    }
  ]
}
```

**응답 422** (검색어 누락/형식 오류)

```json
{ "detail": [{ "loc": ["query", "q"], "msg": "field required" }] }
```

---

## GET /api/image/{path}

NAS WebDAV에서 이미지를 가져와 PDA에 전달하는 프록시.
`{path}` = `img/xxx.jpg` 또는 `real_image/xxx.jpg` (image.file_path 값).

**파라미터**

| 위치  | 이름  | 타입    | 필수 | 설명                              |
| ----- | ----- | ------- | ---- | --------------------------------- |
| path  | path  | string  | Y    | 이미지 상대경로                   |
| query | width | integer | N    | 리사이즈 폭 (px). 미지정 시 원본. |

**응답 200**: `image/jpeg` 바이트 스트림

**응답 404**

```json
{ "detail": "image not found" }
```

---

## GET /api/status

서버 상태 대시보드. DB 건수, 최근 파싱, NAS 연결, 디스크 사용량 등을 반환.

**응답 200**

```json
{
  "server": {
    "uptime": "2시간 30분",
    "version": "1.0.0"
  },
  "database": {
    "products": 11821,
    "barcodes": 11821,
    "images": 11673,
    "stock_entries": 0
  },
  "last_parse": {
    "file": "codepath.xlsx",
    "parsed_at": "2026-03-20 12:00:00",
    "added": 100,
    "updated": 50
  },
  "nas_sync": {
    "last_check": "2026-03-20 12:00:00",
    "status": "connected"
  },
  "disk": {
    "cache_size_mb": 120.5,
    "cache_limit_mb": 500,
    "backup_count": 7
  }
}
```

---

## 에러 코드 정리

| 코드 | 의미           | 발생 상황                       |
| ---- | -------------- | ------------------------------- |
| 200  | 성공           | -                               |
| 404  | 리소스 없음    | 바코드 미등록, 이미지 파일 없음 |
| 422  | 요청 형식 오류 | 필수 파라미터 누락, 타입 불일치 |
| 500  | 서버 내부 오류 | DB 오류, NAS 연결 실패 등       |

---

## 응답 모델 (Pydantic, 구현 완료)

```python
class ImageItem(BaseModel):
    file_path: str
    image_type: str  # "thumbnail" | "real"

class ScanResponse(BaseModel):
    sku_id: str
    product_name: str
    category: str
    brand: str
    barcodes: list[str]
    images: list[ImageItem]
    quantity: int | None = None

class SearchItem(BaseModel):
    sku_id: str
    product_name: str
    category: str
    brand: str
    barcode: str | None = None

class SearchResponse(BaseModel):
    total: int
    items: list[SearchItem]
```
