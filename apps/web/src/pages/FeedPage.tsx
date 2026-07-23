import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getSuggestedUsers, followUser, getTrendingHashtags } from '../api/socialService';
import type { TrendingHashtag } from '../api/socialService';
import { getUserProfile } from '../api/userService';
import { useToast } from '../components/common/toastContext';
import { useFeed } from '../hooks/useFeed';
import Avatar from '../components/common/Avatar';
import PostCard from '../components/social/PostCard';
import PostComposer from '../components/social/PostComposer';
import type { PostAuthor } from '../types/social';
import type { UserProfileData } from '../types/userProfile';
import './FeedPage.css';

interface SuggestedUserRowProps {
    user: PostAuthor;
    onFollowed?: (userId: string) => void;
}

function SuggestedUserRow({ user, onFollowed }: SuggestedUserRowProps) {
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
            notify({ title: 'Không thể theo dõi', body: (err as Error).message, variant: 'error' });
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
    const [currentUserId, setCurrentUserId] = useState<string | null>(null);
    const [currentUser, setCurrentUser] = useState<PostAuthor | null>(null);
    const [scope, setScope] = useState('following');
    const [hashtagFilter, setHashtagFilter] = useState<string | null>(null);
    const [suggested, setSuggested] = useState<PostAuthor[]>([]);
    const [suggestedLoading, setSuggestedLoading] = useState(true);
    const [trending, setTrending] = useState<TrendingHashtag[]>([]);
    const [trendingLoading, setTrendingLoading] = useState(true);
    const notify = useToast();

    const { posts, loading, error, hasMore, loadingMore, loadFeed, loadMore, addPost, removePost } =
        useFeed(scope, hashtagFilter, currentUserId);

    useEffect(() => {
        getUserProfile()
            .then((res) => {
                const data: UserProfileData = ('data' in res ? res.data : res) ?? {};
                const rawUser = data.user ?? data;
                setCurrentUser(rawUser?.id ? { id: rawUser.id, full_name: rawUser.full_name } : null);
                setCurrentUserId(rawUser?.id ?? null);
            })
            .catch(() => {});
    }, []);

    useEffect(() => {
        getSuggestedUsers(8)
            .then((res) => setSuggested(res?.data ?? []))
            .catch(() => setSuggested([]))
            .finally(() => setSuggestedLoading(false));
    }, []);

    useEffect(() => {
        getTrendingHashtags(10)
            .then((res) => setTrending(res?.data ?? []))
            .catch(() => setTrending([]))
            .finally(() => setTrendingLoading(false));
    }, []);

    const handleLoadMore = async () => {
        const ok = await loadMore();
        if (!ok) notify({ title: 'Không tải thêm được bài viết', variant: 'error' });
    };

    const markFollowed = (userId: string) => {
        setSuggested((prev) => prev.filter((u) => u.id !== userId));
    };

    return (
        <div className="feed-page-wrap">
            <div className="feed-page-header">
                <h1 className="feed-page-title">Bảng tin</h1>
                <p className="feed-page-subtitle">Cập nhật mới nhất từ cộng đồng công nghệ bạn theo dõi</p>
            </div>
            <div className="feed-page">
                <div className="feed-main">
                    <PostComposer currentUser={currentUser} currentUserId={currentUserId} onPosted={addPost} />

                    <div className="feed-scope-toggle pill-group">
                        <button
                            type="button"
                            className={`pill${scope === 'following' ? ' active' : ''}`}
                            onClick={() => setScope('following')}
                        >
                            Dành cho bạn
                        </button>
                        <button
                            type="button"
                            className={`pill${scope === 'explore' ? ' active' : ''}`}
                            onClick={() => setScope('explore')}
                        >
                            Khám phá
                        </button>
                        {hashtagFilter && (
                            <button type="button" className="pill active feed-hashtag-filter-pill" onClick={() => setHashtagFilter(null)}>
                                #{hashtagFilter} ✕
                            </button>
                        )}
                    </div>

                    {loading ? (
                        <div className="feed-list">
                            {[0, 1, 2].map((i) => (
                                <div className="card post-skeleton" key={i}>
                                    <div className="post-skeleton-header">
                                        <div className="skeleton post-skeleton-avatar" />
                                        <div className="post-skeleton-lines">
                                            <div className="skeleton post-skeleton-line" style={{ width: '35%' }} />
                                            <div className="skeleton post-skeleton-line" style={{ width: '20%' }} />
                                        </div>
                                    </div>
                                    <div className="skeleton post-skeleton-line" style={{ width: '92%', height: 12 }} />
                                    <div className="skeleton post-skeleton-line" style={{ width: '68%', height: 12 }} />
                                </div>
                            ))}
                        </div>
                    ) : error ? (
                        <div className="card feed-state">
                            <div className="feed-state-icon-wrap">
                                <span className="feed-state-icon" aria-hidden="true">⚠️</span>
                            </div>
                            <span>Không tải được bảng tin.</span>
                            <button className="btn btn-ghost mt-16" onClick={loadFeed}>Thử lại</button>
                        </div>
                    ) : posts.length === 0 ? (
                        <div className="card feed-state">
                            <div className="feed-state-icon-wrap">
                                <span className="feed-state-icon" aria-hidden="true">📰</span>
                            </div>
                            Chưa có bài viết nào. Theo dõi thêm người dùng ở bên phải hoặc tự đăng bài đầu tiên!
                        </div>
                    ) : (
                        <>
                            <div className="feed-list">
                                {posts.map((post) => (
                                    <PostCard
                                        key={post.id}
                                        post={post}
                                        currentUserId={currentUserId}
                                        onDeleted={removePost}
                                        onHashtagClick={setHashtagFilter}
                                    />
                                ))}
                            </div>
                            {hasMore && (
                                <div className="feed-loadmore">
                                    <button className="btn btn-ghost" onClick={handleLoadMore} disabled={loadingMore}>
                                        {loadingMore ? 'Đang tải…' : 'Tải thêm'}
                                    </button>
                                </div>
                            )}
                        </>
                    )}
                </div>

                <div className="feed-sidebar">
                    <div className="card trending-card">
                        <h3 className="section-title"><span className="icon">🔥</span> Chủ đề thịnh hành</h3>
                        {trendingLoading ? (
                            <p className="suggested-empty-hint">Đang tải...</p>
                        ) : trending.length === 0 ? (
                            <p className="suggested-empty-hint">Chưa có chủ đề nổi bật.</p>
                        ) : (
                            <div className="pill-group">
                                {trending.map((h) => (
                                    <button
                                        key={h.tag}
                                        type="button"
                                        className={`pill${hashtagFilter === h.tag ? ' active' : ''}`}
                                        onClick={() => setHashtagFilter(h.tag)}
                                    >
                                        #{h.tag}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="card suggested-card">
                        <h3 className="section-title"><span className="icon">✨</span> Gợi ý theo dõi</h3>
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
        </div>
    );
}
