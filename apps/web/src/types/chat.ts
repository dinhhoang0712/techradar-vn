// Domain types cho ChatbotPage: phiên chat, lịch sử tin nhắn, và metadata khi 1 lượt streaming kết thúc.
export interface ChatMessage {
    role: 'user' | 'assistant' | string;
    content?: string;
    text?: string;
}

export interface ChatSource {
    title?: string;
    source?: string;
    published_date?: string;
}

export interface ChatDoneMeta {
    answer: string;
    session_id?: string;
    sources?: ChatSource[];
    entities?: string[];
    job_titles?: string[];
    query?: string;
}

export interface CreateSessionResponse {
    session_id?: string;
    created_at?: string;
}

export interface AgentStepLog {
    tool: string;
    input: string;
}

// Tin nhắn hiển thị trong cửa sổ chat (UI state của ChatbotPage) — khác với ChatMessage (lịch sử
// thô từ backend): có thêm cờ streaming và steps/meta gắn sau khi trả lời xong. Bong bóng bot hiện
// 3 chấm nhấp nháy bất cứ khi nào streaming=true mà text vẫn rỗng (chưa có token/answer nào về) —
// đúng cho cả chế độ streaming từng token lẫn Agent mode (trả lời một cục sau khi xong).
export interface ChatUiMessage {
    id: number | string;
    role: 'user' | 'bot';
    text: string;
    streaming?: boolean;
    steps?: AgentStepLog[];
    meta?: ChatDoneMeta;
}
