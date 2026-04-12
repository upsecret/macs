import { Navigate, useLocation } from "react-router";
import { useAuthStore } from "../stores/authStore";
import { ROUTE_MIN_ROLE } from "../utils/permissions";

interface Props {
  children: React.ReactNode;
}

export default function ProtectedRoute({ children }: Props) {
  const { token } = useAuthStore();
  const isAdmin = useAuthStore((s) => s.isAdmin);
  const { pathname } = useLocation();

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const required = ROUTE_MIN_ROLE[pathname];
  if (required === "admin" && !isAdmin()) {
    return <Navigate to="/connector" replace />;
  }

  return <>{children}</>;
}
