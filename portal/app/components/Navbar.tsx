import { NavLink, useNavigate } from "react-router";
import { useAuthStore } from "../stores/authStore";
import { getMenusForGroup } from "../utils/permissions";

export default function Navbar() {
  const navigate = useNavigate();
  const { group, employeeNumber, logout } = useAuthStore();
  const menus = getMenusForGroup(group ?? "user");

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <nav className="h-14 bg-navbar-bg flex items-center px-6 shrink-0">
      {/* ── 좌측: 로고 ────────────────────────────────────── */}
      <NavLink to="/" className="flex items-center gap-2 mr-10">
        <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
          <span className="text-white text-sm font-bold">M</span>
        </div>
        <span className="text-white text-lg font-bold tracking-wide">
          MACS
        </span>
      </NavLink>

      {/* ── 중앙: 메뉴 ────────────────────────────────────── */}
      <ul className="flex items-center gap-1 flex-1">
        {menus.map((menu) => (
          <li key={menu.key}>
            <NavLink
              to={menu.path}
              className={({ isActive }) =>
                [
                  "relative px-4 py-2 text-sm rounded-md transition-colors",
                  isActive
                    ? "bg-primary text-white font-medium"
                    : "text-gray-300 hover:text-accent",
                ].join(" ")
              }
            >
              {menu.label}
            </NavLink>
          </li>
        ))}
      </ul>

      {/* ── 우측: 사용자 정보 ──────────────────────────────── */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          {/* 아바타 */}
          <div className="w-7 h-7 rounded-full bg-white/10 flex items-center justify-center">
            <span className="text-xs text-gray-300">
              {employeeNumber?.slice(-2)}
            </span>
          </div>

          {/* 사번 + 그룹 */}
          <div className="leading-tight">
            <p className="text-sm text-white">{employeeNumber}</p>
            <p className="text-[11px] text-primary-text capitalize">{group}</p>
          </div>
        </div>

        {/* 구분선 + 로그아웃 */}
        <div className="w-px h-6 bg-white/15" />
        <button
          onClick={handleLogout}
          className="text-xs text-gray-400 hover:text-accent transition-colors"
        >
          로그아웃
        </button>
      </div>
    </nav>
  );
}
