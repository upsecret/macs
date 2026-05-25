/**
 * portal 접근 자체에 필요한 connector 식별자. PERMISSION 테이블의
 * (system=common, connector=portal-route) 행이 있어야 portal 진입 가능.
 * gateway-service의 portal-route(라우트 id)와 이름을 통일.
 */
export const PORTAL_CONNECTOR = "portal-route";

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
  // non-admin (user, operator, etc.): only connector 페이지
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
