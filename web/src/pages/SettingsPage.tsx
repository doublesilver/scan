import { useState, useEffect } from "react";
import { api } from "../api/client";
import type { ServerStatus, HealthResponse } from "../types";

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div
      style={{
        background: "var(--bg2)",
        border: "1px solid var(--border-light)",
        borderRadius: "var(--radius)",
        padding: 20,
        marginBottom: 20,
      }}
    >
      <div
        style={{
          fontWeight: 700,
          fontSize: 13,
          color: "var(--fg3)",
          textTransform: "uppercase",
          letterSpacing: "0.5px",
          marginBottom: 16,
        }}
      >
        {title}
      </div>
      {children}
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "8px 0",
        borderBottom: "1px solid var(--border-light)",
        fontSize: 13,
      }}
    >
      <span style={{ color: "var(--fg2)" }}>{label}</span>
      <span style={{ fontWeight: 500, color: "var(--fg)" }}>{value}</span>
    </div>
  );
}

export default function SettingsPage() {
  const [status, setStatus] = useState<ServerStatus | null>(null);
  const [health, setHealth] = useState<string | null>(null);
  const [apiKey, setApiKey] = useState(
    () => localStorage.getItem("scan_api_key") ?? "",
  );
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api<ServerStatus>("/status")
      .then(setStatus)
      .catch(() => {});
    api<HealthResponse>("/health")
      .then((d) => setHealth(d.status))
      .catch(() => setHealth("error"));
  }, []);

  function handleSaveKey() {
    localStorage.setItem("scan_api_key", apiKey);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

  return (
    <div style={{ padding: 24, maxWidth: 600 }}>
      <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 24 }}>설정</h2>

      <Section title="서버 상태">
        <Row
          label="Health"
          value={
            health === null ? (
              <span style={{ color: "var(--fg3)" }}>확인 중...</span>
            ) : (
              <span
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  color: health === "ok" ? "#059669" : "#dc2626",
                }}
              >
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: "50%",
                    background: health === "ok" ? "#059669" : "#dc2626",
                    display: "inline-block",
                  }}
                />
                {health === "ok" ? "정상" : health}
              </span>
            )
          }
        />
        {status && (
          <>
            <Row
              label="상품 수"
              value={status.db_products.toLocaleString() + "개"}
            />
            <Row
              label="바코드 수"
              value={status.db_barcodes.toLocaleString() + "개"}
            />
            <Row
              label="이미지 수"
              value={status.db_images.toLocaleString() + "개"}
            />
            <Row
              label="디스크 캐시"
              value={status.disk_cache_mb.toFixed(1) + " MB"}
            />
          </>
        )}
      </Section>

      <Section title="API Key">
        <div style={{ display: "flex", gap: 8 }}>
          <input
            type="text"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="API 키 입력"
            style={{
              flex: 1,
              padding: "8px 12px",
              border: "1px solid var(--border-light)",
              borderRadius: "var(--radius)",
              fontSize: 13,
              fontFamily: "var(--mono)",
              outline: "none",
            }}
          />
          <button
            onClick={handleSaveKey}
            style={{
              padding: "8px 18px",
              border: "none",
              borderRadius: "var(--radius)",
              background: saved ? "#059669" : "var(--accent)",
              color: "#fff",
              fontSize: 13,
              fontWeight: 600,
              cursor: "pointer",
              transition: "background 0.2s",
              whiteSpace: "nowrap",
            }}
          >
            {saved ? "저장됨" : "저장"}
          </button>
        </div>
        <div style={{ fontSize: 12, color: "var(--fg3)", marginTop: 8 }}>
          브라우저 localStorage에 저장됩니다.
        </div>
      </Section>

      <Section title="서버 정보">
        <Row label="서버 URL" value={window.location.origin} />
        <Row label="API Base" value="/api" />
      </Section>
    </div>
  );
}
