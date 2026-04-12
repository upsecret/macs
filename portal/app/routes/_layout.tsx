import { Outlet } from "react-router";
import Sidebar from "../components/Sidebar";
import TopBar from "../components/TopBar";
import ProtectedRoute from "../components/ProtectedRoute";

export default function AppLayout() {
  return (
    <ProtectedRoute>
      <div className="flex flex-row h-screen">
        <Sidebar />
        <div className="flex-1 flex flex-col min-w-0">
          <TopBar />
          <main className="flex-1 bg-bg overflow-auto p-6">
            <Outlet />
          </main>
        </div>
      </div>
    </ProtectedRoute>
  );
}
