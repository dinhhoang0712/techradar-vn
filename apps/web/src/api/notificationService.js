import { apiClient } from '../utils/apiClient';

const API_BASE_URL = '/api/v1';

// ─────────────────────────────────────────────
// GET /notifications — danh sách thông báo (mới nhất trước)
// Returns: array of { id, type, title, body, link, read, created_at }
// ─────────────────────────────────────────────
export const getNotifications = async () => {
    const res = await apiClient('/notifications');
    return res?.data ?? res ?? [];
};

// ─────────────────────────────────────────────
// GET /notifications/unread-count — số thông báo chưa đọc
// ─────────────────────────────────────────────
export const getUnreadCount = async () => {
    const res = await apiClient('/notifications/unread-count');
    return res?.data ?? res ?? 0;
};

// POST /notifications/{id}/read — đánh dấu một thông báo đã đọc
export const markNotificationRead = async (id) =>
    apiClient(`/notifications/${id}/read`, { method: 'POST' });

// POST /notifications/read-all — đánh dấu tất cả đã đọc
export const markAllNotificationsRead = async () =>
    apiClient('/notifications/read-all', { method: 'POST' });

// ─────────────────────────────────────────────
// GET /notifications/stream — SSE realtime.
// Dùng fetch-based SSE (không phải EventSource) để gắn được header Authorization.
// Trả về AbortController; gọi .abort() khi unmount để đóng stream.
//
//   onNotification(n)  — callback mỗi khi nhận một thông báo mới
//   onError(err)       — callback khi stream lỗi (bỏ qua AbortError)
// ─────────────────────────────────────────────
export const streamNotifications = (onNotification, onError) => {
    const token = localStorage.getItem('access_token');
    const controller = new AbortController();

    (async () => {
        try {
            const res = await fetch(`${API_BASE_URL}/notifications/stream`, {
                headers: {
                    Accept: 'text/event-stream',
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                signal: controller.signal,
            });
            if (!res.ok || !res.body) throw new Error(`SSE ${res.status}`);

            const reader = res.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });

                const lines = buffer.split('\n');
                buffer = lines.pop(); // giữ lại phần chưa hoàn chỉnh

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
            if (err?.name !== 'AbortError') onError?.(err);
        }
    })();

    return controller;
};
