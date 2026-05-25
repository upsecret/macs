import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Sidebar from "~/components/Sidebar";
import { useAuthStore } from "~/stores/authStore";

// Next.js navigation hooks mock
vi.mock("next/navigation", () => ({
  usePathname: () => "/connector",
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    refresh: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

describe("components/Sidebar", () => {
  it("admin sees all 4 menus", () => {
    useAuthStore.setState({
      token: "x",
      appName: "portal",
      employeeNumber: "2078432",
      permissions: [{ system: "common", connector: "portal", role: "admin" }],
    });

    render(<Sidebar />);
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

    render(<Sidebar />);
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

    render(<Sidebar />);
    expect(screen.getByText("커넥터연동")).toBeInTheDocument();
    expect(screen.queryByText("권한관리")).not.toBeInTheDocument();
  });
});
