import { useState, useEffect, useRef } from 'react';
import type { KeyboardEvent, MouseEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { createChatSession, streamChatMessage, getChatHistory, getChatSessions, deleteChatSession } from '../api/chatService';
import { runAgent } from '../api/agentService';
import { useAppContext } from '../contexts/appContextStore';
import { useToast } from '../components/common/toastContext';
import { normalizeSessions, sortSessionsNewestFirst } from '../utils/chatSession';
import type { ChatSession, RawChatSession } from '../utils/chatSession';
import MaintenancePage from './MaintenancePage';
import MaintenanceOverlay from '../components/common/MaintenanceOverlay';
import Modal from '../components/common/Modal';
import ChatHeader from '../components/chat/ChatHeader';
import HistoryPanel from '../components/chat/HistoryPanel';
import ChatMessageBubble from '../components/chat/ChatMessageBubble';
import type { ChatUiMessage, ChatMessage } from '../types/chat';
import './ChatbotPage.css';

// ─── Helpers ────────────────────────────────────────────────────────────────

const GREETING = 'Xin chào! Mình là **Tech Radar AI**, trợ lý tư vấn công nghệ dựa trên dữ liệu thực từ thị trường tuyển dụng IT Việt Nam.\n\nBạn có thể hỏi mình về:\n- Cơ hội việc làm theo tech stack\n- Xu hướng công nghệ & mức lương\n- Lộ trình học và chuyển hướng sự nghiệp';

const ACTIVE_SID_KEY = 'chat_session_id';

// ─── Quick prompts ───────────────────────────────────────────────────────────

const QUICK_PROMPTS = [
    'Tôi muốn tìm việc Data Engineer',
    'FPT tuyển kỹ sư phần mềm không?',
    'Shopee đang tuyển vị trí gì?',
    'Lương DevOps engineer ở Việt Nam bao nhiêu?',
    'Vì sao AI được cho là gây hại cho môi trường?',
];

let msgId = 0;

// ─── Component ───────────────────────────────────────────────────────────────

export default function ChatbotPage() {
    const context = useAppContext();
    const settings = context?.settings;
    const navigate = useNavigate();
    const notify = useToast();

    const [sessionId,    setSessionId]    = useState<string | null>(null);
    const [sessionError, setSessionError] = useState(false);
    const [sessions,     setSessions]     = useState<ChatSession[]>([]);   // history list
    const [messages,     setMessages]     = useState<ChatUiMessage[]>([{ id: msgId++, role: 'bot', text: GREETING }]);
    const [input,        setInput]        = useState('');
    const [isStreaming,  setIsStreaming]  = useState(false);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [showHistory, setShowHistory] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
    const [agentMode, setAgentMode] = useState(false);
    const chatWindowRef = useRef<HTMLDivElement>(null);
    const scrollNewSessionToTopRef = useRef(false);
    const shouldAutoScrollRef = useRef(true);
    // sendMessage's streaming callbacks (flush timer, SSE handlers) run outside any effect and can
    // still fire after the user navigates away mid-stream — guard their setState calls with this.
    const isMountedRef = useRef(true);
    useEffect(() => () => { isMountedRef.current = false; }, []);

    // ── Init: load active session or create new ──────────────────────────────

    useEffect(() => {
        const init = async () => {
            try {
                // 1. Fetch sessions from API
                const sessionList = await getChatSessions();
                const msgs = normalizeSessions(sessionList as RawChatSession[] | { data?: RawChatSession[] } | null | undefined);
                setSessions(msgs);

                // 2. Load active session
                const savedSid = localStorage.getItem(ACTIVE_SID_KEY);
                if (savedSid) {
                    setSessionId(savedSid);
                    await loadHistory(savedSid);
                } else if (msgs.length > 0) {
                    // Nếu không có session active, lấy session mới nhất
                    const latest = msgs[0].id;
                    localStorage.setItem(ACTIVE_SID_KEY, latest);
                    setSessionId(latest);
                    await loadHistory(latest);
                } else {
                    await startNewSession();
                }
            } catch (err) {
                console.error('[ChatbotPage] Init error:', err);
                setSessionError(true);
            }
        };
        init();
    }, []);

    useEffect(() => {
        const chatWindow = chatWindowRef.current;
        if (!chatWindow) return;

        if (scrollNewSessionToTopRef.current) {
            scrollNewSessionToTopRef.current = false;
            chatWindow.scrollTo({ top: 0, behavior: 'auto' });
            return;
        }

        if (!shouldAutoScrollRef.current) return;

        requestAnimationFrame(() => {
            chatWindow.scrollTop = chatWindow.scrollHeight;
        });
    }, [messages]);

    const handleChatScroll = () => {
        const chatWindow = chatWindowRef.current;
        if (!chatWindow) return;
        const distanceFromBottom =
            chatWindow.scrollHeight - chatWindow.scrollTop - chatWindow.clientHeight;
        shouldAutoScrollRef.current = distanceFromBottom < 80;
    };

    // ── Core functions ───────────────────────────────────────────────────────

    const loadHistory = async (sid: string) => {
        setLoadingHistory(true);
        try {
            const history = await getChatHistory(sid);
            const msgs = (Array.isArray(history) ? history : (history as { data?: ChatMessage[] })?.data) as ChatMessage[] | undefined;
            if (Array.isArray(msgs) && msgs.length > 0) {
                setMessages(msgs.map(m => ({
                    id:   msgId++,
                    role: m.role === 'user' ? 'user' : 'bot',
                    text: m.content || m.text || '',
                })));
            } else {
                setMessages([{ id: msgId++, role: 'bot', text: GREETING }]);
            }
        } catch {
            setMessages([{ id: msgId++, role: 'bot', text: GREETING }]);
        } finally {
            setLoadingHistory(false);
        }
    };

    const startNewSession = async (): Promise<string> => {
        const res = await createChatSession();
        const sid = ('session_id' in res ? res.session_id : undefined) || ('data' in res ? res.data?.session_id : undefined);
        if (!sid) throw new Error('No session_id returned');
        localStorage.setItem(ACTIVE_SID_KEY, sid);
        setSessionId(sid);

        // Refresh session list from server
        const updatedList = await getChatSessions();
        setSessions(normalizeSessions(updatedList as RawChatSession[] | { data?: RawChatSession[] } | null | undefined));

        return sid;
    };

    // ── Delete a session ─────────────────────────────────────────────────────

    const handleDeleteSession = (sid: string, e: MouseEvent) => {
        e.stopPropagation();
        if (isStreaming) return;
        setDeleteTarget(sid);
    };

    const confirmDeleteSession = async () => {
        const sid = deleteTarget;
        if (!sid) return;
        const previousSessions = sessions;
        setDeleteTarget(null);
        setSessions(prev => prev.filter(s => s.id !== sid));
        if (sid === sessionId) clearSession();
        try {
            await deleteChatSession(sid);
        } catch (err) {
            console.warn('[chat] deleteChatSession failed', err);
            setSessions(previousSessions);
            notify({ title: 'Xoá cuộc trò chuyện thất bại', body: 'Đã khôi phục lại trong lịch sử.', variant: 'error' });
        }
    };

    // ── Switch to an existing session ────────────────────────────────────────

    const switchSession = async (sid: string) => {
        if (sid === sessionId || isStreaming) return;
        scrollNewSessionToTopRef.current = true;
        localStorage.setItem(ACTIVE_SID_KEY, sid);
        setSessionId(sid);
        await loadHistory(sid);
    };

    // ── Clear & start new conversation (optimistic) ──────────────────────────

    const clearSession = async () => {
        if (isStreaming) return;
        setSessionError(false);
        scrollNewSessionToTopRef.current = true;

        // 1. Reset UI ngay lập tức — không chờ API
        const PLACEHOLDER = '__new__';
        const placeholderEntry: ChatSession = {
            id: PLACEHOLDER,
            session_id: PLACEHOLDER,
            title: 'Cuộc trò chuyện mới',
            created_at: new Date().toISOString(),
        };
        setSessions(prev => {
            const updated = [placeholderEntry, ...prev.filter(s => s.id !== PLACEHOLDER)]
                .sort(sortSessionsNewestFirst);
            return updated;
        });
        setSessionId(PLACEHOLDER);
        setMessages([{ id: msgId++, role: 'bot', text: GREETING }]);

        // 2. Gọi API ngầm → thay placeholder bằng session thực
        try {
            const res = await createChatSession();
            const sid = ('session_id' in res ? res.session_id : undefined) || ('data' in res ? res.data?.session_id : undefined);
            if (!sid) throw new Error('No session_id returned');
            localStorage.setItem(ACTIVE_SID_KEY, sid);
            setSessionId(sid);
            // Thay placeholder trong danh sách
            setSessions(prev => {
                const updated = prev.map(s =>
                    s.id === PLACEHOLDER ? { ...s, id: sid, session_id: sid } : s
                ).sort(sortSessionsNewestFirst);
                return updated;
            });
        } catch (err) {
            console.error('[ChatbotPage] clearSession error:', err);
            setSessionError(true);
            // Xóa placeholder nếu tạo session thất bại
            setSessions(prev => {
                const updated = prev.filter(s => s.id !== PLACEHOLDER);
                return updated;
            });
            setSessionId(null);
        }
    };

    // ── Send message (Agent mode: no streaming, one-shot multi-tool answer) ──

    const sendAgentMessage = async (text: string) => {
        const userMsg: ChatUiMessage = { id: msgId++, role: 'user', text };
        const botMsg: ChatUiMessage  = { id: msgId++, role: 'bot', text: '', streaming: true };
        setMessages(prev => [...prev, userMsg, botMsg]);
        setInput('');
        setIsStreaming(true);
        try {
            const res = await runAgent(text);
            if (!isMountedRef.current) return;
            const answer = res?.data?.answer || 'Không nhận được phản hồi từ Agent.';
            const steps = res?.data?.steps || [];
            setMessages(prev => prev.map(m => m.id === botMsg.id ? { ...m, text: answer, streaming: false, steps } : m));
        } catch (err) {
            console.error('[ChatbotPage] Agent error:', err);
            if (!isMountedRef.current) return;
            setMessages(prev => prev.map(m => m.id === botMsg.id
                ? { ...m, text: 'Không thể xử lý yêu cầu ở chế độ Agent lúc này. Vui lòng thử lại.', streaming: false }
                : m));
        } finally {
            if (isMountedRef.current) setIsStreaming(false);
        }
    };

    // ── Send message ─────────────────────────────────────────────────────────

    const sendMessage = (text: string) => {
        if (!text.trim() || isStreaming) return;

        if (agentMode) {
            sendAgentMessage(text);
            return;
        }

        const userMsg: ChatUiMessage = { id: msgId++, role: 'user', text };
        const botMsg: ChatUiMessage  = { id: msgId++, role: 'bot', text: '', streaming: true };
        setMessages(prev => [...prev, userMsg, botMsg]);
        setInput('');
        setIsStreaming(true);

        // Cập nhật title session theo tin nhắn đầu tiên (Local UI update trước)
        setSessions(prev => {
            const userMsgs = messages.filter(m => m.role === 'user');
            if (userMsgs.length === 0 && sessionId) {
                 return prev.map(s => s.id === sessionId ? { ...s, title: text.slice(0, 40) } : s);
            }
            return prev;
        });

        if (!sessionId) {
            setMessages(prev => prev.map(m =>
                m.id === botMsg.id
                    ? { ...m, text: 'Chưa kết nối được tới server. Vui lòng thử lại sau.', streaming: false }
                    : m
            ));
            setIsStreaming(false);
            return;
        }

        let accumulated = '';
        let pendingText = '';
        let flushTimer: ReturnType<typeof setTimeout> | null = null;

        const updateBotText = (textValue: string, extra: Partial<ChatUiMessage> = {}) => {
            if (!isMountedRef.current) return;
            setMessages(prev => prev.map(m =>
                m.id === botMsg.id ? { ...m, text: textValue, ...extra } : m
            ));
        };

        const flushPendingText = () => {
            if (!pendingText) return;
            accumulated += pendingText;
            pendingText = '';
            updateBotText(accumulated);
        };

        const scheduleFlush = () => {
            if (flushTimer) return;
            flushTimer = setTimeout(() => {
                flushTimer = null;
                flushPendingText();
            }, 45);
        };

        streamChatMessage(
            sessionId, text,
            (chunk) => {
                pendingText += chunk;
                scheduleFlush();
            },
            (meta) => {
                if (flushTimer) {
                    clearTimeout(flushTimer);
                    flushTimer = null;
                }
                flushPendingText();
                const finalText = meta?.answer || accumulated;
                updateBotText(finalText, { streaming: false, meta });
                if (isMountedRef.current) setIsStreaming(false);
            },
            (err) => {
                console.error('[ChatbotPage] Stream error:', err);
                if (flushTimer) {
                    clearTimeout(flushTimer);
                    flushTimer = null;
                }
                flushPendingText();
                const errText = accumulated
                    ? accumulated + '\n\n*Kết nối bị gián đoạn.*'
                    : 'Không nhận được phản hồi từ server. Vui lòng thử lại.';
                updateBotText(errText, { streaming: false });
                if (isMountedRef.current) setIsStreaming(false);
            }
        );
    };

    const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(input); }
    };

    // ── Render ───────────────────────────────────────────────────────────────

    if (!settings) {
        return (
            <div className="chat-page flex-center" style={{ minHeight: '100vh', background: 'var(--bg)' }}>
                <div className="loading-spinner"></div>
                <span style={{ color: 'var(--text-3)', marginLeft: 12 }}>Đang kiểm tra trạng thái...</span>
            </div>
        );
    }

    if (settings.isChatEnabled === false) {
        return (
            <MaintenanceOverlay>
                <MaintenancePage message="Chúng tôi đang bảo trì tính năng AI Chat theo định kỳ. Vui lòng quay lại sau." />
            </MaintenanceOverlay>
        );
    }

    return (
        <div className="chat-page">
            {/* ── MIDDLE: Chat ── */}
            <div className="chat-main">
                {/* Chat header */}
                <ChatHeader
                    showHistory={showHistory}
                    onToggleHistory={() => setShowHistory(!showHistory)}
                    sessionError={sessionError}
                    agentMode={agentMode}
                    onToggleAgent={() => setAgentMode(a => !a)}
                    isStreaming={isStreaming}
                    onNewChat={clearSession}
                />

                {/* ── LEFT: History panel (Now moved here for mobile/desktop flexibility) ── */}
                <div className="chat-content-wrap">
                    <HistoryPanel
                        sessions={sessions}
                        sessionId={sessionId}
                        showHistory={showHistory}
                        isStreaming={isStreaming}
                        onNewChat={clearSession}
                        onDelete={handleDeleteSession}
                        onSwitch={(sid) => {
                            switchSession(sid);
                            if (window.innerWidth <= 1024) setShowHistory(false); // Close on selection on mobile
                        }}
                    />

                    <div className="chat-window" ref={chatWindowRef} onScroll={handleChatScroll}>
                        {loadingHistory && (
                            <div className="history-loading">Đang tải lịch sử…</div>
                        )}
                        {messages.map(msg => <ChatMessageBubble key={msg.id} msg={msg} />)}
                    </div>
                </div>

                {/* Quick prompts */}
                <div className="quick-prompts">
                    {QUICK_PROMPTS.map((p, i) => (
                        <button key={i} className="quick-btn" onClick={() => sendMessage(p)}>{p}</button>
                    ))}
                </div>

                {/* Input */}
                <div className="chat-input-bar">
                    <textarea
                        className="chat-input"
                        placeholder="Hỏi về xu hướng công nghệ, lương, lộ trình học... (Enter để gửi)"
                        value={input}
                        onChange={e => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        rows={2}
                        disabled={isStreaming}
                    />
                    <button
                        className="send-btn"
                        onClick={() => sendMessage(input)}
                        disabled={isStreaming || !input.trim()}
                    >
                        {isStreaming ? <span className="dots-animation"><span>.</span><span>.</span><span>.</span></span> : 'Gửi'}
                    </button>
                </div>
            </div>

            {/* ── RIGHT: Profile panel ── */}
            <div className="chat-sidebar">

                <div className="card" style={{ marginTop: 12 }}>
                    <h3 className="section-title">Công cụ liên quan</h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <button className="btn btn-secondary w-full" style={{ justifyContent: 'flex-start' }}
                            onClick={() => navigate('/graph')}>Graph Explorer</button>
                        <button className="btn btn-secondary w-full" style={{ justifyContent: 'flex-start' }}
                            onClick={() => navigate('/dashboard')}>Trend Dashboard</button>
                        <button className="btn btn-secondary w-full" style={{ justifyContent: 'flex-start' }}
                            onClick={() => navigate('/compare')}>So sánh công nghệ</button>
                    </div>
                </div>
            </div>

            {deleteTarget && (
                <Modal title="Xác nhận xoá" onClose={() => setDeleteTarget(null)} width="380px">
                    <p className="modal-body-text">Xoá cuộc trò chuyện này? Hành động này không thể hoàn tác.</p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setDeleteTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={confirmDeleteSession}>Xoá</button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
