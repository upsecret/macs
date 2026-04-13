import { useState, useEffect } from "react";
import { X } from "lucide-react";
import api from "../utils/api";
import type { AvailableRoute, Connector, ConnectorType } from "../types";

interface Props {
  mode: "create" | "edit";
  initial?: Connector;
  onClose: () => void;
  onSaved: () => void;
}

const TYPE_OPTIONS: ConnectorType[] = ["agent", "api", "mcp"];

export default function ConnectorFormModal({ mode, initial, onClose, onSaved }: Props) {
  const [availableRoutes, setAvailableRoutes] = useState<AvailableRoute[]>([]);
  const [id, setId] = useState(initial?.id ?? "");
  const [title, setTitle] = useState(initial?.title ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [type, setType] = useState<ConnectorType>(initial?.type ?? "api");
  const [docsUrl, setDocsUrl] = useState(initial?.docsUrl ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (mode !== "create") return;
    api.get<AvailableRoute[]>("/api/admin/connectors/available-routes")
      .then((r) => {
        setAvailableRoutes(r.data);
        if (r.data.length > 0 && !id) setId(r.data[0].id);
      })
      .catch(() => setAvailableRoutes([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!id || !title.trim() || !type) {
      setError("id, title, type은 필수입니다.");
      return;
    }
    const trimmedDocsUrl = docsUrl.trim();
    if (trimmedDocsUrl && !/^https?:\/\//.test(trimmedDocsUrl)) {
      setError("API 문서 URL 은 http:// 또는 https:// 로 시작해야 합니다.");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        id,
        title: title.trim(),
        description: description.trim() || null,
        type,
        docsUrl: trimmedDocsUrl || null,
      };
      if (mode === "create") {
        await api.post("/api/admin/connectors", payload);
      } else {
        await api.put(`/api/admin/connectors/${id}`, payload);
      }
      onSaved();
      onClose();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "저장에 실패했습니다.";
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">
            {mode === "create" ? "커넥터 등록" : "커넥터 편집"}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Gateway Route ID
            </label>
            {mode === "create" ? (
              availableRoutes.length === 0 ? (
                <p className="text-sm text-gray-400 px-3 py-2 border border-dashed border-gray-300 rounded-lg">
                  등록 가능한 라우트가 없습니다. 경로설정에서 먼저 라우트를 만드세요.
                </p>
              ) : (
                <select
                  value={id}
                  onChange={(e) => setId(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  required
                >
                  {availableRoutes.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.id} — {r.uri}
                    </option>
                  ))}
                </select>
              )
            ) : (
              <input
                type="text"
                value={id}
                readOnly
                className="w-full px-3 py-2 border border-gray-200 bg-gray-50 rounded-lg text-sm font-mono text-gray-600"
              />
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="커넥터 제목"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Description</label>
            <textarea
              value={description ?? ""}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              placeholder="설명"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary resize-none"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Type</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as ConnectorType)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
              required
            >
              {TYPE_OPTIONS.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              API 문서 URL <span className="text-gray-400 text-xs font-normal">(optional)</span>
            </label>
            <input
              type="text"
              value={docsUrl}
              onChange={(e) => setDocsUrl(e.target.value)}
              placeholder="기본: gateway /v3/api-docs/{routeId}"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono focus:ring-2 focus:ring-primary/40 focus:border-primary"
            />
            <p className="mt-1 text-xs text-gray-500">
              서비스의 OpenAPI JSON 이 gateway 를 통한 기본 경로가 아닐 때 절대 URL 을 지정합니다. 예:{" "}
              <code>http://10.40.59.61:8080/token-dic/v3/api-docs</code>
            </p>
          </div>

          {error && (
            <div className="bg-error/5 text-error text-sm px-4 py-3 rounded-lg">{error}</div>
          )}

          <div className="flex items-center justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={saving || (mode === "create" && availableRoutes.length === 0)}
              className="px-4 py-2 text-sm text-white bg-primary rounded-lg hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {saving ? "저장 중..." : "저장"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
