"""문서(DATABASE.md, API_SPEC.md, PARSER_SPEC.md)와 코드 정합성 검증 테스트."""

import re
from pathlib import Path

import pytest

BASE_DIR = Path(__file__).resolve().parent.parent
DOCS_DIR = BASE_DIR / "docs"


def _read(filename: str) -> str:
    return (DOCS_DIR / filename).read_text(encoding="utf-8")


def _read_py(rel_path: str) -> str:
    return (BASE_DIR / rel_path).read_text(encoding="utf-8")


# ---------------------------------------------------------------------------
# DATABASE.md vs schema.py
# ---------------------------------------------------------------------------

class TestDatabaseDoc:
    @pytest.fixture(autouse=True)
    def setup(self):
        self.doc = _read("DATABASE.md")
        self.schema = _read_py("app/db/schema.py")

    def _doc_table_columns(self, table_name: str) -> set[str]:
        pattern = rf"### {table_name}\s*\n\n\|[^\n]+\n\|[-| ]+\n((?:\|[^\n]+\n)+)"
        m = re.search(pattern, self.doc)
        assert m, f"DATABASE.md에서 {table_name} 테이블 정의를 찾을 수 없음"
        columns = set()
        for line in m.group(1).strip().split("\n"):
            col = line.split("|")[1].strip()
            if col:
                columns.add(col)
        return columns

    def _schema_table_columns(self, table_name: str) -> set[str]:
        pattern = rf"CREATE TABLE IF NOT EXISTS {table_name}\s*\((.*?)\);"
        m = re.search(pattern, self.schema, re.DOTALL)
        assert m, f"schema.py에서 {table_name} 테이블을 찾을 수 없음"
        columns = set()
        for line in m.group(1).strip().split("\n"):
            line = line.strip().rstrip(",")
            if not line or line.startswith("FOREIGN") or line.startswith("UNIQUE"):
                continue
            col = line.split()[0]
            if col:
                columns.add(col)
        return columns

    def test_product_columns_match(self):
        doc_cols = self._doc_table_columns("product")
        schema_cols = self._schema_table_columns("product")
        assert doc_cols == schema_cols, f"product 불일치: 문서={doc_cols - schema_cols}, 코드={schema_cols - doc_cols}"

    def test_barcode_columns_match(self):
        doc_cols = self._doc_table_columns("barcode")
        schema_cols = self._schema_table_columns("barcode")
        assert doc_cols == schema_cols, f"barcode 불일치: 문서={doc_cols - schema_cols}, 코드={schema_cols - doc_cols}"

    def test_image_columns_match(self):
        doc_cols = self._doc_table_columns("image")
        schema_cols = self._schema_table_columns("image")
        assert doc_cols == schema_cols, f"image 불일치: 문서={doc_cols - schema_cols}, 코드={schema_cols - doc_cols}"

    def test_image_has_barcode_column(self):
        schema_cols = self._schema_table_columns("image")
        assert "barcode" in schema_cols, "schema.py image 테이블에 barcode 컬럼 필요"
        doc_cols = self._doc_table_columns("image")
        assert "barcode" in doc_cols, "DATABASE.md image 테이블에 barcode 컬럼이 기술되어야 함"

    def test_parse_log_columns_match(self):
        doc_cols = self._doc_table_columns("parse_log")
        schema_cols = self._schema_table_columns("parse_log")
        assert doc_cols == schema_cols, f"parse_log 불일치: 문서={doc_cols - schema_cols}, 코드={schema_cols - doc_cols}"

    def test_barcode_unique_constraint(self):
        doc = self.doc
        schema = self.schema
        schema_has_barcode_unique = bool(re.search(r"barcode\s+TEXT\s+NOT NULL\s+UNIQUE", schema))
        doc_has_barcode_sku_unique = bool(re.search(r"UNIQUE\s*\(barcode,\s*sku_id\)", doc))
        doc_has_barcode_unique_only = bool(re.search(r"barcode.*UNIQUE", doc, re.IGNORECASE)) and not doc_has_barcode_sku_unique
        if schema_has_barcode_unique:
            assert not doc_has_barcode_sku_unique, "문서는 UNIQUE(barcode, sku_id)라 기술하지만 코드는 barcode 단독 UNIQUE"

    def test_image_unique_constraint(self):
        schema_unique = re.search(r"UNIQUE\s*\((\w+),\s*(\w+)\)", self.schema[self.schema.find("CREATE TABLE IF NOT EXISTS image"):])
        assert schema_unique, "schema.py image 테이블에 UNIQUE 제약 없음"
        schema_unique_cols = {schema_unique.group(1), schema_unique.group(2)}
        doc_unique = re.search(r"UNIQUE\s*\((\w+),\s*(\w+)\)", self.doc[self.doc.find("### image"):self.doc.find("### parse_log")])
        assert doc_unique, "DATABASE.md image 테이블에 UNIQUE 제약 없음"
        doc_unique_cols = {doc_unique.group(1), doc_unique.group(2)}
        assert schema_unique_cols == doc_unique_cols, f"image UNIQUE 불일치: 문서={doc_unique_cols}, 코드={schema_unique_cols}"


