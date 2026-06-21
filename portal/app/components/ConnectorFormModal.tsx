"use client";

import { useEffect, useState } from "react";
import { X } from "lucide-react";
import api from "../utils/api";
import type {
  AvailableRoute,
  Connector,
  ConnectorType,
  McpAuthType,
  McpServer,
  McpTransport,
} from "../types";

/**
 * 통합 등록/편집 모달.
 *
 * type 을 가장 위에 두고:
 *  - agent / api  → 기존 gateway route 선택 + docsUrl(선택)
 *  - mcp          → 기존 MCP gateway route(Path=/mcp/{id}) 선택 + transport + auth
 *                   (업스트림은 라우트 uri 가 단일 소스. 호출도 라우트 loopback 경유.)
 *
 * 두 가지 백엔드 리소스(`/api/admin/connectors`, `/api/admin/mcp/servers`)를
 * 같은 모달이 dispatch.
 */

type Initial = Connector | McpServer;

interface Props {
  mode: "create" | "edit";
  /** edit 모드일 때 시작 데이터. type 필드로 어느 백엔드인지 식별. */
  initial?: Initial;
  onClose: () => void;
  onSaved: () => void;
}

const TYPE_OPTIONS: ConnectorType[] = ["agent", "api", "mcp"];
const COMMON_SYSTEMS = ["common", "rms", "fdc", "mes", "yms"];
const TRANSPORTS: McpTransport[] = ["streamable-http"];
const AUTH_TYPES: McpAuthType[] = ["none", "bearer"];

function isMcpInitial(x: Initial | undefined): x is McpServer {
  return !!x && (x as McpServer).endpointUrl !== undefined;
}

