import { useState, useEffect, useCallback } from "react";
import api from "../utils/api";
import type { RouteDefinition, GatewayDefinition } from "../types";

/* ── Helper: definition 요약 텍스트 ────────────────────────── */
function defSummary(d: GatewayDefinition): string {
  const vals = Object.entries(d.args)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, v]) => v);
  return vals.length ? `${d.name}=${vals.join(", ")}` : d.name;
}

/* ── 커넥터 상태 타입 ──────────────────────────────────────── */
interface ConnectorCard {
  id: string;
  uri: string;
  predicates: GatewayDefinition[];
  filters: GatewayDefinition[];
  status: "checking" | "healthy" | "unhealthy";
}

/* ── 내부 인프라 라우트 제외용 ──────────────────────────────── */
const INFRA_IDS = new Set([
  "auth-route",
  "config-route",
  "auth-api-docs",
  "config-api-docs",
]);

export default function Connector() {
  const [connectors, setConnectors] = useState<ConnectorCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  /* ── 라우트 → 커넥터 카드 변환 ──────────────────────────── */
  const fetchConnectors = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await api.get<RouteDefinition[]>("/api/config/routes");

      const cards: ConnectorCard[] = data
        .filter((r) => !INFRA_IDS.has(r.id))
        .map((r) => ({
          id: r.id,
          uri: r.uri,
          predicates: r.predicates,
          filters: r.filters,
          status: "checking" as const,
        }));

      setConnectors(cards);

      // 헬스 체크 (비동기, 개별 실패 허용)
      cards.forEach((card) => {
        checkHealth(card.uri).then((healthy) =>
          setConnectors((prev) =>
            prev.map((c) =>
              c.id === card.id
                ? { ...c, status: healthy ? "healthy" : "unhealthy" }
                : c,
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

  /* ── 헬스 체크 ───────────────────────────────────────────── */
  async function checkHealth(uri: string): Promise<boolean> {
    try {
      // gateway를 통해 대상 서비스의 actuator/health 호출
      const basePath = extractBasePath(uri);
      await api.get(`${basePath}/actuator/health`, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  function extractBasePath(uri: string): string {
    try {
      const url = new URL(uri);
      return `/${url.hostname.split(".")[0]}`;
    } catch {
      return "";
    }
  }

  /* ── 필터링 ──────────────────────────────────────────────── */
  const filtered = connectors.filter(
    (c) =>
      !filter ||
      c.id.toLowerCase().includes(filter.toLowerCase()) ||
      c.uri.toLowerCase().includes(filter.toLowerCase()),
  );

  const selected = connectors.find((c) => c.id === selectedId);

  /* ── 상태 뱃지 ──────────────────────────────────────────── */
  const statusConfig = {
    checking: { label: "확인 중", dot: "bg-gray-400 animate-pulse", bg: "bg-gray-50" },
    healthy: { label: "정상", dot: "bg-green-500", bg: "bg-green-50" },
    unhealthy: { label: "연결 불가", dot: "bg-error", bg: "bg-error/5" },
  };

  /* ── 통계 ────────────────────────────────────────────────── */
  const healthyCount = connectors.filter((c) => c.status === "healthy").length;
  const unhealthyCount = connectors.filter((c) => c.status === "unhealthy").length;

  return (
    <div>
      {/* 페이지 헤더 */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">커넥터 연동</h1>
          <p className="text-sm text-gray-500 mt-1">
            Gateway에 등록된 서비스 커넥터 현황
          </p>
        </div>
        <button
          onClick={fetchConnectors}
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm
                     hover:bg-gray-50 transition-colors"
        >
          새로고침
        </button>
      </div>

      {/* 통계 카드 */}
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
          placeholder="서비스 ID 또는 URI로 검색..."
          className="w-full max-w-md px-4 py-2 border border-gray-300 rounded-lg text-sm
                     focus:ring-2 focus:ring-primary/40 focus:border-primary"
        />
      </div>

      {loading ? (
        <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">
          로딩 중...
        </div>
      ) : (
        <div className="flex gap-6 items-start">
          {/* ── 카드 그리드 ────────────────────────────────── */}
          <div className="flex-1 min-w-0">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {filtered.map((c) => {
                const st = statusConfig[c.status];
                const isSelected = selectedId === c.id;
                return (
                  <div
                    key={c.id}
                    onClick={() => setSelectedId(isSelected ? null : c.id)}
                    className={`bg-white rounded-lg shadow overflow-hidden cursor-pointer
                                transition-all hover:shadow-md ${
                                  isSelected
                                    ? "ring-2 ring-primary"
                                    : "hover:ring-1 hover:ring-gray-200"
                                }`}
                  >
                    {/* 카드 헤더 */}
                    <div className="bg-header px-4 py-3 border-b border-gray-200 flex items-center justify-between">
                      <span className="text-sm font-semibold text-gray-800 font-mono">
                        {c.id}
                      </span>
                      <span
                        className={`inline-flex items-center gap-1.5 text-[11px] px-2 py-0.5 rounded-full ${st.bg}`}
                      >
                        <span className={`w-1.5 h-1.5 rounded-full ${st.dot}`} />
                        {st.label}
                      </span>
                    </div>

                    {/* 카드 바디 */}
                    <div className="px-4 py-3 space-y-2">
                      {/* URI */}
                      <div>
                        <span className="text-[10px] uppercase tracking-wider text-gray-400">
                          URI
                        </span>
                        <p className="text-sm text-gray-700 font-mono truncate">
                          {c.uri}
                        </p>
                      </div>

                      {/* Predicates */}
                      <div>
                        <span className="text-[10px] uppercase tracking-wider text-gray-400">
                          Predicates
                        </span>
                        <div className="flex flex-wrap gap-1 mt-0.5">
                          {c.predicates.map((p, i) => (
                            <span
                              key={i}
                              className="bg-secondary text-primary text-xs px-2 py-0.5 rounded font-mono"
                            >
                              {defSummary(p)}
                            </span>
                          ))}
                        </div>
                      </div>

                      {/* Filters */}
                      {c.filters.length > 0 && (
                        <div>
                          <span className="text-[10px] uppercase tracking-wider text-gray-400">
                            Filters
                          </span>
                          <div className="flex flex-wrap gap-1 mt-0.5">
                            {c.filters.map((f, i) => (
                              <span
                                key={i}
                                className="bg-gray-100 text-gray-600 text-xs px-2 py-0.5 rounded font-mono"
                              >
                                {defSummary(f)}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}

              {filtered.length === 0 && (
                <div className="col-span-full bg-white rounded-lg shadow p-12 text-center text-gray-400">
                  {filter
                    ? "검색 결과가 없습니다."
                    : "등록된 커넥터가 없습니다."}
                </div>
              )}
            </div>
          </div>

          {/* ── 우측: Swagger 패널 ─────────────────────────── */}
          {selected && (
            <div className="w-[480px] shrink-0 bg-white rounded-lg shadow overflow-hidden flex flex-col"
                 style={{ height: "calc(100vh - 180px)" }}>
              <div className="bg-header px-4 py-3 border-b border-gray-200 flex items-center justify-between shrink-0">
                <div>
                  <h3 className="text-sm font-semibold text-gray-800">
                    API 문서
                  </h3>
                  <p className="text-xs text-gray-500 font-mono mt-0.5">
                    {selected.id}
                  </p>
                </div>
                <button
                  onClick={() => setSelectedId(null)}
                  className="text-gray-400 hover:text-gray-600 text-lg leading-none"
                >
                  ✕
                </button>
              </div>
              <iframe
                src={`/swagger-ui.html?urls.primaryName=${encodeURIComponent(selected.id)}`}
                title={`Swagger - ${selected.id}`}
                className="flex-1 w-full border-0"
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
