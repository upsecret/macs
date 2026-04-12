import { useState, useEffect, useCallback } from "react";
import api from "../utils/api";
import type { RouteDefinition, GatewayDefinition } from "../types";

/* ================================================================
   필터/프레디킷 카탈로그 — 이름별 예상 args 정의
   ================================================================ */

interface ArgSpec {
  key: string;
  label: string;
  placeholder: string;
}

interface DefCatalogEntry {
  label: string;
  description: string;
  args: ArgSpec[];
}

const FILTER_CATALOG: Record<string, DefCatalogEntry> = {
  StripPrefix: {
    label: "StripPrefix",
    description: "경로 앞부분 N개 세그먼트 제거",
    args: [{ key: "_genkey_0", label: "Parts", placeholder: "1" }],
  },
  RewritePath: {
    label: "RewritePath",
    description: "요청 경로를 정규식으로 재작성",
    args: [
      { key: "regexp", label: "Regexp", placeholder: "/api/(?<segment>.*)" },
      { key: "replacement", label: "Replacement", placeholder: "/${segment}" },
    ],
  },
  PrefixPath: {
    label: "PrefixPath",
    description: "경로 앞에 prefix 추가",
    args: [{ key: "_genkey_0", label: "Prefix", placeholder: "/api" }],
  },
  SetPath: {
    label: "SetPath",
    description: "경로를 지정한 템플릿으로 변경",
    args: [{ key: "_genkey_0", label: "Template", placeholder: "/{segment}" }],
  },
  AddRequestHeader: {
    label: "AddRequestHeader",
    description: "요청에 헤더 추가",
    args: [
      { key: "name", label: "Header Name", placeholder: "X-Custom-Header" },
      { key: "value", label: "Value", placeholder: "my-value" },
    ],
  },
  AddResponseHeader: {
    label: "AddResponseHeader",
    description: "응답에 헤더 추가",
    args: [
      { key: "name", label: "Header Name", placeholder: "X-Response-Id" },
      { key: "value", label: "Value", placeholder: "my-value" },
    ],
  },
  RemoveRequestHeader: {
    label: "RemoveRequestHeader",
    description: "요청에서 헤더 제거",
    args: [{ key: "_genkey_0", label: "Header Name", placeholder: "Cookie" }],
  },
  RedirectTo: {
    label: "RedirectTo",
    description: "지정 URL로 리다이렉트",
    args: [
      { key: "status", label: "Status", placeholder: "302" },
      { key: "url", label: "URL", placeholder: "https://example.com" },
    ],
  },
  SetStatus: {
    label: "SetStatus",
    description: "응답 상태 코드 변경",
    args: [{ key: "_genkey_0", label: "Status", placeholder: "200" }],
  },
  CircuitBreaker: {
    label: "CircuitBreaker",
    description: "서킷 브레이커 적용",
    args: [
      { key: "name", label: "Name", placeholder: "myCircuitBreaker" },
      { key: "fallbackUri", label: "Fallback URI", placeholder: "forward:/fallback" },
    ],
  },
  Retry: {
    label: "Retry",
    description: "실패 시 재시도",
    args: [
      { key: "retries", label: "Retries", placeholder: "3" },
      { key: "statuses", label: "Statuses", placeholder: "BAD_GATEWAY" },
      { key: "methods", label: "Methods", placeholder: "GET,POST" },
    ],
  },
  RequestRateLimiter: {
    label: "RequestRateLimiter",
    description: "요청 레이트 리밋",
    args: [
      { key: "redis-rate-limiter.replenishRate", label: "Replenish Rate", placeholder: "10" },
      { key: "redis-rate-limiter.burstCapacity", label: "Burst Capacity", placeholder: "20" },
    ],
  },
  DedupeResponseHeader: {
    label: "DedupeResponseHeader",
    description: "중복 응답 헤더 제거",
    args: [
      { key: "_genkey_0", label: "Header Names", placeholder: "Access-Control-Allow-Origin" },
      { key: "strategy", label: "Strategy", placeholder: "RETAIN_UNIQUE" },
    ],
  },
  AuthValidation: {
    label: "AuthValidation",
    description: "auth-server 토큰 검증 — 해당 route id에 대한 사용자 권한 확인",
    args: [],
  },
};

