import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { getFeed, createPost, getSuggestedUsers, followUser, getTrendingHashtags } from '../api/socialService';
import { getUserProfile } from '../api/userService';
import { useToast } from '../components/common/toastContext';
import Avatar from '../components/common/Avatar';
import PostCard from '../components/social/PostCard';
import MentionTextarea from '../components/social/MentionTextarea';
import CompanyTagPicker from '../components/social/CompanyTagPicker';
import { fileToBase64 } from '../utils/fileToBase64';
import './FeedPage.css';

const PAGE_SIZE = 20;
const MAX_IMAGES = 4;
const MAX_IMAGE_BYTES = 3 * 1024 * 1024;

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
    const [currentUser, setCurrentUser] = useState(null);
    const [content, setContent] = useState('');
    const [mentionedIds, setMentionedIds] = useState([]);
    const [composerImages, setComposerImages] = useState([]);
    const [taggedCompany, setTaggedCompany] = useState(null);
    const [posting, setPosting] = useState(false);
    const [posts, setPosts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [nextPage, setNextPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [scope, setScope] = useState('following');
    const [hashtagFilter, setHashtagFilter] = useState(null);
    const [suggested, setSuggested] = useState([]);
    const [suggestedLoading, setSuggestedLoading] = useState(true);
    const [trending, setTrending] = useState([]);
    const [trendingLoading, setTrendingLoading] = useState(true);
    const [composerFocused, setComposerFocused] = useState(false);
    const notify = useToast();

    useEffect(() => {
        getUserProfile()
            .then((res) => {
                const data = res?.data ?? res ?? {};
                const user = data.user ?? data;
                setCurrentUser(user);
                setCurrentUserId(user?.id ?? null);
            })
            .catch(() => {});
    }, []);

    const loadFeed = useCallback(async () => {
        setLoading(true);
        setError(false);
        try {
            const res = await getFeed(0, PAGE_SIZE, { scope, hashtag: hashtagFilter });
            const list = res?.data ?? [];
            setPosts(list);
            setNextPage(1);
            setHasMore(list.length === PAGE_SIZE);
        } catch {
            setError(true);
        } finally {
            setLoading(false);
        }
    }, [scope, hashtagFilter]);

    useEffect(() => {
        loadFeed();
    }, [loadFeed]);

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

    const handleImageSelect = async (e) => {
        const files = Array.from(e.target.files || []);
        e.target.value = ''; // allow re-selecting the same file
        if (files.length === 0) return;
        if (composerImages.length + files.length > MAX_IMAGES) {
            notify({ title: `Tối đa ${MAX_IMAGES} ảnh mỗi bài viết`, variant: 'error' });
            return;
        }
        const oversized = files.find((f) => f.size > MAX_IMAGE_BYTES);
        if (oversized) {
            notify({ title: 'Ảnh quá lớn (tối đa 3MB mỗi ảnh)', variant: 'error' });
            return;
        }
        try {
            const withData = await Promise.all(files.map(async (file) => ({
                id: `${file.name}-${file.size}-${Date.now()}-${Math.random()}`,
                file,
                dataUrl: await fileToBase64(file),
            })));
            setComposerImages((prev) => [...prev, ...withData]);
        } catch {
            notify({ title: 'Không thể đọc ảnh', variant: 'error' });
        }
    };

    const removeComposerImage = (id) => {
        setComposerImages((prev) => prev.filter((img) => img.id !== id));
    };

    const handlePost = async (e) => {
        e.preventDefault();
        const trimmed = content.trim();
        if (!trimmed) return;
        setPosting(true);
        try {
            const images = composerImages.map((img) => ({ contentType: img.file.type || 'image/png', dataBase64: img.dataUrl }));
            const res = await createPost(trimmed, {
                images,
                taggedCompanyId: taggedCompany?.id,
                mentionedUserIds: mentionedIds,
            });
            const newPost = {
                id: res?.data?.id || `tmp-${Date.now()}`,
                author: { id: currentUserId, full_name: 'Bạn', avatar_url: null },
                content: trimmed,
                created_at: new Date().toISOString(),
                like_count: 0,
                comment_count: 0,
                liked_by_me: false,
                image_urls: composerImages.map((img) => img.dataUrl),
                hashtags: [],
                tagged_company: taggedCompany
                    ? { id: taggedCompany.id, name: taggedCompany.name, location: taggedCompany.location }
                    : null,
            };
            setPosts((prev) => [newPost, ...prev]);
            setContent('');
            setMentionedIds([]);
            setComposerImages([]);
            setTaggedCompany(null);
        } catch (err) {
            notify({ title: 'Không thể đăng bài', body: err.message, variant: 'error' });
        } finally {
            setPosting(false);
        }
    };

    const loadMore = async () => {
        setLoadingMore(true);
        try {
            const res = await getFeed(nextPage, PAGE_SIZE, { scope, hashtag: hashtagFilter });
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
        <div className="feed-page-wrap">
            <div className="feed-page-header">
                <h1 className="feed-page-title">Bảng tin</h1>
                <p className="feed-page-subtitle">Cập nhật mới nhất từ cộng đồng công nghệ bạn theo dõi</p>
            </div>
            <div className="feed-page">
                <div className="feed-main">
                    <div className={`card feed-composer${composerFocused ? ' is-focused' : ''}`}>
                        <form onSubmit={handlePost}>
                            <div className="feed-composer-row">
                                <Avatar user={currentUser} size={40} />
                                <MentionTextarea
                                    as="textarea"
                                    className="feed-composer-input"
                                    placeholder="Bạn đang nghĩ gì về công nghệ hôm nay? Dùng #hashtag hoặc @tên để nhắc ai đó"
                                    value={content}
                                    onChange={setContent}
                                    mentionedUserIds={mentionedIds}
                                    onMentionedUserIdsChange={setMentionedIds}
                                    onFocus={() => setComposerFocused(true)}
                                    onBlur={() => setComposerFocused(false)}
                                    maxLength={2000}
                                    rows={3}
                                    disabled={posting}
                                />
                            </div>

                            {composerImages.length > 0 && (
                                <div className="feed-composer-image-strip">
                                    {composerImages.map((img) => (
                                        <div key={img.id} className="feed-composer-thumb">
                                            <img src={img.dataUrl} alt="" />
                                            <button
                                                type="button"
                                                className="feed-composer-thumb-remove"
                                                onClick={() => removeComposerImage(img.id)}
                                                aria-label="Bỏ ảnh"
                                            >
                                                ✕
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            )}

                            <div className="feed-composer-tools">
                                <label className={`btn btn-ghost feed-composer-tool-btn${composerImages.length >= MAX_IMAGES ? ' is-disabled' : ''}`}>
                                    🖼️ Ảnh
                                    <input
                                        type="file"
                                        accept="image/png,image/jpeg,image/jpg,image/webp,image/gif"
                                        multiple
                                        hidden
                                        onChange={handleImageSelect}
                                        disabled={posting || composerImages.length >= MAX_IMAGES}
                                    />
                                </label>
                                <CompanyTagPicker
                                    selected={taggedCompany}
                                    onSelect={setTaggedCompany}
                                    onClear={() => setTaggedCompany(null)}
                                />
                            </div>

                            <div className="feed-composer-footer">
                                <span className="feed-composer-count">{content.length}/2000</span>
                                <button type="submit" className="btn btn-primary" disabled={posting || !content.trim()}>
                                    {posting ? 'Đang đăng...' : 'Đăng bài'}
                                </button>
                            </div>
                        </form>
                    </div>

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
                                        onDeleted={handleDeleted}
                                        onHashtagClick={setHashtagFilter}
                                    />
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
