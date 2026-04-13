import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useResource } from "~/hooks/useResource";

describe("hooks/useResource", () => {
  it("fetches on mount, exposes data + clears loading", async () => {
    const fetcher = vi.fn().mockResolvedValue([1, 2, 3]);

    const { result } = renderHook(() => useResource(fetcher, []));

    // 초기엔 loading=true
    expect(result.current.loading).toBe(true);

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(result.current.data).toEqual([1, 2, 3]);
    expect(result.current.error).toBeNull();
  });

  it("stores error and keeps previous data (stale-while-error)", async () => {
    let count = 0;
    const fetcher = vi.fn(async () => {
      count += 1;
      if (count === 1) return "first";
      throw new Error("boom");
    });

    const { result } = renderHook(() => useResource<string>(fetcher, []));

    await waitFor(() => expect(result.current.data).toBe("first"));

    await act(async () => {
      await result.current.refetch();
    });

    await waitFor(() => {
      expect(result.current.error).toBe("boom");
      expect(result.current.loading).toBe(false);
    });
    // data 는 유지 (stale-while-error)
    expect(result.current.data).toBe("first");
  });

  it("enabled=false: skips mount fetch but refetch() still works", async () => {
    const fetcher = vi.fn().mockResolvedValue("manual");

    const { result } = renderHook(() =>
      useResource(fetcher, [], { enabled: false }),
    );

    // mount 시 호출 안 됨 + loading false 로 정착
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(fetcher).not.toHaveBeenCalled();
    expect(result.current.data).toBeNull();

    await act(async () => {
      await result.current.refetch();
    });

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(result.current.data).toBe("manual");
  });

  it("deps change triggers refetch", async () => {
    const fetcher = vi.fn().mockResolvedValue("x");

    const { result, rerender } = renderHook(
      ({ key }: { key: string }) => useResource(fetcher, [key]),
      { initialProps: { key: "a" } },
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(fetcher).toHaveBeenCalledTimes(1);

    rerender({ key: "b" });
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));
  });

  it("initialData is reflected on first render", () => {
    const fetcher = vi.fn().mockResolvedValue([999]);

    const { result } = renderHook(() =>
      useResource<number[]>(fetcher, [], { initialData: [1, 2] }),
    );

    expect(result.current.data).toEqual([1, 2]);
  });

  it("refetch: loading transitions true → false", async () => {
    let resolve: ((v: string) => void) | undefined;
    const fetcher = vi.fn(
      () =>
        new Promise<string>((r) => {
          resolve = r;
        }),
    );

    const { result } = renderHook(() =>
      useResource(fetcher, [], { enabled: false }),
    );

    act(() => {
      void result.current.refetch();
    });

    await waitFor(() => expect(result.current.loading).toBe(true));

    await act(async () => {
      resolve?.("done");
    });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toBe("done");
  });
});
