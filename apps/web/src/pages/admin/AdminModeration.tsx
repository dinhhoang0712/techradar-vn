import { useCallback, useEffect, useState } from 'react';
import {
    fetchAdminPosts,
    deleteAdminPost,
    fetchAdminPostComments,
    deleteAdminComment,
} from '../../api/adminService';
import type { AdminModerationPost, AdminModerationComment } from '../../types/admin';
import Modal from '../../components/common/Modal';
import { useToast } from '../../components/common/toastContext';
import './AdminModeration.css';

const PAGE_SIZE = 20;

function formatDateTime(iso?: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('vi-VN');
}

function initials(name?: string): string {
    if (!name) return '?';
    return name.trim().charAt(0).toUpperCase();
}

export default function AdminModeration() {
    const [posts, setPosts] = useState<AdminModerationPost[]>([]);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(true);
    const [deletePostTarget, setDeletePostTarget] = useState<AdminModerationPost | null>(null);

    const [commentsPost, setCommentsPost] = useState<AdminModerationPost | null>(null); // post whose comments are shown in modal
    const [comments, setComments] = useState<AdminModerationComment[]>([]);
    const [commentsLoading, setCommentsLoading] = useState(false);
    const [deleteCommentTarget, setDeleteCommentTarget] = useState<AdminModerationComment | null>(null);

    const notify = useToast();

    const loadPosts = useCallback(async (targetPage: number) => {
        try {
            setLoading(true);
            const res = await fetchAdminPosts(targetPage, PAGE_SIZE);
            const data = res?.data || [];
            setPosts(data);
            setHasMore(data.length === PAGE_SIZE);
        } catch (error) {
            console.error('Failed to load posts:', error);
            notify({ title: 'Không tải được danh sách bài viết', variant: 'error' });
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        loadPosts(page);
    }, [page, loadPosts]);

    const handleDeletePost = async () => {
        if (!deletePostTarget) return;
        try {
            await deleteAdminPost(deletePostTarget.id);
            setPosts(prev => prev.filter(p => p.id !== deletePostTarget.id));
            notify({ title: 'Đã xoá bài viết', variant: 'success' });
        } catch (error) {
            console.error('Failed to delete post:', error);
            notify({ title: 'Không thể xoá bài viết', variant: 'error' });
        } finally {
            setDeletePostTarget(null);
        }
    };

    const openComments = async (post: AdminModerationPost) => {
        setCommentsPost(post);
        setCommentsLoading(true);
        try {
            const res = await fetchAdminPostComments(post.id, 0, 50);
            setComments(res?.data || []);
        } catch (error) {
            console.error('Failed to load comments:', error);
            notify({ title: 'Không tải được bình luận', variant: 'error' });
        } finally {
            setCommentsLoading(false);
        }
    };

    const handleDeleteComment = async () => {
        if (!deleteCommentTarget || !commentsPost) return;
        try {
            await deleteAdminComment(deleteCommentTarget.id);
            setComments(prev => prev.filter(c => c.id !== deleteCommentTarget.id));
            setPosts(prev => prev.map(p =>
                p.id === commentsPost.id ? { ...p, comment_count: Math.max(0, p.comment_count - 1) } : p
            ));
            notify({ title: 'Đã xoá bình luận', variant: 'success' });
        } catch (error) {
            console.error('Failed to delete comment:', error);
            notify({ title: 'Không thể xoá bình luận', variant: 'error' });
        } finally {
            setDeleteCommentTarget(null);
        }
    };

    return (
        <div className="admin-moderation">
            <div className="moderation-header">
                <div className="moderation-title">
                    <h2>Kiểm duyệt nội dung</h2>
                    <p>Xem và xử lý bài viết, bình luận vi phạm trên bảng tin cộng đồng.</p>
                </div>
            </div>

            <div className="moderation-card card">
                <table className="moderation-table">
                    <thead>
                        <tr>
                            <th>Tác giả</th>
                            <th>Nội dung</th>
                            <th>Thích</th>
                            <th>Bình luận</th>
                            <th>Đăng lúc</th>
                            <th>Hành động</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading && (
                            <tr><td colSpan={6} className="moderation-state-cell">Đang tải danh sách bài viết…</td></tr>
                        )}
                        {!loading && posts.length === 0 && (
                            <tr><td colSpan={6} className="moderation-state-cell">Không có bài viết nào</td></tr>
                        )}
                        {!loading && posts.map(p => (
                            <tr key={p.id}>
                                <td>
                                    <div className="post-author">
                                        {p.author_avatar_url
                                            ? <img className="post-avatar" src={p.author_avatar_url} alt={p.author_name} />
                                            : <span className="post-avatar post-avatar-fallback">{initials(p.author_name)}</span>}
                                        <span>{p.author_name}</span>
                                    </div>
                                </td>
                                <td className="post-content-cell" title={p.content}>{p.content}</td>
                                <td>{p.like_count}</td>
                                <td>{p.comment_count}</td>
                                <td className="post-time">{formatDateTime(p.created_at)}</td>
                                <td className="m-actions">
                                    <button className="m-btn view" onClick={() => openComments(p)}>
                                        Xem bình luận
                                    </button>
                                    <button className="m-btn del" onClick={() => setDeletePostTarget(p)}>
                                        Xoá
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="moderation-pagination">
                <button className="btn btn-ghost" disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}>
                    ‹ Trang trước
                </button>
                <span className="pagination-page">Trang {page + 1}</span>
                <button className="btn btn-ghost" disabled={!hasMore || loading} onClick={() => setPage(p => p + 1)}>
                    Trang sau ›
                </button>
            </div>

            {commentsPost && (
                <Modal
                    title={`Bình luận trên bài viết của ${commentsPost.author_name}`}
                    onClose={() => setCommentsPost(null)}
                    width="560px"
                >
                    <div className="comments-modal-body">
                        {commentsLoading && <p className="moderation-state-cell">Đang tải bình luận…</p>}
                        {!commentsLoading && comments.length === 0 && (
                            <p className="moderation-state-cell">Bài viết này chưa có bình luận nào.</p>
                        )}
                        {!commentsLoading && comments.map(c => (
                            <div className="comment-row" key={c.id}>
                                <div className="post-author">
                                    {c.author_avatar_url
                                        ? <img className="post-avatar sm" src={c.author_avatar_url} alt={c.author_name} />
                                        : <span className="post-avatar sm post-avatar-fallback">{initials(c.author_name)}</span>}
                                    <div>
                                        <div className="comment-author-name">{c.author_name}</div>
                                        <div className="comment-content">{c.content}</div>
                                        <div className="comment-time">{formatDateTime(c.created_at)}</div>
                                    </div>
                                </div>
                                <button className="m-btn del" onClick={() => setDeleteCommentTarget(c)}>Xoá</button>
                            </div>
                        ))}
                    </div>
                </Modal>
            )}

            {deletePostTarget && (
                <Modal title="Xác nhận xoá bài viết" onClose={() => setDeletePostTarget(null)} width="380px">
                    <p className="modal-body-text">
                        Bạn có chắc chắn muốn xoá bài viết này của "{deletePostTarget.author_name}"? Hành động này không thể hoàn tác.
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setDeletePostTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={handleDeletePost}>Xoá</button>
                    </div>
                </Modal>
            )}

            {deleteCommentTarget && (
                <Modal title="Xác nhận xoá bình luận" onClose={() => setDeleteCommentTarget(null)} width="380px">
                    <p className="modal-body-text">
                        Bạn có chắc chắn muốn xoá bình luận này của "{deleteCommentTarget.author_name}"? Hành động này không thể hoàn tác.
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setDeleteCommentTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={handleDeleteComment}>Xoá</button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
