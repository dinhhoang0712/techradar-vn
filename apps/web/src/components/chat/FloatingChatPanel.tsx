import type { KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useChatController } from '../../hooks/useChatController';
import ChatMessageBubble from './ChatMessageBubble';
import './FloatingChatWidget.css';

const QUICK_PROMPTS = [
    'Xu hướng công nghệ hot nhất?',
    'Lương DevOps ở Việt Nam bao nhiêu?',
    'Lộ trình học Data Engineer?',
];

interface FloatingChatPanelProps {
    onClose: () => void;
}

// Nội dung thực sự của widget chat nổi — tách khỏi FloatingChatWidget.tsx và lazy-load (xem
// FloatingChatWidget.tsx) vì component này kéo theo useChatController + ChatMessageBubble +
// MarkdownContent (react-markdown/remark-gfm) — nếu import thẳng vào UserLayout (luôn eager-load
// ở mọi trang) thì toàn bộ chuỗi dependency đó bị gộp vào bundle chính thay vì chỉ tải khi người
// dùng thực sự bấm mở chat.
export default function FloatingChatPanel({ onClose }: FloatingChatPanelProps) {
    const navigate = useNavigate();

    const {
        messages, input, setInput, isStreaming, sessionError,
        chatWindowRef, handleChatScroll, sendMessage,
    } = useChatController();

    const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(input); }
    };

    return (
        <div className="floating-chat-panel">
            <div className="floating-chat-header">
                <div className="floating-chat-title">
                    <span className="bot-avatar text-avatar gradient-ring active" style={{ width: 28, height: 28, fontSize: '0.7rem' }}>AI</span>
                    Tech Radar AI
                </div>
                <div className="floating-chat-header-actions">
                    <button
                        type="button"
                        className="floating-chat-icon-btn"
                        title="Mở toàn màn hình"
                        onClick={() => navigate('/chat')}
                    >
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M15 3h6v6"></path>
                            <path d="M9 21H3v-6"></path>
                            <path d="M21 3l-7 7"></path>
                            <path d="M3 21l7-7"></path>
                        </svg>
                    </button>
                    <button
                        type="button"
                        className="floating-chat-icon-btn"
                        title="Đóng"
                        onClick={onClose}
                    >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <line x1="18" y1="6" x2="6" y2="18"></line>
                            <line x1="6" y1="6" x2="18" y2="18"></line>
                        </svg>
                    </button>
                </div>
            </div>

            {sessionError && (
                <div className="floating-chat-error">Chưa kết nối được tới server.</div>
            )}

            <div className="floating-chat-window" ref={chatWindowRef} onScroll={handleChatScroll}>
                {messages.map(msg => <ChatMessageBubble key={msg.id} msg={msg} />)}
            </div>

            {messages.length <= 1 && (
                <div className="floating-chat-quick-prompts">
                    {QUICK_PROMPTS.map((p, i) => (
                        <button key={i} className="floating-chat-quick-btn" onClick={() => sendMessage(p)}>{p}</button>
                    ))}
                </div>
            )}

            <div className="floating-chat-input-bar">
                <textarea
                    className="floating-chat-input"
                    placeholder="Hỏi Tech Radar AI..."
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    rows={1}
                    disabled={isStreaming}
                />
                <button
                    type="button"
                    className="floating-chat-send-btn"
                    onClick={() => sendMessage(input)}
                    disabled={isStreaming || !input.trim()}
                >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="22" y1="2" x2="11" y2="13"></line>
                        <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                    </svg>
                </button>
            </div>
        </div>
    );
}
