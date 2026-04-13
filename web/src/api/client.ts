const API_BASE = "/api";

export async function api<T = unknown>(
  path: string,
  options?: RequestInit,
): Promise<T> {
  const key = localStorage.getItem("scan_api_key") || "";
  const headers: Record<string, string> = {
    ...((options?.headers as Record<string, string>) || {}),
  };
  if (key) headers["X-API-Key"] = key;
  if (
    !headers["Content-Type"] &&
    options?.body &&
    typeof options.body === "string"
  ) {
    headers["Content-Type"] = "application/json";
  }
  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.json();
}

export function imageUrl(path: string): string {
  if (path.startsWith("http"))
    return `${API_BASE}/image/${encodeURIComponent(path)}`;
  if (path.startsWith("/static/")) return path;
  return `${API_BASE}/image/${path}`;
}
