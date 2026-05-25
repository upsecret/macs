"use client";

import Sidebar from "~/components/Sidebar";
import TopBar from "~/components/TopBar";
import ProtectedRoute from "~/components/ProtectedRoute";

export default function ProtectedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <ProtectedRoute>
      <div className="flex flex-row h-screen">
        <Sidebar />
        <div className="flex-1 flex flex-col min-w-0">
          <TopBar />
          <main className="flex-1 bg-bg overflow-auto p-6">{children}</main>
        </div>
      </div>
    </ProtectedRoute>
  );
}
