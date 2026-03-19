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


class SearchItem(BaseModel):
    sku_id: str
    product_name: str
    category: str
    brand: str


class SearchResponse(BaseModel):
    total: int
    items: list[SearchItem]
