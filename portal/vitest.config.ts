import { defineConfig } from "vitest/config";
import path from "node:path";

// vite.config.ts 와 분리한다. 이유:
// - reactRouter() plugin 은 파일 라우팅 인덱서라 단위 테스트에서 방해
// - tailwind plugin 은 테스트에 불필요
// alias ~/* 만 재선언해서 프로덕션 import path 와 맞춘다.
export default defineConfig({
  resolve: {
    alias: {
      "~": path.resolve(__dirname, "./app"),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./test/setup.ts",
    globals: true,
    css: false,
    include: ["test/**/*.{test,spec}.{ts,tsx}"],
    coverage: {
      provider: "v8",
      include: ["app/**/*.{ts,tsx}"],
      exclude: ["app/**/*.d.ts", "app/routes.ts"],
    },
  },
});
