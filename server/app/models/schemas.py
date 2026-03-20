from pydantic import BaseModel


class ImageItem(BaseModel):
    file_path: str
    image_type: str


class ScanResponse(BaseModel):
    sku_id: str
    product_name: str
    category: str
    brand: str
    barcodes: list[str]
    images: list[ImageItem]
    quantity: int | None = None


class StockUpdate(BaseModel):
    quantity: int
    memo: str = ""
    updated_by: str = "PDA"


class StockResponse(BaseModel):
    sku_id: str
    quantity: int
    memo: str
    updated_by: str
    updated_at: str


class StockLogItem(BaseModel):
    before_qty: int
    after_qty: int
    memo: str
    updated_by: str
    created_at: str


class SearchItem(BaseModel):
    sku_id: str
    product_name: str
    category: str
    brand: str


class SearchResponse(BaseModel):
    total: int
    items: list[SearchItem]
