import { useState, useEffect, useCallback } from "react";
import api from "../utils/api";
import type {
  SystemConnector,
  GroupInfo,
  GroupMember,
  GroupResource,
  UserResource,
} from "../types";

export default function AuthManage() {
  const [tab, setTab] = useState<"group" | "user">("group");

  /* ── 좌측: System / Group ──────────────────────────────── */
  const [systems, setSystems] = useState<SystemConnector[]>([]);
  const [selectedSystem, setSelectedSystem] = useState("");
  const [groups, setGroups] = useState<GroupInfo[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);

  /* ── 우측(그룹탭): 멤버 / 리소스 ────────────────────────── */
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [groupResources, setGroupResources] = useState<GroupResource[]>([]);

  /* ── 우측(개인탭) ──────────────────────────────────────── */
  const [searchEmpNo, setSearchEmpNo] = useState("");
  const [userResources, setUserResources] = useState<UserResource[]>([]);
  const [userSearched, setUserSearched] = useState(false);

  /* ── 입력 ──────────────────────────────────────────────── */
  const [newGroupName, setNewGroupName] = useState("");
  const [newMemberNo, setNewMemberNo] = useState("");
  const [newGroupRes, setNewGroupRes] = useState("");
  const [newUserRes, setNewUserRes] = useState("");

  /* ── 토스트 ────────────────────────────────────────────── */
  const [toast, setToast] = useState<{ type: "success" | "error"; message: string } | null>(null);
  const showToast = (t: "success" | "error", m: string) => { setToast({ type: t, message: m }); setTimeout(() => setToast(null), 3000); };

  /* ── 데이터 로드 ───────────────────────────────────────── */
  useEffect(() => {
    api.get<SystemConnector[]>("/api/auth/systems").then((r) => setSystems(r.data));
  }, []);

  const fetchGroups = useCallback((sys: string) => {
    api.get<GroupInfo[]>(`/api/auth/systems/${sys}/groups`).then((r) => setGroups(r.data));
  }, []);

  useEffect(() => { if (selectedSystem) fetchGroups(selectedSystem); }, [selectedSystem, fetchGroups]);

  const fetchGroupDetail = useCallback((gid: number) => {
    Promise.all([
      api.get<GroupMember[]>(`/api/auth/groups/${gid}/members`),
      api.get<GroupResource[]>(`/api/auth/groups/${gid}/resources`),
    ]).then(([m, r]) => { setMembers(m.data); setGroupResources(r.data); });
  }, []);

  useEffect(() => { if (selectedGroupId !== null) fetchGroupDetail(selectedGroupId); }, [selectedGroupId, fetchGroupDetail]);

  const fetchUserResources = () => {
    if (!searchEmpNo.trim() || !selectedSystem) return;
    api.get<UserResource[]>(`/api/auth/users/${searchEmpNo.trim()}/systems/${selectedSystem}/resources`)
      .then((r) => { setUserResources(r.data); setUserSearched(true); })
      .catch(() => { setUserResources([]); setUserSearched(true); });
  };

  /* ── 액션 ──────────────────────────────────────────────── */
  const handleCreateGroup = async () => {
    if (!newGroupName.trim() || !selectedSystem) return;
    try {
      await api.post(`/api/auth/systems/${selectedSystem}/groups`, { groupName: newGroupName.trim() });
      showToast("success", `그룹 "${newGroupName.trim()}" 생성`);
      setNewGroupName(""); fetchGroups(selectedSystem);
    } catch (e) { showToast("error", e instanceof Error ? e.message : "실패"); }
  };

  const handleAddMember = async () => {
    if (!newMemberNo.trim() || selectedGroupId === null) return;
    try {
      await api.post(`/api/auth/groups/${selectedGroupId}/members`, { employeeNumber: newMemberNo.trim() });
      showToast("success", `멤버 ${newMemberNo.trim()} 추가`);
      setNewMemberNo(""); fetchGroupDetail(selectedGroupId);
    } catch (e) { showToast("error", e instanceof Error ? e.message : "실패"); }
  };

  const handleRemoveMember = async (emp: string) => {
    if (selectedGroupId === null) return;
    try {
      await api.delete(`/api/auth/groups/${selectedGroupId}/members/${emp}`);
      showToast("success", `멤버 ${emp} 삭제`); fetchGroupDetail(selectedGroupId);
    } catch (e) { showToast("error", e instanceof Error ? e.message : "실패"); }
  };

  const handleAddGroupResource = async () => {
    if (!newGroupRes.trim() || selectedGroupId === null) return;
    try {
      await api.post(`/api/auth/groups/${selectedGroupId}/resources`, { resourceName: newGroupRes.trim() });
      showToast("success", "리소스 추가"); setNewGroupRes(""); fetchGroupDetail(selectedGroupId);
    } catch (e) { showToast("error", e instanceof Error ? e.message : "실패"); }
  };

  const handleRemoveGroupResource = async (res: string) => {
    if (selectedGroupId === null) return;
    try {
      await api.delete(`/api/auth/groups/${selectedGroupId}/resources`, { params: { resourceName: res } });
      showToast("success", "리소스 삭제"); fetchGroupDetail(selectedGroupId);
    } catch (e) { showToast("error", e instanceof Error ? e.message : "실패"); }
  };

  const handleAddUserResource = async () => {
    if (!newUserRes.trim() || !searchEmpNo.trim() || !selectedSystem) return;
    try {
      await api.post(`/api/auth/users/${searchEmpNo.trim()}/systems/${selectedSystem}/resources`, { resourceName: newUserRes.trim() });
      showToast("success", "개인 리소스 추가"); setNewUserRes(""); fetchUserResources();
    } catch (e) { showToast("error", e instanceof Error ? e.message : "실패"); }
  };

  const handleRemoveUserResource = async (res: string) => {
    try {
      await api.delete(`/api/auth/users/${searchEmpNo.trim()}/systems/${selectedSystem}/resources`, { params: { resourceName: res } });
      showToast("success", "개인 리소스 삭제"); fetchUserResources();
    } catch (e) { showToast("error", e instanceof Error ? e.message : "실패"); }
  };

  const selectedGroupName = groups.find((g) => g.groupId === selectedGroupId)?.groupName ?? "";

  /* 시스템별 커넥터 그룹핑 */
  const systemNames = [...new Set(systems.map((s) => s.systemName))];

  /* ── 렌더링 ────────────────────────────────────────────── */
  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">권한관리</h1>

      {toast && (
        <div className={`mb-4 px-4 py-3 rounded-lg text-sm ${toast.type === "success" ? "bg-green-50 text-green-800 border border-green-200" : "bg-error/5 text-error border border-error/20"}`}>
          {toast.message}
        </div>
      )}

      <div className="flex gap-6 items-start">
        {/* ── 좌측: System → Group ────────────────────────── */}
        <div className="w-72 shrink-0 space-y-4">
          {/* System/Connector */}
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <div className="bg-header px-4 py-2.5 border-b border-gray-200">
              <span className="text-sm font-semibold text-gray-700">System / Connector</span>
            </div>
            <ul className="p-2">
              {systemNames.map((sys) => {
                const connectors = systems.filter((s) => s.systemName === sys);
                return (
                  <li key={sys}>
                    <button
                      onClick={() => { setSelectedSystem(sys); setSelectedGroupId(null); setMembers([]); setGroupResources([]); setUserSearched(false); }}
                      className={`w-full text-left px-3 py-2.5 rounded-md text-sm transition-colors ${selectedSystem === sys ? "bg-primary text-white font-medium" : "text-gray-700 hover:bg-gray-100"}`}
                    >
                      <span className="font-medium uppercase">{sys}</span>
                      <div className={`flex flex-wrap gap-1 mt-1 ${selectedSystem === sys ? "text-white/70" : "text-primary-text"}`}>
                        {connectors.map((c) => (
                          <span key={c.connectorName} className={`text-[11px] font-mono px-1.5 py-0.5 rounded ${selectedSystem === sys ? "bg-white/15" : "bg-secondary"}`}>
                            {c.connectorName}
                          </span>
                        ))}
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>

          {/* Group */}
          {selectedSystem && (
            <div className="bg-white rounded-lg shadow overflow-hidden">
              <div className="bg-header px-4 py-2.5 border-b border-gray-200 flex items-center justify-between">
                <span className="text-sm font-semibold text-gray-700">그룹</span>
                <span className="text-xs text-gray-400">{groups.length}개</span>
              </div>
              <ul className="p-2">
                {groups.map((g) => (
                  <li key={g.groupId}>
                    <button
                      onClick={() => setSelectedGroupId(g.groupId)}
                      className={`w-full text-left px-3 py-2 rounded-md text-sm transition-colors ${selectedGroupId === g.groupId ? "bg-primary text-white font-medium" : "text-gray-700 hover:bg-gray-100"}`}
                    >
                      <span className="capitalize">{g.groupName}</span>
                      <span className={`ml-2 text-[10px] ${selectedGroupId === g.groupId ? "text-white/60" : "text-gray-400"}`}>#{g.groupId}</span>
                    </button>
                  </li>
                ))}
              </ul>
              <div className="px-3 py-3 border-t border-gray-100">
                <div className="flex gap-1.5">
                  <input type="text" value={newGroupName} onChange={(e) => setNewGroupName(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleCreateGroup()} placeholder="새 그룹명"
                    className="flex-1 min-w-0 px-2.5 py-1.5 border border-gray-300 rounded-md text-xs focus:ring-2 focus:ring-primary/40 focus:border-primary" />
                  <button onClick={handleCreateGroup} disabled={!newGroupName.trim()}
                    className="px-3 py-1.5 bg-primary text-white text-xs rounded-md hover:bg-primary/90 disabled:opacity-40 shrink-0">추가</button>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* ── 우측 ────────────────────────────────────────── */}
        <div className="flex-1 min-w-0">
          {selectedSystem && (
            <div className="flex gap-1 mb-4">
              {([["group", "그룹 권한"], ["user", "개인 리소스"]] as const).map(([k, l]) => (
                <button key={k} onClick={() => setTab(k)}
                  className={`px-4 py-2 text-sm rounded-t-lg transition-colors ${tab === k ? "bg-white text-primary font-medium shadow" : "text-gray-500 hover:text-gray-700"}`}>
                  {l}
                </button>
              ))}
            </div>
          )}

          {tab === "group" && (
            <>
              {selectedGroupId === null ? (
                <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">
                  {selectedSystem ? "좌측에서 그룹을 선택하세요" : "좌측에서 시스템을 선택하세요"}
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="bg-white rounded-lg shadow overflow-hidden">
                    <div className="bg-header px-5 py-3 border-b border-gray-200">
                      <h2 className="text-base font-semibold text-gray-800">
                        <span className="uppercase">{selectedSystem}</span>
                        <span className="mx-2 text-gray-300">/</span>
                        <span className="capitalize">{selectedGroupName}</span>
                        <span className="ml-2 text-xs font-normal text-gray-400">#{selectedGroupId}</span>
                      </h2>
                    </div>
                  </div>

                  {/* 멤버 */}
                  <div className="bg-white rounded-lg shadow overflow-hidden">
                    <div className="bg-header px-5 py-2.5 border-b border-gray-200">
                      <span className="text-sm font-semibold text-gray-700">멤버 ({members.length})</span>
                    </div>
                    <div className="px-5 py-3 border-b border-gray-100 bg-gray-50/50">
                      <div className="flex gap-2">
                        <input type="text" value={newMemberNo} onChange={(e) => setNewMemberNo(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && handleAddMember()} placeholder="사번 입력"
                          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary" />
                        <button onClick={handleAddMember} disabled={!newMemberNo.trim()}
                          className="px-4 py-2 bg-primary text-white text-sm rounded-lg hover:bg-primary/90 disabled:opacity-40">추가</button>
                      </div>
                    </div>
                    <ul className="divide-y divide-gray-50">
                      {members.map((m) => (
                        <li key={m.employeeNumber} className="px-5 py-2.5 flex items-center justify-between hover:bg-gray-50/50">
                          <span className="text-sm font-mono">{m.employeeNumber}</span>
                          <button onClick={() => handleRemoveMember(m.employeeNumber)} className="text-xs text-error hover:bg-error/10 px-2 py-1 rounded">삭제</button>
                        </li>
                      ))}
                      {members.length === 0 && <li className="px-5 py-6 text-center text-sm text-gray-400">멤버가 없습니다</li>}
                    </ul>
                  </div>

                  {/* 리소스 (커넥터) */}
                  <div className="bg-white rounded-lg shadow overflow-hidden">
                    <div className="bg-header px-5 py-2.5 border-b border-gray-200">
                      <span className="text-sm font-semibold text-gray-700">허용 커넥터 ({groupResources.length})</span>
                    </div>
                    <div className="px-5 py-3 border-b border-gray-100 bg-gray-50/50">
                      <div className="flex gap-2">
                        <input type="text" value={newGroupRes} onChange={(e) => setNewGroupRes(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && handleAddGroupResource()} placeholder="커넥터 이름 (예: validation-history)"
                          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono focus:ring-2 focus:ring-primary/40 focus:border-primary" />
                        <button onClick={handleAddGroupResource} disabled={!newGroupRes.trim()}
                          className="px-4 py-2 bg-primary text-white text-sm rounded-lg hover:bg-primary/90 disabled:opacity-40">추가</button>
                      </div>
                    </div>
                    <ul className="divide-y divide-gray-50">
                      {groupResources.map((r) => (
                        <li key={r.resourceName} className="px-5 py-2.5 flex items-center justify-between hover:bg-gray-50/50">
                          <span className="text-sm font-mono text-gray-700">{r.resourceName}</span>
                          <button onClick={() => handleRemoveGroupResource(r.resourceName)} className="text-xs text-error hover:bg-error/10 px-2 py-1 rounded">삭제</button>
                        </li>
                      ))}
                      {groupResources.length === 0 && <li className="px-5 py-6 text-center text-sm text-gray-400">허용된 커넥터가 없습니다</li>}
                    </ul>
                  </div>
                </div>
              )}
            </>
          )}

          {tab === "user" && (
            <div className="space-y-4">
              <div className="bg-white rounded-lg shadow overflow-hidden">
                <div className="bg-header px-5 py-2.5 border-b border-gray-200">
                  <span className="text-sm font-semibold text-gray-700">사원 검색</span>
                </div>
                <div className="px-5 py-4">
                  <div className="flex gap-2">
                    <input type="text" value={searchEmpNo} onChange={(e) => { setSearchEmpNo(e.target.value); setUserSearched(false); }}
                      onKeyDown={(e) => e.key === "Enter" && fetchUserResources()} placeholder="사번 입력"
                      className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary/40 focus:border-primary" />
                    <button onClick={fetchUserResources} disabled={!searchEmpNo.trim() || !selectedSystem}
                      className="px-4 py-2 bg-primary text-white text-sm rounded-lg hover:bg-primary/90 disabled:opacity-40">검색</button>
                  </div>
                  {!selectedSystem && <p className="text-xs text-gray-400 mt-2">좌측에서 시스템을 먼저 선택하세요</p>}
                </div>
              </div>

              {userSearched && (
                <div className="bg-white rounded-lg shadow overflow-hidden">
                  <div className="bg-header px-5 py-2.5 border-b border-gray-200 flex items-center justify-between">
                    <span className="text-sm font-semibold text-gray-700">{searchEmpNo}의 개인 커넥터 ({userResources.length})</span>
                    <span className="text-xs text-gray-400">System: {selectedSystem}</span>
                  </div>
                  <div className="px-5 py-3 border-b border-gray-100 bg-gray-50/50">
                    <div className="flex gap-2">
                      <input type="text" value={newUserRes} onChange={(e) => setNewUserRes(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleAddUserResource()} placeholder="커넥터 이름"
                        className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono focus:ring-2 focus:ring-primary/40 focus:border-primary" />
                      <button onClick={handleAddUserResource} disabled={!newUserRes.trim()}
                        className="px-4 py-2 bg-primary text-white text-sm rounded-lg hover:bg-primary/90 disabled:opacity-40">추가</button>
                    </div>
                  </div>
                  <ul className="divide-y divide-gray-50">
                    {userResources.map((r) => (
                      <li key={r.resourceName} className="px-5 py-2.5 flex items-center justify-between hover:bg-gray-50/50">
                        <span className="text-sm font-mono text-gray-700">{r.resourceName}</span>
                        <button onClick={() => handleRemoveUserResource(r.resourceName)} className="text-xs text-error hover:bg-error/10 px-2 py-1 rounded">삭제</button>
                      </li>
                    ))}
                    {userResources.length === 0 && <li className="px-5 py-6 text-center text-sm text-gray-400">등록된 개인 커넥터가 없습니다</li>}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
