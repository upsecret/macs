import { useCallback } from "react";
import { useAuthStore } from "../stores/authStore";
import { getDefaultPath } from "../utils/permissions";
import api from "../utils/api";
import type { AuthResponse, UserPermissionsResponse } from "../types";

export function useAuth() {
  const store = useAuthStore();

  const login = useCallback(async (appName: string, employeeNumber: string) => {
    // 1. token 발급 — body 는 employee_number 만. 토큰엔 권한이 들어있지 않다.
    const { data: tokenData } = await api.post<AuthResponse>(
      "/api/auth/token",
      { employee_number: employeeNumber },
      { headers: { app_name: appName, employee_number: employeeNumber } },
    );

    // 2. 권한은 admin-server 에서 별도 조회. 사이드바/route 가드용.
    //    이 호출은 token + app_name 헤더가 필요한데, 아직 store 에 token 이 없어
    //    수동으로 헤더를 넣어준다.
    const { data: permData } = await api.get<UserPermissionsResponse>(
      `/api/admin/permissions/users/${appName}/${employeeNumber}`,
      {
        headers: {
          Authorization: `Bearer ${tokenData.token}`,
          app_name: appName,
          employee_number: employeeNumber,
        },
      },
    );

    store.setAuth({
      token: tokenData.token,
      appName,
      employeeNumber: tokenData.employee_number,
      permissions: permData.permissions ?? [],
    });

    return tokenData;
  }, [store]);

  const logout = useCallback(() => { store.logout(); }, [store]);

  return {
    token: store.token,
    appName: store.appName,
    employeeNumber: store.employeeNumber,
    permissions: store.permissions,
    isAuthenticated: store.isAuthenticated(),
    isAllowedConnector: store.isAllowedConnector,
    defaultPath: getDefaultPath(),
    login,
    logout,
  };
}
