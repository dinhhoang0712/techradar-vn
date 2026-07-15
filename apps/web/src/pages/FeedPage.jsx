import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { getFeed, createPost, getSuggestedUsers, followUser } from '../api/socialService';
import { getUserProfile } from '../api/userService';
import { useToast } from '../components/common/toastContext';
import Avatar from '../components/common/Avatar';
import PostCard from '../components/social/PostCard';
import './FeedPage.css';

const PAGE_SIZE = 20;

function SuggestedUserRow({ user, onFollowed }) {
    const [following, setFollowing] = useState(false);
    const [busy, setBusy] = useState(false);
    const navigate = useNavigate();
    const notify = useToast();

    const handleFollow = async () => {
        setBusy(true);
        try {
            await followUser(user.id);
            setFollowing(true);
            onFollowed?.(user.id);
        } catch (err) {
            notify({ title: 'Không thể theo dõi', body: err.message, variant: 'error' });
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="suggested-user-row">
            <button type="button" className="suggested-user-info" onClick={() => navigate(`/users/${user.id}`)}>
                <Avatar user={user} size={32} />
                <span className="suggested-user-name">{user.full_name}</span>
            </button>
            <button
                type="button"
                className="btn btn-secondary suggested-follow-btn"
                onClick={handleFollow}
                disabled={busy || following}
            >
                {following ? 'Đã theo dõi' : 'Theo dõi'}
            </button>
        </div>
    );
}

export default function FeedPage() {
    const [currentUserId, setCurrentUserId] = useState(null);
    const [content, setContent] = useState('');
    const [posting, setPosting] = useState(false);
    const [posts, setPosts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [nextPage, setNextPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [suggested, setSuggested] = useState([]);
    const [suggestedLoading, setSuggestedLoading] = useState(true);
    const notify = useToast();

    useEffect(() => {
        getUserProfile()
            .then((res) => {
                const data = res?.data ?? res ?? {};
                setCurrentUserId(data.id ?? data.user?.id ?? null);
            })
            .catch(() => {});
    }, []);

    const loadFeed = useCallback(async () => {
        setLoading(true);
        setError(false);
        try {
            const res = await getFeed(0, PAGE_SIZE);
            const list = res?.data ?? [];
            setPosts(list);
            setNextPage(1);
            setHasMore(list.length === PAGE_SIZE);
        } catch {
            setError(true);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadFeed();
    }, [loadFeed]);

    useEffect(() => {
        getSuggestedUsers(8)
            .then((res) => setSuggested(res?.data ?? []))
            .catch(() => setSuggested([]))
            .finally(() => setSuggestedLoading(false));
    }, []);

    const handlePost = async (e) => {
        e.preventDefault();
        const trimmed = content.trim();
        if (!trimmed) return;
        setPosting(true);
        try {
            const res = await createPost(trimmed);
            const newPost = {
                id: res?.data?.id || `tmp-${Date.now()}`,
                author: { id: currentUserId, full_name: 'Bạn', avatar_url: null },
                content: trimmed,
                created_at: new Date().toISOString(),
                like_count: 0,
                comment_count: 0,
                liked_by_me: false,
            };
            setPosts((prev) => [newPost, ...prev]);
            setContent('');
        } catch (err) {
            notify({ title: 'Không thể đăng bài', body: err.message, variant: 'error' });
        } finally {
            setPosting(false);
        }
    };

    const loadMore = async () => {
        setLoadingMore(true);
        try {
            const res = await getFeed(nextPage, PAGE_SIZE);
            const list = res?.data ?? [];
            setPosts((prev) => [...prev, ...list]);
            setNextPage((o) => o + 1);
            setHasMore(list.length === PAGE_SIZE);
        } catch {
            notify({ title: 'Không tải thêm được bài viết', variant: 'error' });
        } finally {
            setLoadingMore(false);
        }
    };

    const handleDeleted = (postId) => {
        setPosts((prev) => prev.filter((p) => p.id !== postId));
    };

    const markFollowed = (userId) => {
        setSuggested((prev) => prev.filter((u) => u.id !== userId));
    };

    return (
        <div className="feed-page">
            <div className="feed-main">
                <div className="card feed-composer">
                    <form onSubmit={handlePost}>
                        <textarea
                            className="form-input feed-composer-input"
                            placeholder="Bạn đang nghĩ gì về công nghệ hôm nay?"
                            value={content}
                            onChange={(e) => setContent(e.target.value)}
                            maxLength={2000}
                            rows={3}
                            disabled={posting}
                        />
                        <div className="feed-composer-footer">
                            <span className="feed-composer-count">{content.length}/2000</span>
                            <button type="submit" className="btn btn-primary" disabled={posting || !content.trim()}>
                                {posting ? 'Đang đăng...' : 'Đăng bài'}
                            </button>
                        </div>
                    </form>
                </div>

                {loading ? (
                    <div className="feed-state">Đang tải bảng tin...</div>
                ) : error ? (
                    <div className="feed-state">
                        <span>Không tải được bảng tin.</span>
                        <button className="btn btn-ghost mt-16" onClick={loadFeed}>Thử lại</button>
                    </div>
                ) : posts.length === 0 ? (
                    <div className="feed-state">
                        Chưa có bài viết nào. Theo dõi thêm người dùng ở bên phải hoặc tự đăng bài đầu tiên!
                    </div>
                ) : (
                    <>
                        <div className="feed-list">
                            {posts.map((post) => (
                                <PostCard key={post.id} post={post} currentUserId={currentUserId} onDeleted={handleDeleted} />
                            ))}
                        </div>
                        {hasMore && (
                            <div className="feed-loadmore">
                                <button className="btn btn-ghost" onClick={loadMore} disabled={loadingMore}>
                                    {loadingMore ? 'Đang tải…' : 'Tải thêm'}
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>

            <div className="feed-sidebar">
                <div className="card suggested-card">
                    <h3 className="section-title">Gợi ý theo dõi</h3>
                    {suggestedLoading ? (
                        <p className="suggested-empty-hint">Đang tải gợi ý...</p>
                    ) : suggested.length === 0 ? (
                        <p className="suggested-empty-hint">Không có gợi ý nào lúc này.</p>
                    ) : (
                        <div className="suggested-user-list">
                            {suggested.map((u) => (
                                <SuggestedUserRow key={u.id} user={u} onFollowed={markFollowed} />
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
