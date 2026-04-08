import { Navigate, useLocation } from "react-router";
import { useAuthStore } from "../stores/authStore";
import { canAccess, getDefaultPath } from "../utils/permissions";

interface Props {
  children: React.ReactNode;
}

export default function ProtectedRoute({ children }: Props) {
  const { token, group } = useAuthStore();
  const { pathname } = useLocation();

  // 미인증 → 로그인 페이지
  if (!token) {
    return <Navigate to="/login" replace />;
  }

  // 인증됐지만 해당 메뉴 권한 없음 → 그룹 기본 페이지
  // (루트 "/" 와 index redirect 경로는 허용)
  if (group && pathname !== "/" && !canAccess(group, pathname)) {
    return <Navigate to={getDefaultPath(group)} replace />;
  }

  return <>{children}</>;
}
