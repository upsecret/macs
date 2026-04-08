export type Group = "admin" | "developer" | "operator" | "user";

export interface MenuItem {
  key: string;
  path: string;
  label: string;
  icon: string;
}

const allMenus: MenuItem[] = [
  { key: "route-config", path: "/route-config", label: "경로설정", icon: "🔀" },
  { key: "auth-manage", path: "/auth-manage", label: "권한관리", icon: "🔐" },
  { key: "connector", path: "/connector", label: "커넥터연동", icon: "🔗" },
  { key: "settings", path: "/settings", label: "설정정보", icon: "⚙️" },
];

const groupMenuKeys: Record<Group, string[]> = {
  admin: ["route-config", "auth-manage", "connector", "settings"],
  operator: ["route-config", "connector", "settings"],
  developer: ["route-config", "connector"],
  user: ["connector"],
};

export function getMenusForGroup(group: string): MenuItem[] {
  const keys = groupMenuKeys[group as Group] ?? [];
  return allMenus.filter((m) => keys.includes(m.key));
}

export function isMenuAllowed(group: string, menuKey: string): boolean {
  const keys = groupMenuKeys[group as Group] ?? [];
  return keys.includes(menuKey);
}

export function canAccess(group: string, pathname: string): boolean {
  const menus = getMenusForGroup(group);
  return menus.some((m) => pathname === m.path || pathname.startsWith(m.path + "/"));
}

export function getDefaultPath(group: string): string {
  const menus = getMenusForGroup(group);
  return menus[0]?.path ?? "/login";
}
