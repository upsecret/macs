import { useMemo, useState } from "react";
import { ChevronDown, ChevronRight, AlertCircle } from "lucide-react";
import api from "../utils/api";
import { useResource } from "../hooks/useResource";

/* ── OpenAPI 3.x 최소 타입 ────────────────────────────────── */

interface OpenApiDoc {
  openapi?: string;
  info?: {
    title?: string;
    version?: string;
    description?: string;
  };
  servers?: Array<{ url: string; description?: string }>;
  paths?: Record<string, PathItem>;
}

interface PathItem {
  [method: string]: Operation | unknown;
}

interface Operation {
  operationId?: string;
  summary?: string;
  description?: string;
  tags?: string[];
  parameters?: Parameter[];
  requestBody?: RequestBody;
  responses?: Record<string, ResponseObj>;
}

interface Parameter {
  name: string;
  in: "query" | "path" | "header" | "cookie" | string;
  required?: boolean;
  description?: string;
  schema?: { type?: string; format?: string; $ref?: string };
}

interface RequestBody {
  required?: boolean;
  content?: Record<string, { schema?: unknown }>;
}

interface ResponseObj {
  description?: string;
  content?: Record<string, { schema?: unknown }>;
}

/* ── 메서드 배지 색상 ────────────────────────────────────── */

const METHOD_COLOR: Record<string, string> = {
  get: "bg-blue-100 text-blue-700",
  post: "bg-green-100 text-green-700",
  put: "bg-amber-100 text-amber-800",
  patch: "bg-violet-100 text-violet-700",
  delete: "bg-rose-100 text-rose-700",
  options: "bg-gray-100 text-gray-700",
  head: "bg-gray-100 text-gray-700",
};

const HTTP_METHODS = ["get", "post", "put", "patch", "delete", "options", "head"];

/* ── Flattened endpoint entry ────────────────────────────── */

interface Endpoint {
  method: string;
  path: string;
  op: Operation;
}

function flatten(doc: OpenApiDoc): Endpoint[] {
  const list: Endpoint[] = [];
  const paths = doc.paths ?? {};
  for (const [path, item] of Object.entries(paths)) {
    if (!item || typeof item !== "object") continue;
    for (const method of HTTP_METHODS) {
      const op = (item as PathItem)[method];
      if (op && typeof op === "object") {
        list.push({ method, path, op: op as Operation });
      }
    }
  }
  return list;
}

function groupByTag(endpoints: Endpoint[]): Record<string, Endpoint[]> {
  const groups: Record<string, Endpoint[]> = {};
  for (const ep of endpoints) {
    const tags = ep.op.tags && ep.op.tags.length > 0 ? ep.op.tags : ["Default"];
    for (const tag of tags) {
      (groups[tag] ??= []).push(ep);
    }
  }
  return groups;
}

/* ── 공통 코드 블록 ──────────────────────────────────────── */

function CodeBlock({ data }: { data: unknown }) {
  return (
    <pre className="bg-gray-50 border border-gray-200 rounded p-2 text-[11px] font-mono text-gray-700 whitespace-pre-wrap break-all">
      {JSON.stringify(data, null, 2)}
    </pre>
  );
}

/* ── Operation 상세 ──────────────────────────────────────── */

