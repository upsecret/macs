import { useMemo, useState } from "react";
import { ArrowLeft, Plus, Pencil, Trash2 } from "lucide-react";
import api from "../utils/api";
import ApiDocsViewer from "../components/ApiDocsViewer";
import ConnectorFormModal from "../components/ConnectorFormModal";
import { useAuthStore } from "../stores/authStore";
import { useResource } from "../hooks/useResource";
import type { Connector, ConnectorType } from "../types";

const TYPE_BADGE: Record<ConnectorType, string> = {
  agent: "bg-violet-100 text-violet-700",
  api: "bg-blue-100 text-blue-700",
  mcp: "bg-amber-100 text-amber-700",
};

/**
 * system 별로 커넥터 그룹화 + 정렬.
 * common 은 항상 맨 앞, 나머지는 알파벳 순.
 */
function groupBySystem(connectors: Connector[]): Array<[string, Connector[]]> {
  const groups = new Map<string, Connector[]>();
  for (const c of connectors) {
    const key = c.system || "common";
    const list = groups.get(key);
    if (list) list.push(c);
    else groups.set(key, [c]);
  }
  return [...groups.entries()].sort(([a], [b]) => {
    if (a === "common") return -1;
    if (b === "common") return 1;
    return a.localeCompare(b);
  });
}

