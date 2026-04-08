import { useState, useEffect, useCallback } from "react";
import api from "../utils/api";
import type {
  AppInfo,
  GroupInfo,
  GroupMember,
  GroupResource,
  UserResource,
} from "../types";

/* ================================================================
   권한관리 페이지
   좌측: App → Group 트리
   우측: 그룹 상세 (멤버 + 리소스) | 개인 리소스 탭
   ================================================================ */

export default function AuthManage() {
  /* ── 탭 ────────────────────────────────────────────────── */
  const [tab, setTab] = useState<"group" | "user">("group");

  /* ── 좌측: App / Group ─────────────────────────────────── */
  const [apps, setApps] = useState<AppInfo[]>([]);
  const [selectedApp, setSelectedApp] = useState("");
  const [groups, setGroups] = useState<GroupInfo[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState("");

  /* ── 우측(그룹탭): 멤버 / 리소스 ───────────────────────── */
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [groupResources, setGroupResources] = useState<GroupResource[]>([]);

  /* ── 우측(개인탭): 사원 리소스 ─────────────────────────── */
  const [searchEmpNo, setSearchEmpNo] = useState("");
  const [userResources, setUserResources] = useState<UserResource[]>([]);
  const [userSearched, setUserSearched] = useState(false);

  /* ── 입력 필드 ─────────────────────────────────────────── */
  const [newGroupName, setNewGroupName] = useState("");
  const [newMemberNo, setNewMemberNo] = useState("");
  const [newGroupRes, setNewGroupRes] = useState("");
  const [newUserRes, setNewUserRes] = useState("");

  /* ── 토스트 ────────────────────────────────────────────── */
  const [toast, setToast] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);
  const showToast = (type: "success" | "error", message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 3000);
  };

  /* ================================================================
     데이터 페치
     ================================================================ */

  useEffect(() => {
    api.get<AppInfo[]>("/api/auth/apps").then((r) => setApps(r.data));
  }, []);

  const fetchGroups = useCallback((appName: string) => {
    api
      .get<GroupInfo[]>(`/api/auth/apps/${appName}/groups`)
      .then((r) => setGroups(r.data));
  }, []);

  useEffect(() => {
    if (selectedApp) fetchGroups(selectedApp);
  }, [selectedApp, fetchGroups]);

  const fetchGroupDetail = useCallback((groupId: string) => {
    Promise.all([
      api.get<GroupMember[]>(`/api/auth/groups/${groupId}/members`),
      api.get<GroupResource[]>(`/api/auth/groups/${groupId}/resources`),
    ]).then(([m, r]) => {
      setMembers(m.data);
      setGroupResources(r.data);
    });
  }, []);

  useEffect(() => {
    if (selectedGroupId) fetchGroupDetail(selectedGroupId);
  }, [selectedGroupId, fetchGroupDetail]);

  const fetchUserResources = () => {
    if (!searchEmpNo.trim() || !selectedApp) return;
    api
      .get<UserResource[]>(
        `/api/auth/users/${searchEmpNo.trim()}/apps/${selectedApp}/resources`,
      )
      .then((r) => {
        setUserResources(r.data);
        setUserSearched(true);
      })
      .catch(() => {
        setUserResources([]);
        setUserSearched(true);
      });
  };

  /* ================================================================
     액션: 그룹
     ================================================================ */

  const handleCreateGroup = async () => {
    if (!newGroupName.trim() || !selectedApp) return;
    try {
      await api.post(`/api/auth/apps/${selectedApp}/groups`, {
        groupName: newGroupName.trim(),
      });
      showToast("success", `그룹 "${newGroupName.trim()}" 생성 완료`);
      setNewGroupName("");
      fetchGroups(selectedApp);
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "그룹 생성 실패");
    }
  };

  /* ================================================================
     액션: 멤버
     ================================================================ */

  const handleAddMember = async () => {
    if (!newMemberNo.trim() || !selectedGroupId) return;
    try {
      await api.post(`/api/auth/groups/${selectedGroupId}/members`, {
        employeeNumber: newMemberNo.trim(),
      });
      showToast("success", `멤버 ${newMemberNo.trim()} 추가 완료`);
      setNewMemberNo("");
      fetchGroupDetail(selectedGroupId);
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "멤버 추가 실패");
    }
  };

  const handleRemoveMember = async (empNo: string) => {
    try {
      await api.delete(
        `/api/auth/groups/${selectedGroupId}/members/${empNo}`,
      );
      showToast("success", `멤버 ${empNo} 삭제 완료`);
      fetchGroupDetail(selectedGroupId);
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "멤버 삭제 실패");
    }
  };

  /* ================================================================
     액션: 그룹 리소스
     ================================================================ */

  const handleAddGroupResource = async () => {
    if (!newGroupRes.trim() || !selectedGroupId) return;
    try {
      await api.post(`/api/auth/groups/${selectedGroupId}/resources`, {
        resourceName: newGroupRes.trim(),
      });
      showToast("success", "리소스 추가 완료");
      setNewGroupRes("");
      fetchGroupDetail(selectedGroupId);
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "리소스 추가 실패");
    }
  };

  const handleRemoveGroupResource = async (resName: string) => {
    try {
      await api.delete(`/api/auth/groups/${selectedGroupId}/resources`, {
        params: { resourceName: resName },
      });
      showToast("success", "리소스 삭제 완료");
      fetchGroupDetail(selectedGroupId);
    } catch (e) {
      showToast("error", e instanceof Error ? e.message : "리소스 삭제 실패");
    }
  };

  /* ================================================================
     액션: 개인 리소스
     ================================================================ */

  const handleAddUserResource = async () => {
    if (!newUserRes.trim() || !searchEmpNo.trim() || !selectedApp) return;
    try {
      await api.post(
        `/api/auth/users/${searchEmpNo.trim()}/apps/${selectedApp}/resources`,
        { resourceName: newUserRes.trim() },
      );
      showToast("success", "개인 리소스 추가 완료");
      setNewUserRes("");
      fetchUserResources();
    } catch (e) {
      showToast(
        "error",
        e instanceof Error ? e.message : "개인 리소스 추가 실패",
      );
    }
  };

  const handleRemoveUserResource = async (resName: string) => {
    try {
      await api.delete(
        `/api/auth/users/${searchEmpNo.trim()}/apps/${selectedApp}/resources`,
        { params: { resourceName: resName } },
      );
      showToast("success", "개인 리소스 삭제 완료");
      fetchUserResources();
    } catch (e) {
      showToast(
        "error",
        e instanceof Error ? e.message : "개인 리소스 삭제 실패",
      );
    }
  };

  /* ── 선택된 그룹 이름 ──────────────────────────────────── */
  const selectedGroupName =
    groups.find((g) => g.groupId === selectedGroupId)?.groupName ?? "";

  /* ================================================================
     렌더링
     ================================================================ */
  return (
    <div>
      {/* 페이지 헤더 */}
      <h1 className="text-2xl font-bold text-gray-900 mb-6">권한관리</h1>

      {/* 토스트 */}
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

      {/* 2-컬럼 레이아웃 */}
      <div className="flex gap-6 items-start">
        {/* ──────────────────────────────────────────────────
            좌측 패널: App → Group 트리
            ────────────────────────────────────────────────── */}
        <div className="w-72 shrink-0 space-y-4">
          {/* App 선택 */}
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <div className="bg-header px-4 py-2.5 border-b border-gray-200">
              <span className="text-sm font-semibold text-gray-700">
                애플리케이션
              </span>
            </div>
            <ul className="p-2">
              {apps.map((app) => (
                <li key={app.appId}>
                  <button
                    onClick={() => {
                      setSelectedApp(app.appName);
                      setSelectedGroupId("");
                      setMembers([]);
                      setGroupResources([]);
                      setUserSearched(false);
                    }}
                    className={`w-full text-left px-3 py-2 rounded-md text-sm transition-colors ${
                      selectedApp === app.appName
                        ? "bg-primary text-white font-medium"
                        : "text-gray-700 hover:bg-gray-100"
                    }`}
                  >
                    {app.appName}
                    {app.description && (
                      <span className="block text-xs mt-0.5 opacity-70">
                        {app.description}
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          </div>

          {/* Group 목록 */}
          {selectedApp && (
            <div className="bg-white rounded-lg shadow overflow-hidden">
              <div className="bg-header px-4 py-2.5 border-b border-gray-200 flex items-center justify-between">
                <span className="text-sm font-semibold text-gray-700">
                  그룹
                </span>
                <span className="text-xs text-gray-400">
                  {groups.length}개
                </span>
              </div>

              <ul className="p-2">
                {groups.map((g) => (
                  <li key={g.groupId}>
                    <button
                      onClick={() => setSelectedGroupId(g.groupId)}
                      className={`w-full text-left px-3 py-2 rounded-md text-sm transition-colors ${
                        selectedGroupId === g.groupId
                          ? "bg-primary text-white font-medium"
                          : "text-gray-700 hover:bg-gray-100"
                      }`}
                    >
                      <span className="capitalize">{g.groupName}</span>
                      <span
                        className={`ml-2 text-[10px] px-1.5 py-0.5 rounded ${
                          selectedGroupId === g.groupId
                            ? "bg-white/20"
                            : "bg-secondary text-primary"
                        }`}
                      >
                        {g.groupId}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>

              {/* 그룹 생성 */}
              <div className="px-3 py-3 border-t border-gray-100">
                <div className="flex gap-1.5">
                  <input
                    type="text"
                    value={newGroupName}
                    onChange={(e) => setNewGroupName(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleCreateGroup()}
                    placeholder="새 그룹명"
                    className="flex-1 min-w-0 px-2.5 py-1.5 border border-gray-300 rounded-md text-xs
                               focus:ring-2 focus:ring-primary/40 focus:border-primary"
                  />
                  <button
                    onClick={handleCreateGroup}
                    disabled={!newGroupName.trim()}
                    className="px-3 py-1.5 bg-primary text-white text-xs rounded-md
                               hover:bg-primary/90 disabled:opacity-40 transition-colors shrink-0"
                  >
                    추가
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* ──────────────────────────────────────────────────
            우측 패널: 상세
            ────────────────────────────────────────────────── */}
        <div className="flex-1 min-w-0">
          {/* 탭 전환 */}
          {selectedApp && (
            <div className="flex gap-1 mb-4">
              {(
                [
                  ["group", "그룹 권한"],
                  ["user", "개인 리소스"],
                ] as const
              ).map(([key, label]) => (
                <button
                  key={key}
                  onClick={() => setTab(key)}
                  className={`px-4 py-2 text-sm rounded-t-lg transition-colors ${
                    tab === key
                      ? "bg-white text-primary font-medium shadow"
                      : "text-gray-500 hover:text-gray-700"
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          )}

          {/* ── 그룹 권한 탭 ─────────────────────────────── */}
          {tab === "group" && (
            <>
              {!selectedGroupId ? (
                <div className="bg-white rounded-lg shadow p-12 text-center text-gray-400">
                  {selectedApp
                    ? "좌측에서 그룹을 선택하세요"
                    : "좌측에서 앱을 선택하세요"}
                </div>
              ) : (
                <div className="space-y-4">
                  {/* 그룹 헤더 */}
                  <div className="bg-white rounded-lg shadow overflow-hidden">
                    <div className="bg-header px-5 py-3 border-b border-gray-200">
                      <h2 className="text-base font-semibold text-gray-800">
                        <span className="capitalize">{selectedGroupName}</span>
                        <span className="ml-2 text-xs font-normal text-gray-400">
                          {selectedGroupId}
                        </span>
                      </h2>
                    </div>
                  </div>

                  {/* 멤버 */}
                  <div className="bg-white rounded-lg shadow overflow-hidden">
                    <div className="bg-header px-5 py-2.5 border-b border-gray-200 flex items-center justify-between">
                      <span className="text-sm font-semibold text-gray-700">
                        멤버 ({members.length})
                      </span>
                    </div>

                    {/* 멤버 추가 */}
                    <div className="px-5 py-3 border-b border-gray-100 bg-gray-50/50">
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={newMemberNo}
                          onChange={(e) => setNewMemberNo(e.target.value)}
                          onKeyDown={(e) =>
                            e.key === "Enter" && handleAddMember()
                          }
                          placeholder="사번 입력 (예: EMP005)"
                          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm
                                     focus:ring-2 focus:ring-primary/40 focus:border-primary"
                        />
                        <button
                          onClick={handleAddMember}
                          disabled={!newMemberNo.trim()}
                          className="px-4 py-2 bg-primary text-white text-sm rounded-lg
                                     hover:bg-primary/90 disabled:opacity-40 transition-colors"
                        >
                          추가
                        </button>
                      </div>
                    </div>

                    {/* 멤버 목록 */}
                    <ul className="divide-y divide-gray-50">
                      {members.map((m) => (
                        <li
                          key={m.employeeNumber}
                          className="px-5 py-2.5 flex items-center justify-between hover:bg-gray-50/50"
                        >
                          <span className="text-sm font-mono">
                            {m.employeeNumber}
                          </span>
                          <button
                            onClick={() =>
                              handleRemoveMember(m.employeeNumber)
                            }
                            className="text-xs text-error hover:bg-error/10 px-2 py-1 rounded transition-colors"
                          >
                            삭제
                          </button>
                        </li>
                      ))}
                      {members.length === 0 && (
                        <li className="px-5 py-6 text-center text-sm text-gray-400">
                          멤버가 없습니다
                        </li>
                      )}
                    </ul>
                  </div>

                  {/* 그룹 리소스 */}
                  <div className="bg-white rounded-lg shadow overflow-hidden">
                    <div className="bg-header px-5 py-2.5 border-b border-gray-200 flex items-center justify-between">
                      <span className="text-sm font-semibold text-gray-700">
                        그룹 리소스 ({groupResources.length})
                      </span>
                    </div>

                    {/* 리소스 추가 */}
                    <div className="px-5 py-3 border-b border-gray-100 bg-gray-50/50">
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={newGroupRes}
                          onChange={(e) => setNewGroupRes(e.target.value)}
                          onKeyDown={(e) =>
                            e.key === "Enter" && handleAddGroupResource()
                          }
                          placeholder="리소스 패턴 (예: /api/v1/**)"
                          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono
                                     focus:ring-2 focus:ring-primary/40 focus:border-primary"
                        />
                        <button
                          onClick={handleAddGroupResource}
                          disabled={!newGroupRes.trim()}
                          className="px-4 py-2 bg-primary text-white text-sm rounded-lg
                                     hover:bg-primary/90 disabled:opacity-40 transition-colors"
                        >
                          추가
                        </button>
                      </div>
                    </div>

                    {/* 리소스 목록 */}
                    <ul className="divide-y divide-gray-50">
                      {groupResources.map((r) => (
                        <li
                          key={r.resourceName}
                          className="px-5 py-2.5 flex items-center justify-between hover:bg-gray-50/50"
                        >
                          <span className="text-sm font-mono text-gray-700">
                            {r.resourceName}
                          </span>
                          <button
                            onClick={() =>
                              handleRemoveGroupResource(r.resourceName)
                            }
                            className="text-xs text-error hover:bg-error/10 px-2 py-1 rounded transition-colors"
                          >
                            삭제
                          </button>
                        </li>
                      ))}
                      {groupResources.length === 0 && (
                        <li className="px-5 py-6 text-center text-sm text-gray-400">
                          리소스가 없습니다
                        </li>
                      )}
                    </ul>
                  </div>
                </div>
              )}
            </>
          )}

          {/* ── 개인 리소스 탭 ───────────────────────────── */}
          {tab === "user" && (
            <div className="space-y-4">
              {/* 사번 검색 */}
              <div className="bg-white rounded-lg shadow overflow-hidden">
                <div className="bg-header px-5 py-2.5 border-b border-gray-200">
                  <span className="text-sm font-semibold text-gray-700">
                    사원 검색
                  </span>
                </div>
                <div className="px-5 py-4">
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={searchEmpNo}
                      onChange={(e) => {
                        setSearchEmpNo(e.target.value);
                        setUserSearched(false);
                      }}
                      onKeyDown={(e) =>
                        e.key === "Enter" && fetchUserResources()
                      }
                      placeholder="사번 입력 (예: EMP001)"
                      className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm
                                 focus:ring-2 focus:ring-primary/40 focus:border-primary"
                    />
                    <button
                      onClick={fetchUserResources}
                      disabled={!searchEmpNo.trim() || !selectedApp}
                      className="px-4 py-2 bg-primary text-white text-sm rounded-lg
                                 hover:bg-primary/90 disabled:opacity-40 transition-colors"
                    >
                      검색
                    </button>
                  </div>
                  {!selectedApp && (
                    <p className="text-xs text-gray-400 mt-2">
                      좌측에서 앱을 먼저 선택하세요
                    </p>
                  )}
                </div>
              </div>

              {/* 개인 리소스 결과 */}
              {userSearched && (
                <div className="bg-white rounded-lg shadow overflow-hidden">
                  <div className="bg-header px-5 py-2.5 border-b border-gray-200 flex items-center justify-between">
                    <span className="text-sm font-semibold text-gray-700">
                      {searchEmpNo}의 개인 리소스 ({userResources.length})
                    </span>
                    <span className="text-xs text-gray-400">
                      App: {selectedApp}
                    </span>
                  </div>

                  {/* 리소스 추가 */}
                  <div className="px-5 py-3 border-b border-gray-100 bg-gray-50/50">
                    <div className="flex gap-2">
                      <input
                        type="text"
                        value={newUserRes}
                        onChange={(e) => setNewUserRes(e.target.value)}
                        onKeyDown={(e) =>
                          e.key === "Enter" && handleAddUserResource()
                        }
                        placeholder="리소스 패턴 (예: /api/test/**)"
                        className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono
                                   focus:ring-2 focus:ring-primary/40 focus:border-primary"
                      />
                      <button
                        onClick={handleAddUserResource}
                        disabled={!newUserRes.trim()}
                        className="px-4 py-2 bg-primary text-white text-sm rounded-lg
                                   hover:bg-primary/90 disabled:opacity-40 transition-colors"
                      >
                        추가
                      </button>
                    </div>
                  </div>

                  {/* 목록 */}
                  <ul className="divide-y divide-gray-50">
                    {userResources.map((r) => (
                      <li
                        key={r.resourceName}
                        className="px-5 py-2.5 flex items-center justify-between hover:bg-gray-50/50"
                      >
                        <span className="text-sm font-mono text-gray-700">
                          {r.resourceName}
                        </span>
                        <button
                          onClick={() =>
                            handleRemoveUserResource(r.resourceName)
                          }
                          className="text-xs text-error hover:bg-error/10 px-2 py-1 rounded transition-colors"
                        >
                          삭제
                        </button>
                      </li>
                    ))}
                    {userResources.length === 0 && (
                      <li className="px-5 py-6 text-center text-sm text-gray-400">
                        등록된 개인 리소스가 없습니다
                      </li>
                    )}
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
