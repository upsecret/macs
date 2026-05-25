import "./globals.css";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "MACS Portal",
  icons: { icon: "/favicon.svg" },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body className="bg-bg min-h-screen">{children}</body>
    </html>
  );
}
