export interface Product {
  sku_id: string;
  product_name: string;
  category: string;
  brand: string;
  barcode?: string;
  thumbnail?: string;
}

export interface ProductsResponse {
  total: number;
  page: number;
  size: number;
  items: Product[];
}

export interface BoxMember {
  sku_id: string;
  sku_name: string;
  barcode?: string;
  location?: string;
}

export interface BoxImage {
  id: number;
  file_path: string;
  image_type: string;
  sort_order: number;
}

export interface BoxResponse {
  qr_code: string;
  box_name: string;
  product_master_id?: number;
  product_master_name: string;
  product_master_image?: string;
  location?: string;
  members: BoxMember[];
  option_images: BoxImage[];
  sourcing_images: BoxImage[];
}
