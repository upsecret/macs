"use client";

import { useMemo, useState } from "react";
import { ArrowLeft, Plus, Pencil, Trash2 } from "lucide-react";
import api from "~/utils/api";
import ApiDocsViewer from "~/components/ApiDocsViewer";
import ConnectorFormModal from "~/components/ConnectorFormModal";
import McpDetailPanel from "~/components/McpDetailPanel";
import { useAuthStore } from "~/stores/authStore";
import { useResource } from "~/hooks/useResource";
import type { Connector, ConnectorType, McpServer } from "~/types";

const TYPE_BADGE: Record<ConnectorType, string> = {
  agent: "bg-violet-100 text-violet-700",
  api: "bg-blue-100 text-blue-700",
  mcp: "bg-amber-100 text-amber-700",
};

/* ── unified card view-model ────────────────────────────── */

interface RegistryItem {
  kind: "connector" | "mcp";
  id: string;
  title: string;          // connector.title or mcp.name
  description: string | null;
  type: ConnectorType;
  system: string;
  /** REST 측 — gateway route 가 살아있어야 활성. MCP 는 항상 true. */
  active: boolean;
  /** for clicking through to the right detail view */
  raw: Connector | McpServer;
}

function fromConnector(c: Connector): RegistryItem {
  return {
    kind: "connector",
    id: c.id,
    title: c.title,
    description: c.description,
    type: c.type,
    system: c.system,
    active: c.active,
    raw: c,
  };
}

function fromMcp(s: McpServer): RegistryItem {
  return {
    kind: "mcp",
    id: s.id,
    title: s.name,
    description: s.description,
    type: "mcp",
    system: s.system,
    // API 커넥터와 동일: 매칭 게이트웨이 라우트가 있어야 활성.
    active: s.active,
    raw: s,
  };
}

