"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuthStore } from "../stores/authStore";
import { ROUTE_MIN_ROLE, PORTAL_CONNECTOR } from "../utils/permissions";

interface Props {
  children: React.ReactNode;
}

export default function ProtectedRoute({ children }: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const { token } = useAuthStore();
  const isAdmin = useAuthStore((s) => s.isAdmin);
  const isAllowedConnector = useAuthStore((s) => s.isAllowedConnector);

  const needsLogin = !token;
  // portal 접근 자체에 connector=portal-route 권한 필요. 로그인은 됐는데 권한이 없으면 401.
  const needsPortalAccess = !needsLogin && !isAllowedConnector(PORTAL_CONNECTOR);
  const required = ROUTE_MIN_ROLE[pathname];
  const needsAdmin =
    !needsLogin && !needsPortalAccess && required === "admin" && !isAdmin();

  useEffect(() => {
    if (needsLogin) {
      router.replace("/login");
    } else if (needsPortalAccess) {
      router.replace("/unauthorized");
    } else if (needsAdmin) {
      router.replace("/connector");
    }
  }, [needsLogin, needsPortalAccess, needsAdmin, router]);

  if (needsLogin || needsPortalAccess || needsAdmin) {
    return null;
  }
  return <>{children}</>;
}
