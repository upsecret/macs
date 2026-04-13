import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import Sidebar from "~/components/Sidebar";
import { useAuthStore } from "~/stores/authStore";

function renderSidebar() {
  return render(
    <MemoryRouter>
      <Sidebar />
    </MemoryRouter>,
  );
}

describe("components/Sidebar", () => {
  it("admin sees all 4 menus", () => {
    useAuthStore.setState({
      token: "x",
      appName: "portal",
      employeeNumber: "2078432",
      permissions: [{ system: "common", connector: "portal", role: "admin" }],
    });

    renderSidebar();
    expect(screen.getByText("커넥터연동")).toBeInTheDocument();
    expect(screen.getByText("권한관리")).toBeInTheDocument();
    expect(screen.getByText("경로설정")).toBeInTheDocument();
    expect(screen.getByText("설정정보")).toBeInTheDocument();
  });

  it("viewer sees only 커넥터연동", () => {
    useAuthStore.setState({
      token: "x",
      appName: "portal",
      employeeNumber: "2065162",
      permissions: [{ system: "common", connector: "portal", role: "viewer" }],
    });

    renderSidebar();
    expect(screen.getByText("커넥터연동")).toBeInTheDocument();
    expect(screen.queryByText("권한관리")).not.toBeInTheDocument();
    expect(screen.queryByText("경로설정")).not.toBeInTheDocument();
    expect(screen.queryByText("설정정보")).not.toBeInTheDocument();
  });

  it("no permissions → only 커넥터연동 (matches getMenusForRole(null))", () => {
    useAuthStore.setState({
      token: "x",
      appName: "portal",
      employeeNumber: "2078432",
      permissions: [],
    });

    renderSidebar();
    expect(screen.getByText("커넥터연동")).toBeInTheDocument();
    expect(screen.queryByText("권한관리")).not.toBeInTheDocument();
  });
});
