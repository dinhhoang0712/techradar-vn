import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePollingJob } from './usePollingJob';

interface JobStatus {
    state: string;
}

interface DataPlatformJob {
    job_name: string;
    status: string;
}

describe('usePollingJob', () => {
    beforeEach(() => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('load() sets status but does not poll when not running', async () => {
        const fetchStatus = vi.fn<() => Promise<JobStatus>>().mockResolvedValue({ state: 'idle' });
        const { result } = renderHook(() => usePollingJob({
            fetchStatus,
            isRunning: (s) => s?.state === 'running',
            intervalMs: 1000,
        }));

        await act(async () => { await result.current.load(); });
        expect(result.current.status).toEqual({ state: 'idle' });

        await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
        expect(fetchStatus).toHaveBeenCalledTimes(1); // no polling started
    });

    it('load() starts polling when the job is already running', async () => {
        const fetchStatus = vi.fn<() => Promise<JobStatus>>()
            .mockResolvedValueOnce({ state: 'running' })
            .mockResolvedValueOnce({ state: 'running' })
            .mockResolvedValueOnce({ state: 'idle' });
        const onSettled = vi.fn();
        const { result } = renderHook(() => usePollingJob({
            fetchStatus,
            isRunning: (s) => s?.state === 'running',
            onSettled,
            intervalMs: 1000,
        }));

        await act(async () => { await result.current.load(); });
        expect(fetchStatus).toHaveBeenCalledTimes(1);

        await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
        expect(result.current.status).toEqual({ state: 'running' });
        expect(onSettled).not.toHaveBeenCalled();

        await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
        expect(result.current.status).toEqual({ state: 'idle' });
        expect(onSettled).toHaveBeenCalledWith({ state: 'idle' });

        // Polling stopped after settling — no further fetches even after more time passes.
        await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
        expect(fetchStatus).toHaveBeenCalledTimes(3);
    });

    it('startPolling() is a no-op if a poll is already active', async () => {
        const fetchStatus = vi.fn<() => Promise<JobStatus>>().mockResolvedValue({ state: 'running' });
        const { result } = renderHook(() => usePollingJob({
            fetchStatus,
            isRunning: (s) => s?.state === 'running',
            intervalMs: 1000,
        }));

        act(() => { result.current.startPolling(); });
        act(() => { result.current.startPolling(); }); // second call should not start a duplicate interval

        await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
        expect(fetchStatus).toHaveBeenCalledTimes(1);
    });

    it('stopPolling() halts further fetches', async () => {
        const fetchStatus = vi.fn<() => Promise<JobStatus>>().mockResolvedValue({ state: 'running' });
        const { result } = renderHook(() => usePollingJob({
            fetchStatus,
            isRunning: (s) => s?.state === 'running',
            intervalMs: 1000,
        }));

        act(() => { result.current.startPolling(); });
        await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
        expect(fetchStatus).toHaveBeenCalledTimes(1);

        act(() => { result.current.stopPolling(); });
        await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
        expect(fetchStatus).toHaveBeenCalledTimes(1);
    });

    it('stops polling on unmount', async () => {
        const fetchStatus = vi.fn<() => Promise<JobStatus>>().mockResolvedValue({ state: 'running' });
        const { result, unmount } = renderHook(() => usePollingJob({
            fetchStatus,
            isRunning: (s) => s?.state === 'running',
            intervalMs: 1000,
        }));

        act(() => { result.current.startPolling(); });
        unmount();

        await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
        expect(fetchStatus).toHaveBeenCalledTimes(0);
    });

    it('works with an array-shaped status (data-platform jobs list)', async () => {
        const fetchStatus = vi.fn<() => Promise<DataPlatformJob[]>>()
            .mockResolvedValueOnce([{ job_name: 'a', status: 'running' }])
            .mockResolvedValueOnce([{ job_name: 'a', status: 'success' }]);
        const { result } = renderHook(() => usePollingJob({
            fetchStatus,
            isRunning: (jobs) => jobs.some((j) => j.status === 'running'),
            intervalMs: 1000,
        }));

        await act(async () => { await result.current.load(); });
        await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
        expect(result.current.status).toEqual([{ job_name: 'a', status: 'success' }]);
    });
});
