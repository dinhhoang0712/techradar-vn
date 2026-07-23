import { useState, useRef, useCallback, useEffect } from 'react';
import type { Dispatch, SetStateAction } from 'react';

interface UsePollingJobOptions<TStatus> {
    fetchStatus: () => Promise<TStatus>;
    isRunning: (status: TStatus) => boolean;
    onSettled?: (status: TStatus) => void;
    intervalMs: number;
}

interface UsePollingJobResult<TStatus> {
    status: TStatus | null;
    setStatus: Dispatch<SetStateAction<TStatus | null>>;
    load: () => Promise<void>;
    startPolling: () => void;
    stopPolling: () => void;
}

/**
 * Trạng thái 1 job chạy nền có thể bị trigger thủ công (crawler/huấn luyện cụm/data-platform job ở
 * AdminSettings): tải trạng thái ban đầu, tự động poll khi job đang chạy, dừng poll khi job xong.
 * Dùng chung vì 3 job ở AdminSettings có hình dạng y hệt nhau, chỉ khác API và điều kiện "đang chạy".
 *
 * `isRunning(status)` xác định trạng thái hiện tại có đang chạy không (còn poll tiếp hay không).
 * `onSettled(status)` gọi đúng 1 lần khi job chuyển từ running → không còn running (show toast kết quả) —
 * chỉ gọi từ vòng poll, không gọi khi `load()` phát hiện job đã settled sẵn từ trước khi mount.
 */
export function usePollingJob<TStatus>({
    fetchStatus, isRunning, onSettled, intervalMs,
}: UsePollingJobOptions<TStatus>): UsePollingJobResult<TStatus> {
    const [status, setStatus] = useState<TStatus | null>(null);
    const pollRef = useRef<number | null>(null);
    const fetchStatusRef = useRef(fetchStatus);
    const isRunningRef = useRef(isRunning);
    const onSettledRef = useRef(onSettled);

    // Đồng bộ ref sau khi render (không phải trong lúc render) để luôn đọc callback mới nhất từ
    // trong setInterval mà không phải liệt kê fetchStatus/isRunning/onSettled vào deps của useCallback.
    useEffect(() => {
        fetchStatusRef.current = fetchStatus;
        isRunningRef.current = isRunning;
        onSettledRef.current = onSettled;
    });

    const stopPolling = useCallback(() => {
        if (pollRef.current) {
            clearInterval(pollRef.current);
            pollRef.current = null;
        }
    }, []);

    const startPolling = useCallback(() => {
        if (pollRef.current) return;
        pollRef.current = setInterval(async () => {
            try {
                const next = await fetchStatusRef.current();
                setStatus(next);
                if (!isRunningRef.current(next)) {
                    stopPolling();
                    onSettledRef.current?.(next);
                }
            } catch (error) {
                console.error('Failed to poll job status:', error);
            }
        }, intervalMs);
    }, [stopPolling, intervalMs]);

    const load = useCallback(async () => {
        try {
            const next = await fetchStatusRef.current();
            setStatus(next);
            if (isRunningRef.current(next)) startPolling();
        } catch (error) {
            console.error('Failed to load job status:', error);
        }
    }, [startPolling]);

    useEffect(() => stopPolling, [stopPolling]);

    return { status, setStatus, load, startPolling, stopPolling };
}
