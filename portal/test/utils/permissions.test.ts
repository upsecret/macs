import { describe, expect, it } from "vitest";
import {
  allMenus,
  getDefaultPath,
  getMenus,
  getMenusForRole,
  ROUTE_MIN_ROLE,
} from "~/utils/permissions";

describe("utils/permissions", () => {
  describe("allMenus", () => {
    it("defines exactly 4 menus in documented order", () => {
      expect(allMenus.map((m) => m.key)).toEqual([
        "connector",
        "auth-manage",
        "route-config",
        "settings",
      ]);
    });

    it("each menu has key / path / label", () => {
      for (const m of allMenus) {
        expect(m.key).toBeTypeOf("string");
        expect(m.path).toMatch(/^\//);
        expect(m.label).toBeTypeOf("string");
      }
    });
  });

  describe("getMenus", () => {
    it("returns all menus unconditionally", () => {
      expect(getMenus()).toHaveLength(4);
    });
  });

  describe("getMenusForRole", () => {
    it("admin sees every menu", () => {
      const menus = getMenusForRole("admin");
      expect(menus).toHaveLength(4);
      expect(menus.map((m) => m.key)).toContain("auth-manage");
    });

    it("viewer only sees the connector menu", () => {
      const menus = getMenusForRole("viewer");
      expect(menus).toHaveLength(1);
      expect(menus[0].key).toBe("connector");
    });

    it("operator only sees the connector menu (non-admin policy)", () => {
      const menus = getMenusForRole("operator");
      expect(menus).toHaveLength(1);
      expect(menus[0].key).toBe("connector");
    });

    it("null role falls back to the connector menu", () => {
      const menus = getMenusForRole(null);
      expect(menus).toHaveLength(1);
      expect(menus[0].key).toBe("connector");
    });

    it("unknown role string is treated as non-admin", () => {
      const menus = getMenusForRole("stranger");
      expect(menus).toHaveLength(1);
      expect(menus[0].key).toBe("connector");
    });
  });

  describe("getDefaultPath", () => {
    it("returns /connector (first menu)", () => {
      expect(getDefaultPath()).toBe("/connector");
    });
  });

  describe("ROUTE_MIN_ROLE", () => {
    it("requires admin for admin pages", () => {
      expect(ROUTE_MIN_ROLE["/auth-manage"]).toBe("admin");
      expect(ROUTE_MIN_ROLE["/route-config"]).toBe("admin");
      expect(ROUTE_MIN_ROLE["/settings"]).toBe("admin");
    });

    it("connector is user-level", () => {
      expect(ROUTE_MIN_ROLE["/connector"]).toBe("user");
    });
  });
});
