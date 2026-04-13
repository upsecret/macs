import { act, renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuth } from "~/hooks/useAuth";
import { useAuthStore } from "~/stores/authStore";
import { server } from "../msw/server";

describe("hooks/useAuth", () => {
  it("login: calls /api/auth/token then fetches permissions and persists to store", async () => {
    const { result } = renderHook(() => useAuth());

    await act(async () => {
      await result.current.login("portal", "2078432");
    });

    await waitFor(() => {
      expect(useAuthStore.getState().token).toBeTruthy();
    });
    const state = useAuthStore.getState();
    expect(state.employeeNumber).toBe("2078432");
    expect(state.appName).toBe("portal");
    expect(state.permissions).toEqual([
      { system: "common", connector: "portal", role: "admin" },
    ]);
    expect(state.isAuthenticated()).toBe(true);
  });

  it("login: token endpoint 500 → throws, store stays clean", async () => {
    server.use(
      http.post("/api/auth/token", () =>
        HttpResponse.json({ message: "upstream down" }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useAuth());

    let caught: unknown;
    await act(async () => {
      try {
        await result.current.login("portal", "2078432");
      } catch (e) {
        caught = e;
      }
    });

    expect(caught).toBeDefined();
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
  });

  it("login: permissions fetch 403 → throws, store stays clean", async () => {
    // token 은 성공, 하지만 permission 조회가 403 (axios 인터셉터가 처리)
    server.use(
      http.get("/api/admin/permissions/users/:app/:emp", () =>
        HttpResponse.json({ message: "forbidden" }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useAuth());

    let caught: unknown;
    await act(async () => {
      try {
        await result.current.login("portal", "2078432");
      } catch (e) {
        caught = e;
      }
    });

    expect(caught).toBeDefined();
    expect(useAuthStore.getState().token).toBeNull();
  });

  it("logout: clears store", async () => {
    // store seed 는 renderHook 호출 전에 — hook subscriber 가 생기기 전이라 re-render 경고 없음
    useAuthStore.setState({
      token: "x",
      appName: "portal",
      employeeNumber: "2078432",
      permissions: [{ system: "common", connector: "portal", role: "admin" }],
    });
    expect(useAuthStore.getState().isAuthenticated()).toBe(true);

    const { result } = renderHook(() => useAuth());

    await act(async () => {
      result.current.logout();
    });

    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    expect(useAuthStore.getState().permissions).toEqual([]);
  });
});
