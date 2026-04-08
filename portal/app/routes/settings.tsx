import { useState, useEffect, useCallback, useRef } from "react";
import api from "../utils/api";
import type { ConfigProperty } from "../types";

/* ── 상수 ──────────────────────────────────────────────────── */
const APPS = ["application", "gateway-service", "auth-server", "config-server"];
const PROFILES = ["default", "dev", "prod"];
const DEFAULT_LABEL = "main";

/* ── 변경 이력 아이템 ──────────────────────────────────────── */
interface ChangeLog {
  id: number;
  time: string;
  action: "CREATE" | "UPDATE" | "DELETE" | "REFRESH";
  detail: string;
}

export default function Settings() {
  /* ── 필터 ──────────────────────────────────────────────── */
  const [application, setApplication] = useState(APPS[0]);
  const [profile, setProfile] = useState(PROFILES[0]);

  /* ── 데이터 ────────────────────────────────────────────── */
  const [properties, setProperties] = useState<ConfigProperty[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  /* ── 인라인 편집 ───────────────────────────────────────── */
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");
  const editRef = useRef<HTMLInputElement>(null);

  /* ── 새 행 추가 ────────────────────────────────────────── */
  const [adding, setAdding] = useState(false);
  const [newKey, setNewKey] = useState("");
  const [newValue, setNewValue] = useState("");
  const newKeyRef = useRef<HTMLInputElement>(null);

  /* ── 삭제 확인 ─────────────────────────────────────────── */
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);

  /* ── 토스트 ────────────────────────────────────────────── */
  const [toast, setToast] = useState<{ type: "success" | "error"; message: string } | null>(null);
  const showToast = (type: "success" | "error", message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 3000);
  };

  /* ── 변경 이력 ─────────────────────────────────────────── */
  const [changeLogs, setChangeLogs] = useState<ChangeLog[]>([]);
  const logIdRef = useRef(0);

  const addLog = (action: ChangeLog["action"], detail: string) => {
    setChangeLogs((prev) => [
      {
        id: ++logIdRef.current,
        time: new Date().toLocaleTimeString("ko-KR"),
        action,
        detail,
      },
      ...prev.slice(0, 49), // 최근 50건 유지
    ]);
  };

  /* ================================================================
     데이터 조회
     ================================================================ */
  const fetchProperties = useCallback(() => {
    setLoading(true);
    setEditingKey(null);
    setAdding(false);
    api
      .get<ConfigProperty[]>("/api/config/properties", {
        params: { application, profile, label: DEFAULT_LABEL },
      })
      .then((r) => setProperties(r.data))
      .catch(() => showToast("error", "설정 조회에 실패했습니다."))
      .finally(() => setLoading(false));
  }, [application, profile]);

  useEffect(() => {
    fetchProperties();
  }, [fetchProperties]);

  /* ================================================================
     생성
     ================================================================ */
  const handleCreate = async () => {
    if (!newKey.trim() || !newValue.trim()) return;
    try {
      await api.post("/api/config/properties", {
        application,
        profile,
        label: DEFAULT_LABEL,
        propKey: newKey.trim(),
        propValue: newValue.trim(),
      });
      addLog("CREATE", `${newKey.trim()} = ${newValue.trim()}`);
      showToast("success", "속성이 추가되었습니다.");
      setNewKey("");
      setNewValue("");
      setAdding(false);
      fetchProperties();
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "추가 실패");
    }
  };

  /* ================================================================
     수정 (인라인)
     ================================================================ */
  const startEdit = (prop: ConfigProperty) => {
    setEditingKey(prop.propKey);
    setEditValue(prop.propValue);
    setTimeout(() => editRef.current?.focus(), 0);
  };

  const cancelEdit = () => {
    setEditingKey(null);
    setEditValue("");
  };

  const handleUpdate = async (propKey: string) => {
    try {
      await api.put("/api/config/properties", {
        application,
        profile,
        label: DEFAULT_LABEL,
        propKey,
        propValue: editValue,
      });
      addLog("UPDATE", `${propKey} → ${editValue}`);
      showToast("success", "속성이 수정되었습니다.");
      setEditingKey(null);
      fetchProperties();
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "수정 실패");
    }
  };

  /* ================================================================
     삭제
     ================================================================ */
  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await api.delete("/api/config/properties", {
        params: { application, profile, label: DEFAULT_LABEL, propKey: deleteTarget },
      });
      addLog("DELETE", deleteTarget);
      showToast("success", "속성이 삭제되었습니다.");
      setDeleteTarget(null);
      fetchProperties();
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "삭제 실패");
    }
  };

  /* ================================================================
     Bus Refresh
     ================================================================ */
  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await api.post("/api/config/properties/refresh");
      addLog("REFRESH", "Bus refresh 전파 완료");
      showToast("success", "변경사항이 모든 서비스에 적용되었습니다.");
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "Refresh 실패");
    } finally {
      setRefreshing(false);
    }
  };

  /* ── 이력 액션별 색상 ──────────────────────────────────── */
  const actionStyle: Record<ChangeLog["action"], string> = {
    CREATE: "bg-green-100 text-green-800",
    UPDATE: "bg-info/10 text-info",
    DELETE: "bg-error/10 text-error",
    REFRESH: "bg-accent/10 text-amber-800",
  };

  /* ================================================================
     렌더링
     ================================================================ */
  return (
    <div>
      {/* ── 페이지 헤더 ──────────────────────────────────── */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">설정정보</h1>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm
                       hover:bg-gray-50 disabled:opacity-50 transition-colors"
          >
            {refreshing ? "전파 중..." : "변경사항 적용"}
          </button>
        </div>
      </div>

      {/* ── 토스트 ───────────────────────────────────────── */}
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

      {/* ── 필터 바 ──────────────────────────────────────── */}
      <div className="bg-white rounded-lg shadow px-5 py-4 mb-4 flex items-center gap-4">
        {/* Application */}
        <div className="flex items-center gap-2">
          <label className="text-sm font-medium text-gray-700">Application</label>
          <select
            value={application}
            onChange={(e) => setApplication(e.target.value)}
            className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm bg-white
                       focus:ring-2 focus:ring-primary/40 focus:border-primary"
          >
            {APPS.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
        </div>

        {/* Profile */}
        <div className="flex items-center gap-2">
          <label className="text-sm font-medium text-gray-700">Profile</label>
          <select
            value={profile}
            onChange={(e) => setProfile(e.target.value)}
            className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm bg-white
                       focus:ring-2 focus:ring-primary/40 focus:border-primary"
          >
            {PROFILES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </div>

        <div className="flex-1" />

        <span className="text-xs text-gray-400">
          Label: <span className="font-mono">{DEFAULT_LABEL}</span>
        </span>
      </div>

      {/* ── Properties 테이블 ────────────────────────────── */}
      <div className="bg-white rounded-lg shadow overflow-hidden mb-6">
        {/* 카드 헤더 */}
        <div className="bg-header px-5 py-3 border-b border-gray-200 flex items-center justify-between">
          <span className="text-sm font-semibold text-gray-700">
            Properties ({properties.length})
          </span>
          <button
            onClick={() => {
              setAdding(true);
              setTimeout(() => newKeyRef.current?.focus(), 0);
            }}
            className="px-3 py-1.5 bg-primary text-white text-xs rounded-md
                       hover:bg-primary/90 transition-colors"
          >
            + 속성 추가
          </button>
        </div>

        {loading ? (
          <div className="p-12 text-center text-gray-400">로딩 중...</div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-100 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th className="px-5 py-3 w-[38%]">Key</th>
                <th className="px-5 py-3 w-[38%]">Value</th>
                <th className="px-5 py-3 w-16">Label</th>
                <th className="px-5 py-3 w-32 text-center">액션</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {/* 새 행 추가 */}
              {adding && (
                <tr className="bg-primary/[0.02]">
                  <td className="px-5 py-2.5">
                    <input
                      ref={newKeyRef}
                      type="text"
                      value={newKey}
                      onChange={(e) => setNewKey(e.target.value)}
                      placeholder="property.key"
                      className="w-full px-2.5 py-1.5 border border-primary/30 rounded text-sm font-mono
                                 focus:ring-2 focus:ring-primary/40 focus:border-primary"
                    />
                  </td>
                  <td className="px-5 py-2.5">
                    <input
                      type="text"
                      value={newValue}
                      onChange={(e) => setNewValue(e.target.value)}
                      onKeyDown={(e) => e.key === "Enter" && handleCreate()}
                      placeholder="value"
                      className="w-full px-2.5 py-1.5 border border-primary/30 rounded text-sm font-mono
                                 focus:ring-2 focus:ring-primary/40 focus:border-primary"
                    />
                  </td>
                  <td className="px-5 py-2.5 text-xs text-gray-400 font-mono">
                    {DEFAULT_LABEL}
                  </td>
                  <td className="px-5 py-2.5 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={handleCreate}
                        disabled={!newKey.trim() || !newValue.trim()}
                        className="px-2.5 py-1 text-xs bg-primary text-white rounded
                                   hover:bg-primary/90 disabled:opacity-40 transition-colors"
                      >
                        저장
                      </button>
                      <button
                        onClick={() => {
                          setAdding(false);
                          setNewKey("");
                          setNewValue("");
                        }}
                        className="px-2.5 py-1 text-xs text-gray-500 hover:bg-gray-100 rounded transition-colors"
                      >
                        취소
                      </button>
                    </div>
                  </td>
                </tr>
              )}

              {/* 기존 행 */}
              {properties.map((prop) => {
                const isEditing = editingKey === prop.propKey;
                return (
                  <tr
                    key={prop.propKey}
                    className={`transition-colors ${isEditing ? "bg-info/[0.03]" : "hover:bg-gray-50/50"}`}
                  >
                    {/* Key */}
                    <td className="px-5 py-2.5 text-sm font-mono text-gray-800">
                      {prop.propKey}
                    </td>

                    {/* Value (인라인 편집) */}
                    <td className="px-5 py-2.5">
                      {isEditing ? (
                        <input
                          ref={editRef}
                          type="text"
                          value={editValue}
                          onChange={(e) => setEditValue(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") handleUpdate(prop.propKey);
                            if (e.key === "Escape") cancelEdit();
                          }}
                          className="w-full px-2.5 py-1.5 border border-info/40 rounded text-sm font-mono
                                     focus:ring-2 focus:ring-info/30 focus:border-info"
                        />
                      ) : (
                        <span
                          className="text-sm font-mono text-gray-600 cursor-pointer hover:text-primary"
                          onDoubleClick={() => startEdit(prop)}
                          title="더블클릭하여 편집"
                        >
                          {prop.propValue || <span className="text-gray-300 italic">empty</span>}
                        </span>
                      )}
                    </td>

                    {/* Label */}
                    <td className="px-5 py-2.5">
                      <span className="text-xs font-mono text-gray-400">{prop.label}</span>
                    </td>

                    {/* 액션 */}
                    <td className="px-5 py-2.5 text-center">
                      {isEditing ? (
                        <div className="flex items-center justify-center gap-1">
                          <button
                            onClick={() => handleUpdate(prop.propKey)}
                            className="px-2.5 py-1 text-xs bg-primary text-white rounded
                                       hover:bg-primary/90 transition-colors"
                          >
                            저장
                          </button>
                          <button
                            onClick={cancelEdit}
                            className="px-2.5 py-1 text-xs text-gray-500 hover:bg-gray-100 rounded transition-colors"
                          >
                            취소
                          </button>
                        </div>
                      ) : (
                        <div className="flex items-center justify-center gap-1">
                          <button
                            onClick={() => startEdit(prop)}
                            className="px-2.5 py-1 text-xs text-info hover:bg-info/10 rounded transition-colors"
                          >
                            수정
                          </button>
                          <button
                            onClick={() => setDeleteTarget(prop.propKey)}
                            className="px-2.5 py-1 text-xs text-error hover:bg-error/10 rounded transition-colors"
                          >
                            삭제
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}

              {!adding && properties.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-5 py-12 text-center text-gray-400">
                    해당 조건의 설정이 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {/* ── 변경 이력 ────────────────────────────────────── */}
      {changeLogs.length > 0 && (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <div className="bg-header px-5 py-3 border-b border-gray-200 flex items-center justify-between">
            <span className="text-sm font-semibold text-gray-700">
              변경 이력 (현재 세션)
            </span>
            <button
              onClick={() => setChangeLogs([])}
              className="text-xs text-gray-400 hover:text-gray-600 transition-colors"
            >
              지우기
            </button>
          </div>
          <ul className="divide-y divide-gray-50 max-h-52 overflow-y-auto">
            {changeLogs.map((log) => (
              <li key={log.id} className="px-5 py-2.5 flex items-center gap-3 text-sm">
                <span className="text-xs text-gray-400 font-mono w-20 shrink-0">
                  {log.time}
                </span>
                <span
                  className={`text-[10px] px-2 py-0.5 rounded font-medium w-16 text-center shrink-0 ${
                    actionStyle[log.action]
                  }`}
                >
                  {log.action}
                </span>
                <span className="text-gray-600 font-mono text-xs truncate">
                  {log.detail}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* ── 삭제 확인 모달 ───────────────────────────────── */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => setDeleteTarget(null)} />
          <div className="relative bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">속성 삭제</h3>
            <p className="text-sm text-gray-600 mb-1">다음 속성을 삭제하시겠습니까?</p>
            <p className="text-sm font-mono text-error mb-6 break-all">{deleteTarget}</p>
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
                className="px-4 py-2 text-sm bg-error text-white rounded-lg
                           hover:bg-error/90 transition-colors"
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
