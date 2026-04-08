import { create } from "zustand";
import { persist } from "zustand/middleware";
import { isMenuAllowed } from "../utils/permissions";

interface AuthPayload {
  token: string;
  appName: string;
  employeeNumber: string;
  group: string;
  allowedResourcesList: string[];
}

interface AuthState {
  /* ── state ─────────────────────────────────────────────── */
  token: string | null;
  appName: string | null;
  employeeNumber: string | null;
  group: string | null;
  allowedResourcesList: string[];

  /* ── actions ───────────────────────────────────────────── */
  setAuth: (payload: AuthPayload) => void;
  logout: () => void;

  /* ── derived helpers ───────────────────────────────────── */
  isAuthenticated: () => boolean;
  isAllowed: (menuKey: string) => boolean;
}

const INITIAL: Pick<
  AuthState,
  "token" | "appName" | "employeeNumber" | "group" | "allowedResourcesList"
> = {
  token: null,
  appName: null,
  employeeNumber: null,
  group: null,
  allowedResourcesList: [],
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      ...INITIAL,

      setAuth: (payload) =>
        set({
          token: payload.token,
          appName: payload.appName,
          employeeNumber: payload.employeeNumber,
          group: payload.group,
          allowedResourcesList: payload.allowedResourcesList,
        }),

      logout: () => set(INITIAL),

      isAuthenticated: () => !!get().token,

      isAllowed: (menuKey: string) => {
        const { group } = get();
        if (!group) return false;
        return isMenuAllowed(group, menuKey);
      },
    }),
    { name: "macs-auth" },
  ),
);
