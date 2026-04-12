import { useNavigate } from "react-router";
import { LogOut } from "lucide-react";
import { useAuthStore } from "../stores/authStore";

export default function TopBar() {
  const navigate = useNavigate();
  const { appName, employeeNumber, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <header className="h-14 bg-white flex items-center justify-end px-6 shrink-0 shadow-sm border-b border-gray-200">
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-full bg-primary/10 flex items-center justify-center">
            <span className="text-xs text-primary font-medium">
              {employeeNumber?.slice(-2)}
            </span>
          </div>
          <div className="leading-tight">
            <p className="text-sm text-gray-800">{employeeNumber}</p>
            <p className="text-[11px] text-gray-500">{appName}</p>
          </div>
        </div>

        <div className="w-px h-6 bg-gray-200" />

        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 text-xs text-gray-500 hover:text-error transition-colors"
        >
          <LogOut size={14} strokeWidth={1.75} />
          로그아웃
        </button>
      </div>
    </header>
  );
}
