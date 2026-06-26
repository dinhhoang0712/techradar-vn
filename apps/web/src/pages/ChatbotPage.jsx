import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { createChatSession, streamChatMessage, getChatHistory, getChatSessions, deleteChatSession } from '../api/chatService';
import { useAppContext } from '../contexts/AppContext';
import MaintenancePage from './MaintenancePage';
import './ChatbotPage.css';

// ─── Helpers ────────────────────────────────────────────────────────────────

const GREETING = 'Xin chào! Mình là **Tech Radar AI**, trợ lý tư vấn công nghệ dựa trên dữ liệu thực từ thị trường tuyển dụng IT Việt Nam.\n\nBạn có thể hỏi mình về:\n- Cơ hội việc làm theo tech stack\n- Xu hướng công nghệ & mức lương\n- Lộ trình học và chuyển hướng sự nghiệp';

const ACTIVE_SID_KEY = 'chat_session_id';

function normalizeSession(session) {
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

function normalizeSessions(payload) {
    const raw = Array.isArray(payload) ? payload : payload?.data || [];
    return raw.map(normalizeSession).filter(Boolean).sort(sortSessionsNewestFirst);
}

function sortSessionsNewestFirst(a, b) {
    return new Date(b.created_at || 0) - new Date(a.created_at || 0);
}

function formatTime(isoStr) {
    if (!isoStr) return '';
    const d = new Date(isoStr);
    const now = new Date();
    const diffMs = now - d;
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1)  return 'vừa xong';
    if (diffMin < 60) return `${diffMin} phút trước`;
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 24)   return `${diffH} giờ trước`;
    return d.toLocaleDateString('vi-VN');
}

// ─── Markdown renderer ───────────────────────────────────────────────────────

