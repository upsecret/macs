import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

// Next.js 마이그레이션 이후: 별도 vite.config.ts 없이 vitest 만 vite 기반으로 동작.
// React JSX 변환을 위해 @vitejs/plugin-react 사용.
// alias ~/* 만 재선언해서 프로덕션 import path 와 맞춘다.
export default defineConfig({
  plugins: [react()],
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
      exclude: ["app/**/*.d.ts"],
    },
  },
});
