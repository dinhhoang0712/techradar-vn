import { useState, useEffect, useCallback, useRef } from 'react';
import type { DependencyList } from 'react';

export interface UseAsyncOptions<T> {
    lazy?: boolean;
    initialData?: T | null;
    onError?: (err: unknown) => void;
}

export interface UseAsyncResult<T> {
    data: T | null;
    setData: (value: T | null | ((prev: T | null) => T | null)) => void;
    loading: boolean;
    error: unknown;
    run: () => Promise<T | undefined>;
    reload: () => Promise<T | undefined>;
}

/**
 * Chuẩn hoá pattern `setLoading(true) → try/fetch/setData → catch → finally setLoading(false)`
 * lặp lại ở hầu hết các trang fetch dữ liệu. Tự động bỏ qua response trả về muộn (deps đổi nhanh
 * trước khi request cũ kịp resolve) để tránh race condition ghi đè state bằng dữ liệu cũ.
 *
 * Mặc định tự chạy `fetchFn` mỗi khi `deps` đổi (kể cả lần mount đầu). Truyền `lazy: true` để chỉ
 * expose `run`/`reload` và tự quyết định khi nào gọi (VD: AdminDashboard chỉ tải dữ liệu 1 tab khi
 * người dùng thực sự mở tab đó, rồi giữ cache — không refetch mỗi lần đổi tab).
 */
export function useAsync<T>(
    fetchFn: () => Promise<T>,
    deps: DependencyList,
    { lazy = false, initialData = null, onError }: UseAsyncOptions<T> = {},
): UseAsyncResult<T> {
    const [data, setData] = useState<T | null>(initialData);
    const [loading, setLoading] = useState(!lazy);
    const [error, setError] = useState<unknown>(null);
    const requestIdRef = useRef(0);
    const fetchFnRef = useRef(fetchFn);
    const onErrorRef = useRef(onError);

    // Đồng bộ ref sau khi render (không phải trong lúc render) để `run` luôn gọi fetchFn/onError mới
    // nhất mà không phải liệt kê chúng vào deps của useCallback bên dưới.
    useEffect(() => {
        fetchFnRef.current = fetchFn;
        onErrorRef.current = onError;
    });

    const run = useCallback(async () => {
        const requestId = ++requestIdRef.current;
        setLoading(true);
        setError(null);
        try {
            const result = await fetchFnRef.current();
            if (requestId !== requestIdRef.current) return; // stale — deps đã đổi trong lúc chờ
            setData(result);
            return result;
        } catch (err) {
            if (requestId !== requestIdRef.current) return;
            setError(err);
            onErrorRef.current?.(err);
        } finally {
            if (requestId === requestIdRef.current) setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps);

    useEffect(() => {
        if (!lazy) run();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps);

    return { data, setData, loading, error, run, reload: run };
}
