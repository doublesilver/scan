from pydantic import BaseModel, Field


class ImageItem(BaseModel):
    file_path: str
    image_type: str


class ScanResponse(BaseModel):
    sku_id: str
    product_name: str
    category: str | None = None
    brand: str | None = None
    barcodes: list[str]
    images: list[ImageItem]
    quantity: int | None = None
    coupang_url: str | None = None
    location: str | None = None


class StockUpdate(BaseModel):
    quantity: int = Field(ge=0, le=999999)
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
    barcode: str | None = None
    thumbnail: str | None = None


class SearchResponse(BaseModel):
    total: int
    items: list[SearchItem]


class PrintRequest(BaseModel):
    barcode: str
    sku_id: str
    product_name: str
    quantity: int = Field(ge=1, le=100)


class CartRequest(BaseModel):
    barcode: str
    sku_id: str
    product_name: str
    quantity: int = Field(ge=1, le=999)


class FamilyMember(BaseModel):
    sku_id: str
    sku_name: str
    barcode: str | None = None
    location: str | None = None


class BoxResponse(BaseModel):
    qr_code: str
    box_name: str
    product_master_name: str
    product_master_image: str | None = None
    location: str | None = None
    members: list[FamilyMember]
    coupang_url: str | None = None
    naver_url: str | None = None
    url_1688: str | None = None
    flow_url: str | None = None


class HistoryItem(BaseModel):
    id: int
    action_type: str
    barcode: str
    sku_id: str
    product_name: str
    quantity: int
    requested_by: str
    created_at: str


class FavoriteItem(BaseModel):
    sku_id: str
    product_name: str
    barcode: str | None = None
    created_at: str


class FavoriteRequest(BaseModel):
    sku_id: str
    product_name: str
    barcode: str | None = None


class RecentScanItem(BaseModel):
    id: int
    barcode: str
    sku_id: str
    product_name: str
    scanned_at: str


class ShelfItem(BaseModel):
    id: int
    floor: int
    zone: str
    shelf_number: int
    label: str | None = None
    photo_path: str | None = None
    photo_url: str | None = None
    cell_key: str | None = None


class ShelfListResponse(BaseModel):
    floor: int
    zone: str
    shelves: list[ShelfItem]


class ShelfUpdate(BaseModel):
    label: str
