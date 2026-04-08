import { useCallback } from "react";
import { useAuthStore } from "../stores/authStore";
import { getDefaultPath } from "../utils/permissions";
import api from "../utils/api";
import type { AuthResponse } from "../types";

export function useAuth() {
  const store = useAuthStore();

  const login = useCallback(async (appName: string, employeeNumber: string) => {
    // 로그인 시에는 store가 비어있으므로 헤더를 명시적으로 전달
    const { data } = await api.post<AuthResponse>(
      "/api/auth/token",
      { app_name: appName, employee_number: employeeNumber },
      {
        headers: {
          app_name: appName,
          employee_number: employeeNumber,
        },
      },
    );

    store.setAuth({
      token: data.token,
      appName: data.app_name,
      employeeNumber: data.employee_number,
      group: data.group,
      allowedResourcesList: data.allowed_resources_list,
    });

    return data;
  }, [store]);

  const logout = useCallback(() => {
    store.logout();
  }, [store]);

  return {
    token: store.token,
    appName: store.appName,
    employeeNumber: store.employeeNumber,
    group: store.group,
    allowedResourcesList: store.allowedResourcesList,
    isAuthenticated: store.isAuthenticated(),
    isAllowed: store.isAllowed,
    defaultPath: getDefaultPath(store.group ?? "user"),
    login,
    logout,
  };
}
