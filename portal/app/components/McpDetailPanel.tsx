"use client";

import { useEffect, useMemo, useState } from "react";
import { RefreshCw, ChevronDown, ChevronRight, Info } from "lucide-react";
import api from "../utils/api";
import type { McpServer, McpTool } from "../types";

/* ── MCP tool inputSchema (JSON Schema 최소 타입) ─────────── */

interface ToolSchema {
  type?: string;
  properties?: Record<string, SchemaProp>;
  required?: string[];
}

interface SchemaProp {
  type?: string;
  description?: string;
  enum?: unknown[];
  items?: { type?: string };
}

interface ParamRow {
  name: string;
  type: string;
  required: boolean;
  description: string;
}

function tryStringify(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function typeLabel(prop: SchemaProp): string {
  if (!prop?.type) return "-";
  if (prop.type === "array") {
    return prop.items?.type ? `${prop.items.type}[]` : "array";
  }
  return prop.type;
}

function toParamRows(inputSchema: unknown): ParamRow[] {
  const schema = (inputSchema ?? {}) as ToolSchema;
  const props = schema.properties ?? {};
  const required = new Set(schema.required ?? []);
  return Object.entries(props).map(([name, prop]) => ({
    name,
    type: typeLabel(prop),
    required: required.has(name),
    description: prop?.description ?? "",
  }));
}

/* ── 도구 스펙 카드 (클릭하면 파라미터/스키마 확장) ───────── */

function ToolSpecRow({ tool }: { tool: McpTool }) {
  const [open, setOpen] = useState(false);
  const params = useMemo(() => toParamRows(tool.inputSchema), [tool.inputSchema]);

  return (
    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-gray-50 transition-colors text-left"
      >
        {open ? (
          <ChevronDown size={14} className="text-gray-400 shrink-0" />
        ) : (
          <ChevronRight size={14} className="text-gray-400 shrink-0" />
        )}
        <span className="font-mono text-sm font-semibold text-gray-800 truncate">{tool.name}</span>
        <span className="text-[11px] text-gray-400 shrink-0">
          {params.length} param{params.length === 1 ? "" : "s"}
        </span>
        {tool.description && (
          <span className="text-sm text-gray-500 truncate ml-auto">{tool.description}</span>
        )}
      </button>

      {open && (
        <div className="px-4 py-3 space-y-4 border-t border-gray-100 bg-gray-50/50">
          {tool.description && (
            <p className="text-sm text-gray-600 whitespace-pre-wrap">{tool.description}</p>
          )}

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">
              Parameters
            </h4>
            {params.length === 0 ? (
              <p className="text-sm text-gray-400">파라미터가 없습니다.</p>
            ) : (
              <table className="w-full text-xs border border-gray-200 rounded overflow-hidden">
                <thead className="bg-white">
                  <tr className="text-left text-gray-500">
                    <th className="px-2 py-1.5 border-b border-gray-200">Name</th>
                    <th className="px-2 py-1.5 border-b border-gray-200">Type</th>
                    <th className="px-2 py-1.5 border-b border-gray-200">Required</th>
                    <th className="px-2 py-1.5 border-b border-gray-200">Description</th>
                  </tr>
                </thead>
                <tbody className="bg-white">
                  {params.map((p) => (
                    <tr key={p.name} className="border-t border-gray-100">
                      <td className="px-2 py-1.5 font-mono text-gray-800">{p.name}</td>
                      <td className="px-2 py-1.5 font-mono text-gray-600">{p.type}</td>
                      <td className="px-2 py-1.5">
                        {p.required ? (
                          <span className="text-error">required</span>
                        ) : (
                          <span className="text-gray-400">optional</span>
                        )}
                      </td>
                      <td className="px-2 py-1.5 text-gray-600">{p.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">
              Input Schema
            </h4>
            <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-[11px] font-mono whitespace-pre-wrap max-h-60 overflow-auto">
              {tryStringify(tool.inputSchema)}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * MCP 서버 상세 — 노출된 도구 스펙(읽기 전용)만 보여준다.
 * API 커넥터의 문서 뷰어처럼 도구 호출 없이 스펙만 조회하며, 조회 실패 시
 * "준비중" 안내를 띄운다. connector 페이지 상세 뷰에서 type=mcp 일 때 dispatch 된다.
 */
export default function McpDetailPanel({ server }: { server: McpServer }) {
  const [tools, setTools] = useState<McpTool[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [toolsError, setToolsError] = useState<string | null>(null);

  useEffect(() => {
    loadTools();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [server.id]);

  const loadTools = async () => {
    setToolsLoading(true);
    setToolsError(null);
    try {
      const r = await api.get<McpTool[]>(`/api/admin/mcp/servers/${server.id}/tools`);
      setTools(r.data);
    } catch (err: unknown) {
      setToolsError(err instanceof Error ? err.message : "도구 목록을 불러오지 못했습니다.");
      setTools([]);
    } finally {
      setToolsLoading(false);
    }
  };

  return (
    <div>
      {/* Tools 스펙 (읽기 전용) */}
      <div className="flex items-center gap-2 mb-3">
        <h3 className="text-sm font-semibold text-gray-800">Tools</h3>
        <span className="text-xs text-gray-400">노출된 도구 스펙 (호출 불가, 조회 전용)</span>
        <button
          onClick={loadTools}
          className="ml-auto flex items-center gap-1.5 px-2.5 py-1 text-xs text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50"
        >
          <RefreshCw size={13} strokeWidth={1.75} />
          새로고침
        </button>
      </div>

      {toolsLoading ? (
        <div className="bg-white rounded-lg shadow p-8 text-center text-gray-400 text-sm">
          불러오는 중...
        </div>
      ) : toolsError ? (
        // 도구 조회 실패(429/502 등)는 원시 에러 대신 API 커넥터 문서처럼 "준비중" 안내.
        <div className="bg-info/5 border border-info/20 rounded-lg p-6">
          <div className="flex items-start gap-3">
            <Info size={18} className="text-info shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-sm font-medium text-gray-900">MCP 서버 준비중입니다.</p>
              <button
                type="button"
                onClick={loadTools}
                className="mt-3 text-xs text-info hover:underline"
              >
                다시 시도
              </button>
            </div>
          </div>
        </div>
      ) : tools.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-8 text-center text-gray-400 text-sm">
          서버가 노출한 도구가 없습니다.
        </div>
      ) : (
        <div className="space-y-1.5">
          {tools.map((t) => (
            <ToolSpecRow key={t.name} tool={t} />
          ))}
        </div>
      )}
    </div>
  );
}
