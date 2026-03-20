# 파서 명세

## codepath.xlsx

### 파일 형식

- 헤더 행 없음.
- 컬럼 고정: A(바코드), B(Windows 절대경로).
- 행 수: 수만 건 예상. read_only=True 모드로 메모리 절약.

### 경로 변환

B컬럼 값 예시:

```
Z:\물류부\scan\img\8801234567890.jpg
Z:\물류부\scan\real_image\8801234567890_1.jpg
```

변환 규칙: `Z:\물류부\scan\` (대소문자 무관) 이후 부분만 추출 → 백슬래시 → 슬래시.

```python
_PATH_PREFIX_RE = re.compile(r"^.*?\\scan\\", re.IGNORECASE)
relative = str(PurePosixPath(PureWindowsPath(cleaned)))
# 결과: img/8801234567890.jpg
#       real_image/8801234567890_1.jpg
```

`image_type` 판정: 경로에 `real_image` 포함 → `real`, 그 외 → `thumbnail`.

### 파싱 라이브러리

openpyxl (read_only=True, data_only=True).

### DB 적재 동작

1. `barcode` upsert: `ON CONFLICT(barcode) DO UPDATE SET updated_at` (sku_id가 NULL이거나 빈 문자열인 경우만).
   - codepath에는 SKU ID 없음 → barcode 테이블에 sku_id=NULL 상태로 생성.
2. `image` upsert: `ON CONFLICT(barcode, file_path) DO UPDATE SET image_type, updated_at`.
3. B컬럼이 None 또는 `"none"` 문자열인 경우 image 적재 스킵.

### 에러 처리

- 행 단위 try/except: 1행 실패 시 다음 행 계속.
- 로그 출력: 에러 10건까지만 logger.warning.
- parse_log: 파싱 완료 후 1건 INSERT (에러 상세 최대 50건 JSON 저장).

---

## sku*download.xlsx (coupangmd00_sku_download*\*.xlsx)

### 파일 형식

- 1행: 헤더.
- 쿠팡 xlsx 특성상 셀 타입이 `inlineStr` → openpyxl read_only 모드 미지원.
- lxml으로 xl/worksheets/sheet1.xml 직접 파싱.

### lxml 파싱 방식

```python
z = zipfile.ZipFile(file_path)
xml_bytes = z.read("xl/worksheets/sheet1.xml")
root = etree.fromstring(xml_bytes)
NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
rows = root.findall(".//s:row", NS)
```

셀값 추출 우선순위:

1. `<v>` 태그 (숫자/날짜 타입)
2. `<is><t>` 태그 (inlineStr 타입)

### 헤더 자동 매칭

쿠팡이 헤더명을 변경해도 대응하기 위해 `SequenceMatcher` 유사도 기반 매칭.

**임계값: 0.5 이상인 헤더 중 최고 유사도 컬럼 선택.**

필수 컬럼:

| 내부 필드    | 매칭 후보 키워드                            |
| ------------ | ------------------------------------------- |
| sku_id       | sku id, skuid, sku_id, 상품id               |
| product_name | 상품명, 상품 명, 제품명, 품명, product name |
| barcode      | 바코드, barcode, bar code, ean              |

옵션 컬럼:

| 내부 필드 | 매칭 후보 키워드                 |
| --------- | -------------------------------- |
| category  | 카테고리, 업종, 분류, category   |
| brand     | 브랜드, 제조사, brand            |
| moq       | 최소구매수량, moq, 최소 구매수량 |
| weight    | 중량, 무게, weight               |

필수 컬럼 매칭 실패 시 → 에러 로그 후 파싱 중단 (해당 파일 전체 스킵).

옵션 컬럼 (`category`, `brand` 제외) → `product.extra` JSON에 저장.

### DB 적재 동작

1. `product` upsert:
   - 존재 시 UPDATE (product_name, category, brand, extra, updated_at).
   - 미존재 시 INSERT.
2. `barcode` upsert: `ON CONFLICT(barcode) DO UPDATE SET sku_id, updated_at`.
   - 바코드가 빈 문자열 또는 `"none"` 문자열인 경우 스킵.

### 에러 처리

codepath_parser와 동일:

- 행 단위 try/except, 에러 10건까지 logger.warning.
- parse_log 1건 INSERT.

---

## 적재 순서 권장

```
1. sku_download.xlsx 먼저 적재 → product에 실 SKU ID + 상품정보 저장
2. codepath.xlsx 적재 → barcode + image 연결
```

역순(codepath → sku_download) 적재 시: codepath에서 생성된 placeholder product 행(sku_id = 바코드값)이 sku_download의 실 SKU ID와 연결되지 않음. 현재 코드는 이 한계 존재.
