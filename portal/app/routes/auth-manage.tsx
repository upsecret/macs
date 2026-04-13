import { useCallback, useEffect, useState } from "react";
import axios from "axios";
import { Trash2, Plus, Search, Key, Copy } from "lucide-react";
import api from "../utils/api";
import { useResource } from "../hooks/useResource";
import type { AuthResponse, Permission, AvailableRoute, RouteDefinition } from "../types";

const rawClient = axios.create({ baseURL: "", timeout: 10000 });

// 새 권한 모델: {client_app}:{system}:{connector}:{role}
// employee_number 중심으로 부여/조회.
// role 값은 admin / operator / viewer (PoC 단계라 enforce 는 connector 매칭만).
const ROLE_OPTIONS = ["admin", "operator", "viewer"] as const;
const COMMON_SYSTEMS = ["common", "rms", "fdc", "mes", "yms"];

function decodeJwt(token: string): { header: unknown; payload: unknown } | null {
  try {
    const [h, p] = token.split(".");
    const decode = (s: string) =>
      JSON.parse(atob(s.replace(/-/g, "+").replace(/_/g, "/")));
    return { header: decode(h), payload: decode(p) };
  } catch {
    return null;
  }
}

export default function AuthManage() {
  // ── 조회 (employee 중심, app 은 optional 필터) ────────────────
  const [searchEmp, setSearchEmp] = useState("");
  const [searchApp, setSearchApp] = useState("");
  const [searched, setSearched] = useState(false);

  // ── 권한 부여 폼 ─────────────────────────────────────────────
  const [formApp, setFormApp] = useState("portal");
  const [formEmp, setFormEmp] = useState("");
  const [formSystem, setFormSystem] = useState("common");
  const [formConnector, setFormConnector] = useState("");
  const [formRole, setFormRole] = useState<(typeof ROLE_OPTIONS)[number]>("viewer");
  const [formError, setFormError] = useState<string | null>(null);
  const [granting, setGranting] = useState(false);

  // ── 토큰 발급 디버그 (employee_number 만) ─────────────────────
  const [tokenEmp, setTokenEmp] = useState("");
  const [tokenResult, setTokenResult] = useState<AuthResponse | null>(null);
  const [tokenError, setTokenError] = useState<string | null>(null);
  const [issuing, setIssuing] = useState(false);

  // ── available routes (mount 시 auto-fetch, 2개 endpoint 병합) ─
  const { data: availableRoutesData } = useResource<string[]>(
    async () => {
      const [routesRes, availRes] = await Promise.all([
        api
          .get<RouteDefinition[]>("/api/config/routes")
          .catch(() => ({ data: [] as RouteDefinition[] })),
        api
          .get<AvailableRoute[]>("/api/admin/connectors/available-routes")
          .catch(() => ({ data: [] as AvailableRoute[] })),
      ]);
      const ids = new Set<string>();
      routesRes.data.forEach((r) => ids.add(r.id));
      availRes.data.forEach((r) => ids.add(r.id));
      return [...ids].sort();
    },
    [],
    { initialData: [] },
  );
  const availableRoutes = availableRoutesData ?? [];

  // 최초 로드 후 formConnector 기본값 세팅 (빈 값이면 첫 route id 로 채움)
  useEffect(() => {
    if (!formConnector && availableRoutes.length > 0) {
      setFormConnector(availableRoutes[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [availableRoutes]);

  // ── permissions 검색 (on-demand, enabled=false) ──────────────
  const {
    data: permissionsData,
    loading,
    refetch: runSearch,
  } = useResource<Permission[]>(
    () => {
      const params: Record<string, string> = { employeeNumber: searchEmp.trim() };
      if (searchApp.trim()) params.appName = searchApp.trim();
      return api.get<Permission[]>("/api/admin/permissions", { params }).then((r) => r.data);
    },
    [searchEmp, searchApp],
    { enabled: false, initialData: [] },
  );
  const permissions = permissionsData ?? [];

  const handleSearch = useCallback(async () => {
    if (!searchEmp.trim()) return;
    setSearched(true);
    await runSearch();
  }, [searchEmp, runSearch]);

  const handleRevoke = async (p: Permission) => {
    if (!confirm(`권한 해제: ${p.employeeNumber} → ${p.appName}/${p.system}/${p.connector} (${p.role})?`)) return;
    try {
      await api.delete("/api/admin/permissions", {
        params: {
          appName: p.appName,
          employeeNumber: p.employeeNumber,
          system: p.system,
          connector: p.connector,
        },
      });
      handleSearch();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "삭제 실패";
      alert(message);
    }
  };

  const handleGrant = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (!formApp.trim() || !formEmp.trim() || !formSystem.trim() || !formConnector || !formRole) {
      setFormError("모든 필드를 입력하세요.");
      return;
    }
    setGranting(true);
    try {
      await api.post("/api/admin/permissions", {
        appName: formApp.trim(),
        employeeNumber: formEmp.trim(),
        system: formSystem.trim(),
        connector: formConnector,
        role: formRole,
      });
      // 검색 결과가 부여 대상 사번이면 자동 새로고침
      if (searched && searchEmp === formEmp.trim()) {
        handleSearch();
      }
      setFormEmp("");
      alert("권한이 부여되었습니다.");
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "권한 부여 실패";
      setFormError(message);
    } finally {
      setGranting(false);
    }
  };

  const handleIssueToken = async (e: React.FormEvent) => {
    e.preventDefault();
    setTokenError(null);
    setTokenResult(null);
    if (!tokenEmp.trim()) {
      setTokenError("employee_number 필수");
      return;
    }
    setIssuing(true);
    try {
      const { data } = await rawClient.post<AuthResponse>(
        "/api/auth/token",
        { employee_number: tokenEmp.trim() },
        {
          headers: {
            "Content-Type": "application/json",
            // app_name 헤더는 gateway HeaderValidationFilter 가 강제하므로 임의값 portal 로 채움.
            // 토큰 자체엔 app_name 이 들어가지 않는다.
            app_name: "portal",
            employee_number: tokenEmp.trim(),
          },
        },
      );
      setTokenResult(data);
    } catch (err: unknown) {
      let message = "토큰 발급 실패";
      if (axios.isAxiosError(err)) {
        const body = err.response?.data as { message?: string; error?: string } | undefined;
        message = body?.message ?? body?.error ?? err.message;
      } else if (err instanceof Error) {
        message = err.message;
      }
      setTokenError(message);
    } finally {
      setIssuing(false);
    }
  };

  const copyToken = () => {
    if (tokenResult?.token) {
      navigator.clipboard.writeText(tokenResult.token).then(
        () => alert("토큰이 복사되었습니다."),
        () => alert("복사 실패"),
      );
    }
  };

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">권한 관리</h1>
        <p className="text-sm text-gray-500 mt-1">
          사번을 중심으로 <code className="text-xs">{"{client_app}:{system}:{connector}:{role}"}</code> 권한을 부여·조회·해제합니다. connector 는 gateway route id 와 같습니다.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* ── 조회 (employee 중심) ─────────────────────── */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold text-gray-800 mb-4">사용자 권한 조회</h2>

          <div className="flex items-end gap-2 mb-4">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">
                Employee Number <span className="text-error">*</span>
              </label>
              <input
                type="text"
                value={searchEmp}
                onChange={(e) => setSearchEmp(e.target.value)}
                placeholder="2078432"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">App Name <span className="text-gray-400">(optional)</span></label>
              <input
                type="text"
                value={searchApp}
                onChange={(e) => setSearchApp(e.target.value)}
                placeholder="portal"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
              />
            </div>
            <button
              onClick={handleSearch}
              className="flex items-center gap-1.5 px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 transition-colors shrink-0"
            >
              <Search size={16} strokeWidth={2} />
              조회
            </button>
          </div>

          {loading ? (
            <div className="text-center py-8 text-gray-400 text-sm">로딩 중...</div>
          ) : searched && permissions.length === 0 ? (
            <div className="text-center py-8 text-gray-400 text-sm">권한이 없습니다.</div>
          ) : permissions.length > 0 ? (
            <div className="overflow-hidden border border-gray-200 rounded-lg">
              <table className="w-full text-sm">
                <thead className="bg-header">
                  <tr>
                    <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600 uppercase">App</th>
                    <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600 uppercase">System</th>
                    <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600 uppercase">Connector</th>
                    <th className="px-3 py-2 text-left text-xs font-semibold text-gray-600 uppercase">Role</th>
                    <th className="px-3 py-2 w-10"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {permissions.map((p) => (
                    <tr key={`${p.appName}:${p.system}:${p.connector}`} className="hover:bg-gray-50">
                      <td className="px-3 py-2 text-gray-700">{p.appName}</td>
                      <td className="px-3 py-2 text-gray-700">{p.system}</td>
                      <td className="px-3 py-2 font-mono text-gray-700">{p.connector}</td>
                      <td className="px-3 py-2">
                        <span className="inline-block px-2 py-0.5 rounded bg-secondary text-primary text-xs font-mono">
                          {p.role}
                        </span>
                      </td>
                      <td className="px-3 py-2">
                        <button
                          onClick={() => handleRevoke(p)}
                          className="text-gray-400 hover:text-error transition-colors"
                          title="권한 해제"
                        >
                          <Trash2 size={14} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-center py-8 text-gray-400 text-sm">사번을 입력하세요.</div>
          )}
        </div>

        {/* ── 권한 부여 ──────────────────────────────── */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold text-gray-800 mb-4">권한 부여</h2>

          <form onSubmit={handleGrant} className="space-y-4">
            <div>
              <label className="block text-xs text-gray-500 mb-1">
                Employee Number <span className="text-error">*</span>
              </label>
              <input
                type="text"
                value={formEmp}
                onChange={(e) => setFormEmp(e.target.value)}
                placeholder="2065162"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-gray-500 mb-1">Client App</label>
                <input
                  type="text"
                  value={formApp}
                  onChange={(e) => setFormApp(e.target.value)}
                  placeholder="portal"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  required
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">System</label>
                <input
                  type="text"
                  list="system-options"
                  value={formSystem}
                  onChange={(e) => setFormSystem(e.target.value)}
                  placeholder="common"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  required
                />
                <datalist id="system-options">
                  {COMMON_SYSTEMS.map((s) => (
                    <option key={s} value={s} />
                  ))}
                </datalist>
              </div>
            </div>

            <div>
              <label className="block text-xs text-gray-500 mb-1">Connector (Route ID)</label>
              {availableRoutes.length === 0 ? (
                <p className="text-sm text-gray-400 px-3 py-2 border border-dashed border-gray-300 rounded-lg">
                  사용 가능한 라우트가 없습니다. 경로설정에서 먼저 라우트를 만드세요.
                </p>
              ) : (
                <select
                  value={formConnector}
                  onChange={(e) => setFormConnector(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  required
                >
                  {availableRoutes.map((id) => (
                    <option key={id} value={id}>{id}</option>
                  ))}
                </select>
              )}
            </div>

            <div>
              <label className="block text-xs text-gray-500 mb-1">Role</label>
              <select
                value={formRole}
                onChange={(e) => setFormRole(e.target.value as (typeof ROLE_OPTIONS)[number])}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
                required
              >
                {ROLE_OPTIONS.map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </select>
              <p className="text-[11px] text-gray-400 mt-1">
                role 은 현재 enforce 되지 않고 connector 매칭만 사용됩니다 (README §9 TODO 참조).
              </p>
            </div>

            {formError && (
              <div className="bg-error/5 text-error text-sm px-4 py-3 rounded-lg">{formError}</div>
            )}

            <button
              type="submit"
              disabled={granting || availableRoutes.length === 0}
              className="w-full flex items-center justify-center gap-1.5 px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Plus size={16} strokeWidth={2} />
              {granting ? "부여 중..." : "권한 부여"}
            </button>
          </form>
        </div>
      </div>

      {/* ── 토큰 발급 디버그 ─────────────────────────── */}
      <div className="bg-white rounded-lg shadow p-6 mt-6">
        <h2 className="text-lg font-semibold text-gray-800 mb-1">토큰 발급 (디버그)</h2>
        <p className="text-xs text-gray-500 mb-4">
          임의의 employee_number 에 대해 JWT 를 받아본다. 토큰엔 sub(=employee_number) 만 들어 있고 권한은 매 요청 auth-server 가 PERMISSION 테이블에서 확인한다. 현재 로그인 세션에는 영향을 주지 않는다.
        </p>

        <form onSubmit={handleIssueToken} className="flex items-end gap-2 mb-4">
          <div className="flex-1">
            <label className="block text-xs text-gray-500 mb-1">Employee Number</label>
            <input
              type="text"
              value={tokenEmp}
              onChange={(e) => setTokenEmp(e.target.value)}
              placeholder="2078432"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
              required
            />
          </div>
          <button
            type="submit"
            disabled={issuing}
            className="flex items-center gap-1.5 px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors shrink-0"
          >
            <Key size={16} strokeWidth={2} />
            {issuing ? "발급 중..." : "토큰 발급"}
          </button>
        </form>

        {tokenError && (
          <div className="bg-error/5 text-error text-sm px-4 py-3 rounded-lg mb-4">{tokenError}</div>
        )}

        {tokenResult && (
          <div className="space-y-4">
            {/* 토큰 */}
            <div>
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">JWT Token</span>
                <button
                  type="button"
                  onClick={copyToken}
                  className="flex items-center gap-1 text-xs text-primary hover:underline"
                >
                  <Copy size={12} /> 복사
                </button>
              </div>
              <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-[11px] font-mono text-gray-700 whitespace-pre-wrap break-all">
                {tokenResult.token}
              </pre>
            </div>

            {/* JWT decode */}
            {(() => {
              const decoded = decodeJwt(tokenResult.token);
              if (!decoded) return null;
              return (
                <div>
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">JWT Payload</span>
                  <pre className="bg-gray-50 border border-gray-200 rounded p-3 text-[11px] font-mono text-gray-700 whitespace-pre-wrap mt-1">
                    {JSON.stringify(decoded.payload, null, 2)}
                  </pre>
                </div>
              );
            })()}
          </div>
        )}
      </div>
    </div>
  );
}