export default function ConnectorFormModal({ mode, initial, onClose, onSaved }: Props) {
  // type 결정: edit 모드면 initial 의 종류로 fix. create 모드는 사용자가 선택, 기본 api.
  const initialType: ConnectorType = isMcpInitial(initial)
    ? "mcp"
    : (initial as Connector | undefined)?.type ?? "api";
  const [type, setType] = useState<ConnectorType>(initialType);
  const isMcp = type === "mcp";

  // 공통 필드
  const [id, setId] = useState(initial?.id ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [system, setSystem] = useState(initial?.system ?? "common");

  // REST 측 (agent/api)
  const [availableRoutes, setAvailableRoutes] = useState<AvailableRoute[]>([]);
  const [title, setTitle] = useState(
    !isMcpInitial(initial) && initial ? (initial as Connector).title : "",
  );
  const [docsUrl, setDocsUrl] = useState(
    !isMcpInitial(initial) && initial ? (initial as Connector).docsUrl ?? "" : "",
  );

  // MCP 측
  const [name, setName] = useState(isMcpInitial(initial) ? initial.name : "");
  const [transport, setTransport] = useState<McpTransport>(
    isMcpInitial(initial) ? initial.transport : "streamable-http",
  );
  const [authType, setAuthType] = useState<McpAuthType>(
    isMcpInitial(initial) ? initial.authType : "none",
  );
  const [authToken, setAuthToken] = useState("");

  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // create 모드: type 에 맞는 available routes 로드. MCP 도 API 와 동일하게
  // 경로설정에서 만든 라우트를 선택해 연동한다.
  useEffect(() => {
    if (mode !== "create") return;
    const url = isMcp
      ? "/api/admin/mcp/available-routes"
      : "/api/admin/connectors/available-routes";
    api
      .get<AvailableRoute[]>(url)
      .then((r) => {
        setAvailableRoutes(r.data);
        setId(r.data.length > 0 ? r.data[0].id : "");
      })
      .catch(() => {
        setAvailableRoutes([]);
        setId("");
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, isMcp]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmedSystem = system.trim();
    if (!trimmedSystem) {
      setError("system 은 필수입니다.");
      return;
    }
    setSaving(true);
    try {
      if (isMcp) {
        if (!id.trim() || !name.trim()) {
          throw new Error("라우트와 name 은 필수입니다.");
        }
        if (authType === "bearer" && mode === "create" && !authToken.trim()) {
          throw new Error("authType=bearer 일 때 authToken 이 필요합니다.");
        }
        const payload = {
          id: id.trim(),
          name: name.trim(),
          description: description?.trim() || null,
          // endpointUrl 은 보내지 않는다 — 업스트림은 선택한 라우트 uri 가 단일 소스.
          transport,
          authType,
          authToken: authToken.trim() ? authToken.trim() : null,
          system: trimmedSystem,
        };
        if (mode === "create") {
          await api.post("/api/admin/mcp/servers", payload);
        } else {
          await api.put(`/api/admin/mcp/servers/${id.trim()}`, payload);
        }
      } else {
        if (!id || !title.trim()) {
          throw new Error("id, title 은 필수입니다.");
        }
        const trimmedDocsUrl = docsUrl.trim();
        if (trimmedDocsUrl && !/^https?:\/\//.test(trimmedDocsUrl)) {
          throw new Error("API 문서 URL 은 http:// 또는 https:// 로 시작해야 합니다.");
        }
        const payload = {
          id,
          title: title.trim(),
          description: description?.trim() || null,
          type,
          system: trimmedSystem,
          docsUrl: trimmedDocsUrl || null,
        };
        if (mode === "create") {
          await api.post("/api/admin/connectors", payload);
        } else {
          await api.put(`/api/admin/connectors/${id}`, payload);
        }
      }
      onSaved();
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">
            {mode === "create" ? "커넥터 등록" : "커넥터 편집"}
          </h2>
          <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          {/* ── Type 선택 (최상단) ───────────────────────── */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Type</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as ConnectorType)}
              disabled={mode === "edit"}
              className={`w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary ${
                mode === "edit" ? "border-gray-200 bg-gray-50 text-gray-600" : "border-gray-300"
              }`}
              required
            >
              {TYPE_OPTIONS.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
            {mode === "create" && (
              <p className="mt-1 text-xs text-gray-500">
                {isMcp
                  ? "경로설정에서 만든 MCP 라우트(Path=/mcp/{id})를 선택해 연동합니다."
                  : "기존 gateway route 에 메타데이터를 부여합니다."}
              </p>
            )}
          </div>

          {/* ── ID ───────────────────────────────────────── */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Gateway Route ID
            </label>
            {mode === "create" ? (
              availableRoutes.length === 0 ? (
                <p className="text-sm text-gray-400 px-3 py-2 border border-dashed border-gray-300 rounded-lg">
                  등록 가능한 {isMcp ? "MCP " : ""}라우트가 없습니다. 경로설정에서 먼저{" "}
                  {isMcp ? "Path=/mcp/{id} 라우트를" : "라우트를"} 만드세요.
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
                className="w-full px-3 py-2 border rounded-lg text-sm font-mono border-gray-200 bg-gray-50 text-gray-600"
                required
              />
            )}
          </div>

          {/* ── Title / Name ─────────────────────────────── */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              {isMcp ? "Display Name" : "Title"}
            </label>
            <input
              type="text"
              value={isMcp ? name : title}
              onChange={(e) => (isMcp ? setName(e.target.value) : setTitle(e.target.value))}
              placeholder={isMcp ? "Dummy MCP Server" : "커넥터 제목"}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
              required
            />
          </div>

          {/* ── Description ──────────────────────────────── */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Description</label>
            <textarea
              value={description ?? ""}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              placeholder="설명"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary resize-none"
            />
          </div>

          {/* ── System ───────────────────────────────────── */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">System</label>
            <input
              type="text"
              list="connector-system-options"
              value={system}
              onChange={(e) => setSystem(e.target.value)}
              placeholder="common"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
              required
            />
            <datalist id="connector-system-options">
              {COMMON_SYSTEMS.map((s) => <option key={s} value={s} />)}
            </datalist>
          </div>

          {/* ── MCP 전용 필드 ───────────────────────────── */}
          {isMcp ? (
            <>
              {(() => {
                const sel = availableRoutes.find((r) => r.id === id);
                return sel ? (
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">
                      Upstream <span className="text-gray-400 text-xs font-normal">(라우트 uri · 자동)</span>
                    </label>
                    <input
                      type="text"
                      value={sel.uri}
                      readOnly
                      className="w-full px-3 py-2 border rounded-lg text-sm font-mono border-gray-200 bg-gray-50 text-gray-500"
                    />
                  </div>
                ) : null;
              })()}

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">
                    Transport
                  </label>
                  <select
                    value={transport}
                    onChange={(e) => setTransport(e.target.value as McpTransport)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  >
                    {TRANSPORTS.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">
                    Authentication
                  </label>
                  <select
                    value={authType}
                    onChange={(e) => setAuthType(e.target.value as McpAuthType)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  >
                    {AUTH_TYPES.map((a) => <option key={a} value={a}>{a}</option>)}
                  </select>
                </div>
              </div>

              {authType === "bearer" && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">
                    Bearer Token
                    {mode === "edit" && (
                      <span className="text-gray-400 text-xs font-normal ml-1">
                        (변경 시에만 입력)
                      </span>
                    )}
                  </label>
                  <input
                    type="password"
                    value={authToken}
                    onChange={(e) => setAuthToken(e.target.value)}
                    placeholder={
                      mode === "edit" && isMcpInitial(initial) && initial.hasAuthToken
                        ? "(stored)"
                        : ""
                    }
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  />
                </div>
              )}
            </>
          ) : (
            /* ── REST(agent/api) 전용 필드 ─────────────── */
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                API 문서 URL{" "}
                <span className="text-gray-400 text-xs font-normal">(optional)</span>
              </label>
              <input
                type="text"
                value={docsUrl}
                onChange={(e) => setDocsUrl(e.target.value)}
                placeholder="기본: gateway /v3/api-docs/{routeId}"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono focus:ring-2 focus:ring-primary/40 focus:border-primary"
              />
              <p className="mt-1 text-xs text-gray-500">
                서비스의 OpenAPI JSON 이 gateway 를 통한 기본 경로가 아닐 때 절대 URL 을
                지정합니다.
              </p>
            </div>
          )}

          {error && (
            <div className="bg-error/5 text-error text-sm px-4 py-3 rounded-lg">{error}</div>
          )}

          <div className="flex items-center justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={
                saving || (mode === "create" && availableRoutes.length === 0)
              }
              className="px-4 py-2 text-sm text-white bg-primary rounded-lg hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {saving ? "저장 중..." : "저장"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
