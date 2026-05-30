import { apiClient } from '../utils/apiClient';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

const API_BASE_URL = 'https://datamining.ankkun.space/api/v1';

const parseSseData = (line) => {
    const data = line.slice(5);
    return data.startsWith(' ') ? data.slice(1) : data;
};

const normalizeChatPayload = (payload, sessionId, query) => {
    const body = payload?.data || payload || {};
    const answer =
        typeof body === 'string'
            ? body
            : body?.answer || body?.message || body?.content || '';
    return {
        ...body,
        answer,
        query: body?.query || query,
        session_id: body?.session_id || sessionId,
    };
};

// ─────────────────────────────────────────────
// GET /chat — Health check của RAG service
// ─────────────────────────────────────────────
export const checkChatHealth = async () => {
    return await apiClient('/chat');
};

// ─────────────────────────────────────────────
// POST /chat/session — Tạo session chat mới
// Returns: { session_id: string, created_at: string }
// ─────────────────────────────────────────────
export const createChatSession = async () => {
    return await apiClient('/chat/session', {
        method: 'POST',
    });
};

// ─────────────────────────────────────────────
// GET /chat/session/{session_id}/messages — Lấy lịch sử message
// ─────────────────────────────────────────────
export const getChatHistory = async (sessionId) => {
    return await apiClient(`/chat/session/${sessionId}/messages`);
};

// ─────────────────────────────────────────────
// GET /chat/sessions — Lấy danh sách session chat của user hiện tại
// ─────────────────────────────────────────────
export const getChatSessions = async () => {
    return await apiClient('/chat/sessions');
};

// ─────────────────────────────────────────────
// POST /chat/session/{session_id}/messages — Gửi message (non-stream)
// Body:    { query: string }
// Returns: { answer, entities, job_titles, query, session_id, sources }
// ─────────────────────────────────────────────
export const sendChatMessage = async (sessionId, query) => {
    return await apiClient(`/chat/session/${sessionId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ query }),
    });
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
export const streamChatMessage = async (sessionId, query, onToken, onDone, onError) => {
    try {
        if (Platform.OS !== 'web') {
            const response = await sendChatMessage(sessionId, query);
            const payload = normalizeChatPayload(response, sessionId, query);

            if (!payload.answer) {
                throw new Error('Không nhận được phản hồi từ máy chủ.');
            }

            onDone(payload);
            return;
        }

        const token = await AsyncStorage.getItem('access_token');

        const headers = {
            'Content-Type': 'application/json',
        };
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const response = await fetch(
            `${API_BASE_URL}/chat/session/${sessionId}/messages/stream`,
            {
                method: 'POST',
                headers,
                body: JSON.stringify({ query }),
            }
        );

        if (!response.ok) {
            throw new Error(`Stream error: ${response.status}`);
        }

        if (!response.body?.getReader) {
            const payload = await response.json();
            onDone({
                ...payload,
                answer: payload?.answer || payload?.message || payload?.content || '',
                query,
                session_id: payload?.session_id || sessionId,
            });
            return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            const lines = buffer.split('\n');
            buffer = lines.pop(); // Phần chưa hoàn chỉnh giữ lại

            for (const line of lines) {
                if (!line.trim() || line.startsWith('event:')) continue;

                if (line.startsWith('data:')) {
                    const rawData = parseSseData(line);

                    try {
                        const parsed = JSON.parse(rawData);
                        if (parsed && typeof parsed === 'object' && parsed.answer !== undefined) {
                            onDone(parsed);
                        }
                    } catch {
                        // Không phải JSON → text token
                        onToken(rawData);
                    }
                }
            }
        }
    } catch (error) {
        console.error('[chatService Mobile] streamChatMessage error:', error);
        if (onError) onError(error);
    }
};
