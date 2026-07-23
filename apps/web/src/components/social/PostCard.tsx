import { useState, useEffect } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { likePost, unlikePost, getComments, addComment, deletePost, reportPost, reportComment } from '../../api/socialService';
import { useToast } from '../common/toastContext';
import Modal from '../common/Modal';
import Avatar from '../common/Avatar';
import CompanyLogo from '../common/CompanyLogo';
import MentionTextarea from './MentionTextarea';
import ImageLightbox from './ImageLightbox';
import { tokenizeHashtags } from '../../utils/hashtags';
import { groupComments } from '../../utils/comments';
import { timeAgo } from '../../utils/timeAgo';
import type { Post, Comment } from '../../types/social';
import './PostCard.css';

interface CommentRowProps {
    comment: Comment;
    currentUserId: string | null;
    onReply: (comment: Comment) => void;
    onReport: (id: string | number) => void;
}

function CommentRow({ comment, currentUserId, onReply, onReport }: CommentRowProps) {
    return (
        <div className="post-comment-row">
            <Avatar user={comment.author} size={28} />
            <div className="post-comment-body">
                <span className="post-comment-author">{comment.author?.full_name || 'Người dùng'}</span>
                <span className="post-comment-text">{comment.content}</span>
                <div className="post-comment-footer">
                    <span className="post-comment-time">{timeAgo(comment.created_at)}</span>
                    <button type="button" className="post-comment-reply-btn" onClick={() => onReply(comment)}>
                        Trả lời
                    </button>
                </div>
            </div>
            {comment.author?.id !== currentUserId && (
                <button
                    type="button"
                    className="post-comment-report-btn"
                    title="Báo cáo bình luận"
                    onClick={() => onReport(comment.id)}
                >
                    🚩
                </button>
            )}
        </div>
    );
}

type ReportTarget = { type: 'post' | 'comment'; id: string | number };

interface PostCardProps {
    post: Post;
    currentUserId: string | null;
    onDeleted?: (postId: string) => void;
    onHashtagClick?: (tag: string) => void;
}

