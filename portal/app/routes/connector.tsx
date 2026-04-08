import { useState, useEffect, useCallback } from "react";
import axios from "axios";
import api from "../utils/api";
import { useAuthStore } from "../stores/authStore";
import type { SystemConnector } from "../types";

/* 헬스체크 전용 — 인터셉터(자동 로그아웃) 없는 별도 인스턴스 */
const healthClient = axios.create({ timeout: 5000 });

/* ── 커넥터 카드 ──────────────────────────────────────────── */
interface ConnectorCard {
  systemName: string;
  connectorName: string;
  description: string;
  swaggerUrl: string | null;
  status: "checking" | "healthy" | "unhealthy";
}

/* ── 커넥터 → Swagger URL 매핑 ─────────────────────────────── */
const CONNECTOR_SWAGGER: Record<string, string> = {
  "qa-tool": "/v3/api-docs/qa-service",
  "portal": "/v3/api-docs/auth-server",
};

/* ── 커넥터 → 헬스체크 (인증 불필요 경로만) ──────────────────── */
const CONNECTOR_HEALTH: Record<string, string> = {
  "qa-tool": "/actuator/health",
  "portal": "/actuator/health",
};

export default function Connector() {
  const { allowedResourcesList } = useAuthStore();
  const [connectors, setConnectors] = useState<ConnectorCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");
  const [selectedConnector, setSelectedConnector] = useState<string | null>(null);
  const [swaggerSize, setSwaggerSize] = useState<"half" | "full">("half");

  const fetchConnectors = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await api.get<SystemConnector[]>("/api/auth/systems");

      const cards: ConnectorCard[] = data
        .filter((sc) => sc.connectorName !== "portal") // portal 자체는 제외
        .map((sc) => ({
          systemName: sc.systemName,
          connectorName: sc.connectorName,
          description: sc.description ?? "",
          swaggerUrl: CONNECTOR_SWAGGER[sc.connectorName] ?? null,
          status: "checking" as const,
        }));

      setConnectors(cards);

      // 헬스 체크 (비동기)
      cards.forEach((card) => {
        const healthPath = CONNECTOR_HEALTH[card.connectorName];
        if (!healthPath) {
          setConnectors((prev) =>
            prev.map((c) =>
              c.connectorName === card.connectorName ? { ...c, status: "healthy" } : c,
            ),
          );
          return;
        }
        healthClient
          .get(healthPath)
          .then(() =>
            setConnectors((prev) =>
              prev.map((c) =>
                c.connectorName === card.connectorName ? { ...c, status: "healthy" } : c,
              ),
            ),
          )
          .catch(() =>
            setConnectors((prev) =>
              prev.map((c) =>
                c.connectorName === card.connectorName ? { ...c, status: "unhealthy" } : c,
              ),
            ),
          );
      });
    } catch {
      setConnectors([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchConnectors();
  }, [fetchConnectors]);

  /* ── 필터링 ──────────────────────────────────────────────── */
  const filtered = connectors.filter(
    (c) =>
      !filter ||
      c.connectorName.toLowerCase().includes(filter.toLowerCase()) ||
      c.systemName.toLowerCase().includes(filter.toLowerCase()) ||
      c.description.toLowerCase().includes(filter.toLowerCase()),
  );

  const selected = connectors.find((c) => c.connectorName === selectedConnector);

  const statusConfig = {
    checking: { label: "확인 중", dot: "bg-gray-400 animate-pulse", bg: "bg-gray-50" },
    healthy: { label: "정상", dot: "bg-green-500", bg: "bg-green-50" },
    unhealthy: { label: "연결 불가", dot: "bg-error", bg: "bg-error/5" },
  };

  /* ── 시스템별 그룹핑 ─────────────────────────────────────── */
  const systems = [...new Set(filtered.map((c) => c.systemName))];

  const healthyCount = connectors.filter((c) => c.status === "healthy").length;
  const unhealthyCount = connectors.filter((c) => c.status === "unhealthy").length;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">커넥터 연동</h1>
          <p className="text-sm text-gray-500 mt-1">
            등록된 System / Connector 현황
          </p>
        </div>
        <button
          onClick={fetchConnectors}
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm hover:bg-gray-50 transition-colors"
        >
          새로고침
        </button>
      </div>

      {/* 통계 */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        {[
          { label: "전체", value: connectors.length, color: "text-gray-900" },
          { label: "정상", value: healthyCount, color: "text-green-600" },
          { label: "연결 불가", value: unhealthyCount, color: "text-error" },
        ].map((stat) => (
          <div key={stat.label} className="bg-white rounded-lg shadow px-5 py-4">
            <p className="text-xs text-gray-500 mb-1">{stat.label}</p>
            <p className={`text-2xl font-bold ${stat.color}`}>{stat.value}</p>
          </div>
        ))}
      </div>

      {/* 검색 */}
      <div className="mb-4">
        <input
          type="text"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="시스템 또는 커넥터 이름으로 검색..."
          className="w-full max-w-md px-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary"
        />
      </div>

      {loading ? (
        <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">로딩 중...</div>
      ) : (
        <div className="flex gap-6 items-start">
          {/* 카드 그리드 */}
          <div className="flex-1 min-w-0 space-y-6">
            {systems.map((sys) => (
              <div key={sys}>
                <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-3">
                  {sys}
                </h2>
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                  {filtered
                    .filter((c) => c.systemName === sys)
                    .map((c) => {
                      const st = statusConfig[c.status];
                      const isSelected = selectedConnector === c.connectorName;
                      const hasPermission = allowedResourcesList.includes(c.connectorName);
                      return (
                        <div
                          key={c.connectorName}
                          onClick={() => hasPermission && setSelectedConnector(isSelected ? null : c.connectorName)}
                          className={[
                            "rounded-lg shadow overflow-hidden transition-all",
                            hasPermission
                              ? `bg-white cursor-pointer hover:shadow-md ${isSelected ? "ring-2 ring-primary" : "hover:ring-1 hover:ring-gray-200"}`
                              : "bg-gray-100 opacity-60 cursor-not-allowed",
                          ].join(" ")}
                        >
                          {/* 카드 헤더 */}
                          <div className={`px-4 py-3 border-b border-gray-200 flex items-center justify-between ${hasPermission ? "bg-header" : "bg-gray-200"}`}>
                            <span className={`text-sm font-semibold font-mono ${hasPermission ? "text-gray-800" : "text-gray-400"}`}>
                              {c.connectorName}
                            </span>
                            <div className="flex items-center gap-2">
                              {!hasPermission && (
                                <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-300 text-gray-500">
                                  권한 없음
                                </span>
                              )}
                              <span className={`inline-flex items-center gap-1.5 text-[11px] px-2 py-0.5 rounded-full ${hasPermission ? st.bg : "bg-gray-200"}`}>
                                <span className={`w-1.5 h-1.5 rounded-full ${hasPermission ? st.dot : "bg-gray-400"}`} />
                                {hasPermission ? st.label : "-"}
                              </span>
                            </div>
                          </div>

                          {/* 카드 바디 */}
                          <div className="px-4 py-3 space-y-2">
                            <div>
                              <span className="text-[10px] uppercase tracking-wider text-gray-400">System</span>
                              <p className={`text-sm font-medium uppercase ${hasPermission ? "text-gray-700" : "text-gray-400"}`}>{c.systemName}</p>
                            </div>
                            {c.description && (
                              <div>
                                <span className="text-[10px] uppercase tracking-wider text-gray-400">Description</span>
                                <p className={`text-sm ${hasPermission ? "text-gray-600" : "text-gray-400"}`}>{c.description}</p>
                              </div>
                            )}
                            <div className="flex gap-2 pt-1">
                              {c.swaggerUrl && hasPermission && (
                                <span className="bg-secondary text-primary text-xs px-2 py-0.5 rounded font-mono">
                                  Swagger
                                </span>
                              )}
                              {CONNECTOR_HEALTH[c.connectorName] && hasPermission && (
                                <span className="bg-gray-100 text-gray-600 text-xs px-2 py-0.5 rounded font-mono">
                                  {CONNECTOR_HEALTH[c.connectorName]}
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                </div>
              </div>
            ))}

            {filtered.length === 0 && (
              <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">
                {filter ? "검색 결과가 없습니다." : "등록된 커넥터가 없습니다."}
              </div>
            )}
          </div>

          {/* Swagger 패널 */}
          {selected?.swaggerUrl && (
            <div
              className={[
                "shrink-0 bg-white rounded-lg shadow overflow-hidden flex flex-col transition-all duration-200",
                swaggerSize === "full"
                  ? "fixed inset-4 z-50"
                  : "w-1/2 min-w-[480px]",
              ].join(" ")}
              style={swaggerSize === "half" ? { height: "calc(100vh - 180px)" } : undefined}
            >
              {/* 풀스크린 배경 딤 */}
              {swaggerSize === "full" && (
                <div className="fixed inset-0 bg-black/30 -z-10" onClick={() => setSwaggerSize("half")} />
              )}

              <div className="bg-header px-4 py-3 border-b border-gray-200 flex items-center justify-between shrink-0">
                <div>
                  <h3 className="text-sm font-semibold text-gray-800">API 문서</h3>
                  <p className="text-xs text-gray-500 font-mono mt-0.5">
                    {selected.systemName} / {selected.connectorName}
                  </p>
                </div>
                <div className="flex items-center gap-1">
                  {/* 반반 보기 */}
                  <button
                    onClick={() => setSwaggerSize("half")}
                    title="반반 보기"
                    className={`p-1.5 rounded transition-colors ${swaggerSize === "half" ? "bg-primary/10 text-primary" : "text-gray-400 hover:text-gray-600"}`}
                  >
                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <rect x="1" y="2" width="14" height="12" rx="1.5" />
                      <line x1="8" y1="2" x2="8" y2="14" />
                    </svg>
                  </button>
                  {/* 전체 보기 */}
                  <button
                    onClick={() => setSwaggerSize("full")}
                    title="전체 화면"
                    className={`p-1.5 rounded transition-colors ${swaggerSize === "full" ? "bg-primary/10 text-primary" : "text-gray-400 hover:text-gray-600"}`}
                  >
                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <rect x="1" y="2" width="14" height="12" rx="1.5" />
                    </svg>
                  </button>
                  {/* 닫기 */}
                  <button
                    onClick={() => { setSelectedConnector(null); setSwaggerSize("half"); }}
                    className="p-1.5 text-gray-400 hover:text-gray-600 transition-colors"
                    title="닫기"
                  >
                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <line x1="4" y1="4" x2="12" y2="12" />
                      <line x1="12" y1="4" x2="4" y2="12" />
                    </svg>
                  </button>
                </div>
              </div>
              <iframe
                src={`/webjars/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config&urls.primaryName=${encodeURIComponent("qa-service")}`}
                title={`Swagger - ${selected.connectorName}`}
                className="flex-1 w-full border-0"
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
