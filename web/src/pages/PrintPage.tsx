import { useState } from "react";
import { api } from "../api/client";
import type { Product, ProductsResponse } from "../types";

export default function PrintPage() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Product[]>([]);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<Product | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [printing, setPrinting] = useState(false);
  const [printMsg, setPrintMsg] = useState("");

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    setSearching(true);
    setSelected(null);
    setPrintMsg("");
    try {
      const data = await api<ProductsResponse>(
        `/products?page=1&size=20&q=${encodeURIComponent(query)}`,
      );
      setResults(data.items);
    } catch {
      alert("검색 실패");
    } finally {
      setSearching(false);
    }
  };

  const handlePrint = async (isTest: boolean) => {
    if (!selected) return;
    setPrinting(true);
    setPrintMsg("");
    const endpoint = isTest ? "/print/test" : "/print";
    try {
      await api(endpoint, {
        method: "POST",
        body: JSON.stringify({
          barcode: selected.barcode ?? "",
          sku_id: selected.sku_id,
          product_name: selected.product_name,
          quantity,
        }),
      });
      setPrintMsg(
        isTest ? "테스트 인쇄 완료" : `인쇄 완료 (수량: ${quantity})`,
      );
    } catch {
      setPrintMsg("인쇄 실패");
    } finally {
      setPrinting(false);
    }
  };

  return (
    <div style={{ maxWidth: 700 }}>
      <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 20 }}>
        라벨 인쇄
      </h2>

      <form
        onSubmit={handleSearch}
        className="catalog-search"
        style={{ marginBottom: 20, maxWidth: "100%" }}
      >
        <input
          type="text"
          placeholder="상품명, SKU, 바코드로 검색..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button type="submit" disabled={searching}>
          {searching ? "검색 중..." : "검색"}
        </button>
      </form>

      {!selected && results.length > 0 && (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 8,
            marginBottom: 20,
          }}
        >
          {results.map((p) => (
            <div
              key={p.sku_id}
              onClick={() => {
                setSelected(p);
                setResults([]);
                setPrintMsg("");
              }}
              style={{
                background: "var(--bg2)",
                border: "1px solid var(--border-light)",
                borderRadius: 8,
                padding: "12px 16px",
                cursor: "pointer",
                transition: "border-color 0.15s",
              }}
              onMouseEnter={(e) =>
                (e.currentTarget.style.borderColor = "var(--accent)")
              }
              onMouseLeave={(e) =>
                (e.currentTarget.style.borderColor = "var(--border-light)")
              }
            >
              <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 2 }}>
                {p.product_name}
              </div>
              <div
                style={{
                  display: "flex",
                  gap: 12,
                  fontSize: 12,
                  color: "var(--fg3)",
                }}
              >
                <span style={{ fontFamily: "var(--mono)" }}>
                  SKU: {p.sku_id}
                </span>
                {p.barcode && (
                  <span style={{ fontFamily: "var(--mono)" }}>
                    바코드: {p.barcode}
                  </span>
                )}
                {p.category && <span>{p.category}</span>}
              </div>
            </div>
          ))}
        </div>
      )}

      {!selected && !searching && results.length === 0 && query && (
        <div className="catalog-empty">검색 결과가 없습니다</div>
      )}

      {selected && (
        <div
          style={{
            background: "var(--bg2)",
            border: "1px solid var(--border-light)",
            borderRadius: 10,
            padding: 20,
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "flex-start",
              justifyContent: "space-between",
              marginBottom: 16,
            }}
          >
            <div>
              <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 4 }}>
                {selected.product_name}
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: "var(--fg3)",
                  fontFamily: "var(--mono)",
                  marginBottom: 2,
                }}
              >
                SKU: {selected.sku_id}
              </div>
              {selected.barcode && (
                <div
                  style={{
                    fontSize: 12,
                    color: "var(--fg3)",
                    fontFamily: "var(--mono)",
                  }}
                >
                  바코드: {selected.barcode}
                </div>
              )}
            </div>
            <button
              onClick={() => {
                setSelected(null);
                setPrintMsg("");
              }}
              style={{
                background: "transparent",
                border: "none",
                color: "var(--fg3)",
                cursor: "pointer",
                fontSize: 13,
              }}
            >
              변경
            </button>
          </div>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 12,
              marginBottom: 20,
            }}
          >
            <label style={{ fontSize: 13, fontWeight: 600 }}>수량</label>
            <input
              type="number"
              min={1}
              max={100}
              value={quantity}
              onChange={(e) =>
                setQuantity(
                  Math.max(1, Math.min(100, parseInt(e.target.value, 10) || 1)),
                )
              }
              style={{
                width: 80,
                padding: "8px 10px",
                border: "1px solid var(--border)",
                borderRadius: 6,
                fontSize: 14,
                textAlign: "center",
                fontFamily: "var(--sans)",
              }}
            />
          </div>

          <div style={{ display: "flex", gap: 8 }}>
            <button
              onClick={() => handlePrint(false)}
              disabled={printing}
              style={btnPrintStyle}
            >
              {printing ? "인쇄 중..." : "인쇄"}
            </button>
            <button
              onClick={() => handlePrint(true)}
              disabled={printing}
              style={btnTestStyle}
            >
              테스트 인쇄
            </button>
          </div>

          {printMsg && (
            <div
              style={{
                marginTop: 14,
                padding: "10px 14px",
                borderRadius: 6,
                fontSize: 13,
                background: printMsg.includes("실패") ? "#fee2e2" : "#dcfce7",
                color: printMsg.includes("실패") ? "#b91c1c" : "#166534",
              }}
            >
              {printMsg}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

const btnPrintStyle: React.CSSProperties = {
  padding: "10px 24px",
  background: "var(--accent)",
  color: "#fff",
  border: "none",
  borderRadius: 6,
  fontWeight: 700,
  fontSize: 14,
  cursor: "pointer",
};
const btnTestStyle: React.CSSProperties = {
  padding: "10px 20px",
  background: "transparent",
  color: "var(--fg2)",
  border: "1px solid var(--border)",
  borderRadius: 6,
  fontWeight: 500,
  fontSize: 14,
  cursor: "pointer",
};