const PREDICATE_CATALOG: Record<string, DefCatalogEntry> = {
  Path: {
    label: "Path",
    description: "경로 패턴 매칭",
    args: [{ key: "_genkey_0", label: "Pattern", placeholder: "/api/**" }],
  },
  Method: {
    label: "Method",
    description: "HTTP 메서드 매칭",
    args: [{ key: "_genkey_0", label: "Methods", placeholder: "GET,POST" }],
  },
  Host: {
    label: "Host",
    description: "호스트 패턴 매칭",
    args: [{ key: "_genkey_0", label: "Pattern", placeholder: "**.example.com" }],
  },
  Header: {
    label: "Header",
    description: "요청 헤더 값 매칭",
    args: [
      { key: "header", label: "Header", placeholder: "X-Request-Id" },
      { key: "regexp", label: "Regexp", placeholder: "\\d+" },
    ],
  },
  Query: {
    label: "Query",
    description: "쿼리 파라미터 매칭",
    args: [
      { key: "param", label: "Param", placeholder: "color" },
      { key: "regexp", label: "Regexp", placeholder: "gr.+" },
    ],
  },
  Cookie: {
    label: "Cookie",
    description: "쿠키 값 매칭",
    args: [
      { key: "name", label: "Name", placeholder: "session" },
      { key: "regexp", label: "Regexp", placeholder: ".+" },
    ],
  },
  After: {
    label: "After",
    description: "지정 시각 이후 매칭",
    args: [{ key: "_genkey_0", label: "Datetime", placeholder: "2025-01-01T00:00:00+09:00" }],
  },
  Weight: {
    label: "Weight",
    description: "가중치 기반 라우팅",
    args: [
      { key: "group", label: "Group", placeholder: "service-group" },
      { key: "weight", label: "Weight", placeholder: "8" },
    ],
  },
};

const ALL_FILTER_NAMES = [...Object.keys(FILTER_CATALOG), "Custom"];
const ALL_PREDICATE_NAMES = [...Object.keys(PREDICATE_CATALOG), "Custom"];

/* ── Helper: GatewayDefinition 요약 텍스트 ─────────────────── */
function defSummary(d: GatewayDefinition): string {
  const vals = Object.entries(d.args)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, v]) => v);
  return vals.length ? `${d.name}=${vals.join(", ")}` : d.name;
}

/* ── 빈 값 ────────────────────────────────────────────────── */
const emptyDef = (name = ""): GatewayDefinition => ({ name, args: {} });

const EMPTY_FORM: RouteDefinition = {
  id: "",
  uri: "",
  predicates: [{ name: "Path", args: { _genkey_0: "" } }],
  filters: [{ name: "StripPrefix", args: { _genkey_0: "1" } }],
  order: 0,
};

/* ================================================================
   DefinitionEditor — 단일 predicate/filter 편집 UI
   ================================================================ */

