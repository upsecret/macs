import { Outlet } from "react-router";
import Navbar from "../components/Navbar";
import ProtectedRoute from "../components/ProtectedRoute";

export default function AppLayout() {
  return (
    <ProtectedRoute>
      <div className="flex flex-col h-screen">
        <Navbar />
        <main className="flex-1 bg-bg overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