function OperationDetails({ op }: { op: Operation }) {
  return (
    <div className="px-4 py-3 space-y-4 border-t border-gray-100 bg-gray-50/50">
      {op.description && (
        <p className="text-sm text-gray-600 whitespace-pre-wrap">{op.description}</p>
      )}

      {op.parameters && op.parameters.length > 0 && (
        <div>
          <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">
            Parameters
          </h4>
          <table className="w-full text-xs border border-gray-200 rounded overflow-hidden">
            <thead className="bg-white">
              <tr className="text-left text-gray-500">
                <th className="px-2 py-1.5 border-b border-gray-200">Name</th>
                <th className="px-2 py-1.5 border-b border-gray-200">In</th>
                <th className="px-2 py-1.5 border-b border-gray-200">Type</th>
                <th className="px-2 py-1.5 border-b border-gray-200">Required</th>
                <th className="px-2 py-1.5 border-b border-gray-200">Description</th>
              </tr>
            </thead>
            <tbody className="bg-white">
              {op.parameters.map((p, i) => (
                <tr key={i} className="border-t border-gray-100">
                  <td className="px-2 py-1.5 font-mono text-gray-800">{p.name}</td>
                  <td className="px-2 py-1.5 text-gray-600">{p.in}</td>
                  <td className="px-2 py-1.5 font-mono text-gray-600">
                    {p.schema?.type ?? p.schema?.$ref?.split("/").pop() ?? "-"}
                  </td>
                  <td className="px-2 py-1.5">
                    {p.required ? (
                      <span className="text-error">required</span>
                    ) : (
                      <span className="text-gray-400">optional</span>
                    )}
                  </td>
                  <td className="px-2 py-1.5 text-gray-600">{p.description ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {op.requestBody?.content && (
        <div>
          <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">
            Request Body {op.requestBody.required && <span className="text-error">(required)</span>}
          </h4>
          {Object.entries(op.requestBody.content).map(([contentType, entry]) => (
            <div key={contentType} className="mb-2">
              <div className="text-[11px] font-mono text-gray-500 mb-1">{contentType}</div>
              <CodeBlock data={entry.schema} />
            </div>
          ))}
        </div>
      )}

      {op.responses && Object.keys(op.responses).length > 0 && (
        <div>
          <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">
            Responses
          </h4>
          <table className="w-full text-xs border border-gray-200 rounded overflow-hidden">
            <thead className="bg-white">
              <tr className="text-left text-gray-500">
                <th className="px-2 py-1.5 border-b border-gray-200 w-16">Status</th>
                <th className="px-2 py-1.5 border-b border-gray-200">Description</th>
                <th className="px-2 py-1.5 border-b border-gray-200 w-40">Content-Type</th>
              </tr>
            </thead>
            <tbody className="bg-white">
              {Object.entries(op.responses).map(([status, r]) => (
                <tr key={status} className="border-t border-gray-100">
                  <td className="px-2 py-1.5 font-mono text-gray-800">{status}</td>
                  <td className="px-2 py-1.5 text-gray-600">{r.description ?? "-"}</td>
                  <td className="px-2 py-1.5 font-mono text-gray-500">
                    {r.content ? Object.keys(r.content).join(", ") : "-"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ── Operation 한 줄 (클릭하면 확장) ─────────────────────── */

function OperationRow({ ep }: { ep: Endpoint }) {
  const [open, setOpen] = useState(false);
  const methodClass = METHOD_COLOR[ep.method] ?? "bg-gray-100 text-gray-700";

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
        <span
          className={`text-[11px] font-mono font-semibold px-2 py-0.5 rounded uppercase shrink-0 ${methodClass}`}
        >
          {ep.method}
        </span>
        <span className="font-mono text-sm text-gray-800 truncate">{ep.path}</span>
        {ep.op.summary && (
          <span className="text-sm text-gray-500 truncate ml-auto">{ep.op.summary}</span>
        )}
      </button>
      {open && <OperationDetails op={ep.op} />}
    </div>
  );
}

/* ── 메인 뷰어 ──────────────────────────────────────────── */

interface Props {
  connectorId: string;
}

export default function ApiDocsViewer({ connectorId }: Props) {
  const {
    data: doc,
    loading,
    error,
    refetch,
  } = useResource<OpenApiDoc>(
    () =>
      api
        .get<OpenApiDoc>(`/api/admin/connectors/${connectorId}/api-docs`)
        .then((r) => r.data),
    [connectorId],
  );

  const groups = useMemo(() => {
    if (!doc) return {} as Record<string, Endpoint[]>;
    return groupByTag(flatten(doc));
  }, [doc]);

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow p-8 text-center text-gray-400 text-sm">
        API 문서 로딩 중...
      </div>
    );
  }

  if (error || !doc) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex items-start gap-3">
          <AlertCircle size={18} className="text-error shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="text-sm font-medium text-gray-900">API 문서를 가져올 수 없습니다.</p>
            <p className="text-xs text-gray-500 mt-1">
              {error ?? "응답이 비어있습니다."}
            </p>
            <button
              type="button"
              onClick={() => void refetch()}
              className="mt-3 text-xs text-primary hover:underline"
            >
              다시 시도
            </button>
          </div>
        </div>
      </div>
    );
  }

  const info = doc.info ?? {};
  const servers = doc.servers ?? [];
  const tagNames = Object.keys(groups).sort((a, b) => a.localeCompare(b));

  return (
    <div className="space-y-4">
      {/* ── Info 헤더 ───────────────────────── */}
      <div className="bg-white rounded-lg shadow p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-1">
              <h2 className="text-lg font-bold text-gray-900">{info.title ?? connectorId}</h2>
              {info.version && (
                <span className="text-xs font-mono px-2 py-0.5 rounded bg-gray-100 text-gray-600">
                  v{info.version}
                </span>
              )}
            </div>
            {info.description && (
              <p className="text-sm text-gray-600 whitespace-pre-wrap">{info.description}</p>
            )}
            {servers.length > 0 && (
              <div className="mt-2">
                <span className="text-[10px] uppercase tracking-wider text-gray-400">Servers</span>
                <ul className="mt-0.5 space-y-0.5">
                  {servers.map((s, i) => (
                    <li key={i} className="text-xs font-mono text-gray-700">
                      {s.url}
                      {s.description && (
                        <span className="text-gray-400"> — {s.description}</span>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
          <button
            type="button"
            onClick={() => void refetch()}
            className="text-xs text-primary hover:underline shrink-0"
          >
            새로고침
          </button>
        </div>
      </div>

      {/* ── Endpoints ───────────────────────── */}
      {tagNames.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-8 text-center text-gray-400 text-sm">
          등록된 엔드포인트가 없습니다.
        </div>
      ) : (
        tagNames.map((tag) => (
          <div key={tag} className="space-y-2">
            <h3 className="text-sm font-semibold text-gray-700 px-1">{tag}</h3>
            <div className="space-y-1.5">
              {groups[tag].map((ep, i) => (
                <OperationRow key={`${ep.method}:${ep.path}:${i}`} ep={ep} />
              ))}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
