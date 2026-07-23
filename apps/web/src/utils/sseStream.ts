// Utility dùng chung cho các stream SSE cần gắn header Authorization.
// EventSource gốc không gắn được header nên phải tự dựng bằng fetch + ReadableStream,
// kèm buffer/line parsing và tự động reconnect khi mất kết nối tạm thời.
// Dùng chung bởi messagingService (streamConversations), notificationService
// (streamNotifications) và socialService (streamFeed) — trước đây mỗi service tự
// copy-paste y hệt đoạn logic này.
const API_BASE_URL = '/api/v1';
const RECONNECT_DELAY_MS = 3000;

/**
 * Mở một kết nối SSE tới `${API_BASE_URL}${endpoint}` bằng fetch (không dùng EventSource)
 * để có thể gắn header Authorization — cần cho mọi endpoint stream yêu cầu access token.
 *
 *   endpoint   — path tương đối (đã gồm query string nếu cần), vd '/notifications/stream'
 *   onData(d)  — callback mỗi khi nhận được một dòng `data:` đã parse JSON
 *   onError(e) — callback khi stream lỗi hẳn (bỏ qua AbortError; mất kết nối tạm thời
 *                tự reconnect sau RECONNECT_DELAY_MS; 401 dừng hẳn vì access token đã hết hạn)
 *
 * Trả về AbortController; gọi .abort() khi unmount (hoặc khi tham số stream đổi) để đóng stream.
 */
export const openSseStream = (
    endpoint: string,
    onData: (data: unknown) => void,
    onError?: (err: Error) => void,
): AbortController => {
    const controller = new AbortController();

    (async () => {
        while (!controller.signal.aborted) {
            try {
                const token = localStorage.getItem('access_token');
                const res = await fetch(`${API_BASE_URL}${endpoint}`, {
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
                                onData(JSON.parse(raw));
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
