import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { likePost, unlikePost, getComments, addComment, deletePost } from '../../api/socialService';
import { useToast } from '../common/toastContext';
import Modal from '../common/Modal';
import Avatar from '../common/Avatar';
import './PostCard.css';

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

export default function PostCard({ post, currentUserId, onDeleted }) {
    const [liked, setLiked] = useState(!!post.liked_by_me);
    const [likeCount, setLikeCount] = useState(post.like_count || 0);
    const [commentCount, setCommentCount] = useState(post.comment_count || 0);
    const [commentsOpen, setCommentsOpen] = useState(false);
    const [comments, setComments] = useState([]);
    const [commentsLoading, setCommentsLoading] = useState(false);
    const [commentInput, setCommentInput] = useState('');
    const [postingComment, setPostingComment] = useState(false);
    const [confirmingDelete, setConfirmingDelete] = useState(false);
    const navigate = useNavigate();
    const notify = useToast();

    const isOwn = currentUserId && post.author?.id === currentUserId;

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

    const submitComment = async (e) => {
        e.preventDefault();
        const content = commentInput.trim();
        if (!content) return;
        setPostingComment(true);
        try {
            const res = await addComment(post.id, content);
            const newComment = {
                id: res?.data?.id || `tmp-${Date.now()}`,
                author: { id: currentUserId, full_name: 'Bạn', avatar_url: null },
                content,
                created_at: new Date().toISOString(),
            };
            setComments((prev) => [...prev, newComment]);
            setCommentCount((c) => c + 1);
            setCommentInput('');
        } catch (err) {
            notify({ title: 'Không thể gửi bình luận', body: err.message, variant: 'error' });
        } finally {
            setPostingComment(false);
        }
    };

    const handleDelete = async () => {
        try {
            await deletePost(post.id);
            setConfirmingDelete(false);
            onDeleted?.(post.id);
        } catch (err) {
            notify({ title: 'Không thể xoá bài viết', body: err.message, variant: 'error' });
        }
    };

    const goToProfile = () => navigate(`/users/${post.author?.id}`);

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
                {isOwn && (
                    <button type="button" className="post-delete-btn" title="Xoá bài viết" onClick={() => setConfirmingDelete(true)}>
                        ✕
                    </button>
                )}
            </div>

            <p className="post-content">{post.content}</p>

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
                            {comments.length === 0 && <p className="post-comments-empty">Chưa có bình luận nào.</p>}
                            {comments.map((c) => (
                                <div key={c.id} className="post-comment-row">
                                    <Avatar user={c.author} size={28} />
                                    <div className="post-comment-body">
                                        <span className="post-comment-author">{c.author?.full_name || 'Người dùng'}</span>
                                        <span className="post-comment-text">{c.content}</span>
                                        <span className="post-comment-time">{timeAgo(c.created_at)}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                    <form className="post-comment-form" onSubmit={submitComment}>
                        <input
                            className="post-comment-input"
                            placeholder="Viết bình luận..."
                            value={commentInput}
                            onChange={(e) => setCommentInput(e.target.value)}
                            maxLength={1000}
                            disabled={postingComment}
                        />
                        <button type="submit" className="btn btn-secondary" disabled={postingComment || !commentInput.trim()}>
                            Gửi
                        </button>
                    </form>
                </div>
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
        </div>
    );
}
