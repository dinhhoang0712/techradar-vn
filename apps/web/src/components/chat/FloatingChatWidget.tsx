import { useState, lazy, Suspense } from 'react';
import { useLocation } from 'react-router-dom';
import { useAppContext } from '../../contexts/appContextStore';
import './FloatingChatWidget.css';

// Panel thật (ChatMessageBubble + MarkdownContent + useChatController) chỉ lazy-load khi người
// dùng thực sự bấm mở — wrapper này mount eager ở UserLayout (mọi trang) nên phải nhẹ, không kéo
// theo react-markdown/chat service vào bundle chính.
const FloatingChatPanel = lazy(() => import('./FloatingChatPanel'));

// Nút chat nổi góc dưới-phải, hiện trên mọi trang (trừ /chat — đã có bản đầy đủ ở đó rồi) và khi
// tính năng chat không bị tắt bảo trì.
export default function FloatingChatWidget() {
    const { settings } = useAppContext() ?? {};
    const location = useLocation();
    const [open, setOpen] = useState(false);

    if (!settings || settings.isChatEnabled === false) return null;
    if (location.pathname === '/chat') return null;

    return (
        <div className="floating-chat-root">
            {open && (
                <Suspense fallback={<div className="floating-chat-panel floating-chat-panel-loading">Đang tải...</div>}>
                    <FloatingChatPanel onClose={() => setOpen(false)} />
                </Suspense>
            )}

            <button
                type="button"
                className={`floating-chat-fab${open ? ' open' : ''}`}
                onClick={() => setOpen(o => !o)}
                title={open ? 'Đóng chat' : 'Hỏi Tech Radar AI'}
            >
                {!open && <span className="floating-chat-fab-pulse" />}
                {open ? (
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="18" y1="6" x2="6" y2="18"></line>
                        <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                ) : (
                    <svg width="27" height="27" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M12 8V4H8"></path>
                        <rect width="16" height="12" x="4" y="8" rx="2"></rect>
                        <path d="M2 14h2"></path>
                        <path d="M20 14h2"></path>
                        <path d="M15 13v2"></path>
                        <path d="M9 13v2"></path>
                    </svg>
                )}
            </button>
        </div>
    );
}
