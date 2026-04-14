import { useState, useEffect, useCallback } from "react";
import { api, imageUrl } from "../api/client";
import type { MapLayout, MapCell, MapLevel } from "../types";

type CellModalState = {
  cellId: string;
  cell: MapCell;
} | null;

const CELL_COLORS: Record<string, string> = {
  used: "#d1fae5",
  empty: "#f3f4f6",
  aisle: "#374151",
  table: "#92400e",
  pc: "#1e40af",
};

const CELL_TEXT_COLORS: Record<string, string> = {
  aisle: "#fff",
  table: "#fff",
  pc: "#fff",
};

function LevelItem({
  level,
  onDelete,
}: {
  level: MapLevel & { id?: number };
  onDelete?: () => void;
}) {
  return (
    <div
      style={{
        display: "flex",
        gap: 12,
        padding: "10px 0",
        borderBottom: "1px solid var(--border-light)",
        alignItems: "flex-start",
      }}
    >
      {level.photo && (
        <img
          src={imageUrl(level.photo)}
          alt=""
          style={{
            width: 56,
            height: 56,
            objectFit: "cover",
            borderRadius: 6,
            flexShrink: 0,
            background: "#f3f4f6",
          }}
        />
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: 13 }}>{level.label}</div>
        {level.itemLabel && (
          <div style={{ fontSize: 13, color: "var(--fg2)", marginTop: 2 }}>
            {level.itemLabel}
          </div>
        )}
        {level.sku && (
          <div
            style={{
              fontFamily: "var(--mono)",
              fontSize: 11,
              color: "var(--fg3)",
              marginTop: 2,
            }}
          >
            {level.sku}
          </div>
        )}
      </div>
      {onDelete && (
        <button
          onClick={onDelete}
          style={{
            padding: "4px 10px",
            border: "1px solid #fca5a5",
            borderRadius: 6,
            background: "#fff",
            color: "#dc2626",
            fontSize: 12,
            cursor: "pointer",
            flexShrink: 0,
          }}
        >
          삭제
        </button>
      )}
    </div>
  );
}