# ---------------------------------------------------------------------------
# API_SPEC.md vs routes.py
# ---------------------------------------------------------------------------

class TestApiSpecDoc:
    @pytest.fixture(autouse=True)
    def setup(self):
        self.doc = _read("API_SPEC.md")
        self.routes = _read_py("app/api/routes.py")

    def test_search_min_length(self):
        code_match = re.search(r"min_length\s*=\s*(\d+)", self.routes)
        assert code_match, "routes.py에서 min_length를 찾을 수 없음"
        code_min = int(code_match.group(1))

        doc_match = re.search(r"최소\s*(\d+)자", self.doc)
        assert doc_match, "API_SPEC.md에서 최소 길이를 찾을 수 없음"
        doc_min = int(doc_match.group(1))

        assert code_min == doc_min, f"검색 최소 길이 불일치: 문서={doc_min}, 코드={code_min}"

    def test_endpoints_exist_in_code(self):
        doc_endpoints = re.findall(r"\|\s*(GET|POST|PUT|DELETE)\s*\|\s*(\S+)\s*\|", self.doc)
        assert len(doc_endpoints) >= 3, "API_SPEC.md에서 엔드포인트를 찾을 수 없음"

        for method, path in doc_endpoints:
            if path == "/health":
                continue
            route_path = path.replace("{barcode}", "{barcode}").replace("{path}", "{path:path}")
            assert route_path.split("/")[-1].split("{")[0] in self.routes or path.split("/")[-1].split("{")[0] in self.routes, \
                f"코드에 {method} {path} 엔드포인트 없음"

    def _extract_pydantic_fields(self, schemas: str, class_name: str) -> set[str]:
        pattern = rf"class {class_name}\(BaseModel\):\s*\n"
        m = re.search(pattern, schemas)
        assert m, f"schemas.py에서 {class_name}을 찾을 수 없음"
        fields = set()
        for line in schemas[m.end():].split("\n"):
            stripped = line.strip()
            if not stripped or stripped.startswith("class "):
                break
            if ":" in stripped:
                field = stripped.split(":")[0].strip()
                if field:
                    fields.add(field)
        return fields

    def test_scan_response_fields(self):
        schemas = _read_py("app/models/schemas.py")

        doc_section = self.doc[self.doc.find("GET /api/scan"):self.doc.find("GET /api/search")]
        doc_json = re.search(r"```json\s*\n\{(.*?)\}\s*\n```", doc_section, re.DOTALL)
        assert doc_json, "API_SPEC.md에서 scan 응답 JSON을 찾을 수 없음"
        doc_fields = set()
        for line in doc_json.group(1).split("\n"):
            line = line.strip()
            if line.startswith('"') and ":" in line:
                m = re.match(r'"(\w+)":', line)
                if m:
                    doc_fields.add(m.group(1))

        code_fields = self._extract_pydantic_fields(schemas, "ScanResponse")
        assert doc_fields == code_fields, f"ScanResponse 필드 불일치: 문서={doc_fields - code_fields}, 코드={code_fields - doc_fields}"

    def test_search_response_fields(self):
        schemas = _read_py("app/models/schemas.py")

        doc_section = self.doc[self.doc.find("GET /api/search"):self.doc.find("GET /api/image")]
        doc_json = re.search(r"```json\s*\n\{(.*?)\}\s*\n```", doc_section, re.DOTALL)
        assert doc_json, "API_SPEC.md에서 search 응답 JSON을 찾을 수 없음"

        items_obj = re.search(r'"items":\s*\[\s*\{(.*?)\}', doc_json.group(1), re.DOTALL)
        assert items_obj, "API_SPEC.md search 응답에서 items 내부 필드를 찾을 수 없음"
        doc_item_fields = set(re.findall(r'"(\w+)":', items_obj.group(1)))

        code_fields = self._extract_pydantic_fields(schemas, "SearchItem")
        assert doc_item_fields == code_fields, f"SearchItem 필드 불일치: 문서={doc_item_fields - code_fields}, 코드={code_fields - doc_item_fields}"


# ---------------------------------------------------------------------------
# PARSER_SPEC.md vs 파서 코드
# ---------------------------------------------------------------------------

