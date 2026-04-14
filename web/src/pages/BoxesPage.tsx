import { useState, useEffect, useCallback } from "react";
import { api, imageUrl } from "../api/client";
import type { BoxResponse } from "../types";

interface BoxListItem {
  qr_code: string;
  box_name: string;
  product_master_name: string;
  created_at: string;
}

interface BoxListResponse {
  total: number;
  items: BoxListItem[];
}

const PAGE_SIZE = 20;

export default function BoxesPage() {
  const [items, setItems] = useState<BoxListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    qr_code: "",
    box_name: "",
    product_master_name: "",
    location: "",
  });

  const [detail, setDetail] = useState<BoxResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const totalPages = Math.ceil(total / PAGE_SIZE);

  const fetchBoxes = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api<BoxListResponse>(
        `/boxes?page=${page}&size=${PAGE_SIZE}`,
      );
      setItems(data.items);
      setTotal(data.total);
    } catch {
      alert("박스 목록 불러오기 실패");
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchBoxes();
  }, [fetchBoxes]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.qr_code || !form.box_name) return;
    setCreating(true);
    try {
      await api("/box", {
        method: "POST",
        body: JSON.stringify({ ...form, members: [] }),
      });
      setShowCreate(false);
      setForm({
        qr_code: "",
        box_name: "",
        product_master_name: "",
        location: "",
      });
      setPage(1);
      fetchBoxes();
    } catch {
      alert("박스 생성 실패");
    } finally {
      setCreating(false);
    }
  };

  const openDetail = async (qr_code: string) => {
    setDetailLoading(true);
    setDetail(null);
    try {
      const data = await api<BoxResponse>(`/box/${qr_code}`);
      setDetail(data);
    } catch {
      alert("박스 정보 불러오기 실패");
    } finally {
      setDetailLoading(false);
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
        }}
      >
        <h2 style={{ fontSize: 18, fontWeight: 700 }}>외박스 관리</h2>
        <button onClick={() => setShowCreate(true)} style={btnStyle}>
          새 외박스
        </button>
      </div>

      {loading ? (
        <div className="catalog-loading">불러오는 중...</div>
      ) : items.length === 0 ? (
        <div className="catalog-empty">등록된 외박스가 없습니다</div>
      ) : (
        <table style={tableStyle}>
          <thead>
            <tr
              style={{
                background: "var(--bg)",
                borderBottom: "2px solid var(--border-light)",
              }}
            >
              <th style={thStyle}>QR코드</th>
              <th style={thStyle}>박스명</th>
              <th style={thStyle}>마스터</th>
              <th style={thStyle}>생성일</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr
                key={item.qr_code}
                onClick={() => openDetail(item.qr_code)}
                style={{
                  cursor: "pointer",
                  borderBottom: "1px solid var(--border-light)",
                }}
                onMouseEnter={(e) =>
                  (e.currentTarget.style.background = "var(--bg)")
                }
                onMouseLeave={(e) => (e.currentTarget.style.background = "")}
              >
                <td
                  style={{
                    ...tdStyle,
                    fontFamily: "var(--mono)",
                    fontSize: 12,
                  }}
                >
                  {item.qr_code}
                </td>
                <td style={tdStyle}>{item.box_name}</td>
                <td style={tdStyle}>{item.product_master_name}</td>
                <td style={{ ...tdStyle, color: "var(--fg3)", fontSize: 12 }}>
                  {new Date(item.created_at).toLocaleDateString("ko-KR")}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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

      {/* 새 외박스 모달 */}
      {showCreate && (
        <Overlay onClose={() => setShowCreate(false)}>
          <h3 style={modalTitleStyle}>새 외박스 등록</h3>
          <form onSubmit={handleCreate}>
            <label style={labelStyle}>QR코드 *</label>
            <input
              style={inputStyle}
              value={form.qr_code}
              onChange={(e) => setForm({ ...form, qr_code: e.target.value })}
              required
              placeholder="QR코드 입력"
            />
            <label style={labelStyle}>박스명 *</label>
            <input
              style={inputStyle}
              value={form.box_name}
              onChange={(e) => setForm({ ...form, box_name: e.target.value })}
              required
              placeholder="박스명 입력"
            />
            <label style={labelStyle}>상품명</label>
            <input
              style={inputStyle}
              value={form.product_master_name}
              onChange={(e) =>
                setForm({ ...form, product_master_name: e.target.value })
              }
              placeholder="마스터 상품명"
            />
            <label style={labelStyle}>위치</label>
            <input
              style={inputStyle}
              value={form.location}
              onChange={(e) => setForm({ ...form, location: e.target.value })}
              placeholder="위치"
            />
            <div style={{ display: "flex", gap: 8, marginTop: 20 }}>
              <button
                type="button"
                onClick={() => setShowCreate(false)}
                style={btnSecStyle}
              >
                취소
              </button>
              <button type="submit" disabled={creating} style={btnStyle}>
                {creating ? "저장 중..." : "저장"}
              </button>
            </div>
          </form>
        </Overlay>
      )}

      {/* 상세 모달 */}
      {(detailLoading || detail) && (
        <Overlay
          onClose={() => {
            setDetail(null);
          }}
        >
          {detailLoading ? (
            <div
              style={{ padding: 40, textAlign: "center", color: "var(--fg3)" }}
            >
              불러오는 중...
            </div>
          ) : detail ? (
            <BoxDetail detail={detail} />
          ) : null}
        </Overlay>
      )}
    </div>
  );
}

