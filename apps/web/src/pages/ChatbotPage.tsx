import type { KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAppContext } from '../contexts/appContextStore';
import { useChatController } from '../hooks/useChatController';
import MaintenancePage from './MaintenancePage';
import MaintenanceOverlay from '../components/common/MaintenanceOverlay';
import Modal from '../components/common/Modal';
import ChatHeader from '../components/chat/ChatHeader';
import HistoryPanel from '../components/chat/HistoryPanel';
import ChatMessageBubble from '../components/chat/ChatMessageBubble';
import './ChatbotPage.css';

const QUICK_PROMPTS = [
    'Tôi muốn tìm việc Data Engineer',
    'FPT tuyển kỹ sư phần mềm không?',
    'Shopee đang tuyển vị trí gì?',
    'Lương DevOps engineer ở Việt Nam bao nhiêu?',
    'Vì sao AI được cho là gây hại cho môi trường?',
];

export default function ChatbotPage() {
    const context = useAppContext();
    const settings = context?.settings;
    const navigate = useNavigate();

    const {
        sessionId, sessionError, sessions, messages, input, setInput,
        isStreaming, loadingHistory, showHistory, setShowHistory,
        deleteTarget, setDeleteTarget, agentMode, setAgentMode,
        chatWindowRef, handleChatScroll,
        handleDeleteSession, confirmDeleteSession, switchSession, clearSession, sendMessage,
    } = useChatController();

    const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(input); }
    };

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
