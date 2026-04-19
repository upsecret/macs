import { useMemo, useState } from "react";
import { ChevronDown, ChevronRight, Info, Copy } from "lucide-react";
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

/* ── Gateway route metadata (admin-server) ────────────────── */

interface RouteMetadata {
  pathPredicate: string | null;
  stripPrefix: number | null;
  rewriteRules: Array<{ regexp: string; replacement: string }>;
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

function flatten(doc: OpenApiDoc, meta: RouteMetadata | null): Endpoint[] {
  const list: Endpoint[] = [];
  const paths = doc.paths ?? {};
  for (const [rawPath, item] of Object.entries(paths)) {
    if (!item || typeof item !== "object") continue;
    const path = transformPath(rawPath, meta);
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

/* ── Path 변환 (upstream → gateway) ────────────────────────
 * Gateway 라우트는 `/<prefix>/**` 로 들어온 요청에서 StripPrefix=N 을 떼고
 * upstream 으로 전달한다. OpenAPI 문서는 upstream 기준의 경로만 알고 있으므로,
 * 사용자에게 보여줄 때는 gateway-facing 경로로 되돌려야 한다.
 *
 * 본 구현은 pathPredicate 의 앞쪽 N 세그먼트(와일드카드 제외)를 upstream path
 * 앞에 prepend 하는 방식만 지원한다. RewritePath 의 일반적 regex 반전은 불가능하므로
 * 단순한 prefix-strip 형태(`/x/(?<r>.*)` → `/${r}`)만 보정 대상으로 간주한다.
 */
function transformPath(upstreamPath: string, meta: RouteMetadata | null): string {
  if (!meta) return upstreamPath;
  const prefix = gatewayPrefix(meta);
  if (!prefix) return upstreamPath;
  const normalized = upstreamPath.startsWith("/") ? upstreamPath : "/" + upstreamPath;
  return prefix + normalized;
}

function gatewayPrefix(meta: RouteMetadata): string {
  if (!meta.pathPredicate) return "";
  const n = meta.stripPrefix ?? 0;
  if (n <= 0) return "";
  const segs = meta.pathPredicate
    .split("/")
    .map((s) => s.trim())
    .filter((s) => s.length > 0 && s !== "**" && s !== "*");
  if (segs.length === 0) return "";
  return "/" + segs.slice(0, n).join("/");
}

/* ── 게이트웨이 공통 헤더 안내 ──────────────────────────── */

interface GatewayUsageNoticeProps {
  connectorId: string;
  /** 첫 번째 endpoint path (있으면 curl 예시에 사용) */
  samplePath?: string;
  /** 첫 번째 endpoint method (있으면 curl 예시에 사용) */
  sampleMethod?: string;
  /** 현재 환경의 gateway base URL (host) */
  baseUrl: string;
}

function GatewayUsageNotice({ connectorId, samplePath, sampleMethod, baseUrl }: GatewayUsageNoticeProps) {
  const path = samplePath ?? `/${connectorId}`;
  const method = (sampleMethod ?? "get").toUpperCase();
  const methodFlag = method === "GET" ? "" : ` -X ${method}`;

  const curlSample = `curl${methodFlag} '${baseUrl}${path}' \\
  -H 'Client-App: portal' \\
  -H 'Employee-Number: 2078432' \\
  -H 'Authorization: Bearer <JWT>'   # AuthValidation 적용된 route 만`;

  const copyCurl = () => {
    navigator.clipboard.writeText(curlSample).then(
      () => alert("curl 예시가 복사되었습니다."),
      () => alert("복사 실패"),
    );
  };

  return (
    <div className="bg-info/5 border border-info/20 rounded-lg p-4">
      <div className="flex items-start gap-3">
        <Info size={18} className="text-info shrink-0 mt-0.5" />
        <div className="flex-1 min-w-0">
          <h3 className="text-sm font-semibold text-gray-900">게이트웨이 호출 시 공통 헤더</h3>
          <p className="text-xs text-gray-600 mt-1">
            아래 endpoint 들은 모두 <code className="text-[11px] font-mono">macs-gateway</code> 를 통해 호출되며,
            전역 <code className="text-[11px] font-mono">HeaderValidationFilter</code> 가
            요청에 다음 두 헤더가 없으면 <strong>HTTP 400</strong> 으로 거절합니다.
          </p>
          <ul className="mt-2 space-y-1 text-xs text-gray-700">
            <li>
              <code className="font-mono px-1.5 py-0.5 rounded bg-white border border-gray-200">Client-App</code>
              {" "}— 호출하는 client 식별자 (예: <code className="font-mono">portal</code>)
            </li>
            <li>
              <code className="font-mono px-1.5 py-0.5 rounded bg-white border border-gray-200">Employee-Number</code>
              {" "}— 사번 (예: <code className="font-mono">2078432</code>)
            </li>
            <li>
              <code className="font-mono px-1.5 py-0.5 rounded bg-white border border-gray-200">Authorization: Bearer &lt;JWT&gt;</code>
              {" "}— route 에 <code className="font-mono">AuthValidation</code> 필터가 붙은 경우만. 토큰은
              {" "}<code className="font-mono">POST /api/auth/token</code> 에서 발급
            </li>
          </ul>

          <div className="mt-3">
            <div className="flex items-center justify-between mb-1">
              <span className="text-[10px] uppercase tracking-wider text-gray-500 font-semibold">
                curl 예시
              </span>
              <button
                type="button"
                onClick={copyCurl}
                className="flex items-center gap-1 text-[11px] text-info hover:underline"
              >
                <Copy size={11} /> 복사
              </button>
            </div>
            <pre className="bg-white border border-gray-200 rounded p-2 text-[11px] font-mono text-gray-700 whitespace-pre-wrap overflow-x-auto">
{curlSample}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
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

/* ── 환경별 base URL ─────────────────────────────────────
 * Gateway 가 노출되는 외부 host. 브라우저에서 보고 있는 hostname 을 기준으로
 * prod / qa / 로컬을 구분한다. curl 예시의 host 에 사용.
 */
function effectiveBaseUrl(): string {
  if (typeof window === "undefined") return "http://localhost:8080";
  const host = window.location.hostname;
  if (host === "macs.skhynix.com") return "https://macs.skhynix.com";
  if (host === "qa.macs.skhynix.com") return "https://qa.macs.skhynix.com";
  return "http://localhost:8080";
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

  // Route metadata 는 보조 정보 — 실패해도 뷰어는 그대로 동작해야 하므로 실패 시 null 로 취급.
  const { data: routeMeta } = useResource<RouteMetadata | null>(
    () =>
      api
        .get<RouteMetadata>(`/api/admin/connectors/${connectorId}/route-metadata`)
        .then((r) => r.data)
        .catch(() => null),
    [connectorId],
    { initialData: null },
  );

  const endpoints = useMemo(
    () => (doc ? flatten(doc, routeMeta ?? null) : []),
    [doc, routeMeta],
  );
  const groups = useMemo(() => groupByTag(endpoints), [endpoints]);
  const sample = endpoints[0];
  const baseUrl = effectiveBaseUrl();

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow p-8 text-center text-gray-400 text-sm">
        API 문서 로딩 중...
      </div>
    );
  }

  if (error || !doc) {
    return (
      <div className="bg-info/5 border border-info/20 rounded-lg p-6">
        <div className="flex items-start gap-3">
          <Info size={18} className="text-info shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="text-sm font-medium text-gray-900">API 문서 준비중입니다.</p>
            <button
              type="button"
              onClick={() => void refetch()}
              className="mt-3 text-xs text-info hover:underline"
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

      {/* ── 게이트웨이 호출 가이드 (항상 표시) ───── */}
      <GatewayUsageNotice
        connectorId={connectorId}
        samplePath={sample?.path}
        sampleMethod={sample?.method}
        baseUrl={baseUrl}
      />

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
