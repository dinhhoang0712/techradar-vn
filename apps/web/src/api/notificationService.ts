import { apiClient } from '../utils/apiClient';
import type { Notification } from '../types/notification';

const API_BASE_URL = '/api/v1';

interface GetNotificationsOptions {
    limit?: number;
    offset?: number;
}

// ─────────────────────────────────────────────
// GET /notifications — danh sách thông báo (mới nhất trước)
// Returns: array of { id, type, title, body, link, read, created_at }
// Tuỳ chọn { limit, offset } để phân trang (mặc định giữ nguyên hành vi cũ).
// ─────────────────────────────────────────────
export const getNotifications = async ({ limit, offset }: GetNotificationsOptions = {}): Promise<Notification[]> => {
    const params = new URLSearchParams();
    if (limit != null) params.set('limit', String(limit));
    if (offset != null) params.set('offset', String(offset));
    const qs = params.toString();
    const res = await apiClient<{ data?: Notification[] } | Notification[]>(`/notifications${qs ? `?${qs}` : ''}`);
    return (Array.isArray(res) ? res : res?.data) ?? [];
};

// ─────────────────────────────────────────────
// GET /notifications/unread-count — số thông báo chưa đọc, có thể lọc theo type
// (vd. 'ADMIN_JOB_REPEATED_FAILURE' cho tile cảnh báo job lỗi lặp lại trên LiveMetricsPanel).
// ─────────────────────────────────────────────
export const getUnreadCount = async (type?: string): Promise<number> => {
    const res = await apiClient<{ data?: number } | number>(`/notifications/unread-count${type ? `?type=${type}` : ''}`);
    return (typeof res === 'number' ? res : res?.data) ?? 0;
};

// POST /notifications/{id}/read — đánh dấu một thông báo đã đọc
export const markNotificationRead = async (id: string): Promise<unknown> =>
    apiClient(`/notifications/${id}/read`, { method: 'POST' });

// POST /notifications/read-all — đánh dấu tất cả đã đọc
export const markAllNotificationsRead = async (): Promise<unknown> =>
    apiClient('/notifications/read-all', { method: 'POST' });

// ─────────────────────────────────────────────
// GET /notifications/stream — SSE realtime.
// Dùng fetch-based SSE (không phải EventSource) để gắn được header Authorization.
// Trả về AbortController; gọi .abort() khi unmount để đóng stream.
//
//   onNotification(n)  — callback mỗi khi nhận một thông báo mới
//   onError(err)       — callback khi stream lỗi hẳn (bỏ qua AbortError, và các lần
//                         mất kết nối tạm thời tự reconnect được — xem RECONNECT_DELAY_MS)
// ─────────────────────────────────────────────
const RECONNECT_DELAY_MS = 3000;

export const streamNotifications = (
    onNotification: (notification: Notification) => void,
    onError?: (err: Error) => void,
): AbortController => {
    const controller = new AbortController();

    (async () => {
        while (!controller.signal.aborted) {
            try {
                const token = localStorage.getItem('access_token');
                const res = await fetch(`${API_BASE_URL}/notifications/stream`, {
                    headers: {
                        Accept: 'text/event-stream',
                        ...(token ? { Authorization: `Bearer ${token}` } : {}),
                    },
                    signal: controller.signal,
                });
                if (!res.ok || !res.body) {
                    if (res.status === 401) {
                        // Token hết hạn — reconnect với cùng token cũ sẽ luôn thất bại, dừng hẳn.
                        onError?.(new Error('SSE 401'));
                        return;
                    }
                    throw new Error(`SSE ${res.status}`);
                }

                const reader = res.body.getReader();
                const decoder = new TextDecoder('utf-8');
                let buffer = '';

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break; // server đóng kết nối (vd idle timeout) — reconnect bên dưới
                    buffer += decoder.decode(value, { stream: true });

                    const lines = buffer.split('\n');
                    buffer = lines.pop() ?? ''; // giữ lại phần chưa hoàn chỉnh

                    for (const line of lines) {
                        if (!line || line.startsWith(':')) continue; // heartbeat comment
                        if (line.startsWith('event:')) continue;     // event type — xử lý ở data
                        if (line.startsWith('data:')) {
                            const raw = line.slice(5).trimStart();
                            try {
                                onNotification(JSON.parse(raw));
                            } catch {
                                /* dòng data không phải JSON — bỏ qua */
                            }
                        }
                    }
                }
            } catch (err) {
                if (err instanceof Error && err.name === 'AbortError') return;
                onError?.(err as Error);
            }
            if (controller.signal.aborted) return;
            await new Promise((resolve) => setTimeout(resolve, RECONNECT_DELAY_MS));
        }
    })();

    return controller;
};
