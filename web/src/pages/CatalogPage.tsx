import { useState, useEffect, useCallback } from "react";
import { api, imageUrl } from "../api/client";
import FilterSidebar from "../components/FilterSidebar";
import ProductCard from "../components/ProductCard";
import type { Product, ProductsResponse } from "../types";

export default function CatalogPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(40);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("");
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [imageFilter, setImageFilter] = useState("all");
  const [locationFilter, setLocationFilter] = useState("all");

  const totalPages = Math.ceil(total / size);

  const fetchProducts = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        size: String(size),
      });
      if (query) params.set("q", query);
      if (category) params.set("category", category);
      const data = await api<ProductsResponse>(`/products?${params}`);
      setProducts(data.items);
      setTotal(data.total);

      const cats = [
        ...new Set(data.items.map((p) => p.category).filter(Boolean)),
      ];
      setCategories((prev) => {
        const merged = new Set([...prev, ...cats]);
        return [...merged].sort();
      });
    } catch (err) {
      console.error("Failed to fetch products:", err);
    } finally {
      setLoading(false);
    }
  }, [page, size, query, category]);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
  };

  const handleCategorySelect = (cat: string) => {
    setCategory(cat);
    setPage(1);
  };

  const filteredProducts = products.filter((p) => {
    if (imageFilter === "has" && !p.thumbnail) return false;
    if (imageFilter === "none" && p.thumbnail) return false;
    if (locationFilter === "has" && !p.location) return false;
    if (locationFilter === "none" && p.location) return false;
    return true;
  });

  return (
    <div className="catalog-layout">
      <FilterSidebar
        categories={categories}
        selectedCategory={category}
        onSelectCategory={handleCategorySelect}
        imageFilter={imageFilter}
        onImageFilter={setImageFilter}
        locationFilter={locationFilter}
        onLocationFilter={setLocationFilter}
      />
      <div className="catalog-main">
        <div className="catalog-toolbar">
          <form onSubmit={handleSearch} className="catalog-search">
            <input
              type="text"
              placeholder="상품명, SKU, 바코드 검색..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <button type="submit">검색</button>
          </form>
          <div className="catalog-meta">
            <span className="catalog-count">
              {total.toLocaleString()}개 상품
            </span>
            <select
              value={size}
              onChange={(e) => {
                setSize(Number(e.target.value));
                setPage(1);
              }}
            >
              <option value={20}>20개</option>
              <option value={40}>40개</option>
              <option value={80}>80개</option>
              <option value={100}>100개</option>
            </select>
          </div>
        </div>

        {loading ? (
          <div className="catalog-loading">불러오는 중...</div>
        ) : filteredProducts.length === 0 ? (
          <div className="catalog-empty">상품이 없습니다</div>
        ) : (
          <div className="catalog-grid">
            {filteredProducts.map((p) => (
              <ProductCard
                key={p.sku_id}
                product={p}
                onClick={setSelectedProduct}
              />
            ))}
          </div>
        )}

        {totalPages > 1 && (
          <div className="catalog-pagination">
            <button disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
              이전
            </button>
            <span>
              {page} / {totalPages}
            </span>
            <button
              disabled={page >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              다음
            </button>
          </div>
        )}
      </div>

      {selectedProduct && (
        <div
          className="modal-overlay"
          onClick={(e) =>
            e.target === e.currentTarget && setSelectedProduct(null)
          }
        >
          <div className="modal" style={{ maxWidth: 480 }}>
            {selectedProduct.thumbnail && (
              <img
                src={imageUrl(selectedProduct.thumbnail)}
                alt=""
                style={{
                  width: "100%",
                  borderRadius: "8px 8px 0 0",
                  maxHeight: 300,
                  objectFit: "cover",
                }}
              />
            )}
            <div style={{ padding: "16px 20px" }}>
              <h2 style={{ margin: "0 0 8px", fontSize: 18 }}>
                {selectedProduct.product_name}
              </h2>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 8,
                  fontSize: 13,
                  color: "#6b7280",
                  marginBottom: 16,
                }}
              >
                <div>
                  <strong>SKU:</strong> {selectedProduct.sku_id}
                </div>
                <div>
                  <strong>카테고리:</strong> {selectedProduct.category || "-"}
                </div>
                <div>
                  <strong>브랜드:</strong> {selectedProduct.brand || "-"}
                </div>
                <div>
                  <strong>위치:</strong> {selectedProduct.location || "-"}
                </div>
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <button
                  className="btn-primary"
                  onClick={() => {
                    /* TODO: edit */
                  }}
                >
                  편집
                </button>
                <button
                  className="btn-secondary"
                  onClick={() => setSelectedProduct(null)}
                >
                  닫기
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
