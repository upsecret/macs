"use client";

import { useRouter } from "next/navigation";
import { useAuthStore } from "~/stores/authStore";

export default function UnauthorizedPage() {
  const router = useRouter();
  const { employeeNumber, logout } = useAuthStore();

  const handleRelogin = () => {
    logout();
    router.replace("/login");
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-xl shadow-lg p-8 text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-error/10 mb-4">
            <span className="text-3xl">⛔</span>
          </div>

          <h1 className="text-2xl font-bold text-gray-900 mb-2">
            401 — 접근 권한이 없습니다
          </h1>
          <p className="text-sm text-gray-600 mb-6">
            {employeeNumber ? (
              <>
                사번 <span className="font-mono text-gray-800">{employeeNumber}</span> 는
                <br />
                portal 사용 권한이 부여되어 있지 않습니다.
              </>
            ) : (
              "이 페이지를 보려면 로그인이 필요합니다."
            )}
          </p>
          <p className="text-xs text-gray-500 mb-6">
            관리자에게 portal 접근 권한 부여를 요청하세요.
            <br />
            (system=<code>common</code>, connector=<code>portal-route</code>)
          </p>

          <button
            onClick={handleRelogin}
            className="w-full bg-primary text-white py-2.5 rounded-lg font-medium
                       hover:bg-primary/90 active:bg-primary/80 transition-colors"
          >
            다른 계정으로 로그인
          </button>
        </div>
      </div>
    </div>
  );
}
