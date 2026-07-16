import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useMessagingContext } from '../contexts/messagingStore';
import { useToast } from '../components/common/toastContext';
import Avatar from '../components/common/Avatar';
import './MessagesPage.css';

function timeAgo(iso) {
    if (!iso) return '';
    const then = new Date(iso).getTime();
    if (Number.isNaN(then)) return '';
    const diff = Math.max(0, Date.now() - then) / 1000;
    if (diff < 60) return 'vừa xong';
    if (diff < 3600) return `${Math.floor(diff / 60)} phút trước`;
    if (diff < 86400) return `${Math.floor(diff / 3600)} giờ trước`;
    return `${Math.floor(diff / 86400)} ngày trước`;
}

function ConversationRow({ conversation, active, onSelect }) {
    return (
        <button type="button" className={`conv-row${active ? ' active' : ''}`} onClick={onSelect}>
            <Avatar user={conversation.other_user} size={40} />
            <div className="conv-row-body">
                <div className="conv-row-top">
                    <span className="conv-row-name">{conversation.other_user?.full_name || 'Người dùng'}</span>
                    <span className="conv-row-time">{timeAgo(conversation.last_message_at)}</span>
                </div>
                <div className="conv-row-bottom">
                    <span className="conv-row-preview">{conversation.last_message_content || 'Chưa có tin nhắn'}</span>
                    {conversation.unread_count > 0 && <span className="conv-unread-badge">{conversation.unread_count}</span>}
                </div>
            </div>
        </button>
    );
}

export default function MessagesPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const notify = useToast();
    const {
        currentUserId,
        conversations,
        conversationsLoading,
        messagesByConversation,
        activeConversationId,
        selectConversation,
        send,
    } = useMessagingContext();

    const [input, setInput] = useState('');
    const [sending, setSending] = useState(false);
    const threadRef = useRef(null);

    const paramConversation = searchParams.get('conversation');

    // Chọn cuộc trò chuyện theo deep-link (?conversation=) khi danh sách đã sẵn sàng.
    useEffect(() => {
        if (paramConversation && paramConversation !== activeConversationId) {
            selectConversation(paramConversation);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [paramConversation]);

    useEffect(() => {
        const el = threadRef.current;
        if (el) el.scrollTop = el.scrollHeight;
    }, [messagesByConversation, activeConversationId]);

    const handleSelect = (conversationId) => {
        setSearchParams({ conversation: conversationId });
        selectConversation(conversationId);
    };

    const activeConversation = conversations.find((c) => c.id === activeConversationId);
    const activeMessages = messagesByConversation[activeConversationId] || [];

    const handleSend = async (e) => {
        e.preventDefault();
        const content = input.trim();
        if (!content || !activeConversationId) return;
        setSending(true);
        try {
            await send(activeConversationId, content);
            setInput('');
        } catch (err) {
            notify({ title: 'Không thể gửi tin nhắn', body: err.message, variant: 'error' });
        } finally {
            setSending(false);
        }
    };

    return (
        <div className="messages-page">
            <div className="messages-sidebar card">
                <h2 className="section-title">Tin nhắn</h2>
                {conversationsLoading ? (
                    <div className="conv-list">
                        {[0, 1, 2, 3].map((i) => (
                            <div className="conv-row-skeleton" key={i}>
                                <div className="skeleton conv-row-skeleton-avatar" />
                                <div className="conv-row-skeleton-lines">
                                    <div className="skeleton conv-row-skeleton-line" style={{ width: '55%' }} />
                                    <div className="skeleton conv-row-skeleton-line" style={{ width: '82%' }} />
                                </div>
                            </div>
                        ))}
                    </div>
                ) : conversations.length === 0 ? (
                    <div className="messages-state">
                        <span className="messages-empty-icon" aria-hidden="true">💬</span>
                        Chưa có cuộc trò chuyện nào.
                    </div>
                ) : (
                    <div className="conv-list">
                        {conversations.map((c) => (
                            <ConversationRow
                                key={c.id}
                                conversation={c}
                                active={c.id === activeConversationId}
                                onSelect={() => handleSelect(c.id)}
                            />
                        ))}
                    </div>
                )}
            </div>

            <div className="messages-thread card">
                {!activeConversationId ? (
                    <div className="messages-state thread-empty">Chọn một cuộc trò chuyện để bắt đầu.</div>
                ) : (
                    <>
                        <div className="thread-header">
                            <button
                                type="button"
                                className="thread-header-user"
                                onClick={() => navigate(`/users/${activeConversation?.other_user?.id}`)}
                            >
                                <Avatar user={activeConversation?.other_user} size={32} />
                                <span>{activeConversation?.other_user?.full_name || 'Người dùng'}</span>
                            </button>
                        </div>

                        <div className="thread-messages" ref={threadRef}>
                            {activeMessages.length === 0 ? (
                                <div className="messages-state">Chưa có tin nhắn. Hãy bắt đầu cuộc trò chuyện!</div>
                            ) : (
                                activeMessages.map((m) => (
                                    <div key={m.id} className={`msg-row${m.sender_id === currentUserId ? ' own' : ''}`}>
                                        <div className="msg-bubble">
                                            <span className="msg-text">{m.content}</span>
                                            <span className="msg-time">{timeAgo(m.created_at)}</span>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>

                        <form className="thread-input-bar" onSubmit={handleSend}>
                            <input
                                className="thread-input"
                                placeholder="Nhập tin nhắn..."
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                maxLength={2000}
                                disabled={sending}
                            />
                            <button type="submit" className="btn btn-primary" disabled={sending || !input.trim()}>
                                Gửi
                            </button>
                        </form>
                    </>
                )}
            </div>
        </div>
    );
}
