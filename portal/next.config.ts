import type { NextConfig } from "next";

// 백엔드는 docker compose의 gateway-service:8080 (컨테이너 내부) 또는 localhost:8080 (로컬 dev) 으로 프록시.
// 이전 nginx.conf 의 location 규칙과 동등.
const GATEWAY = process.env.GATEWAY_URL ?? "http://gateway-service:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${GATEWAY}/api/:path*` },
      { source: "/v3/:path*", destination: `${GATEWAY}/v3/:path*` },
      { source: "/webjars/:path*", destination: `${GATEWAY}/webjars/:path*` },
      { source: "/swagger-ui/:path*", destination: `${GATEWAY}/swagger-ui/:path*` },
      { source: "/swagger-ui.html", destination: `${GATEWAY}/swagger-ui.html` },
      { source: "/actuator/:path*", destination: `${GATEWAY}/actuator/:path*` },
    ];
  },
};

export default nextConfig;