function DefinitionEditor({
  value,
  catalog,
  allNames,
  onChange,
  onRemove,
  typeLabel,
}: {
  value: GatewayDefinition;
  catalog: Record<string, DefCatalogEntry>;
  allNames: string[];
  onChange: (d: GatewayDefinition) => void;
  onRemove: () => void;
  typeLabel: string;
}) {
  const isCustom = !catalog[value.name];
  const entry = catalog[value.name];
  const argSpecs: ArgSpec[] = entry?.args ?? [];

  const handleNameChange = (name: string) => {
    if (name === "Custom") {
      onChange({ name: "", args: {} });
    } else {
      const newEntry = catalog[name];
      const args: Record<string, string> = {};
      newEntry?.args.forEach((a) => (args[a.key] = ""));
      onChange({ name, args });
    }
  };

  const handleArgChange = (key: string, val: string) => {
    onChange({ ...value, args: { ...value.args, [key]: val } });
  };

  const handleAddArg = () => {
    const key = `arg_${Object.keys(value.args).length}`;
    onChange({ ...value, args: { ...value.args, [key]: "" } });
  };

  const handleRemoveArg = (key: string) => {
    const next = { ...value.args };
    delete next[key];
    onChange({ ...value, args: next });
  };

  return (
    <div className="border border-gray-200 rounded-lg p-3 bg-white">
      <div className="flex items-center gap-2 mb-2">
        <select
          value={isCustom ? "Custom" : value.name}
          onChange={(e) => handleNameChange(e.target.value)}
          className="px-2 py-1.5 border border-gray-300 rounded-md text-sm bg-white
                     focus:ring-2 focus:ring-primary/40 focus:border-primary"
        >
          <option value="" disabled>
            {typeLabel} 선택
          </option>
          {allNames.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>

        {isCustom && (
          <input
            type="text"
            value={value.name}
            onChange={(e) => onChange({ ...value, name: e.target.value })}
            placeholder="Custom name"
            className="px-2 py-1.5 border border-gray-300 rounded-md text-sm font-mono
                       focus:ring-2 focus:ring-primary/40 focus:border-primary"
          />
        )}

        {entry && (
          <span className="text-xs text-gray-400">{entry.description}</span>
        )}

        <div className="flex-1" />
        <button
          onClick={onRemove}
          className="text-gray-400 hover:text-error text-sm transition-colors"
        >
          ✕
        </button>
      </div>

      {/* Args 편집 */}
      <div className="space-y-1.5">
        {/* Catalog에 정의된 args */}
        {argSpecs.map((spec) => (
          <div key={spec.key} className="flex items-center gap-2">
            <label className="text-xs text-gray-500 w-28 shrink-0 text-right">
              {spec.label}
            </label>
            <input
              type="text"
              value={value.args[spec.key] ?? ""}
              onChange={(e) => handleArgChange(spec.key, e.target.value)}
              placeholder={spec.placeholder}
              className="flex-1 px-2 py-1.5 border border-gray-200 rounded text-sm font-mono
                         focus:ring-2 focus:ring-primary/40 focus:border-primary"
            />
          </div>
        ))}

        {/* Catalog 외 추가 args (custom 또는 extra args) */}
        {Object.entries(value.args)
          .filter(([k]) => !argSpecs.some((s) => s.key === k))
          .map(([key, val]) => (
            <div key={key} className="flex items-center gap-2">
              <input
                type="text"
                value={key}
                onChange={(e) => {
                  const next = { ...value.args };
                  delete next[key];
                  next[e.target.value] = val;
                  onChange({ ...value, args: next });
                }}
                placeholder="key"
                className="w-28 shrink-0 px-2 py-1.5 border border-gray-200 rounded text-xs font-mono text-right
                           focus:ring-2 focus:ring-primary/40"
              />
              <input
                type="text"
                value={val}
                onChange={(e) => handleArgChange(key, e.target.value)}
                placeholder="value"
                className="flex-1 px-2 py-1.5 border border-gray-200 rounded text-sm font-mono
                           focus:ring-2 focus:ring-primary/40 focus:border-primary"
              />
              <button
                onClick={() => handleRemoveArg(key)}
                className="text-gray-400 hover:text-error text-xs"
              >
                ✕
              </button>
            </div>
          ))}

        {/* Custom arg 추가 버튼 */}
        {(isCustom || argSpecs.length > 0) && (
          <button
            onClick={handleAddArg}
            className="text-xs text-primary hover:underline mt-1"
          >
            + arg 추가
          </button>
        )}
      </div>
    </div>
  );
}

/* ================================================================
   RouteConfig 메인 페이지
   ================================================================ */

export default function RouteConfig() {
  const [routes, setRoutes] = useState<RouteDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<"create" | "edit">("create");
  const [form, setForm] = useState<RouteDefinition>(EMPTY_FORM);
  const [registerSwagger, setRegisterSwagger] = useState(true);
  const [saving, setSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [toast, setToast] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  const showToast = (type: "success" | "error", message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 3000);
  };

  /* ── 조회 ──────────────────────────────────────────────── */
  const fetchRoutes = useCallback(() => {
    setLoading(true);
    api
      .get<RouteDefinition[]>("/api/config/routes")
      .then((r) => setRoutes(r.data))
      .catch(() => showToast("error", "라우트 목록을 불러올 수 없습니다."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchRoutes();
  }, [fetchRoutes]);

  /* ── 모달 ──────────────────────────────────────────────── */
  const openCreate = () => {
    setForm(EMPTY_FORM);
    setRegisterSwagger(true);
    setModalMode("create");
    setModalOpen(true);
  };
  const openEdit = (route: RouteDefinition) => {
    setForm({ ...route });
    setRegisterSwagger(true);
    setModalMode("edit");
    setModalOpen(true);
  };

  /* ── 저장 ──────────────────────────────────────────────── */
  const handleSave = async () => {
    if (!form.id.trim() || !form.uri.trim()) return;
    setSaving(true);
    const payload = {
      ...form,
      id: form.id.trim(),
      uri: form.uri.trim(),
      predicates: form.predicates.filter((p) => p.name),
      filters: form.filters.filter((f) => f.name),
      registerSwagger,
    };
    try {
      if (modalMode === "create") {
        await api.post("/api/config/routes", payload);
        showToast("success", `라우트 "${payload.id}" 생성 완료`);
      } else {
        await api.put(`/api/config/routes/${payload.id}`, payload);
        showToast("success", `라우트 "${payload.id}" 수정 완료`);
      }
      setModalOpen(false);
      fetchRoutes();
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "저장 실패");
    } finally {
      setSaving(false);
    }
  };

  /* ── 삭제 ──────────────────────────────────────────────── */
  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await api.delete(`/api/config/routes/${deleteTarget}`);
      showToast("success", `라우트 "${deleteTarget}" 삭제 완료`);
      setDeleteTarget(null);
      fetchRoutes();
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "삭제 실패");
    }
  };

  /* ── Bus Refresh ───────────────────────────────────────── */
  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await api.post("/api/config/properties/refresh");
      showToast("success", "설정 변경사항이 전파되었습니다.");
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "Refresh 실패");
    } finally {
      setRefreshing(false);
    }
  };

  /* ── 폼 업데이트 helpers ───────────────────────────────── */
  const updateDef = (
    field: "predicates" | "filters",
    index: number,
    def: GatewayDefinition,
  ) =>
    setForm((f) => ({
      ...f,
      [field]: f[field].map((d, i) => (i === index ? def : d)),
    }));

  const addDef = (field: "predicates" | "filters") =>
    setForm((f) => ({ ...f, [field]: [...f[field], emptyDef()] }));

  const removeDef = (field: "predicates" | "filters", index: number) =>
    setForm((f) => ({ ...f, [field]: f[field].filter((_, i) => i !== index) }));

  /* ── 렌더 ──────────────────────────────────────────────── */
  return (
    <div>
      {/* 헤더 */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">경로설정</h1>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm
                       hover:bg-gray-50 disabled:opacity-50 transition-colors"
          >
            {refreshing ? "전파 중..." : "변경사항 반영"}
          </button>
          <button
            onClick={openCreate}
            className="px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 transition-colors"
          >
            + 라우트 추가
          </button>
        </div>
      </div>

      {toast && (
        <div
          className={`mb-4 px-4 py-3 rounded-lg text-sm ${
            toast.type === "success"
              ? "bg-green-50 text-green-800 border border-green-200"
              : "bg-error/5 text-error border border-error/20"
          }`}
        >
          {toast.message}
        </div>
      )}

      {/* 테이블 */}
      {loading ? (
        <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">
          로딩 중...
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <div className="bg-header px-5 py-3 border-b border-gray-200">
            <span className="text-sm font-medium text-gray-600">
              Gateway Routes ({routes.length})
            </span>
          </div>

          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-100 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th className="px-5 py-3">Route ID</th>
                <th className="px-5 py-3">URI</th>
                <th className="px-5 py-3">Predicates</th>
                <th className="px-5 py-3">Filters</th>
                <th className="px-5 py-3 w-14 text-center">Order</th>
                <th className="px-5 py-3 w-28 text-center">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {routes.map((route) => (
                <tr
                  key={route.id}
                  className="hover:bg-gray-50/50 transition-colors"
                >
                  <td className="px-5 py-3.5 text-sm font-mono font-medium text-primary">
                    {route.id}
                  </td>
                  <td className="px-5 py-3.5 text-sm text-gray-700 font-mono">
                    {route.uri}
                  </td>
                  <td className="px-5 py-3.5">
                    <div className="flex flex-wrap gap-1">
                      {route.predicates.map((p, i) => (
                        <span
                          key={i}
                          className="bg-secondary text-primary text-xs px-2 py-0.5 rounded font-mono"
                          title={JSON.stringify(p.args)}
                        >
                          {defSummary(p)}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-5 py-3.5">
                    <div className="flex flex-wrap gap-1">
                      {route.filters.map((f, i) => (
                        <span
                          key={i}
                          className="bg-gray-100 text-gray-600 text-xs px-2 py-0.5 rounded font-mono"
                          title={JSON.stringify(f.args)}
                        >
                          {defSummary(f)}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-5 py-3.5 text-center text-sm text-gray-500">
                    {route.order}
                  </td>
                  <td className="px-5 py-3.5 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={() => openEdit(route)}
                        className="px-2.5 py-1 text-xs text-info hover:bg-info/10 rounded transition-colors"
                      >
                        수정
                      </button>
                      <button
                        onClick={() => setDeleteTarget(route.id)}
                        className="px-2.5 py-1 text-xs text-error hover:bg-error/10 rounded transition-colors"
                      >
                        삭제
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {routes.length === 0 && (
                <tr>
                  <td
                    colSpan={6}
                    className="px-5 py-12 text-center text-gray-400"
                  >
                    등록된 라우트가 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* ── 생성/수정 모달 ───────────────────────────────── */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setModalOpen(false)}
          />
          <div className="relative bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 max-h-[85vh] flex flex-col">
            {/* 모달 헤더 */}
            <div className="bg-header px-6 py-4 rounded-t-xl border-b border-gray-200 shrink-0">
              <h2 className="text-lg font-semibold text-gray-900">
                {modalMode === "create" ? "라우트 추가" : "라우트 수정"}
              </h2>
            </div>

            {/* 모달 바디 */}
            <div className="px-6 py-5 space-y-5 overflow-y-auto flex-1">
              {/* 기본 정보 */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Route ID
                  </label>
                  <input
                    type="text"
                    value={form.id}
                    onChange={(e) => setForm({ ...form, id: e.target.value })}
                    disabled={modalMode === "edit"}
                    placeholder="my-service-route"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm
                               focus:ring-2 focus:ring-primary/40 focus:border-primary
                               disabled:bg-gray-100 disabled:text-gray-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    URI
                  </label>
                  <input
                    type="text"
                    value={form.uri}
                    onChange={(e) => setForm({ ...form, uri: e.target.value })}
                    placeholder="http://my-service:8080"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm
                               focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  />
                </div>
              </div>

              {/* Order */}
              <div className="w-32">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Order
                </label>
                <input
                  type="number"
                  value={form.order}
                  onChange={(e) =>
                    setForm({ ...form, order: parseInt(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm
                             focus:ring-2 focus:ring-primary/40 focus:border-primary"
                />
              </div>

              {/* Predicates */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-sm font-medium text-gray-700">
                    Predicates
                  </label>
                  <button
                    onClick={() => addDef("predicates")}
                    className="text-xs text-primary hover:underline"
                  >
                    + 추가
                  </button>
                </div>
                <div className="space-y-2">
                  {form.predicates.map((p, i) => (
                    <DefinitionEditor
                      key={i}
                      value={p}
                      catalog={PREDICATE_CATALOG}
                      allNames={ALL_PREDICATE_NAMES}
                      onChange={(d) => updateDef("predicates", i, d)}
                      onRemove={() => removeDef("predicates", i)}
                      typeLabel="Predicate"
                    />
                  ))}
                </div>
              </div>

              {/* Filters */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-sm font-medium text-gray-700">
                    Filters
                  </label>
                  <button
                    onClick={() => addDef("filters")}
                    className="text-xs text-primary hover:underline"
                  >
                    + 추가
                  </button>
                </div>
                <div className="space-y-2">
                  {form.filters.map((f, i) => (
                    <DefinitionEditor
                      key={i}
                      value={f}
                      catalog={FILTER_CATALOG}
                      allNames={ALL_FILTER_NAMES}
                      onChange={(d) => updateDef("filters", i, d)}
                      onRemove={() => removeDef("filters", i)}
                      typeLabel="Filter"
                    />
                  ))}
                </div>
              </div>

              {/* Swagger 자동 등록 */}
              <div className="flex items-start gap-2 pt-2 border-t border-gray-100">
                <input
                  id="register-swagger"
                  type="checkbox"
                  checked={registerSwagger}
                  onChange={(e) => setRegisterSwagger(e.target.checked)}
                  className="mt-0.5 w-4 h-4 text-primary rounded focus:ring-2 focus:ring-primary/40"
                />
                <label htmlFor="register-swagger" className="text-sm text-gray-700 leading-tight">
                  <span className="font-medium">Gateway Swagger에 등록</span>
                  <br />
                  <span className="text-xs text-gray-500">
                    동반 /v3/api-docs/{`{id}`} 라우트와 springdoc URL을 자동 추가. backing service가 OpenAPI를 제공하지 않으면 해제하세요.
                  </span>
                </label>
              </div>
            </div>

            {/* 모달 푸터 */}
            <div className="px-6 py-4 border-t border-gray-200 flex justify-end gap-2 shrink-0">
              <button
                onClick={() => setModalOpen(false)}
                className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg
                           hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleSave}
                disabled={saving || !form.id.trim() || !form.uri.trim()}
                className="px-4 py-2 text-sm bg-primary text-white rounded-lg
                           hover:bg-primary/90 disabled:opacity-50 transition-colors"
              >
                {saving
                  ? "저장 중..."
                  : modalMode === "create"
                    ? "생성"
                    : "저장"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── 삭제 확인 모달 ───────────────────────────────── */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setDeleteTarget(null)}
          />
          <div className="relative bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">
              라우트 삭제
            </h3>
            <p className="text-sm text-gray-600 mb-1">
              다음 라우트를 삭제하시겠습니까?
            </p>
            <p className="text-sm font-mono text-error mb-6">{deleteTarget}</p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setDeleteTarget(null)}
                className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-lg
                           hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 text-sm bg-error text-white rounded-lg hover:bg-error/90 transition-colors"
              >
                삭제
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
