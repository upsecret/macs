import { useCallback } from "react";
import { useAuthStore } from "../stores/authStore";
import { getDefaultPath } from "../utils/permissions";
import api from "../utils/api";
import type { AuthResponse } from "../types";

export function useAuth() {
  const store = useAuthStore();

  const login = useCallback(async (appName: string, employeeNumber: string) => {
    const { data } = await api.post<AuthResponse>(
      "/api/auth/token",
      { app_name: appName, employee_number: employeeNumber },
      { headers: { app_name: appName, employee_number: employeeNumber } },
    );

    store.setAuth({
      token: data.token,
      appName: data.system,
      employeeNumber: data.employee_number,
      group: data.group,
      allowedResourcesList: data.allowed_resources_list,
    });

    return data;
  }, [store]);

  const logout = useCallback(() => { store.logout(); }, [store]);

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