export default function ConnectorPage() {
  const isAdmin = useAuthStore((s) => s.isAdmin());
  const [filter, setFilter] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [modal, setModal] = useState<"create" | "edit" | null>(null);

  const { data: connectorsData, loading, refetch: fetchConnectors } = useResource<Connector[]>(
    () => api.get<Connector[]>("/api/admin/connectors").then((r) => r.data),
    [],
  );
  const connectors = connectorsData ?? [];

  const selected = connectors.find((c) => c.id === selectedId) ?? null;

  // ⚠️ hooks(useMemo) 는 반드시 early return 전에 호출 (React Rules of Hooks).
  // 상세 뷰일 때도 동일 개수의 hooks 가 호출돼야 error #300 을 피함.
  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return connectors;
    return connectors.filter(
      (c) =>
        c.id.toLowerCase().includes(q) ||
        c.title.toLowerCase().includes(q) ||
        (c.description ?? "").toLowerCase().includes(q) ||
        c.system.toLowerCase().includes(q),
    );
  }, [connectors, filter]);

  const grouped = useMemo(() => groupBySystem(filtered), [filtered]);

  const handleDelete = async (id: string) => {
    if (!confirm(`커넥터 "${id}"를 삭제하시겠습니까? (gateway 라우트는 유지됩니다)`)) return;
    try {
      await api.delete(`/api/admin/connectors/${id}`);
      setSelectedId(null);
      await fetchConnectors();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "삭제에 실패했습니다.";
      alert(message);
    }
  };

  /* ── 상세 뷰 ──────────────────────────────────────────────── */
  if (selected) {
    const activeBadge = selected.active
      ? "bg-green-50 text-green-700"
      : "bg-gray-100 text-gray-500";
    return (
      <div>
        <div className="flex items-center gap-4 mb-6">
          <button
            onClick={() => setSelectedId(null)}
            className="flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors"
          >
            <ArrowLeft size={16} strokeWidth={1.75} />
            목록으로
          </button>
          <h1 className="text-2xl font-bold text-gray-900">{selected.title}</h1>
        </div>

        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <div className="flex items-start justify-between mb-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-4 flex-1">
              <div>
                <span className="text-[10px] uppercase tracking-wider text-gray-400">Type</span>
                <p>
                  <span className={`inline-block text-xs px-2 py-0.5 rounded font-mono ${TYPE_BADGE[selected.type]}`}>
                    {selected.type}
                  </span>
                </p>
              </div>
              <div>
                <span className="text-[10px] uppercase tracking-wider text-gray-400">System</span>
                <p className="text-sm font-mono text-gray-800">{selected.system}</p>
              </div>
              <div>
                <span className="text-[10px] uppercase tracking-wider text-gray-400">Status</span>
                <p>
                  <span className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-0.5 rounded-full ${activeBadge}`}>
                    <span className={`w-1.5 h-1.5 rounded-full ${selected.active ? "bg-green-500" : "bg-gray-400"}`} />
                    {selected.active ? "활성" : "비활성 (라우트 없음)"}
                  </span>
                </p>
              </div>
              <div className="md:col-span-2">
                <span className="text-[10px] uppercase tracking-wider text-gray-400">Description</span>
                <p className="text-sm text-gray-600">
                  {selected.description || <span className="text-gray-400">-</span>}
                </p>
              </div>
            </div>
          </div>

          {isAdmin && (
            <div className="flex items-center justify-end gap-2 pt-4 border-t border-gray-100">
              <button
                onClick={() => setModal("edit")}
                className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <Pencil size={14} strokeWidth={1.75} />
                편집
              </button>
              <button
                onClick={() => handleDelete(selected.id)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-error border border-error/30 rounded-lg hover:bg-error/5 transition-colors"
              >
                <Trash2 size={14} strokeWidth={1.75} />
                삭제
              </button>
            </div>
          )}
        </div>

        {/* API 문서 — admin-server 프록시 를 통해 OpenAPI JSON 을 받아 구조화 렌더 */}
        <div>
          <h3 className="text-sm font-semibold text-gray-800 mb-3">API 문서</h3>
          <ApiDocsViewer connectorId={selected.id} />
        </div>

        {modal === "edit" && (
          <ConnectorFormModal
            mode="edit"
            initial={selected}
            onClose={() => setModal(null)}
            onSaved={fetchConnectors}
          />
        )}
      </div>
    );
  }

  /* ── 목록 뷰 ──────────────────────────────────────────────── */
  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">커넥터 연동</h1>
          <p className="text-sm text-gray-500 mt-1">등록된 커넥터 현황</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={fetchConnectors}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors"
          >
            새로고침
          </button>
          {isAdmin && (
            <button
              onClick={() => setModal("create")}
              className="flex items-center gap-1.5 px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 transition-colors"
            >
              <Plus size={16} strokeWidth={2} />
              등록하기
            </button>
          )}
        </div>
      </div>

      <div className="mb-4">
        <input
          type="text"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="title, description, system 으로 검색..."
          className="w-full max-w-md px-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
        />
      </div>

      {loading ? (
        <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">로딩 중...</div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">
          {filter ? "검색 결과가 없습니다." : "등록된 커넥터가 없습니다. 우측 상단 '등록하기'로 추가하세요."}
        </div>
      ) : (
        <div className="space-y-8">
          {grouped.map(([sys, cards]) => (
            <section key={sys}>
              <div className="flex items-baseline gap-2 mb-3">
                <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wider">{sys}</h2>
                <span className="text-xs text-gray-400">({cards.length})</span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                {cards.map((c) => {
                  const activeBadge = c.active
                    ? "bg-green-50 text-green-700"
                    : "bg-gray-100 text-gray-500";
                  return (
                    <div
                      key={c.id}
                      onClick={() => setSelectedId(c.id)}
                      className="bg-white rounded-lg shadow overflow-hidden cursor-pointer hover:shadow-md hover:ring-1 hover:ring-gray-200 transition-all"
                    >
                      <div className="px-4 py-3 border-b border-gray-200 bg-header flex items-center justify-between gap-2">
                        <span className="text-sm font-semibold text-gray-800 truncate">{c.title}</span>
                        <div className="flex items-center gap-1.5 shrink-0">
                          <span className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${TYPE_BADGE[c.type]}`}>
                            {c.type}
                          </span>
                          <span className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full ${activeBadge}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${c.active ? "bg-green-500" : "bg-gray-400"}`} />
                            {c.active ? "활성" : "비활성"}
                          </span>
                        </div>
                      </div>
                      <div className="px-4 py-3 space-y-2">
                        {c.description && (
                          <div>
                            <span className="text-[10px] uppercase tracking-wider text-gray-400">Description</span>
                            <p className="text-sm text-gray-600 line-clamp-2">{c.description}</p>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      )}

      {modal === "create" && (
        <ConnectorFormModal
          mode="create"
          onClose={() => setModal(null)}
          onSaved={fetchConnectors}
        />
      )}
    </div>
  );
}
