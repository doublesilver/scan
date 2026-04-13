import { imageUrl } from "../api/client";
import type { Product } from "../types";

interface ProductCardProps {
  product: Product;
}

export default function ProductCard({ product }: ProductCardProps) {
  return (
    <div className="product-card">
      <div className="product-card-image">
        {product.thumbnail ? (
          <img
            src={imageUrl(product.thumbnail)}
            alt={product.product_name}
            loading="lazy"
          />
        ) : (
          <div className="product-card-placeholder">
            {product.product_name.slice(0, 2)}
          </div>
        )}
      </div>
      <div className="product-card-body">
        <div className="product-card-name">{product.product_name}</div>
        <div className="product-card-sku">{product.sku_id}</div>
        {product.category && (
          <div className="product-card-category">{product.category}</div>
        )}
      </div>
    </div>
  );
}
