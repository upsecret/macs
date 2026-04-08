import { useState, useCallback } from "react";
import api from "../utils/api";

interface UseApiReturn<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  get: (url: string, params?: Record<string, string>) => Promise<T>;
  post: (url: string, body?: unknown) => Promise<T>;
  put: (url: string, body?: unknown) => Promise<T>;
  del: (url: string, params?: Record<string, string>) => Promise<void>;
}

export function useApi<T = unknown>(): UseApiReturn<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const request = useCallback(
    async (method: string, url: string, body?: unknown, params?: Record<string, string>) => {
      setLoading(true);
      setError(null);
      try {
        const res = await api.request<T>({ method, url, data: body, params });
        setData(res.data);
        return res.data;
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "요청에 실패했습니다.";
        setError(msg);
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  return {
    data,
    loading,
    error,
    get: (url, params) => request("GET", url, undefined, params),
    post: (url, body) => request("POST", url, body),
    put: (url, body) => request("PUT", url, body),
    del: (url, params) => request("DELETE", url, undefined, params) as Promise<void>,
  };
}
