// Helpers thuần cho danh sách phiên chat — tách khỏi ChatbotPage.jsx để HistoryPanel dùng chung
// formatTime mà không phải import cả trang chat.
export interface RawChatSession {
    session_id?: string;
    id?: string;
    title?: string;
    created_at?: string;
    createdAt?: string;
    [key: string]: unknown;
}

export interface ChatSession {
    id: string;
    session_id: string;
    title: string;
    created_at: string;
    [key: string]: unknown;
}

export function normalizeSession(session: RawChatSession | null | undefined): ChatSession | null {
    const id = session?.session_id || session?.id;
    if (!id) return null;
    return {
        ...session,
        id,
        session_id: id,
        title: session?.title || 'Cuộc trò chuyện mới',
        created_at: session?.created_at || session?.createdAt || new Date().toISOString(),
    };
}

export function sortSessionsNewestFirst(a: { created_at?: string }, b: { created_at?: string }): number {
    return new Date(b.created_at || 0).getTime() - new Date(a.created_at || 0).getTime();
}

export function normalizeSessions(payload: RawChatSession[] | { data?: RawChatSession[] } | null | undefined): ChatSession[] {
    const raw: RawChatSession[] = Array.isArray(payload) ? payload : payload?.data || [];
    return raw
        .map(normalizeSession)
        .filter((s): s is ChatSession => s !== null)
        .sort(sortSessionsNewestFirst);
}

export function formatTime(isoStr?: string | null): string {
    if (!isoStr) return '';
    const d = new Date(isoStr);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'vừa xong';
    if (diffMin < 60) return `${diffMin} phút trước`;
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 24) return `${diffH} giờ trước`;
    return d.toLocaleDateString('vi-VN');
}
