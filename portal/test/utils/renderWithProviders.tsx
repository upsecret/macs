import { type ReactElement } from "react";
import { render, type RenderOptions } from "@testing-library/react";
import { MemoryRouter } from "react-router";

interface Options extends Omit<RenderOptions, "wrapper"> {
  route?: string;
}

// MemoryRouter 기반 래퍼. RR7 의 data router(createMemoryRouter + RouterProvider)
// 는 loader/action 이 있을 때만 필요하고, 현재 라우트들은 컴포넌트 본체에서
// 모든 fetching 을 하므로 MemoryRouter 로 충분하다.
export function renderWithProviders(ui: ReactElement, { route = "/", ...rest }: Options = {}) {
  return render(ui, {
    wrapper: ({ children }) => (
      <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
    ),
    ...rest,
  });
}
