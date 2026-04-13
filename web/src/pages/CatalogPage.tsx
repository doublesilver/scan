import { useState, useEffect, useCallback } from "react";
import { api } from "../api/client";
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

  return (
    <div className="catalog-layout">
      <FilterSidebar
        categories={categories}
        selected={category}
        onSelect={handleCategorySelect}
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
        ) : products.length === 0 ? (
          <div className="catalog-empty">상품이 없습니다</div>
        ) : (
          <div className="catalog-grid">
            {products.map((p) => (
              <ProductCard key={p.sku_id} product={p} />
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
    </div>
  );
}
