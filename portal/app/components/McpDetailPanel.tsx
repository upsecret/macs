import { useEffect, useState } from "react";
import { Play, Activity, RefreshCw } from "lucide-react";
import api from "../utils/api";
import type {
  McpContentBlock,
  McpServer,
  McpTool,
  McpToolCallResponse,
} from "../types";

type Health = "unknown" | "ok" | "fail" | "checking";

function tryStringify(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function ContentBlockView({ block }: { block: McpContentBlock }) {
  if (block.type === "text") {
    return (
      <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-sm whitespace-pre-wrap font-mono">
        {block.text ?? ""}
      </pre>
    );
  }
  if (block.type === "image" && typeof block.data === "string") {
    const mime = block.mimeType ?? "image/png";
    return (
      <img
        src={`data:${mime};base64,${block.data}`}
        alt="MCP image"
        className="max-w-full rounded border border-gray-200"
      />
    );
  }
  return (
    <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-xs whitespace-pre-wrap font-mono">
      {tryStringify(block)}
    </pre>
  );
}

/**
 * MCP 서버 상세 — endpoint/transport/auth 메타 + 도구 목록 + 호출 패널.
 * connector 페이지 상세 뷰에서 type=mcp 일 때 dispatch 되어 렌더된다.
 */
export default function McpDetailPanel({ server }: { server: McpServer }) {
  const [tools, setTools] = useState<McpTool[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [toolsError, setToolsError] = useState<string | null>(null);
  const [selectedToolName, setSelectedToolName] = useState<string | null>(null);
  const [argsJson, setArgsJson] = useState("{}");
  const [running, setRunning] = useState(false);
  const [callResult, setCallResult] = useState<McpToolCallResponse | null>(null);
  const [callError, setCallError] = useState<string | null>(null);
  const [health, setHealth] = useState<Health>("unknown");

  const selectedTool = tools.find((t) => t.name === selectedToolName) ?? null;

  useEffect(() => {
    loadTools();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [server.id]);

  const loadTools = async () => {
    setToolsLoading(true);
    setToolsError(null);
    setCallResult(null);
    setCallError(null);
    try {
      const r = await api.get<McpTool[]>(`/api/admin/mcp/servers/${server.id}/tools`);
      setTools(r.data);
      setSelectedToolName(r.data[0]?.name ?? null);
      setHealth("ok");
    } catch (err: unknown) {
      setToolsError(err instanceof Error ? err.message : "도구 목록을 불러오지 못했습니다.");
      setTools([]);
      setSelectedToolName(null);
      setHealth("fail");
    } finally {
      setToolsLoading(false);
    }
  };

  const probeHealth = async () => {
    setHealth("checking");
    try {
      const r = await api.get<{ healthy: boolean }>(
        `/api/admin/mcp/servers/${server.id}/health`,
      );
      setHealth(r.data.healthy ? "ok" : "fail");
    } catch {
      setHealth("fail");
    }
  };

  // 도구 선택 시 inputSchema 기반 stub args 자동 작성
  useEffect(() => {
    if (!selectedTool) {
      setArgsJson("{}");
      return;
    }
    const schema = selectedTool.inputSchema as
      | { properties?: Record<string, { type?: string }> }
      | undefined;
    const props = schema?.properties ?? {};
    const stub: Record<string, unknown> = {};
    for (const [key, def] of Object.entries(props)) {
      const t = def?.type;
      stub[key] =
        t === "number" || t === "integer" ? 0
        : t === "boolean" ? false
        : t === "array" ? []
        : t === "object" ? {}
        : "";
    }
    setArgsJson(JSON.stringify(stub, null, 2));
    setCallResult(null);
    setCallError(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedToolName]);

  const runTool = async () => {
    if (!selectedTool) return;
    let parsed: unknown;
    try {
      parsed = argsJson.trim() ? JSON.parse(argsJson) : {};
    } catch (e) {
      setCallError(`arguments JSON 파싱 실패: ${e instanceof Error ? e.message : String(e)}`);
      return;
    }
    setRunning(true);
    setCallError(null);
    setCallResult(null);
    try {
      const r = await api.post<McpToolCallResponse>(
        `/api/admin/mcp/servers/${server.id}/tools/call`,
        { name: selectedTool.name, arguments: parsed },
      );
      setCallResult(r.data);
    } catch (err: unknown) {
      setCallError(err instanceof Error ? err.message : "도구 호출에 실패했습니다.");
    } finally {
      setRunning(false);
    }
  };

  const healthBadge =
    health === "ok" ? "bg-green-50 text-green-700"
    : health === "fail" ? "bg-red-50 text-red-700"
    : health === "checking" ? "bg-yellow-50 text-yellow-700"
    : "bg-gray-100 text-gray-500";
  const healthLabel =
    health === "ok" ? "Connected"
    : health === "fail" ? "Disconnected"
    : health === "checking" ? "Checking..."
    : "Unknown";

  return (
    <div>
      {/* MCP 메타 + 상태 + 액션 */}
      <div className="bg-white rounded-lg shadow p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <span
            className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-0.5 rounded-full ${healthBadge}`}
          >
            <Activity size={12} strokeWidth={2} />
            {healthLabel}
          </span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-4">
          <div className="md:col-span-2">
            <span className="text-[10px] uppercase tracking-wider text-gray-400">
              Endpoint URL
            </span>
            <p className="text-sm font-mono text-gray-800 break-all">{server.endpointUrl}</p>
          </div>
          <div>
            <span className="text-[10px] uppercase tracking-wider text-gray-400">Transport</span>
            <p className="text-sm font-mono text-gray-800">{server.transport}</p>
          </div>
          <div>
            <span className="text-[10px] uppercase tracking-wider text-gray-400">Auth</span>
            <p className="text-sm font-mono text-gray-800">
              {server.authType}
              {server.hasAuthToken && (
                <span className="ml-2 text-xs text-gray-500">(token set)</span>
              )}
            </p>
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 pt-4 mt-4 border-t border-gray-100">
          <button
            onClick={probeHealth}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            <Activity size={14} strokeWidth={1.75} />
            연결 확인
          </button>
          <button
            onClick={loadTools}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            <RefreshCw size={14} strokeWidth={1.75} />
            도구 새로고침
          </button>
        </div>
      </div>

      {/* Tools list + invoke */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-white rounded-lg shadow p-5 lg:col-span-1">
          <h3 className="text-sm font-semibold text-gray-800 mb-3">Tools</h3>
          {toolsLoading ? (
            <p className="text-sm text-gray-400">불러오는 중...</p>
          ) : toolsError ? (
            <p className="text-sm text-error">{toolsError}</p>
          ) : tools.length === 0 ? (
            <p className="text-sm text-gray-400">서버가 노출한 도구가 없습니다.</p>
          ) : (
            <ul className="space-y-1">
              {tools.map((t) => (
                <li key={t.name}>
                  <button
                    onClick={() => setSelectedToolName(t.name)}
                    className={`w-full text-left px-3 py-2 rounded text-sm ${
                      selectedToolName === t.name
                        ? "bg-primary/10 text-primary font-medium"
                        : "text-gray-700 hover:bg-gray-50"
                    }`}
                  >
                    <div className="font-mono">{t.name}</div>
                    {t.description && (
                      <div className="text-xs text-gray-500 line-clamp-2 mt-0.5">
                        {t.description}
                      </div>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="bg-white rounded-lg shadow p-5 lg:col-span-2">
          {!selectedTool ? (
            <p className="text-sm text-gray-400">왼쪽에서 도구를 선택하세요.</p>
          ) : (
            <>
              <div className="mb-4">
                <h3 className="text-base font-semibold text-gray-900 font-mono">
                  {selectedTool.name}
                </h3>
                {selectedTool.description && (
                  <p className="text-sm text-gray-600 mt-1">{selectedTool.description}</p>
                )}
              </div>

              <div className="mb-3">
                <span className="text-[10px] uppercase tracking-wider text-gray-400">
                  Input Schema
                </span>
                <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-xs font-mono whitespace-pre-wrap mt-1 max-h-40 overflow-auto">
                  {tryStringify(selectedTool.inputSchema)}
                </pre>
              </div>

              <div className="mb-3">
                <label className="block text-sm font-medium text-gray-700 mb-1.5">
                  Arguments (JSON)
                </label>
                <textarea
                  value={argsJson}
                  onChange={(e) => setArgsJson(e.target.value)}
                  rows={6}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono focus:ring-2 focus:ring-primary/40 focus:border-primary"
                />
              </div>

              <div className="flex items-center justify-between">
                <button
                  onClick={runTool}
                  disabled={running}
                  className="flex items-center gap-1.5 px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 disabled:opacity-50"
                >
                  <Play size={14} strokeWidth={2} />
                  {running ? "실행 중..." : "도구 실행"}
                </button>
                {callResult && (
                  <span
                    className={`text-xs ${
                      callResult.error ? "text-error" : "text-green-700"
                    }`}
                  >
                    {callResult.error ? "isError = true" : "succeeded"}
                  </span>
                )}
              </div>

              {callError && (
                <div className="bg-error/5 text-error text-sm px-4 py-3 rounded-lg mt-3">
                  {callError}
                </div>
              )}

              {callResult && (
                <div className="mt-4 space-y-2">
                  <span className="text-[10px] uppercase tracking-wider text-gray-400">
                    Result Content
                  </span>
                  {callResult.content.length === 0 ? (
                    <p className="text-sm text-gray-400">(empty content[])</p>
                  ) : (
                    callResult.content.map((b, i) => <ContentBlockView key={i} block={b} />)
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
