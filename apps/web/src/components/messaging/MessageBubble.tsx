import { useState } from 'react';
import { timeAgo } from '../../utils/timeAgo';
import type { DirectMessage } from '../../types/messaging';

// Bảng reaction cố định kiểu Messenger — phải khớp với SetMessageReactionUseCase.ALLOWED_EMOJI
// ở backend (apps/backend/.../messaging/application/SetMessageReactionUseCase.java).
const REACTION_CHOICES = ['👍', '❤️', '😂', '😮', '😢', '😡'];

interface MessageBubbleProps {
    message: DirectMessage;
    own: boolean;
    showSeen: boolean;
    onReact: (emoji: string) => void;
    onRemoveReaction: () => void;
}

export default function MessageBubble({ message, own, showSeen, onReact, onRemoveReaction }: MessageBubbleProps) {
    const [pickerOpen, setPickerOpen] = useState(false);
    const reactions = message.reactions ?? [];
    const myEmoji = reactions.find((r) => r.reacted_by_me)?.emoji;
    // Tin nhắn chỉ có 1 emoji "like" nhanh (nút 👍 cạnh khung soạn) hiển thị lớn, không khung bong bóng.
    const isQuickLike = message.content === '👍' && !message.attachment;

    const handlePick = (emoji: string) => {
        setPickerOpen(false);
        if (emoji === myEmoji) {
            onRemoveReaction();
        } else {
            onReact(emoji);
        }
    };

    return (
        <div className={`msg-row${own ? ' own' : ''}`}>
            <div className="msg-bubble-wrap">
                <div
                    className="msg-react-zone"
                    onMouseEnter={() => setPickerOpen(true)}
                    onMouseLeave={() => setPickerOpen(false)}
                >
                    <button
                        type="button"
                        className="msg-react-trigger"
                        onClick={() => setPickerOpen((v) => !v)}
                        aria-label="Thả cảm xúc"
                    >
                        🙂
                    </button>
                    {pickerOpen && (
                        <div className="reaction-picker">
                            {REACTION_CHOICES.map((emoji) => (
                                <button
                                    type="button"
                                    key={emoji}
                                    className={`reaction-picker-emoji${emoji === myEmoji ? ' active' : ''}`}
                                    onClick={() => handlePick(emoji)}
                                >
                                    {emoji}
                                </button>
                            ))}
                        </div>
                    )}
                </div>

                <div className={`msg-bubble${isQuickLike ? ' msg-bubble--emoji' : ''}`}>
                    {message.attachment && (
                        message.attachment.content_type.startsWith('image/') ? (
                            <a href={message.attachment.url} target="_blank" rel="noreferrer" className="msg-attachment-image-link">
                                <img className="msg-attachment-image" src={message.attachment.url} alt={message.attachment.filename} />
                            </a>
                        ) : (
                            <a href={message.attachment.url} target="_blank" rel="noreferrer" className="msg-attachment-file">
                                <span className="msg-attachment-file-icon" aria-hidden="true">📎</span>
                                <span className="msg-attachment-file-name">{message.attachment.filename}</span>
                            </a>
                        )
                    )}
                    {message.content && <span className="msg-text">{message.content}</span>}
                    <span className="msg-time">{timeAgo(message.created_at)}</span>
                </div>

                {reactions.length > 0 && (
                    <div className="msg-reactions">
                        {reactions.map((r) => (
                            <button
                                type="button"
                                key={r.emoji}
                                className={`reaction-badge${r.reacted_by_me ? ' mine' : ''}`}
                                onClick={() => handlePick(r.emoji)}
                            >
                                {r.emoji} {r.count}
                            </button>
                        ))}
                    </div>
                )}
            </div>
            {showSeen && <span className="msg-seen">Đã xem</span>}
        </div>
    );
}
