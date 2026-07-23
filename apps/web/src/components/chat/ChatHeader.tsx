interface ChatHeaderProps {
    showHistory: boolean;
    onToggleHistory: () => void;
    sessionError: boolean;
    agentMode: boolean;
    onToggleAgent: () => void;
    isStreaming: boolean;
    onNewChat: () => void;
}

// Thanh header của ChatbotPage: toggle lịch sử, trạng thái lỗi kết nối, toggle chế độ Agent, và nút
// bắt đầu cuộc trò chuyện mới.
export default function ChatHeader({
    showHistory, onToggleHistory, sessionError,
    agentMode, onToggleAgent, isStreaming, onNewChat,
}: ChatHeaderProps) {
    return (
        <div className="chat-header">
            <div className="flex-center gap-12">
                <button
                    className={`btn btn-ghost history-toggle-btn ${showHistory ? 'active' : ''}`}
                    onClick={onToggleHistory}
                    title={showHistory ? 'Ẩn lịch sử' : 'Xem lịch sử'}
                >
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                    </svg>
                    <span className="hide-mobile">{showHistory ? 'Ẩn lịch sử' : 'Lịch sử'}</span>
                </button>
                <span className="chat-logo-ping" aria-hidden="true">
                    <span className="chat-ping-ring"></span>
                    <span className="chat-ping-dot"></span>
                </span>
                <span className="chat-header-title">Tech Radar AI</span>
            </div>
            {sessionError && (
                <span className="chat-status-err">Mất kết nối server</span>
            )}
            <button
                className={`btn btn-ghost history-toggle-btn ${agentMode ? 'active' : ''}`}
                onClick={onToggleAgent}
                disabled={isStreaming}
                title={agentMode ? 'Tắt chế độ Agent' : 'Bật chế độ Agent (suy luận đa bước, không streaming)'}
            >
                <span className="hide-mobile">{agentMode ? 'Chế độ Agent: Bật' : 'Chế độ Agent'}</span>
            </button>
            <button
                className="btn btn-ghost new-chat-btn"
                onClick={onNewChat}
                disabled={isStreaming}
                title="Bắt đầu cuộc trò chuyện mới"
            >
                Cuộc trò chuyện mới
            </button>
        </div>
    );
}