class TestParserSpecDoc:
    @pytest.fixture(autouse=True)
    def setup(self):
        self.doc = _read("PARSER_SPEC.md")
        self.codepath = _read_py("app/services/codepath_parser.py")
        self.sku = _read_py("app/services/sku_parser.py")

    def test_codepath_inserts_to_barcode_table(self):
        assert "INSERT INTO barcode" in self.codepath, "codepath 파서가 barcode 테이블에 INSERT해야 함"

    def test_codepath_inserts_to_image_table(self):
        assert "INSERT INTO image" in self.codepath, "codepath 파서가 image 테이블에 INSERT해야 함"

    def test_codepath_image_uses_barcode_column(self):
        m = re.search(r"INSERT INTO image\s*\(([^)]+)\)", self.codepath)
        assert m, "codepath 파서에서 image INSERT를 찾을 수 없음"
        columns = m.group(1)
        assert "barcode" in columns, "codepath 파서의 image INSERT에 barcode 컬럼이 포함되어야 함"

        doc_image_section = self.doc[self.doc.find("### DB 적재 동작"):self.doc.find("### 에러 처리")]
        doc_mentions_barcode_in_image = "image" in doc_image_section.lower() and "barcode" in doc_image_section.lower()
        assert doc_mentions_barcode_in_image, "PARSER_SPEC.md codepath DB 적재 동작에서 image 테이블의 barcode 컬럼 사용을 기술해야 함"

    def test_codepath_product_placeholder_doc_vs_code(self):
        doc_mentions_product_placeholder = "product" in self.doc[self.doc.find("codepath"):self.doc.find("sku_download")].lower() and "placeholder" in self.doc[self.doc.find("codepath"):self.doc.find("sku_download")].lower()
        code_inserts_product = "INSERT INTO product" in self.codepath
        if doc_mentions_product_placeholder and not code_inserts_product:
            pytest.fail("PARSER_SPEC.md는 product placeholder 생성을 기술하지만 코드에는 없음. 문서 수정 필요.")

    def test_sku_required_columns_match_doc(self):
        doc_required_section = self.doc[self.doc.find("필수 컬럼:"):self.doc.find("옵션 컬럼:")]
        doc_required = set(re.findall(r"\|\s*(\w+)\s*\|", doc_required_section))
        doc_required -= {"내부", "매칭"}

        code_required = set(re.findall(r'"(\w+)":\s*\[', self.sku[:self.sku.find("OPTIONAL_COLUMNS")]))
        assert doc_required == code_required, f"SKU 필수 컬럼 불일치: 문서={doc_required - code_required}, 코드={code_required - doc_required}"

    def test_sku_optional_columns_match_doc(self):
        doc_optional_section = self.doc[self.doc.find("옵션 컬럼:"):self.doc.find("필수 컬럼 매칭 실패")]
        doc_optional = set(re.findall(r"\|\s*(\w+)\s*\|", doc_optional_section))
        doc_optional -= {"내부", "매칭"}

        optional_block = self.sku[self.sku.find("OPTIONAL_COLUMNS"):]
        optional_block = optional_block[:optional_block.find("}") + 1]
        code_optional = set(re.findall(r'"(\w+)":\s*\[', optional_block))
        assert doc_optional == code_optional, f"SKU 옵션 컬럼 불일치: 문서={doc_optional - code_optional}, 코드={code_optional - doc_optional}"

    def test_similarity_threshold_match(self):
        doc_match = re.search(r"임계값[:\s]*(\d+\.?\d*)", self.doc)
        assert doc_match, "PARSER_SPEC.md에서 유사도 임계값을 찾을 수 없음"
        doc_threshold = float(doc_match.group(1))

        code_match = re.search(r"best_score\s*>=\s*(\d+\.?\d*)", self.sku)
        assert code_match, "sku_parser.py에서 유사도 임계값을 찾을 수 없음"
        code_threshold = float(code_match.group(1))

        assert doc_threshold == code_threshold, f"유사도 임계값 불일치: 문서={doc_threshold}, 코드={code_threshold}"

    def test_codepath_barcode_on_conflict(self):
        code_conflict = re.search(r"ON CONFLICT\((\w+)\)", self.codepath)
        assert code_conflict, "codepath 파서에서 ON CONFLICT를 찾을 수 없음"
        code_conflict_col = code_conflict.group(1)

        doc_codepath = self.doc[self.doc.find("## codepath"):self.doc.find("## sku_download")]
        doc_conflict = re.search(r"ON CONFLICT\(([^)]+)\)", doc_codepath)
        assert doc_conflict, "PARSER_SPEC.md codepath에서 ON CONFLICT를 찾을 수 없음"
        doc_conflict_cols = doc_conflict.group(1).replace(" ", "")

        assert code_conflict_col == doc_conflict_cols, f"codepath ON CONFLICT 불일치: 문서={doc_conflict_cols}, 코드={code_conflict_col}"
