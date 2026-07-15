import { apiClient } from '../utils/apiClient';

const API_BASE_URL = '/api/v1';

/**
 * Danh sách cuộc trò chuyện của người dùng hiện tại, mới hoạt động nhất trước.
 * Endpoint: GET /conversations
 */
export const getConversations = async () => {
    return await apiClient('/conversations', { method: 'GET' });
};

/**
 * Lấy (hoặc tạo mới) cuộc trò chuyện 1-1 với một người dùng khác.
 * Endpoint: POST /conversations/with/{userId}
 */
export const getOrCreateConversation = async (userId) => {
    return await apiClient(`/conversations/with/${encodeURIComponent(userId)}`, { method: 'POST' });
};

/**
 * Lịch sử tin nhắn của một cuộc trò chuyện, cũ nhất trước.
 * Endpoint: GET /conversations/{id}/messages
 */
export const getMessages = async (conversationId, page = 0, size = 30) => {
    return await apiClient(`/conversations/${encodeURIComponent(conversationId)}/messages?page=${page}&size=${size}`, {
        method: 'GET',
    });
};

/**
 * Gửi tin nhắn vào một cuộc trò chuyện.
 * Endpoint: POST /conversations/{id}/messages
 */
export const sendMessage = async (conversationId, content) => {
    return await apiClient(`/conversations/${encodeURIComponent(conversationId)}/messages`, {
        method: 'POST',
        body: JSON.stringify({ content }),
    });
};

/**
 * Đánh dấu đã đọc toàn bộ tin nhắn của người kia trong cuộc trò chuyện.
 * Endpoint: POST /conversations/{id}/read
 */
export const markConversationRead = async (conversationId) => {
    return await apiClient(`/conversations/${encodeURIComponent(conversationId)}/read`, { method: 'POST' });
};

/**
 * Stream SSE tin nhắn đến trong thời gian thực, gộp mọi cuộc trò chuyện của người dùng.
 * EventSource gốc không gắn được header Authorization nên phải dùng fetch + ReadableStream
 * (giống hệt cách apps/web/src/api/notificationService.js#streamNotifications xử lý).
 *
 *   onMessage(m)  — callback mỗi khi có DirectMessageResponse mới
 *   onError(err)  — callback khi stream lỗi (bỏ qua AbortError)
 * Trả về AbortController; gọi .abort() khi unmount để đóng stream.
 */
export const streamConversations = (onMessage, onError) => {
    const token = localStorage.getItem('access_token');
    const controller = new AbortController();

    (async () => {
        try {
            const res = await fetch(`${API_BASE_URL}/conversations/stream`, {
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
                            onMessage(JSON.parse(raw));
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
