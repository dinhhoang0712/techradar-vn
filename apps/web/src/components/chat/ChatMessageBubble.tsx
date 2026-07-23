import { renderMarkdown } from '../../utils/markdown';
import type { ChatUiMessage } from '../../types/chat';

// 1 dòng tin nhắn trong cửa sổ chat — bot (kèm steps của Agent mode/sources RAG nếu có) hoặc user.
export default function ChatMessageBubble({ msg }: { msg: ChatUiMessage }) {
    return (
        <div className={`chat-bubble-wrap ${msg.role}`}>
            {msg.role === 'bot' && (
                <div className={`bot-avatar text-avatar gradient-ring${msg.streaming ? ' active' : ''}`}>AI</div>
            )}
            <div className={`chat-bubble ${msg.role}`}>
                <div className="bubble-content">
                    {msg.streaming && !msg.text ? (
                        // Chưa có token/answer nào về (vừa gửi request, đang chờ AI xử lý) — hiện
                        // 3 chấm nhấp nháy thay vì để bubble trống trơn (trước đây chỉ có 1 gạch
                        // cursor-blink 2px, nhìn như bị đứng chứ không phải đang xử lý).
                        <span className="dots-animation"><span>.</span><span>.</span><span>.</span></span>
                    ) : (
                        <>
                            {renderMarkdown(msg.text)}
                            {msg.streaming && <span className="cursor-blink" />}
                        </>
                    )}
                </div>
                {msg.steps && msg.steps.length > 0 && (
                    <details className="agent-steps">
                        <summary>Các bước đã thực hiện ({msg.steps.length})</summary>
                        <ul>
                            {msg.steps.map((s, i) => (
                                <li key={i}><strong>{s.tool}</strong>: {s.input}</li>
                            ))}
                        </ul>
                    </details>
                )}
                {msg.meta?.sources && msg.meta.sources.length > 0 && (
                    <details className="chat-sources">
                        <summary>Nguồn tham khảo ({msg.meta.sources.length})</summary>
                        <ul>
                            {msg.meta.sources.map((s, i) => (
                                <li key={i}>
                                    <span className="chat-source-title">{s.title || 'Bài viết'}</span>
                                    {(s.source || s.published_date) && (
                                        <span className="chat-source-meta">
                                            {s.source}
                                            {s.source && s.published_date ? ' · ' : ''}
                                            {s.published_date}
                                        </span>
                                    )}
                                </li>
                            ))}
                        </ul>
                    </details>
                )}
            </div>
            {msg.role === 'user' && <div className="user-avatar text-avatar">U</div>}
        </div>
    );
}
