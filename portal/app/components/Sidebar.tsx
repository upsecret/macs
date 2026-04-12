import { NavLink } from "react-router";
import { Route, ShieldCheck, Plug, Settings, type LucideIcon } from "lucide-react";
import { useAuthStore } from "../stores/authStore";
import { getMenusForRole } from "../utils/permissions";

const ICONS: Record<string, LucideIcon> = {
  "route-config": Route,
  "auth-manage": ShieldCheck,
  "connector": Plug,
  "settings": Settings,
};

export default function Sidebar() {
  const getRole = useAuthStore((s) => s.getRole);
  const menus = getMenusForRole(getRole());

  return (
    <aside className="w-56 bg-navbar-bg h-screen shrink-0 flex flex-col">
      <NavLink to="/" className="h-14 flex items-center px-6 shrink-0">
        <span className="text-white text-xl font-extrabold tracking-widest">
          MACS
        </span>
      </NavLink>

      <ul className="flex-1 px-3 py-4 space-y-1">
        {menus.map((menu) => {
          const Icon = ICONS[menu.key];
          return (
            <li key={menu.key}>
              <NavLink
                to={menu.path}
                className={({ isActive }) =>
                  [
                    "flex items-center gap-3 px-3 py-2 text-sm rounded-md transition-colors",
                    isActive
                      ? "bg-primary text-white font-medium"
                      : "text-gray-300 hover:text-accent hover:bg-white/5",
                  ].join(" ")
                }
              >
                {Icon && <Icon size={18} strokeWidth={1.75} />}
                <span>{menu.label}</span>
              </NavLink>
            </li>
          );
        })}
      </ul>
    </aside>
  );
}
