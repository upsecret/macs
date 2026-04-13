import { http, HttpResponse } from "msw";

// 테스트 기본 응답. 특정 테스트에서 `server.use(...)` 로 덮어쓸 수 있다.
// 새 권한 모델: JWT 엔 employee_number 만, 권한은 별도 fetch.

const SAMPLE_TOKEN =
  "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIyMDc4NDMyIiwiZW1wbG95ZWVfbnVtYmVyIjoiMjA3ODQzMiIsImlhdCI6MSwiZXhwIjo5OTk5OTk5OTk5fQ.sig";

export const handlers = [
  // ── Auth ────────────────────────────────────────────────
  http.post("/api/auth/token", async ({ request }) => {
    const body = (await request.json()) as { employee_number?: string };
    return HttpResponse.json({
      token: SAMPLE_TOKEN,
      employee_number: body.employee_number ?? "2078432",
    });
  }),

  http.post("/api/auth/validate", () =>
    HttpResponse.json({ valid: true, allowed: true, employee_number: "2078432" }),
  ),

  // ── Admin permissions ───────────────────────────────────
  http.get("/api/admin/permissions/users/:app/:emp", ({ params }) =>
    HttpResponse.json({
      appName: params.app,
      employeeNumber: params.emp,
      permissions: [{ system: "common", connector: "portal", role: "admin" }],
    }),
  ),

  http.get("/api/admin/permissions", () => HttpResponse.json([])),

  http.post("/api/admin/permissions", async ({ request }) => {
    const body = (await request.json()) as {
      appName: string;
      employeeNumber: string;
      system: string;
      connector: string;
      role: string;
    };
    return HttpResponse.json(
      { ...body, createdAt: new Date().toISOString() },
      { status: 201 },
    );
  }),

  http.delete("/api/admin/permissions", () => new HttpResponse(null, { status: 204 })),

  // ── Connector ───────────────────────────────────────────
  http.get("/api/admin/connectors", () => HttpResponse.json([])),
  http.get("/api/admin/connectors/available-routes", () => HttpResponse.json([])),

  // ── Config (routes + properties) ────────────────────────
  http.get("/api/config/routes", () => HttpResponse.json([])),
  http.get("/api/config/properties", () => HttpResponse.json([])),
  http.post("/api/config/properties/refresh", () =>
    HttpResponse.json({ status: "refresh event published" }),
  ),
];
