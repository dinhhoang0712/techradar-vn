import { formatTime } from '../../utils/chatSession';
import type { ChatSession } from '../../utils/chatSession';
import type { MouseEvent } from 'react';

interface HistoryPanelProps {
    sessions: ChatSession[];
    sessionId: string | null;
    showHistory: boolean;
    isStreaming: boolean;
    onSwitch: (sessionId: string) => void;
    onDelete: (sessionId: string, e: MouseEvent) => void;
    onNewChat: () => void;
}

// Panel lịch sử các cuộc trò chuyện — bấm 1 mục để chuyển phiên, hoặc xoá.
export default function HistoryPanel({ sessions, sessionId, showHistory, isStreaming, onSwitch, onDelete, onNewChat }: HistoryPanelProps) {
    return (
        <div className={`chat-history-panel ${showHistory ? 'is-open' : ''}`}>
            <div className="history-header">
                <span className="history-title">Lịch sử</span>
                <button
                    className="new-chat-icon-btn"
                    onClick={onNewChat}
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
                        onClick={() => onSwitch(s.id)}
                    >
                        <div className="history-item-body">
                            <span className="history-item-title">{s.title}</span>
                            <span className="history-item-time">{formatTime(s.created_at)}</span>
                        </div>
                        <button
                            className="history-item-del"
                            title="Xoá cuộc trò chuyện"
                            onClick={(e) => onDelete(s.id, e)}
                        >
                            🗑
                        </button>
                    </div>
                ))}
            </div>
        </div>
    );
}
