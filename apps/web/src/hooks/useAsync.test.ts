import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useAsync } from './useAsync';

describe('useAsync', () => {
    it('runs on mount and exposes the resolved data', async () => {
        const fetchFn = vi.fn().mockResolvedValue({ value: 42 });
        const { result } = renderHook(() => useAsync(fetchFn, []));

        expect(result.current.loading).toBe(true);
        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.data).toEqual({ value: 42 });
        expect(result.current.error).toBe(null);
        expect(fetchFn).toHaveBeenCalledTimes(1);
    });

    it('captures a rejected fetch as `error` and calls onError', async () => {
        const err = new Error('boom');
        const fetchFn = vi.fn().mockRejectedValue(err);
        const onError = vi.fn();
        const { result } = renderHook(() => useAsync(fetchFn, [], { onError }));

        await waitFor(() => expect(result.current.loading).toBe(false));
        expect(result.current.error).toBe(err);
        expect(onError).toHaveBeenCalledWith(err);
    });

    it('re-runs when deps change', async () => {
        const fetchFn = vi.fn().mockResolvedValue('ok');
        const { rerender } = renderHook(({ id }) => useAsync(fetchFn, [id]), { initialProps: { id: 1 } });
        await waitFor(() => expect(fetchFn).toHaveBeenCalledTimes(1));

        rerender({ id: 2 });
        await waitFor(() => expect(fetchFn).toHaveBeenCalledTimes(2));
    });

    it('ignores a stale response when deps change before the first request resolves', async () => {
        let resolveFirst: (value: string) => void;
        const fetchFn = vi.fn()
            .mockImplementationOnce(() => new Promise<string>((resolve) => { resolveFirst = resolve; }))
            .mockResolvedValueOnce('second');

        const { result, rerender } = renderHook(({ id }) => useAsync(fetchFn, [id]), { initialProps: { id: 1 } });
        rerender({ id: 2 });
        await waitFor(() => expect(result.current.data).toBe('second'));

        act(() => resolveFirst('first'));
        await new Promise((r) => setTimeout(r, 0));
        expect(result.current.data).toBe('second'); // stale "first" response never overwrote it
    });

    it('does not auto-run when lazy, but `run` fetches and returns the result', async () => {
        const fetchFn = vi.fn<() => Promise<string>>().mockResolvedValue('lazy-data');
        const { result } = renderHook(() => useAsync(fetchFn, [], { lazy: true }));

        expect(result.current.loading).toBe(false);
        expect(fetchFn).not.toHaveBeenCalled();

        let returned: string | undefined;
        await act(async () => {
            returned = await result.current.run();
        });
        expect(returned).toBe('lazy-data');
        expect(result.current.data).toBe('lazy-data');
    });

    it('setData allows optimistic/manual updates outside of fetches', async () => {
        const fetchFn = vi.fn().mockResolvedValue([1, 2]);
        const { result } = renderHook(() => useAsync(fetchFn, []));
        await waitFor(() => expect(result.current.loading).toBe(false));

        act(() => result.current.setData([1, 2, 3]));
        expect(result.current.data).toEqual([1, 2, 3]);
    });
});