function BoxDetail({ detail }: { detail: BoxResponse }) {
  const qrSrc = `/api/qr/image?data=${encodeURIComponent(detail.qr_code)}&size=6`;
  return (
    <div>
      <h3 style={modalTitleStyle}>{detail.box_name}</h3>
      <div
        style={{ display: "flex", gap: 24, marginBottom: 16, flexWrap: "wrap" }}
      >
        <div>
          <img
            src={qrSrc}
            alt="QR"
            style={{ width: 120, height: 120, display: "block" }}
          />
          <a
            href={qrSrc}
            download={`${detail.qr_code}.png`}
            style={{
              display: "block",
              fontSize: 12,
              color: "var(--accent)",
              marginTop: 4,
            }}
          >
            QR 다운로드
          </a>
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ marginBottom: 6 }}>
            <span style={{ color: "var(--fg3)", fontSize: 12 }}>마스터: </span>
            <span style={{ fontWeight: 600 }}>
              {detail.product_master_name}
            </span>
          </div>
          {detail.product_master_image && (
            <img
              src={imageUrl(detail.product_master_image)}
              alt="마스터 이미지"
              style={{
                width: 80,
                height: 80,
                objectFit: "cover",
                borderRadius: 4,
              }}
            />
          )}
        </div>
      </div>

      {detail.members.length > 0 && (
        <>
          <h4 style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
            구성 상품
          </h4>
          <table style={{ ...tableStyle, marginBottom: 16 }}>
            <thead>
              <tr style={{ background: "var(--bg)" }}>
                <th style={thStyle}>SKU</th>
                <th style={thStyle}>상품명</th>
                <th style={thStyle}>바코드</th>
                <th style={thStyle}>위치</th>
              </tr>
            </thead>
            <tbody>
              {detail.members.map((m) => (
                <tr
                  key={m.sku_id}
                  style={{ borderBottom: "1px solid var(--border-light)" }}
                >
                  <td
                    style={{
                      ...tdStyle,
                      fontFamily: "var(--mono)",
                      fontSize: 11,
                    }}
                  >
                    {m.sku_id}
                  </td>
                  <td style={tdStyle}>{m.sku_name}</td>
                  <td
                    style={{
                      ...tdStyle,
                      fontFamily: "var(--mono)",
                      fontSize: 11,
                    }}
                  >
                    {m.barcode ?? "-"}
                  </td>
                  <td style={tdStyle}>{m.location ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {detail.option_images.length > 0 && (
        <ImageGroup title="옵션 이미지" images={detail.option_images} />
      )}
      {detail.sourcing_images.length > 0 && (
        <ImageGroup title="소싱 이미지" images={detail.sourcing_images} />
      )}
    </div>
  );
}

function ImageGroup({
  title,
  images,
}: {
  title: string;
  images: { id: number; file_path: string }[];
}) {
  return (
    <div style={{ marginBottom: 16 }}>
      <h4 style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
        {title}
      </h4>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {images.map((img) => (
          <img
            key={img.id}
            src={imageUrl(img.file_path)}
            alt=""
            style={{
              width: 80,
              height: 80,
              objectFit: "cover",
              borderRadius: 4,
              border: "1px solid var(--border-light)",
            }}
          />
        ))}
      </div>
    </div>
  );
}

function Overlay({
  children,
  onClose,
}: {
  children: React.ReactNode;
  onClose: () => void;
}) {
  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.45)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 200,
        padding: 24,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: "var(--bg2)",
          borderRadius: 10,
          padding: 24,
          width: "100%",
          maxWidth: 600,
          maxHeight: "85vh",
          overflowY: "auto",
          boxShadow: "var(--shadow-lg)",
        }}
      >
        {children}
      </div>
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
const btnStyle: React.CSSProperties = {
  padding: "8px 18px",
  background: "var(--accent)",
  color: "#fff",
  border: "none",
  borderRadius: 6,
  fontWeight: 600,
  fontSize: 13,
  cursor: "pointer",
};
const btnSecStyle: React.CSSProperties = {
  padding: "8px 18px",
  background: "transparent",
  color: "var(--fg2)",
  border: "1px solid var(--border)",
  borderRadius: 6,
  fontWeight: 500,
  fontSize: 13,
  cursor: "pointer",
};
const modalTitleStyle: React.CSSProperties = {
  fontSize: 16,
  fontWeight: 700,
  marginBottom: 16,
};
const labelStyle: React.CSSProperties = {
  display: "block",
  fontSize: 12,
  fontWeight: 600,
  color: "var(--fg2)",
  marginBottom: 4,
  marginTop: 12,
};
const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "8px 12px",
  border: "1px solid var(--border)",
  borderRadius: 6,
  fontSize: 13,
  fontFamily: "var(--sans)",
  outline: "none",
};