export default function PostCard({ post, currentUserId, onDeleted, onHashtagClick }: PostCardProps) {
    const [liked, setLiked] = useState(!!post.liked_by_me);
    const [likeCount, setLikeCount] = useState(post.like_count || 0);
    const [commentCount, setCommentCount] = useState(post.comment_count || 0);
    const [commentsOpen, setCommentsOpen] = useState(false);
    const [comments, setComments] = useState<Comment[]>([]);
    const [commentsLoading, setCommentsLoading] = useState(false);
    const [commentInput, setCommentInput] = useState('');
    const [commentMentionedIds, setCommentMentionedIds] = useState<string[]>([]);
    const [postingComment, setPostingComment] = useState(false);
    const [replyingTo, setReplyingTo] = useState<string | number | null>(null); // top-level comment id being replied to
    const [replyInput, setReplyInput] = useState('');
    const [replyMentionedIds, setReplyMentionedIds] = useState<string[]>([]);
    const [postingReply, setPostingReply] = useState(false);
    const [confirmingDelete, setConfirmingDelete] = useState(false);
    const [reportTarget, setReportTarget] = useState<ReportTarget | null>(null);
    const [reportReason, setReportReason] = useState('');
    const [submittingReport, setSubmittingReport] = useState(false);
    const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
    const navigate = useNavigate();
    const notify = useToast();

    const isOwn = Boolean(currentUserId && post.author?.id === currentUserId);
    const images = post.image_urls || [];
    const { topLevel, repliesByParentId } = groupComments(comments);

    // Feed sends live updates by patching this post's like_count/comment_count in place (see
    // FeedPage's SSE handler) — resync local state so counts update for posts already on screen.
    useEffect(() => {
        setLikeCount(post.like_count || 0);
    }, [post.like_count]);

    useEffect(() => {
        setCommentCount(post.comment_count || 0);
    }, [post.comment_count]);

    const toggleLike = async () => {
        const next = !liked;
        setLiked(next);
        setLikeCount((c) => Math.max(0, c + (next ? 1 : -1)));
        try {
            await (next ? likePost(post.id) : unlikePost(post.id));
        } catch {
            setLiked(!next);
            setLikeCount((c) => Math.max(0, c + (next ? -1 : 1)));
            notify({ title: 'Không thể cập nhật lượt thích', variant: 'error' });
        }
    };

    const openComments = async () => {
        setCommentsOpen((o) => !o);
        if (!commentsOpen && comments.length === 0) {
            setCommentsLoading(true);
            try {
                const res = await getComments(post.id);
                setComments(res?.data ?? []);
            } catch {
                notify({ title: 'Không tải được bình luận', variant: 'error' });
            } finally {
                setCommentsLoading(false);
            }
        }
    };

    const submitComment = async (e: FormEvent) => {
        e.preventDefault();
        const content = commentInput.trim();
        if (!content) return;
        setPostingComment(true);
        try {
            const res = await addComment(post.id, content, { mentionedUserIds: commentMentionedIds });
            const newComment: Comment = {
                id: res?.data?.id || `tmp-${Date.now()}`,
                author: { id: currentUserId ?? '', full_name: 'Bạn', avatar_url: null },
                content,
                parent_id: null,
                created_at: new Date().toISOString(),
            };
            setComments((prev) => [...prev, newComment]);
            setCommentCount((c) => c + 1);
            setCommentInput('');
            setCommentMentionedIds([]);
        } catch (err) {
            notify({ title: 'Không thể gửi bình luận', body: (err as Error).message, variant: 'error' });
        } finally {
            setPostingComment(false);
        }
    };

    const startReply = (comment: Comment) => {
        // Replying to a reply collapses to the same top-level thread, per the backend's 1-level cap.
        const targetParentId = comment.parent_id || comment.id;
        setReplyingTo(targetParentId);
        setReplyInput('');
        setReplyMentionedIds([]);
    };

    const submitReply = async (e: FormEvent) => {
        e.preventDefault();
        const content = replyInput.trim();
        if (!content || !replyingTo) return;
        setPostingReply(true);
        try {
            const res = await addComment(post.id, content, { parentId: String(replyingTo), mentionedUserIds: replyMentionedIds });
            const newReply: Comment = {
                id: res?.data?.id || `tmp-${Date.now()}`,
                author: { id: currentUserId ?? '', full_name: 'Bạn', avatar_url: null },
                content,
                parent_id: replyingTo,
                created_at: new Date().toISOString(),
            };
            setComments((prev) => [...prev, newReply]);
            setCommentCount((c) => c + 1);
            setReplyingTo(null);
            setReplyInput('');
            setReplyMentionedIds([]);
        } catch (err) {
            notify({ title: 'Không thể gửi trả lời', body: (err as Error).message, variant: 'error' });
        } finally {
            setPostingReply(false);
        }
    };

    const handleDelete = async () => {
        try {
            await deletePost(post.id);
            setConfirmingDelete(false);
            onDeleted?.(post.id);
        } catch (err) {
            notify({ title: 'Không thể xoá bài viết', body: (err as Error).message, variant: 'error' });
        }
    };

    const closeReport = () => {
        setReportTarget(null);
        setReportReason('');
    };

    const submitReport = async () => {
        const reason = reportReason.trim();
        if (!reportTarget || !reason) return;
        setSubmittingReport(true);
        try {
            if (reportTarget.type === 'post') {
                await reportPost(String(reportTarget.id), reason);
            } else {
                await reportComment(String(reportTarget.id), reason);
            }
            notify({ title: 'Đã gửi báo cáo, cảm ơn bạn', variant: 'success' });
            closeReport();
        } catch (err) {
            notify({ title: 'Không thể gửi báo cáo', body: (err as Error).message, variant: 'error' });
        } finally {
            setSubmittingReport(false);
        }
    };

    const goToProfile = () => navigate(`/users/${post.author?.id}`);

    const renderReply = (reply: Comment) => (
        <div key={reply.id} className="post-reply-row">
            <CommentRow comment={reply} currentUserId={currentUserId} onReply={startReply} onReport={(id) => setReportTarget({ type: 'comment', id })} />
        </div>
    );

    return (
        <div className="post-card card">
            <div className="post-header">
                <button type="button" className="post-author" onClick={goToProfile}>
                    <Avatar user={post.author} />
                    <div className="post-author-info">
                        <span className="post-author-name">{post.author?.full_name || 'Người dùng'}</span>
                        <span className="post-time">{timeAgo(post.created_at)}</span>
                    </div>
                </button>
                <div className="post-header-actions">
                    {!isOwn && (
                        <button type="button" className="post-report-btn" title="Báo cáo bài viết" onClick={() => setReportTarget({ type: 'post', id: post.id })}>
                            🚩
                        </button>
                    )}
                    {isOwn && (
                        <button type="button" className="post-delete-btn" title="Xoá bài viết" onClick={() => setConfirmingDelete(true)}>
                            ✕
                        </button>
                    )}
                </div>
            </div>

            <p className="post-content">
                {tokenizeHashtags(post.content, post.hashtags).map((tok, i) =>
                    tok.type === 'tag' ? (
                        <span
                            key={i}
                            className="chip hashtag-chip"
                            onClick={(e) => {
                                e.stopPropagation();
                                onHashtagClick?.(tok.value);
                            }}
                        >
                            {tok.raw}
                        </span>
                    ) : (
                        <span key={i}>{tok.value}</span>
                    )
                )}
            </p>

            {post.tagged_company && (
                <div className="post-tagged-company">
                    <CompanyLogo name={post.tagged_company.name} size={20} />
                    <span>{post.tagged_company.name}</span>
                </div>
            )}

            {images.length > 0 && (
                <div className={`post-image-grid post-image-grid-${Math.min(images.length, 4)}`}>
                    {images.slice(0, 4).map((url, i) => (
                        <button
                            type="button"
                            key={url}
                            className="post-image-grid-item"
                            onClick={() => setLightboxIndex(i)}
                        >
                            <img src={url} alt={`Ảnh bài viết ${i + 1}`} loading="lazy" />
                        </button>
                    ))}
                </div>
            )}

            <div className="post-actions">
                <button type="button" className={`post-action-btn${liked ? ' liked' : ''}`} onClick={toggleLike}>
                    <span>{liked ? '♥' : '♡'}</span> {likeCount > 0 ? likeCount : ''} Thích
                </button>
                <button type="button" className="post-action-btn" onClick={openComments}>
                    <span>💬</span> {commentCount > 0 ? commentCount : ''} Bình luận
                </button>
            </div>

            {commentsOpen && (
                <div className="post-comments">
                    {commentsLoading ? (
                        <div className="post-comments-loading">Đang tải bình luận…</div>
                    ) : (
                        <div className="post-comment-list">
                            {topLevel.length === 0 && <p className="post-comments-empty">Chưa có bình luận nào.</p>}
                            {topLevel.map((c) => (
                                <div key={c.id}>
                                    <CommentRow
                                        comment={c}
                                        currentUserId={currentUserId}
                                        onReply={startReply}
                                        onReport={(id) => setReportTarget({ type: 'comment', id })}
                                    />
                                    {(repliesByParentId.get(c.id) || []).length > 0 && (
                                        <div className="post-reply-list">
                                            {(repliesByParentId.get(c.id) || []).map(renderReply)}
                                        </div>
                                    )}
                                    {replyingTo === c.id && (
                                        <form className="post-comment-form post-reply-form" onSubmit={submitReply}>
                                            <MentionTextarea
                                                as="input"
                                                className="post-comment-input"
                                                placeholder={`Trả lời ${c.author?.full_name || 'bình luận'}...`}
                                                value={replyInput}
                                                onChange={setReplyInput}
                                                mentionedUserIds={replyMentionedIds}
                                                onMentionedUserIdsChange={setReplyMentionedIds}
                                                maxLength={1000}
                                                disabled={postingReply}
                                            />
                                            <button type="submit" className="btn btn-secondary" disabled={postingReply || !replyInput.trim()}>
                                                Gửi
                                            </button>
                                            <button type="button" className="btn btn-ghost" onClick={() => setReplyingTo(null)}>
                                                Hủy
                                            </button>
                                        </form>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                    <form className="post-comment-form" onSubmit={submitComment}>
                        <MentionTextarea
                            as="input"
                            className="post-comment-input"
                            placeholder="Viết bình luận..."
                            value={commentInput}
                            onChange={setCommentInput}
                            mentionedUserIds={commentMentionedIds}
                            onMentionedUserIdsChange={setCommentMentionedIds}
                            maxLength={1000}
                            disabled={postingComment}
                        />
                        <button type="submit" className="btn btn-secondary" disabled={postingComment || !commentInput.trim()}>
                            Gửi
                        </button>
                    </form>
                </div>
            )}

            {lightboxIndex !== null && (
                <ImageLightbox images={images} startIndex={lightboxIndex} onClose={() => setLightboxIndex(null)} />
            )}

            {confirmingDelete && (
                <Modal title="Xác nhận xoá" onClose={() => setConfirmingDelete(false)} width="380px">
                    <p className="modal-body-text">Xoá bài viết này? Hành động này không thể hoàn tác.</p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setConfirmingDelete(false)}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={handleDelete}>Xoá</button>
                    </div>
                </Modal>
            )}

            {reportTarget && (
                <Modal
                    title={reportTarget.type === 'post' ? 'Báo cáo bài viết' : 'Báo cáo bình luận'}
                    onClose={closeReport}
                    width="420px"
                >
                    <p className="modal-body-text">Vui lòng cho biết lý do bạn báo cáo nội dung này. Quản trị viên sẽ xem xét.</p>
                    <textarea
                        className="report-reason-input"
                        rows={3}
                        maxLength={500}
                        placeholder="VD: spam, ngôn từ thù ghét, thông tin sai lệch..."
                        value={reportReason}
                        onChange={(e) => setReportReason(e.target.value)}
                        disabled={submittingReport}
                        autoFocus
                    />
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={closeReport} disabled={submittingReport}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={submitReport} disabled={submittingReport || !reportReason.trim()}>
                            {submittingReport ? 'Đang gửi…' : 'Gửi báo cáo'}
                        </button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
