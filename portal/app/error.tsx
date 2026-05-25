"use client";

import { useEffect } from "react";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg">
      <div className="text-center">
        <h1 className="text-3xl font-bold text-error mb-4">오류가 발생했습니다</h1>
        <p className="text-gray-600">{error.message || "알 수 없는 오류"}</p>
        <div className="mt-6 flex items-center justify-center gap-3">
          <button
            onClick={reset}
            className="px-4 py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 transition-colors"
          >
            다시 시도
          </button>
          <a href="/" className="text-primary hover:underline">
            홈으로 돌아가기
          </a>
        </div>
      </div>
    </div>
  );
}
