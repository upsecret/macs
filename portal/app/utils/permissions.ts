export interface MenuItem {
  key: string;
  path: string;
  label: string;
}

export const allMenus: MenuItem[] = [
  { key: "connector", path: "/connector", label: "커넥터연동" },
  { key: "auth-manage", path: "/auth-manage", label: "권한관리" },
  { key: "route-config", path: "/route-config", label: "경로설정" },
  { key: "settings", path: "/settings", label: "설정정보" },
];

export function getMenus(): MenuItem[] {
  return allMenus;
}

export function getMenusForRole(role: string | null): MenuItem[] {
  if (role === "admin") return allMenus;
  // non-admin (user, viewer, etc.): only connector 페이지
  return allMenus.filter((m) => m.key === "connector");
}

export function getDefaultPath(): string {
  return allMenus[0]?.path ?? "/login";
}

export const ROUTE_MIN_ROLE: Record<string, "admin" | "user"> = {
  "/auth-manage": "admin",
  "/route-config": "admin",
  "/settings": "admin",
  "/connector": "user",
};
