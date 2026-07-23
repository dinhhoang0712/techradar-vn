import { apiClient } from '../utils/apiClient';
import type { ChatDoneMeta, CreateSessionResponse } from '../types/chat';

const API_BASE_URL = '/api/v1';

const parseSseData = (line: string): string => {
    const data = line.slice(5);
    return data.startsWith(' ') ? data.slice(1) : data;
};

// ─────────────────────────────────────────────
// POST /chat/session — Tạo session chat mới
// Returns: { session_id: string, created_at: string }
// ─────────────────────────────────────────────
export const createChatSession = async (): Promise<CreateSessionResponse | { data?: CreateSessionResponse }> => {
    return await apiClient('/chat/session', {
        method: 'POST',
    });
};

// ─────────────────────────────────────────────
// GET /chat/session/{session_id}/messages — Lấy lịch sử message
// Returns: array of message objects
// ─────────────────────────────────────────────
export const getChatHistory = async (sessionId: string): Promise<unknown> => {
    return await apiClient(`/chat/session/${sessionId}/messages`);
};

// ─────────────────────────────────────────────
// GET /chat/sessions — Lấy danh sách session chat của user hiện tại
// Returns: array of session objects
// ─────────────────────────────────────────────
export const getChatSessions = async (): Promise<unknown> => {
    return await apiClient('/chat/sessions');
};

// ─────────────────────────────────────────────
// DELETE /chat/session/{session_id} — Xoá session (và message) của user
// ─────────────────────────────────────────────
export const deleteChatSession = async (sessionId: string): Promise<unknown> => {
    return await apiClient(`/chat/session/${sessionId}`, { method: 'DELETE' });
};

// ─────────────────────────────────────────────
// POST /chat/session/{session_id}/messages/stream — Gửi message + SSE stream
// SSE Events:
//   token → data = text chunk (string)
//   done  → data = JSON { answer, session_id, sources, entities, job_titles, query }
//
// Params:
//   sessionId  — UUID của phiên chat
//   query      — Câu hỏi của người dùng
//   onToken    — callback(chunk: string) mỗi khi nhận được token mới
//   onDone     — callback(meta: object) khi stream kết thúc
//   onError    — callback(error: Error) khi có lỗi
// ─────────────────────────────────────────────
export const streamChatMessage = async (
    sessionId: string,
    query: string,
    onToken: (chunk: string) => void,
    onDone: (meta: ChatDoneMeta) => void,
    onError?: (error: Error) => void,
): Promise<void> => {
    const token = localStorage.getItem('access_token');

    const headers: Record<string, string> = {
        'Content-Type': 'application/json',
    };
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    try {
        const response = await fetch(
            `${API_BASE_URL}/chat/session/${sessionId}/messages/stream`,
            {
                method: 'POST',
                headers,
                body: JSON.stringify({ query }),
            }
        );

        if (!response.ok || !response.body) {
            throw new Error(`Stream error: ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let currentEvent = 'message'; // SSE default event type, per spec

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            // Xử lý từng SSE event trong buffer
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? ''; // Phần chưa hoàn chỉnh giữ lại

            for (const line of lines) {
                if (!line.trim()) {
                    currentEvent = 'message'; // dòng trống kết thúc 1 event, reset về default
                    continue;
                }

                if (line.startsWith('event:')) {
                    currentEvent = line.slice(6).trim();
                    continue;
                }

                if (line.startsWith('data:')) {
                    const rawData = parseSseData(line);

                    if (currentEvent === 'error') {
                        // Backend báo lỗi LLM (vd hết quota) rồi đóng stream — phải báo cho
                        // người dùng thay vì để bong bóng chat kẹt "đang gõ" vĩnh viễn.
                        let message = rawData;
                        try {
                            message = JSON.parse(rawData).detail || rawData;
                        } catch {
                            // rawData không phải JSON — dùng nguyên văn
                        }
                        onError?.(new Error(message));
                        return;
                    }

                    // Thử parse JSON (event done)
                    try {
                        const parsed = JSON.parse(rawData);
                        // Nếu parse thành công và có field answer → đây là event "done"
                        if (parsed && typeof parsed === 'object' && parsed.answer !== undefined) {
                            onDone(parsed);
                        } else {
                            // JSON object khác (hiếm) — bỏ qua
                        }
                    } catch {
                        // Không phải JSON → đây là text token (event "token")
                        onToken(rawData);
                    }
                }
            }
        }
    } catch (error) {
        console.error('[chatService] streamChatMessage error:', error);
        if (onError) onError(error as Error);
    }
};
