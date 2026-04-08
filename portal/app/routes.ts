import { type RouteConfig, route, layout, index } from "@react-router/dev/routes";

export default [
  route("login", "routes/login.tsx"),
  layout("routes/_layout.tsx", [
    index("routes/_index.tsx"),
    route("route-config", "routes/route-config.tsx"),
    route("auth-manage", "routes/auth-manage.tsx"),
    route("connector", "routes/connector.tsx"),
    route("settings", "routes/settings.tsx"),
  ]),
] satisfies RouteConfig;
