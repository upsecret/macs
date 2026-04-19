import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router";
import { render } from "@testing-library/react";
import ProtectedRoute from "~/components/ProtectedRoute";
import { useAuthStore } from "~/stores/authStore";

function renderAt(pathname: string) {
  return render(
    <MemoryRouter initialEntries={[pathname]}>
      <Routes>
        <Route path="/login" element={<div>LOGIN_PAGE</div>} />
        <Route path="/connector" element={<div>CONNECTOR_PAGE</div>} />
        <Route
          path="/auth-manage"
          element={
            <ProtectedRoute>
              <div>AUTH_MANAGE_CONTENT</div>
            </ProtectedRoute>
          }
        />
        <Route
          path="/route-config"
          element={
            <ProtectedRoute>
              <div>ROUTE_CONFIG_CONTENT</div>
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("components/ProtectedRoute", () => {
  it("redirects to /login when not authenticated", () => {
    renderAt("/auth-manage");
    expect(screen.getByText("LOGIN_PAGE")).toBeInTheDocument();
    expect(screen.queryByText("AUTH_MANAGE_CONTENT")).not.toBeInTheDocument();
  });

  it("renders children when authenticated admin hits admin route", () => {
    useAuthStore.setState({
      token: "x",
      clientApp: "portal",
      employeeNumber: "2078432",
      permissions: [{ system: "common", connector: "portal", role: "admin" }],
    });
    renderAt("/auth-manage");
    expect(screen.getByText("AUTH_MANAGE_CONTENT")).toBeInTheDocument();
  });

  it("redirects non-admin away from admin-only route to /connector", () => {
    useAuthStore.setState({
      token: "x",
      clientApp: "portal",
      employeeNumber: "2065162",
      permissions: [{ system: "common", connector: "portal", role: "viewer" }],
    });
    renderAt("/auth-manage");
    expect(screen.getByText("CONNECTOR_PAGE")).toBeInTheDocument();
    expect(screen.queryByText("AUTH_MANAGE_CONTENT")).not.toBeInTheDocument();
  });

  it("admin can access route-config too", () => {
    useAuthStore.setState({
      token: "x",
      clientApp: "portal",
      employeeNumber: "2078432",
      permissions: [{ system: "common", connector: "portal", role: "admin" }],
    });
    renderAt("/route-config");
    expect(screen.getByText("ROUTE_CONFIG_CONTENT")).toBeInTheDocument();
  });
});