function CellModal({
  state,
  onClose,
  onRefresh,
}: {
  state: CellModalState;
  onClose: () => void;
  onRefresh: () => void;
}) {
  const [newLabel, setNewLabel] = useState("");
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");

  if (!state) return null;

  const { cellId, cell } = state;

  async function handleAddLevel() {
    if (!newLabel.trim()) return;
    setAdding(true);
    setError("");
    try {
      await api(`/cells/${encodeURIComponent(cellId)}/levels`, {
        method: "POST",
        body: JSON.stringify({ label: newLabel.trim() }),
      });
      setNewLabel("");
      onRefresh();
    } catch {
      setError("추가 실패");
    } finally {
      setAdding(false);
    }
  }

  async function handleDeleteLevel(levelId: number) {
    try {
      await api(`/levels/${levelId}`, { method: "DELETE" });
      onRefresh();
    } catch {
      setError("삭제 실패");
    }
  }

  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.5)",
        zIndex: 200,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: "#fff",
          borderRadius: 12,
          width: 480,
          maxWidth: "90vw",
          maxHeight: "80vh",
          display: "flex",
          flexDirection: "column",
          boxShadow: "var(--shadow-lg)",
        }}
      >
        <div
          style={{
            padding: "16px 20px",
            borderBottom: "1px solid var(--border-light)",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <div>
            <div style={{ fontWeight: 700, fontSize: 15 }}>{cell.label}</div>
            <div style={{ fontSize: 12, color: "var(--fg3)", marginTop: 2 }}>
              {cellId}
            </div>
          </div>
          <button
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              fontSize: 20,
              cursor: "pointer",
              color: "var(--fg3)",
            }}
          >
            ×
          </button>
        </div>

        <div style={{ flex: 1, overflowY: "auto", padding: "0 20px" }}>
          {cell.levels.length === 0 ? (
            <div
              style={{
                textAlign: "center",
                padding: "32px 0",
                color: "var(--fg3)",
              }}
            >
              레벨 없음
            </div>
          ) : (
            cell.levels.map((lv: MapLevel & { id?: number }, i: number) => (
              <LevelItem
                key={lv.id ?? i}
                level={lv}
                onDelete={
                  lv.id !== undefined
                    ? () => handleDeleteLevel(lv.id!)
                    : undefined
                }
              />
            ))
          )}
        </div>

        <div
          style={{
            padding: "16px 20px",
            borderTop: "1px solid var(--border-light)",
          }}
        >
          {error && (
            <div style={{ color: "#dc2626", fontSize: 12, marginBottom: 8 }}>
              {error}
            </div>
          )}
          <div style={{ display: "flex", gap: 8 }}>
            <input
              type="text"
              placeholder="레벨 라벨 (예: A)"
              value={newLabel}
              onChange={(e) => setNewLabel(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleAddLevel()}
              style={{
                flex: 1,
                padding: "8px 12px",
                border: "1px solid var(--border-light)",
                borderRadius: "var(--radius)",
                fontSize: 13,
                outline: "none",
              }}
            />
            <button
              onClick={handleAddLevel}
              disabled={adding || !newLabel.trim()}
              style={{
                padding: "8px 16px",
                border: "none",
                borderRadius: "var(--radius)",
                background: "var(--accent)",
                color: "#fff",
                fontSize: 13,
                fontWeight: 600,
                cursor: adding ? "not-allowed" : "pointer",
                opacity: adding ? 0.6 : 1,
              }}
            >
              추가
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function MapPage() {
  const [layout, setLayout] = useState<MapLayout | null>(null);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState<CellModalState>(null);

  const fetchLayout = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api<MapLayout>("/map-layout");
      setLayout(data);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLayout();
  }, [fetchLayout]);

  function openCell(cellId: string) {
    if (!layout) return;
    const cell = layout.cells[cellId];
    if (!cell) return;
    setModal({ cellId, cell });
  }

  function handleRefresh() {
    fetchLayout().then(() => {
      if (modal && layout) {
        const updated = layout.cells[modal.cellId];
        if (updated) setModal({ cellId: modal.cellId, cell: updated });
      }
    });
  }

  if (loading) {
    return <div className="catalog-loading">불러오는 중...</div>;
  }

  if (!layout) {
    return (
      <div className="catalog-empty">도면 데이터를 불러올 수 없습니다.</div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 4 }}>
        {layout.title}
      </h2>
      <div style={{ fontSize: 13, color: "var(--fg3)", marginBottom: 24 }}>
        {layout.floor}층
      </div>

      {layout.zones.map((zone) => (
        <div key={zone.code} style={{ marginBottom: 32 }}>
          <div
            style={{
              fontWeight: 600,
              fontSize: 14,
              marginBottom: 10,
              padding: "6px 12px",
              background: "var(--navy)",
              color: "#fff",
              borderRadius: "var(--radius)",
              display: "inline-block",
            }}
          >
            {zone.name} ({zone.code})
          </div>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: `repeat(${zone.cols}, 1fr)`,
              gap: 4,
              maxWidth: zone.cols * 72 + (zone.cols - 1) * 4,
            }}
          >
            {Array.from({ length: zone.rows }, (_, r) =>
              Array.from({ length: zone.cols }, (_, c) => {
                const cellId = `${zone.code}-${r + 1}-${c + 1}`;
                const cell = layout.cells[cellId];
                const status = cell?.status ?? "empty";
                const bg = CELL_COLORS[status] ?? CELL_COLORS.empty;
                const fg = CELL_TEXT_COLORS[status] ?? "var(--fg)";
                const isInteractive =
                  status !== "aisle" && status !== "empty" && cell;

                return (
                  <div
                    key={cellId}
                    onClick={() => isInteractive && openCell(cellId)}
                    title={cellId}
                    style={{
                      width: 68,
                      height: 52,
                      background: bg,
                      borderRadius: 6,
                      display: "flex",
                      flexDirection: "column",
                      alignItems: "center",
                      justifyContent: "center",
                      fontSize: 11,
                      fontWeight: 600,
                      color: fg,
                      cursor: isInteractive ? "pointer" : "default",
                      border: "1px solid rgba(0,0,0,0.08)",
                      userSelect: "none",
                      transition: "opacity 0.15s",
                    }}
                    onMouseEnter={(e) => {
                      if (isInteractive)
                        (e.currentTarget as HTMLDivElement).style.opacity =
                          "0.8";
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLDivElement).style.opacity = "1";
                    }}
                  >
                    <span>
                      {cell?.label ?? cellId.split("-").slice(1).join("-")}
                    </span>
                    {cell && cell.levels.length > 0 && (
                      <span
                        style={{
                          fontSize: 10,
                          fontWeight: 400,
                          color: status === "used" ? "#059669" : fg,
                          marginTop: 2,
                        }}
                      >
                        {cell.levels.length}개
                      </span>
                    )}
                  </div>
                );
              }),
            )}
          </div>
        </div>
      ))}

      <div
        style={{
          display: "flex",
          gap: 16,
          marginTop: 8,
          flexWrap: "wrap",
        }}
      >
        {Object.entries(CELL_COLORS).map(([status, bg]) => (
          <div
            key={status}
            style={{ display: "flex", alignItems: "center", gap: 6 }}
          >
            <div
              style={{
                width: 16,
                height: 16,
                background: bg,
                borderRadius: 3,
                border: "1px solid rgba(0,0,0,0.1)",
              }}
            />
            <span style={{ fontSize: 12, color: "var(--fg3)" }}>
              {
                {
                  used: "사용중",
                  empty: "비어있음",
                  aisle: "통로",
                  table: "작업대",
                  pc: "PC",
                }[status]
              }
            </span>
          </div>
        ))}
      </div>

      <CellModal
        state={modal}
        onClose={() => setModal(null)}
        onRefresh={handleRefresh}
      />
    </div>
  );
}
