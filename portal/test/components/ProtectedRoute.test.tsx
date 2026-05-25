import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import ProtectedRoute from "~/components/ProtectedRoute";
import { useAuthStore } from "~/stores/authStore";

// usePathname 은 each test 에서 동적으로 변경하기 위해 변수 기반.
let currentPath = "/auth-manage";
const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => currentPath,
  useRouter: () => ({
    push: vi.fn(),
    replace: replaceMock,
    back: vi.fn(),
    forward: vi.fn(),
    refresh: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

beforeEach(() => {
  replaceMock.mockReset();
});

describe("components/ProtectedRoute", () => {
  it("redirects to /login when not authenticated", () => {
    currentPath = "/auth-manage";
    render(
      <ProtectedRoute>
        <div>AUTH_MANAGE_CONTENT</div>
      </ProtectedRoute>,
    );
    expect(replaceMock).toHaveBeenCalledWith("/login");
    expect(screen.queryByText("AUTH_MANAGE_CONTENT")).not.toBeInTheDocument();
  });

  it("renders children when authenticated admin hits admin route", () => {
    useAuthStore.setState({
      token: "x",
      appName: "portal",
      employeeNumber: "2078432",
      permissions: [{ system: "common", connector: "portal-route", role: "admin" }],
    });
    currentPath = "/auth-manage";
    render(
      <ProtectedRoute>
        <div>AUTH_MANAGE_CONTENT</div>
      </ProtectedRoute>,
    );
    expect(screen.getByText("AUTH_MANAGE_CONTENT")).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("redirects non-admin away from admin-only route to /connector", () => {
    useAuthStore.setState({
      token: "x",
      appName: "portal",
      employeeNumber: "2065162",
      permissions: [{ system: "common", connector: "portal-route", role: "user" }],
    });
    currentPath = "/auth-manage";
    render(
      <ProtectedRoute>
        <div>AUTH_MANAGE_CONTENT</div>
      </ProtectedRoute>,
    );
    expect(replaceMock).toHaveBeenCalledWith("/connector");
    expect(screen.queryByText("AUTH_MANAGE_CONTENT")).not.toBeInTheDocument();
  });

  it("admin can access route-config too", () => {
    useAuthStore.setState({
      token: "x",
      appName: "portal",
      employeeNumber: "2078432",
      permissions: [{ system: "common", connector: "portal-route", role: "admin" }],
    });
    currentPath = "/route-config";
    render(
      <ProtectedRoute>
        <div>ROUTE_CONFIG_CONTENT</div>
      </ProtectedRoute>,
    );
    expect(screen.getByText("ROUTE_CONFIG_CONTENT")).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });
});
