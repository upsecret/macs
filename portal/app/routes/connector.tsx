import { useState, useEffect, useCallback } from "react";
import api from "../utils/api";
import type { SystemConnector } from "../types";

/* ── 커넥터 카드 (시스템별 그룹) ───────────────────────────── */
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

/* ── 커넥터 → API 경로 (healthcheck용) ──────────────────────── */
const CONNECTOR_HEALTH: Record<string, string> = {
  "qa-tool": "/api/qa/hello",
  "portal": "/actuator/health",
};

export default function Connector() {
  const [connectors, setConnectors] = useState<ConnectorCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");
  const [selectedConnector, setSelectedConnector] = useState<string | null>(null);

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
        api
          .get(healthPath, { timeout: 5000, headers: { app_name: "portal", employee_number: "SYSTEM" } })
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
                      return (
                        <div
                          key={c.connectorName}
                          onClick={() => setSelectedConnector(isSelected ? null : c.connectorName)}
                          className={`bg-white rounded-lg shadow overflow-hidden cursor-pointer transition-all hover:shadow-md ${
                            isSelected ? "ring-2 ring-primary" : "hover:ring-1 hover:ring-gray-200"
                          }`}
                        >
                          {/* 카드 헤더 */}
                          <div className="bg-header px-4 py-3 border-b border-gray-200 flex items-center justify-between">
                            <span className="text-sm font-semibold text-gray-800 font-mono">
                              {c.connectorName}
                            </span>
                            <span className={`inline-flex items-center gap-1.5 text-[11px] px-2 py-0.5 rounded-full ${st.bg}`}>
                              <span className={`w-1.5 h-1.5 rounded-full ${st.dot}`} />
                              {st.label}
                            </span>
                          </div>

                          {/* 카드 바디 */}
                          <div className="px-4 py-3 space-y-2">
                            <div>
                              <span className="text-[10px] uppercase tracking-wider text-gray-400">System</span>
                              <p className="text-sm text-gray-700 font-medium uppercase">{c.systemName}</p>
                            </div>
                            {c.description && (
                              <div>
                                <span className="text-[10px] uppercase tracking-wider text-gray-400">Description</span>
                                <p className="text-sm text-gray-600">{c.description}</p>
                              </div>
                            )}
                            <div className="flex gap-2 pt-1">
                              {c.swaggerUrl && (
                                <span className="bg-secondary text-primary text-xs px-2 py-0.5 rounded font-mono">
                                  Swagger
                                </span>
                              )}
                              {CONNECTOR_HEALTH[c.connectorName] && (
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
              className="w-[480px] shrink-0 bg-white rounded-lg shadow overflow-hidden flex flex-col"
              style={{ height: "calc(100vh - 180px)" }}
            >
              <div className="bg-header px-4 py-3 border-b border-gray-200 flex items-center justify-between shrink-0">
                <div>
                  <h3 className="text-sm font-semibold text-gray-800">API 문서</h3>
                  <p className="text-xs text-gray-500 font-mono mt-0.5">
                    {selected.systemName} / {selected.connectorName}
                  </p>
                </div>
                <button
                  onClick={() => setSelectedConnector(null)}
                  className="text-gray-400 hover:text-gray-600 text-lg leading-none"
                >
                  ✕
                </button>
              </div>
              <iframe
                src={`/swagger-ui.html?urls.primaryName=${encodeURIComponent("qa-service")}`}
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
