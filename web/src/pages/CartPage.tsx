import { useState, useEffect } from "react";
import { api } from "../api/client";

interface CartItem {
  id: number;
  sku_id: string;
  barcode: string;
  product_name: string;
  quantity: number;
  added_by: string;
  created_at: string;
}

export default function CartPage() {
  const [items, setItems] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);

  const fetchItems = async () => {
    setLoading(true);
    try {
      const data = await api<CartItem[]>("/cart/items");
      setItems(data);
    } catch {
      alert("장바구니 불러오기 실패");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchItems();
  }, []);

  const handleQtyChange = async (id: number, quantity: number) => {
    if (quantity < 1) return;
    try {
      await api(`/cart/${id}`, {
        method: "PATCH",
        body: JSON.stringify({ quantity }),
      });
      setItems((prev) =>
        prev.map((item) => (item.id === id ? { ...item, quantity } : item)),
      );
    } catch {
      alert("수량 변경 실패");
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await api(`/cart/${id}`, { method: "DELETE" });
      setItems((prev) => prev.filter((item) => item.id !== id));
    } catch {
      alert("삭제 실패");
    }
  };

  const handleDeleteAll = async () => {
    if (!confirm("장바구니를 전부 비우시겠습니까?")) return;
    try {
      await api("/cart/all", { method: "DELETE" });
      setItems([]);
    } catch {
      alert("전체 삭제 실패");
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      await api("/cart/export", { method: "POST" });
      alert("Google Sheets로 내보내기 완료");
    } catch {
      alert("내보내기 실패");
    } finally {
      setExporting(false);
    }
  };

  return (
    <div>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 20,
          flexWrap: "wrap",
          gap: 8,
        }}
      >
        <h2 style={{ fontSize: 18, fontWeight: 700 }}>장바구니</h2>
        <div style={{ display: "flex", gap: 8 }}>
          <button
            onClick={handleExport}
            disabled={exporting || items.length === 0}
            style={btnSecStyle}
          >
            {exporting ? "내보내는 중..." : "Google Sheets 내보내기"}
          </button>
          <button
            onClick={handleDeleteAll}
            disabled={items.length === 0}
            style={btnDangerStyle}
          >
            전체 삭제
          </button>
        </div>
      </div>

      {loading ? (
        <div className="catalog-loading">불러오는 중...</div>
      ) : items.length === 0 ? (
        <div className="catalog-empty">장바구니가 비어 있습니다</div>
      ) : (
        <table style={tableStyle}>
          <thead>
            <tr
              style={{
                background: "var(--bg)",
                borderBottom: "2px solid var(--border-light)",
              }}
            >
              <th style={thStyle}>상품명</th>
              <th style={thStyle}>SKU</th>
              <th style={thStyle}>바코드</th>
              <th style={thStyle}>수량</th>
              <th style={thStyle}>추가자</th>
              <th style={thStyle}>날짜</th>
              <th style={thStyle}></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr
                key={item.id}
                style={{ borderBottom: "1px solid var(--border-light)" }}
              >
                <td style={tdStyle}>{item.product_name}</td>
                <td
                  style={{
                    ...tdStyle,
                    fontFamily: "var(--mono)",
                    fontSize: 12,
                  }}
                >
                  {item.sku_id}
                </td>
                <td
                  style={{
                    ...tdStyle,
                    fontFamily: "var(--mono)",
                    fontSize: 12,
                  }}
                >
                  {item.barcode}
                </td>
                <td style={{ ...tdStyle, width: 90 }}>
                  <input
                    type="number"
                    min={1}
                    defaultValue={item.quantity}
                    onBlur={(e) => {
                      const v = parseInt(e.target.value, 10);
                      if (!isNaN(v) && v !== item.quantity)
                        handleQtyChange(item.id, v);
                    }}
                    style={{
                      width: 70,
                      padding: "4px 8px",
                      border: "1px solid var(--border)",
                      borderRadius: 4,
                      fontSize: 13,
                      textAlign: "center",
                      fontFamily: "var(--sans)",
                    }}
                  />
                </td>
                <td style={{ ...tdStyle, color: "var(--fg3)", fontSize: 12 }}>
                  {item.added_by}
                </td>
                <td style={{ ...tdStyle, color: "var(--fg3)", fontSize: 12 }}>
                  {new Date(item.created_at).toLocaleDateString("ko-KR")}
                </td>
                <td style={{ ...tdStyle, textAlign: "right" }}>
                  <button
                    onClick={() => handleDelete(item.id)}
                    style={{
                      padding: "4px 10px",
                      background: "transparent",
                      border: "1px solid var(--border)",
                      borderRadius: 4,
                      fontSize: 12,
                      cursor: "pointer",
                      color: "var(--fg3)",
                    }}
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

const tableStyle: React.CSSProperties = {
  width: "100%",
  borderCollapse: "collapse",
  background: "var(--bg2)",
  borderRadius: 8,
  overflow: "hidden",
  border: "1px solid var(--border-light)",
};
const thStyle: React.CSSProperties = {
  padding: "10px 14px",
  textAlign: "left",
  fontSize: 12,
  fontWeight: 600,
  color: "var(--fg2)",
  whiteSpace: "nowrap",
};
const tdStyle: React.CSSProperties = { padding: "10px 14px" };
const btnSecStyle: React.CSSProperties = {
  padding: "8px 16px",
  background: "transparent",
  color: "var(--fg2)",
  border: "1px solid var(--border)",
  borderRadius: 6,
  fontWeight: 500,
  fontSize: 13,
  cursor: "pointer",
};
const btnDangerStyle: React.CSSProperties = {
  padding: "8px 16px",
  background: "#ef4444",
  color: "#fff",
  border: "none",
  borderRadius: 6,
  fontWeight: 600,
  fontSize: 13,
  cursor: "pointer",
};
