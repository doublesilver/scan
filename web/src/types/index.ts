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

// Map types
export interface MapLevel {
  label: string;
  itemLabel?: string;
  sku?: string;
  photo?: string;
}

export interface MapCell {
  label: string;
  status: string;
  levels: MapLevel[];
}

export interface MapZone {
  code: string;
  name: string;
  rows: number;
  cols: number;
}

export interface MapLayout {
  floor: string;
  title: string;
  zones: MapZone[];
  cells: Record<string, MapCell>;
}

// Data management types
export interface ParseLog {
  id: number;
  file_name: string;
  file_type: string;
  total_records: number;
  added: number;
  updated: number;
  skipped: number;
  errors: number;
  parsed_at: string;
}

// Settings types
export interface ServerStatus {
  db_products: number;
  db_barcodes: number;
  db_images: number;
  disk_cache_mb: number;
  [key: string]: unknown;
}

export interface HealthResponse {
  status: string;
}
