import { beforeEach, describe, expect, it } from "vitest";
import { useAuthStore } from "~/stores/authStore";

const ADMIN_PERM = { system: "common", connector: "portal", role: "admin" };
const VIEWER_PERM = { system: "common", connector: "portal", role: "viewer" };

function seed(perms = [ADMIN_PERM]) {
  useAuthStore.getState().setAuth({
    token: "test-token",
    clientApp: "portal",
    employeeNumber: "2078432",
    permissions: perms,
  });
}

describe("stores/authStore", () => {
  beforeEach(() => {
    useAuthStore.getState().logout();
  });

  describe("setAuth + isAuthenticated", () => {
    it("starts unauthenticated", () => {
      expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    });

    it("becomes authenticated after setAuth", () => {
      seed();
      const s = useAuthStore.getState();
      expect(s.isAuthenticated()).toBe(true);
      expect(s.token).toBe("test-token");
      expect(s.employeeNumber).toBe("2078432");
      expect(s.permissions).toHaveLength(1);
    });
  });

  describe("isAdmin", () => {
    it("true when any permission has role=admin", () => {
      seed([ADMIN_PERM]);
      expect(useAuthStore.getState().isAdmin()).toBe(true);
    });

    it("false when no admin permission", () => {
      seed([VIEWER_PERM]);
      expect(useAuthStore.getState().isAdmin()).toBe(false);
    });

    it("false when permissions empty", () => {
      seed([]);
      expect(useAuthStore.getState().isAdmin()).toBe(false);
    });
  });

  describe("isAllowedConnector", () => {
    it("true when permissions contain a matching connector", () => {
      seed([{ system: "common", connector: "rms-service", role: "viewer" }]);
      expect(useAuthStore.getState().isAllowedConnector("rms-service")).toBe(true);
    });

    it("false when no permission matches", () => {
      seed([ADMIN_PERM]);
      expect(useAuthStore.getState().isAllowedConnector("rms-service")).toBe(false);
    });
  });

  describe("getRole", () => {
    it("returns admin when any permission is admin (regardless of order)", () => {
      seed([VIEWER_PERM, ADMIN_PERM]);
      expect(useAuthStore.getState().getRole()).toBe("admin");
    });

    it("returns first permission role when no admin", () => {
      seed([VIEWER_PERM]);
      expect(useAuthStore.getState().getRole()).toBe("viewer");
    });

    it("returns null when no permissions", () => {
      seed([]);
      expect(useAuthStore.getState().getRole()).toBeNull();
    });
  });

  describe("logout", () => {
    it("clears token/permissions/employeeNumber", () => {
      seed();
      useAuthStore.getState().logout();
      const s = useAuthStore.getState();
      expect(s.token).toBeNull();
      expect(s.employeeNumber).toBeNull();
      expect(s.permissions).toEqual([]);
      expect(s.isAuthenticated()).toBe(false);
    });
  });
});
