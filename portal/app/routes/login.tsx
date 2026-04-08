import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../hooks/useAuth";

export default function Login() {
  const navigate = useNavigate();
  const { isAuthenticated, defaultPath, login } = useAuth();
  const [employeeNumber, setEmployeeNumber] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // 이미 인증된 상태면 기본 페이지로 이동
  useEffect(() => {
    if (isAuthenticated) {
      navigate(defaultPath, { replace: true });
    }
  }, [isAuthenticated, defaultPath, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!employeeNumber.trim()) return;

    setError("");
    setLoading(true);

    try {
      const data = await login("portal", employeeNumber.trim());
      // 로그인 성공 → 그룹별 기본 경로로 이동
      const { group } = data;
      const groupDefaultPaths: Record<string, string> = {
        admin: "/route-config",
        operator: "/route-config",
        developer: "/route-config",
        user: "/connector",
      };
      navigate(groupDefaultPaths[group] ?? "/connector", { replace: true });
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "로그인에 실패했습니다.";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg">
      <div className="w-full max-w-sm">
        {/* 로고 영역 */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-primary rounded-2xl mb-4">
            <span className="text-white text-2xl font-bold">M</span>
          </div>
          <h1 className="text-2xl font-bold text-gray-900">MACS Portal</h1>
          <p className="text-sm text-gray-500 mt-1">
            Micro Application Configuration System
          </p>
        </div>

        {/* 로그인 카드 */}
        <div className="bg-white rounded-xl shadow-lg p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label
                htmlFor="employee-number"
                className="block text-sm font-medium text-gray-700 mb-1.5"
              >
                사번
              </label>
              <input
                id="employee-number"
                type="text"
                value={employeeNumber}
                onChange={(e) => setEmployeeNumber(e.target.value)}
                placeholder="2078432"
                autoFocus
                autoComplete="username"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg
                           focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary
                           placeholder:text-gray-400 transition-shadow"
                required
              />
            </div>

            {/* 에러 메시지 */}
            {error && (
              <div className="flex items-start gap-2 bg-error/5 text-error text-sm px-4 py-3 rounded-lg">
                <span className="shrink-0 mt-0.5">⚠</span>
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading || !employeeNumber.trim()}
              className="w-full bg-primary text-white py-2.5 rounded-lg font-medium
                         hover:bg-primary/90 active:bg-primary/80
                         disabled:opacity-50 disabled:cursor-not-allowed
                         transition-colors"
            >
              {loading ? (
                <span className="inline-flex items-center gap-2">
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                    <circle
                      className="opacity-25"
                      cx="12" cy="12" r="10"
                      stroke="currentColor" strokeWidth="4" fill="none"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                    />
                  </svg>
                  인증 중...
                </span>
              ) : (
                "로그인"
              )}
            </button>
          </form>
        </div>

        {/* 테스트 계정 안내 */}
        <div className="mt-6 bg-white/60 rounded-lg p-4">
          <p className="text-xs font-medium text-gray-500 mb-2 text-center">
            테스트 계정
          </p>
          <div className="grid grid-cols-2 gap-2 text-xs">
            {[
              { id: "2078432", role: "admin (all)", color: "bg-error/10 text-error" },
              { id: "2065162", role: "operator/admin", color: "bg-accent/10 text-amber-700" },
            ].map(({ id, role, color }) => (
              <button
                key={id}
                type="button"
                onClick={() => setEmployeeNumber(id)}
                className="flex items-center justify-between px-3 py-2 rounded-md border border-gray-200
                           hover:border-primary/30 hover:bg-primary/5 transition-colors text-left"
              >
                <span className="font-mono text-gray-700">{id}</span>
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${color}`}>
                  {role}
                </span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