function groupBySystem(items: RegistryItem[]): Array<[string, RegistryItem[]]> {
  const groups = new Map<string, RegistryItem[]>();
  for (const it of items) {
    const key = it.system || "common";
    const list = groups.get(key);
    if (list) list.push(it);
    else groups.set(key, [it]);
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
  const [selectedKey, setSelectedKey] = useState<string | null>(null); // "kind:id"
  const [modal, setModal] = useState<"create" | "edit" | null>(null);

  const { data: connectorsData, refetch: refetchConnectors } = useResource<Connector[]>(
    () => api.get<Connector[]>("/api/admin/connectors").then((r) => r.data),
    [],
  );
  const { data: mcpData, refetch: refetchMcp } = useResource<McpServer[]>(
    () => api.get<McpServer[]>("/api/admin/mcp/servers").then((r) => r.data),
    [],
  );

  const refetchAll = async () => {
    await Promise.all([refetchConnectors(), refetchMcp()]);
  };

  const items: RegistryItem[] = useMemo(() => {
    const a = (connectorsData ?? []).map(fromConnector);
    const b = (mcpData ?? []).map(fromMcp);
    return [...a, ...b];
  }, [connectorsData, mcpData]);

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (c) =>
        c.id.toLowerCase().includes(q) ||
        c.title.toLowerCase().includes(q) ||
        (c.description ?? "").toLowerCase().includes(q) ||
        c.system.toLowerCase().includes(q) ||
        c.type.toLowerCase().includes(q),
    );
  }, [items, filter]);

  const grouped = useMemo(() => groupBySystem(filtered), [filtered]);
  const loading = connectorsData == null && mcpData == null;

  const selected = useMemo(
    () => items.find((it) => `${it.kind}:${it.id}` === selectedKey) ?? null,
    [items, selectedKey],
  );

  const handleDelete = async (it: RegistryItem) => {
    const target =
      it.kind === "mcp"
        ? `MCP 서버 "${it.id}"`
        : `커넥터 "${it.id}" (gateway 라우트는 유지됨)`;
    if (!confirm(`${target} 를 삭제하시겠습니까?`)) return;
    try {
      const url =
        it.kind === "mcp"
          ? `/api/admin/mcp/servers/${it.id}`
          : `/api/admin/connectors/${it.id}`;
      await api.delete(url);
      setSelectedKey(null);
      await refetchAll();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "삭제 실패");
    }
  };

  /* ── 상세 뷰 ──────────────────────────────────────────── */
  if (selected) {
    const activeBadge = selected.active
      ? "bg-green-50 text-green-700"
      : "bg-gray-100 text-gray-500";
    const mcp = selected.kind === "mcp" ? (selected.raw as McpServer) : null;
    return (
      <div>
        <div className="flex items-center gap-4 mb-6">
          <button
            onClick={() => setSelectedKey(null)}
            className="flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50"
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
                  <span
                    className={`inline-block text-xs px-2 py-0.5 rounded font-mono ${TYPE_BADGE[selected.type]}`}
                  >
                    {selected.type}
                  </span>
                </p>
              </div>
              <div>
                <span className="text-[10px] uppercase tracking-wider text-gray-400">System</span>
                <p className="text-sm font-mono text-gray-800">{selected.system}</p>
              </div>
              {(
                <div>
                  <span className="text-[10px] uppercase tracking-wider text-gray-400">
                    Status
                  </span>
                  <p>
                    <span
                      className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-0.5 rounded-full ${activeBadge}`}
                    >
                      <span
                        className={`w-1.5 h-1.5 rounded-full ${
                          selected.active ? "bg-green-500" : "bg-gray-400"
                        }`}
                      />
                      {selected.active ? "활성" : "비활성 (라우트 없음)"}
                    </span>
                  </p>
                </div>
              )}
              {mcp && (
                <>
                  <div>
                    <span className="text-[10px] uppercase tracking-wider text-gray-400">Auth</span>
                    <p className="text-sm font-mono text-gray-800">
                      {mcp.authType}
                      {mcp.hasAuthToken && (
                        <span className="ml-2 text-xs text-gray-500">(token set)</span>
                      )}
                    </p>
                  </div>
                  <div className="md:col-span-2">
                    <span className="text-[10px] uppercase tracking-wider text-gray-400">
                      Gateway Endpoint (연동 경로)
                    </span>
                    {mcp.gatewayPath ? (
                      <p className="text-sm font-mono text-gray-800 break-all">
                        {mcp.gatewayPath}
                        <span className="ml-2 text-xs text-gray-500">(게이트웨이 경유)</span>
                      </p>
                    ) : (
                      <p className="text-sm text-error">
                        매칭 게이트웨이 라우트 없음 — 경로설정에서{" "}
                        <code className="font-mono">Path=/mcp/{mcp.id}</code> 라우트를 먼저 등록하세요.
                      </p>
                    )}
                  </div>
                  <div>
                    <span className="text-[10px] uppercase tracking-wider text-gray-400">
                      Transport
                    </span>
                    <p className="text-sm font-mono text-gray-800">{mcp.transport}</p>
                  </div>
                </>
              )}
              <div className="md:col-span-2">
                <span className="text-[10px] uppercase tracking-wider text-gray-400">
                  Description
                </span>
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
                className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                <Pencil size={14} strokeWidth={1.75} />
                편집
              </button>
              <button
                onClick={() => handleDelete(selected)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-error border border-error/30 rounded-lg hover:bg-error/5"
              >
                <Trash2 size={14} strokeWidth={1.75} />
                삭제
              </button>
            </div>
          )}
        </div>

        {/* type 별 분기: mcp 면 도구 패널, 그 외엔 API 문서 뷰어 */}
        {selected.kind === "mcp" ? (
          <McpDetailPanel server={selected.raw as McpServer} />
        ) : (
          <div>
            <h3 className="text-sm font-semibold text-gray-800 mb-3">API 문서</h3>
            <ApiDocsViewer connectorId={selected.id} />
          </div>
        )}

        {modal === "edit" && (
          <ConnectorFormModal
            mode="edit"
            initial={selected.raw}
            onClose={() => setModal(null)}
            onSaved={refetchAll}
          />
        )}
      </div>
    );
  }

  /* ── 목록 뷰 ──────────────────────────────────────────── */
  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">커넥터 연동</h1>
          <p className="text-sm text-gray-500 mt-1">
            REST(agent/api) · MCP 커넥터 통합 등록
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={refetchAll}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50"
          >
            새로고침
          </button>
          {isAdmin && (
            <button
              onClick={() => setModal("create")}
              className="flex items-center gap-1.5 px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90"
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
          placeholder="title, description, system, type 으로 검색..."
          className="w-full max-w-md px-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
        />
      </div>

      {loading ? (
        <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">
          로딩 중...
        </div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">
          {filter
            ? "검색 결과가 없습니다."
            : "등록된 커넥터가 없습니다. 우측 상단 '등록하기' 로 추가하세요."}
        </div>
      ) : (
        <div className="space-y-8">
          {grouped.map(([sys, cards]) => (
            <section key={sys}>
              <div className="flex items-baseline gap-2 mb-3">
                <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wider">
                  {sys}
                </h2>
                <span className="text-xs text-gray-400">({cards.length})</span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                {cards.map((c) => {
                  const cardKey = `${c.kind}:${c.id}`;
                  const showActive = true;
                  const activeBadge = c.active
                    ? "bg-green-50 text-green-700"
                    : "bg-gray-100 text-gray-500";
                  return (
                    <div
                      key={cardKey}
                      onClick={() => setSelectedKey(cardKey)}
                      className="bg-white rounded-lg shadow overflow-hidden cursor-pointer hover:shadow-md hover:ring-1 hover:ring-gray-200 transition-all"
                    >
                      <div className="px-4 py-3 border-b border-gray-200 bg-header flex items-center justify-between gap-2">
                        <span className="text-sm font-semibold text-gray-800 truncate">
                          {c.title}
                        </span>
                        <div className="flex items-center gap-1.5 shrink-0">
                          <span
                            className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${TYPE_BADGE[c.type]}`}
                          >
                            {c.type}
                          </span>
                          {showActive && (
                            <span
                              className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full ${activeBadge}`}
                            >
                              <span
                                className={`w-1.5 h-1.5 rounded-full ${
                                  c.active ? "bg-green-500" : "bg-gray-400"
                                }`}
                              />
                              {c.active ? "활성" : "비활성"}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="px-4 py-3 space-y-2">
                        {c.description && (
                          <div>
                            <span className="text-[10px] uppercase tracking-wider text-gray-400">
                              Description
                            </span>
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
          onSaved={refetchAll}
        />
      )}
    </div>
  );
}
