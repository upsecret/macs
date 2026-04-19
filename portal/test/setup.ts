import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "./msw/server";
import { useAuthStore } from "~/stores/authStore";

// React 19 + @testing-library/react 16 에서 필요.
// 없으면 "The current testing environment is not configured to support act(...)" 경고.
// @ts-expect-error - React internal flag, no public type
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

// api.ts 의 401/403 인터셉터가 window.location.href = "/login" 으로 navigate 하는데
// jsdom 은 실제 navigation 을 지원 안 해 stderr 로 에러를 뱉는다. 테스트에는 영향 없지만
// 로그가 지저분해지므로 setter 를 no-op 으로 대체한다.
Object.defineProperty(window, "location", {
  configurable: true,
  value: new Proxy(window.location, {
    set(target, prop, value) {
      if (prop === "href") {
        // swallow
        return true;
      }
      (target as unknown as Record<string | symbol, unknown>)[prop] = value;
      return true;
    },
  }),
});

// axios 는 브라우저에서 XHR 을 쓰지만 jsdom + msw/node 환경에선 node 어댑터로 fallback.
// msw 2.x + node 18+ 에서 fetch/undici 기반으로 가로채므로 별도 axios adapter 설정 필요 없음.

beforeAll(() => {
  server.listen({ onUnhandledRequest: "error" });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  // zustand persist 로 남은 localStorage 와 store 상태 전부 초기화
  window.localStorage.clear();
  useAuthStore.setState({
    token: null,
    clientApp: null,
    employeeNumber: null,
    permissions: [],
  });
});

afterAll(() => {
  server.close();
});
