import { type ReactElement } from "react";
import { render, type RenderOptions } from "@testing-library/react";

interface Options extends Omit<RenderOptions, "wrapper"> {
  route?: string;
}

// Next.js App Router 환경 — useRouter/usePathname 등은 test/setup.ts 에서 mock 처리.
// React Router 시절 MemoryRouter 래퍼는 더 이상 필요 없음 (단순 fragment).
// `route` 옵션은 호출부 호환을 위해 유지하되, mock 된 usePathname 이 직접 반영하도록 setup에서 처리.
export function renderWithProviders(
  ui: ReactElement,
  { route: _route = "/", ...rest }: Options = {},
) {
  return render(ui, {
    wrapper: ({ children }) => <>{children}</>,
    ...rest,
  });
}
