import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { PermissionEntry } from "../types";

interface AuthPayload {
  token: string;
  clientApp: string;
  employeeNumber: string;
  permissions: PermissionEntry[];
}

interface AuthState {
  /* ── state ─────────────────────────────────────────────── */
  token: string | null;
  clientApp: string | null;
  employeeNumber: string | null;
  permissions: PermissionEntry[];

  /* ── actions ───────────────────────────────────────────── */
  setAuth: (payload: AuthPayload) => void;
  logout: () => void;

  /* ── derived helpers ───────────────────────────────────── */
  isAuthenticated: () => boolean;
  isAllowedConnector: (connectorId: string) => boolean;
  getRole: () => string | null;
  isAdmin: () => boolean;
}

const INITIAL: Pick<
  AuthState,
  "token" | "clientApp" | "employeeNumber" | "permissions"
> = {
  token: null,
  clientApp: null,
  employeeNumber: null,
  permissions: [],
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      ...INITIAL,

      setAuth: (payload) =>
        set({
          token: payload.token,
          clientApp: payload.clientApp,
          employeeNumber: payload.employeeNumber,
          permissions: payload.permissions,
        }),

      logout: () => set(INITIAL),

      isAuthenticated: () => !!get().token,

      isAllowedConnector: (connectorId: string) =>
        get().permissions.some((p) => p.connector === connectorId),

      getRole: () => {
        const perms = get().permissions;
        if (perms.some((p) => p.role === "admin")) return "admin";
        if (perms.length > 0) return perms[0].role;
        return null;
      },

      isAdmin: () => get().permissions.some((p) => p.role === "admin"),
    }),
    {
      name: "macs-auth",
      version: 2,
    },
  ),
);
