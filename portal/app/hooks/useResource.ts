import { useCallback, useEffect, useRef, useState, type DependencyList } from "react";

export interface UseResourceReturn<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

export interface UseResourceOptions<T> {
  /** mount 시 자동 fetch 여부. false 면 refetch() 를 수동 호출해야 한다. 기본 true. */
  enabled?: boolean;
  /** 첫 render 시 data 초기값. */
  initialData?: T | null;
}

/**
 * 서버 리소스를 {data, loading, error, refetch} 로 추상화하는 훅.
 *
 * TanStack Query 의 축소판 계약과 의도적으로 유사하게 설계:
 * - 호출자는 fetcher 로 어떤 Promise 도 전달 가능 (axios, fetch, ...)
 * - stale-while-error: 실패해도 이전 data 는 유지
 * - enabled: false 로 on-demand 검색 가능
 * - refetch 반환은 Promise<void> 로 체인 가능
 *
 * 나중에 TanStack Query 로 교체하려면 `loading` → `isLoading` 이름 교체만 하면 됨.
 */
export function useResource<T>(
  fetcher: () => Promise<T>,
  deps: DependencyList,
  options: UseResourceOptions<T> = {},
): UseResourceReturn<T> {
  const { enabled = true, initialData = null } = options;

  const [data, setData] = useState<T | null>(initialData);
  const [loading, setLoading] = useState<boolean>(enabled);
  const [error, setError] = useState<string | null>(null);

  // fetcher 는 호출 시점에 클로저를 캡처. useEffect deps 에는 넣지 않고
  // 호출자가 명시적으로 deps 배열을 넘기게 해서 무한 루프를 막는다.
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  // 언마운트 후 setState 방지
  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const refetch = useCallback(async (): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetcherRef.current();
      if (mountedRef.current) {
        setData(result);
      }
    } catch (err: unknown) {
      if (mountedRef.current) {
        setError(err instanceof Error ? err.message : "요청에 실패했습니다.");
        // stale-while-error: data 는 이전 값 유지
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    void refetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, loading, error, refetch };
}
