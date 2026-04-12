import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

// 로컬 개발: 백엔드는 docker compose로 띄우고 포털만 `npm run dev`로 실행.
// 아래 경로들은 gateway-service(8080)로 프록시 — nginx.conf의 규칙과 동일.
const GATEWAY = "http://localhost:8080";

export default defineConfig({
  plugins: [tailwindcss(), reactRouter()],
  server: {
    port: 3000,
    proxy: {
      "/api": { target: GATEWAY, changeOrigin: true },
      "/v3": { target: GATEWAY, changeOrigin: true },
      "/webjars": { target: GATEWAY, changeOrigin: true },
      "/swagger-ui": { target: GATEWAY, changeOrigin: true },
      "/actuator": { target: GATEWAY, changeOrigin: true },
    },
  },
});