function renderMarkdown(text) {
    const lines = text.split('\n');
    const elements = [];
    let i = 0;
    while (i < lines.length) {
        const line = lines[i];
        if (line.includes('|') && lines[i + 1]?.includes('---')) {
            const headers = line.split('|').filter(h => h.trim());
            const rows = [];
            i += 2;
            while (i < lines.length && lines[i].includes('|')) {
                rows.push(lines[i].split('|').filter(c => c.trim()));
                i++;
            }
            elements.push(
                <table key={i} className="md-table">
                    <thead><tr>{headers.map((h, j) => <th key={j}>{inlineMarkdown(h.trim())}</th>)}</tr></thead>
                    <tbody>{rows.map((r, j) => <tr key={j}>{r.map((c, k) => <td key={k}>{inlineMarkdown(c.trim())}</td>)}</tr>)}</tbody>
                </table>
            );
        } else if (/^#{1,3} /.test(line)) {
            const level = line.match(/^#+/)[0].length;
            const content = line.replace(/^#+\s/, '');
            const Tag = `h${Math.min(level + 2, 6)}`;
            elements.push(<Tag key={i} className="md-heading">{inlineMarkdown(content)}</Tag>);
            i++;
        } else if (/^[*\-] /.test(line)) {
            const items = [];
            while (i < lines.length && /^[*\-] /.test(lines[i])) {
                items.push(<li key={i}>{inlineMarkdown(lines[i].replace(/^[*\-] /, ''))}</li>);
                i++;
            }
            elements.push(<ul key={`ul-${i}`} className="md-list">{items}</ul>);
        } else if (line.trim() === '') {
            elements.push(<br key={`br-${i}`} />);
            i++;
        } else {
            elements.push(<p key={i} className="md-p">{inlineMarkdown(line)}</p>);
            i++;
        }
    }
    return elements;
}

function inlineMarkdown(text) {
    const parts = [];
    let rest = text;
    let key = 0;
    while (rest.length > 0) {
        const boldMatch   = rest.match(/\*\*(.+?)\*\*/);
        const italicMatch = rest.match(/\*(.+?)\*/);
        const codeMatch   = rest.match(/`(.+?)`/);
        const earliest = [
            boldMatch   ? { idx: rest.indexOf(boldMatch[0]),   len: boldMatch[0].length,   el: <strong key={key++}>{boldMatch[1]}</strong> }   : null,
            italicMatch ? { idx: rest.indexOf(italicMatch[0]), len: italicMatch[0].length, el: <em key={key++}>{italicMatch[1]}</em> }           : null,
            codeMatch   ? { idx: rest.indexOf(codeMatch[0]),   len: codeMatch[0].length,   el: <code key={key++} className="md-code">{codeMatch[1]}</code> } : null,
        ].filter(Boolean).sort((a, b) => a.idx - b.idx)[0];
        if (!earliest) { parts.push(rest); break; }
        if (earliest.idx > 0) parts.push(rest.slice(0, earliest.idx));
        parts.push(earliest.el);
        rest = rest.slice(earliest.idx + earliest.len);
    }
    return parts;
}

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

    const [sessionId,    setSessionId]    = useState(null);
    const [sessionError, setSessionError] = useState(false);
    const [sessions,     setSessions]     = useState([]);   // history list
    const [messages,     setMessages]     = useState([{ id: msgId++, role: 'bot', text: GREETING }]);
    const [input,        setInput]        = useState('');
    const [isStreaming,  setIsStreaming]  = useState(false);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [showHistory, setShowHistory] = useState(false);
    const chatWindowRef = useRef();
    const scrollNewSessionToTopRef = useRef(false);
    const shouldAutoScrollRef = useRef(true);



    // ── Init: load active session or create new ──────────────────────────────

    useEffect(() => {
        const init = async () => {
            try {
                // 1. Fetch sessions from API
                const sessionList = await getChatSessions();
                const msgs = normalizeSessions(sessionList);
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
        // eslint-disable-next-line react-hooks/exhaustive-deps
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

    const loadHistory = async (sid) => {
        setLoadingHistory(true);
        try {
            const history = await getChatHistory(sid);
            const msgs = Array.isArray(history) ? history : history?.data;
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

    const startNewSession = async () => {
        const res = await createChatSession();
        const sid = res?.session_id || res?.data?.session_id;
        if (!sid) throw new Error('No session_id returned');
        localStorage.setItem(ACTIVE_SID_KEY, sid);
        setSessionId(sid);
        
        // Refresh session list from server
        const updatedList = await getChatSessions();
        setSessions(normalizeSessions(updatedList));
        
        return sid;
    };

    // ── Delete a session ─────────────────────────────────────────────────────

    const handleDeleteSession = async (sid, e) => {
        e.stopPropagation();
        if (isStreaming) return;
        if (!window.confirm('Xoá cuộc trò chuyện này?')) return;
        setSessions(prev => prev.filter(s => s.id !== sid));
        if (sid === sessionId) clearSession();
        try {
            await deleteChatSession(sid);
        } catch (err) {
            console.warn('[chat] deleteChatSession failed', err);
        }
    };

    // ── Switch to an existing session ────────────────────────────────────────

    const switchSession = async (sid) => {
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
        const placeholderEntry = {
            id: PLACEHOLDER,
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
            const sid = res?.session_id || res?.data?.session_id;
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

    // ── Send message ─────────────────────────────────────────────────────────

    const sendMessage = (text) => {
        if (!text.trim() || isStreaming) return;

        const userMsg = { id: msgId++, role: 'user', text };
        const botMsg  = { id: msgId++, role: 'bot', text: '', streaming: true };
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
        let flushTimer = null;

        const updateBotText = (textValue, extra = {}) => {
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
                setIsStreaming(false);
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
                setIsStreaming(false);
            }
        );
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(input); }
    };

    // ── Render ───────────────────────────────────────────────────────────────

    if (!settings) {
        return (
            <div className="chat-page flex-center" style={{ minHeight: '100vh', background: '#000' }}>
                <div className="loading-spinner"></div>
                <span style={{ color: '#888', marginLeft: 12 }}>Đang kiểm tra trạng thái...</span>
            </div>
        );
    }

    if (settings.isChatEnabled === false) {
        return (
            <div style={{
                position: 'fixed',
                top: 0,
                left: 0,
                width: '100vw',
                height: '100vh',
                zIndex: 9999,
                background: '#000'
            }}>
                <MaintenancePage message="Chúng tôi đang bảo trì tính năng AI Chat theo định kỳ. Vui lòng quay lại sau." />
            </div>
        );
    }

    return (
        <div className="chat-page">
            {/* ── MIDDLE: Chat ── */}
            <div className="chat-main">
                {/* Chat header */}
                <div className="chat-header">
                    <div className="flex-center gap-12">
                        <button 
                            className={`btn btn-ghost history-toggle-btn ${showHistory ? 'active' : ''}`}
                            onClick={() => setShowHistory(!showHistory)}
                            title={showHistory ? 'Ẩn lịch sử' : 'Xem lịch sử'}
                        >
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                            </svg>
                            <span className="hide-mobile">{showHistory ? 'Ẩn lịch sử' : 'Lịch sử'}</span>
                        </button>
                        <span className="chat-header-title">Tech Radar AI</span>
                    </div>
                    {sessionError && (
                        <span className="chat-status-err">Mất kết nối server</span>
                    )}
                    <button
                        className="btn btn-ghost new-chat-btn"
                        onClick={clearSession}
                        disabled={isStreaming}
                        title="Bắt đầu cuộc trò chuyện mới"
                    >
                        Cuộc trò chuyện mới
                    </button>
                </div>

                {/* ── LEFT: History panel (Now moved here for mobile/desktop flexibility) ── */}
                <div className="chat-content-wrap">
                    <div className={`chat-history-panel ${showHistory ? 'is-open' : ''}`}>
                        <div className="history-header">
                            <span className="history-title">Lịch sử</span>
                            <button
                                className="new-chat-icon-btn"
                                onClick={clearSession}
                                disabled={isStreaming}
                                title="Cuộc trò chuyện mới"
                            >
                                +
                            </button>
                        </div>

                        <div className="history-list">
                            {sessions.length === 0 && (
                                <p className="history-empty">Chưa có cuộc trò chuyện nào.</p>
                            )}
                            {sessions.map(s => (
                                <div
                                    key={s.id}
                                    className={`history-item${s.id === sessionId ? ' active' : ''}`}
                                    onClick={() => {
                                        switchSession(s.id);
                                        if (window.innerWidth <= 1024) setShowHistory(false); // Close on selection on mobile
                                    }}
                                >
                                    <div className="history-item-body">
                                        <span className="history-item-title">{s.title}</span>
                                        <span className="history-item-time">{formatTime(s.created_at)}</span>
                                    </div>
                                    <button
                                        className="history-item-del"
                                        title="Xoá cuộc trò chuyện"
                                        onClick={(e) => handleDeleteSession(s.id, e)}
                                    >
                                        🗑
                                    </button>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="chat-window" ref={chatWindowRef} onScroll={handleChatScroll}>
                        {loadingHistory && (
                            <div className="history-loading">Đang tải lịch sử…</div>
                        )}
                        {messages.map(msg => (
                            <div key={msg.id} className={`chat-bubble-wrap ${msg.role}`}>
                                {msg.role === 'bot' && <div className="bot-avatar text-avatar">AI</div>}
                                <div className={`chat-bubble ${msg.role}`}>
                                    <div className="bubble-content">
                                        {renderMarkdown(msg.text)}
                                        {msg.streaming && <span className="cursor-blink" />}
                                    </div>
                                </div>
                                {msg.role === 'user' && <div className="user-avatar text-avatar">U</div>}
                            </div>
                        ))}
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

        </div>
    );
}
