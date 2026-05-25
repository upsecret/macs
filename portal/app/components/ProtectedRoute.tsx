"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuthStore } from "../stores/authStore";
import { ROUTE_MIN_ROLE } from "../utils/permissions";

interface Props {
  children: React.ReactNode;
}

export default function ProtectedRoute({ children }: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const { token } = useAuthStore();
  const isAdmin = useAuthStore((s) => s.isAdmin);

  const needsLogin = !token;
  const required = ROUTE_MIN_ROLE[pathname];
  const needsAdmin = !needsLogin && required === "admin" && !isAdmin();

  useEffect(() => {
    if (needsLogin) {
      router.replace("/login");
    } else if (needsAdmin) {
      router.replace("/connector");
    }
  }, [needsLogin, needsAdmin, router]);

  if (needsLogin || needsAdmin) {
    return null;
  }
  return <>{children}</>;
}
