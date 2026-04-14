import { useState, useEffect, useRef } from "react";
import { api } from "../api/client";
import type { ParseLog } from "../types";

function UploadSection({
  title,
  endpoint,
}: {
  title: string;
  endpoint: string;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(
    null,
  );

  async function handleUpload() {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    setLoading(true);
    setMessage(null);
    try {
      const form = new FormData();
      form.append("file", file);
      const key = localStorage.getItem("scan_api_key") || "";
      const headers: Record<string, string> = {};
      if (key) headers["X-API-Key"] = key;
      const res = await fetch(`/api${endpoint}`, {
        method: "POST",
        headers,
        body: form,
      });
      if (!res.ok) throw new Error(`${res.status}`);
      const data = await res.json();
      setMessage({
        text: `완료: 추가 ${data.added ?? 0}, 수정 ${data.updated ?? 0}, 스킵 ${data.skipped ?? 0}`,
        ok: true,
      });
      if (fileRef.current) fileRef.current.value = "";
    } catch (err) {
      setMessage({ text: `업로드 실패: ${err}`, ok: false });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      style={{
        background: "var(--bg2)",
        border: "1px solid var(--border-light)",
        borderRadius: "var(--radius)",
        padding: 20,
        marginBottom: 16,
      }}
    >
      <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 12 }}>
        {title}
      </div>
      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <input
          ref={fileRef}
          type="file"
          accept=".xlsx"
          style={{ flex: 1, fontSize: 13 }}
        />
        <button
          onClick={handleUpload}
          disabled={loading}
          style={{
            padding: "8px 18px",
            border: "none",
            borderRadius: "var(--radius)",
            background: loading ? "var(--border)" : "var(--accent)",
            color: "#fff",
            fontSize: 13,
            fontWeight: 600,
            cursor: loading ? "not-allowed" : "pointer",
            whiteSpace: "nowrap",
          }}
        >
          {loading ? "업로드 중..." : "업로드"}
        </button>
      </div>
      {message && (
        <div
          style={{
            marginTop: 10,
            padding: "8px 12px",
            borderRadius: 6,
            fontSize: 13,
            background: message.ok ? "#d1fae5" : "#fee2e2",
            color: message.ok ? "#065f46" : "#dc2626",
          }}
        >
          {message.text}
        </div>
      )}
    </div>
  );
}

export default function DataPage() {
  const [logs, setLogs] = useState<ParseLog[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api<ParseLog[]>("/parse-logs?limit=20")
      .then(setLogs)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return (
    <div style={{ padding: 24, maxWidth: 900 }}>
      <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 24 }}>
        데이터 관리
      </h2>

      <UploadSection title="SKU 데이터 업로드" endpoint="/import/products" />
      <UploadSection title="URL 데이터 업로드" endpoint="/import/urls-upload" />

      <div style={{ marginTop: 32 }}>
        <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 12 }}>
          파싱 이력
        </div>
        {loading ? (
          <div className="catalog-loading">불러오는 중...</div>
        ) : logs.length === 0 ? (
          <div className="catalog-empty">이력 없음</div>
        ) : (
          <div
            style={{
              background: "var(--bg2)",
              border: "1px solid var(--border-light)",
              borderRadius: "var(--radius)",
              overflow: "hidden",
            }}
          >
            <table
              style={{
                width: "100%",
                borderCollapse: "collapse",
                fontSize: 13,
              }}
            >
              <thead>
                <tr
                  style={{
                    background: "var(--bg)",
                    borderBottom: "1px solid var(--border-light)",
                  }}
                >
                  {[
                    "파일명",
                    "타입",
                    "전체",
                    "추가",
                    "수정",
                    "스킵",
                    "오류",
                    "일시",
                  ].map((h) => (
                    <th
                      key={h}
                      style={{
                        padding: "10px 12px",
                        textAlign: "left",
                        fontWeight: 600,
                        color: "var(--fg2)",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr
                    key={log.id}
                    style={{ borderBottom: "1px solid var(--border-light)" }}
                  >
                    <td
                      style={{
                        padding: "10px 12px",
                        maxWidth: 200,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                        color: "var(--fg)",
                      }}
                      title={log.file_name}
                    >
                      {log.file_name}
                    </td>
                    <td style={{ padding: "10px 12px", color: "var(--fg3)" }}>
                      {log.file_type}
                    </td>
                    <td style={{ padding: "10px 12px" }}>
                      {log.total_records}
                    </td>
                    <td
                      style={{
                        padding: "10px 12px",
                        color: log.added > 0 ? "#059669" : "var(--fg3)",
                      }}
                    >
                      {log.added}
                    </td>
                    <td
                      style={{
                        padding: "10px 12px",
                        color: log.updated > 0 ? "#2563eb" : "var(--fg3)",
                      }}
                    >
                      {log.updated}
                    </td>
                    <td style={{ padding: "10px 12px", color: "var(--fg3)" }}>
                      {log.skipped}
                    </td>
                    <td
                      style={{
                        padding: "10px 12px",
                        color: log.errors > 0 ? "#dc2626" : "var(--fg3)",
                      }}
                    >
                      {log.errors}
                    </td>
                    <td
                      style={{
                        padding: "10px 12px",
                        color: "var(--fg3)",
                        whiteSpace: "nowrap",
                        fontFamily: "var(--mono)",
                        fontSize: 11,
                      }}
                    >
                      {new Date(log.parsed_at).toLocaleString("ko-KR")}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
