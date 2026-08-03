import { useState, useEffect, useRef } from 'react';
import type { FormEvent, ChangeEvent } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useMessagingContext } from '../contexts/messagingStore';
import { useToast } from '../components/common/toastContext';
import Avatar from '../components/common/Avatar';
import MessageBubble from '../components/messaging/MessageBubble';
import { timeAgo } from '../utils/timeAgo';
import { fileToBase64 } from '../utils/fileToBase64';
import type { Conversation } from '../types/messaging';
import './MessagesPage.css';

// Phải khớp với FileUploadValidator.MAX_BYTES ở backend (apps/backend/.../shared/util/FileUploadValidator.java).
const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

interface PendingAttachment {
    file: File;
    dataUrl: string;
}

interface ConversationRowProps {
    conversation: Conversation;
    active: boolean;
    onSelect: () => void;
}

function ConversationRow({ conversation, active, onSelect }: ConversationRowProps) {
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
        conversationsError,
        messagesByConversation,
        messagesError,
        activeConversationId,
        selectConversation,
        refreshConversations,
        loadMessages,
        send,
        setReaction,
        removeReaction,
    } = useMessagingContext()!;

    const [input, setInput] = useState('');
    const [sending, setSending] = useState(false);
    const [pendingAttachment, setPendingAttachment] = useState<PendingAttachment | null>(null);
    const threadRef = useRef<HTMLDivElement>(null);

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

    const handleSelect = (conversationId: string) => {
        setSearchParams({ conversation: conversationId });
        selectConversation(conversationId);
    };

    const activeConversation = conversations.find((c) => c.id === activeConversationId);
    const activeMessages = (activeConversationId && messagesByConversation[activeConversationId]) || [];
    const lastOwnMessageId = [...activeMessages].reverse().find((m) => m.sender_id === currentUserId)?.id;

    const handleSend = async (e: FormEvent) => {
        e.preventDefault();
        const content = input.trim();
        if ((!content && !pendingAttachment) || !activeConversationId) return;
        setSending(true);
        try {
            const attachment = pendingAttachment
                ? {
                    content_type: pendingAttachment.file.type || 'application/octet-stream',
                    filename: pendingAttachment.file.name,
                    data_base64: pendingAttachment.dataUrl,
                }
                : undefined;
            await send(activeConversationId, content, attachment);
            setInput('');
            setPendingAttachment(null);
        } catch (err) {
            notify({ title: 'Không thể gửi tin nhắn', body: (err as Error).message, variant: 'error' });
        } finally {
            setSending(false);
        }
    };

    const handleQuickLike = async () => {
        if (!activeConversationId || sending) return;
        setSending(true);
        try {
            await send(activeConversationId, '👍');
        } catch (err) {
            notify({ title: 'Không thể gửi tin nhắn', body: (err as Error).message, variant: 'error' });
        } finally {
            setSending(false);
        }
    };

    const handleAttachmentSelect = async (e: ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        e.target.value = ''; // cho phép chọn lại cùng 1 file
        if (!file) return;
        if (file.size > MAX_ATTACHMENT_BYTES) {
            notify({ title: 'File quá lớn (tối đa 10MB)', variant: 'error' });
            return;
        }
        try {
            const dataUrl = await fileToBase64(file);
            setPendingAttachment({ file, dataUrl });
        } catch {
            notify({ title: 'Không thể đọc file', variant: 'error' });
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
                ) : conversationsError ? (
                    <div className="messages-state">
                        <span>Không tải được danh sách trò chuyện.</span>
                        <button className="btn btn-ghost mt-16" onClick={refreshConversations}>Thử lại</button>
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
                            {messagesError[activeConversationId] ? (
                                <div className="messages-state">
                                    <span>Không tải được tin nhắn.</span>
                                    <button
                                        className="btn btn-ghost mt-16"
                                        onClick={() => loadMessages(activeConversationId, { force: true })}
                                    >
                                        Thử lại
                                    </button>
                                </div>
                            ) : activeMessages.length === 0 ? (
                                <div className="messages-state">Chưa có tin nhắn. Hãy bắt đầu cuộc trò chuyện!</div>
                            ) : (
                                activeMessages.map((m) => (
                                    <MessageBubble
                                        key={m.id}
                                        message={m}
                                        own={m.sender_id === currentUserId}
                                        showSeen={m.sender_id === currentUserId && !!m.read && m.id === lastOwnMessageId}
                                        onReact={(emoji) => setReaction(activeConversationId, m.id, emoji)}
                                        onRemoveReaction={() => removeReaction(activeConversationId, m.id)}
                                    />
                                ))
                            )}
                        </div>

                        {pendingAttachment && (
                            <div className="thread-pending-attachment">
                                {pendingAttachment.file.type.startsWith('image/') ? (
                                    <img src={pendingAttachment.dataUrl} alt="" className="thread-pending-attachment-thumb" />
                                ) : (
                                    <span className="thread-pending-attachment-icon" aria-hidden="true">📎</span>
                                )}
                                <span className="thread-pending-attachment-name">{pendingAttachment.file.name}</span>
                                <button
                                    type="button"
                                    className="thread-pending-attachment-remove"
                                    onClick={() => setPendingAttachment(null)}
                                    aria-label="Bỏ file đính kèm"
                                >
                                    ✕
                                </button>
                            </div>
                        )}

                        <form className="thread-input-bar" onSubmit={handleSend}>
                            <label className="thread-attach-btn" aria-label="Đính kèm ảnh hoặc file">
                                📎
                                <input type="file" hidden onChange={handleAttachmentSelect} disabled={sending} />
                            </label>
                            <input
                                className="thread-input"
                                placeholder="Nhập tin nhắn..."
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                maxLength={2000}
                                disabled={sending}
                            />
                            {input.trim() || pendingAttachment ? (
                                <button type="submit" className="btn btn-primary" disabled={sending}>
                                    Gửi
                                </button>
                            ) : (
                                <button
                                    type="button"
                                    className="thread-like-btn"
                                    onClick={handleQuickLike}
                                    disabled={sending}
                                    aria-label="Gửi biểu tượng thích"
                                >
                                    👍
                                </button>
                            )}
                        </form>
                    </>
                )}
            </div>
        </div>
    );
}
