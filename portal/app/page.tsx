"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function IndexPage() {
  const router = useRouter();
  useEffect(() => {
    // 인증/롤 분기는 /route-config 의 ProtectedRoute 가 처리.
    router.replace("/route-config");
  }, [router]);
  return null;
}
