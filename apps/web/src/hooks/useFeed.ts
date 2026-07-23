import { useState, useEffect, useCallback, useRef } from 'react';
import { getFeed, streamFeed } from '../api/socialService';
import type { Post } from '../types/social';

const PAGE_SIZE = 20;

interface UseFeedResult {
    posts: Post[];
    loading: boolean;
    error: boolean;
    hasMore: boolean;
    loadingMore: boolean;
    loadFeed: () => Promise<void>;
    loadMore: () => Promise<boolean>;
    addPost: (post: Post) => void;
    removePost: (postId: string) => void;
}

/**
 * Danh sách bài viết của Bảng tin: tải trang đầu khi `scope`/`hashtagFilter` đổi, hỗ trợ "Tải thêm",
 * và nhận cập nhật realtime (bài mới/like/comment) qua SSE — dùng chung logic fetch+phân trang+stream
 * để FeedPage không phải tự quản lý 6 state rời rạc.
 */
export function useFeed(scope: string, hashtagFilter: string | null, currentUserId: string | null): UseFeedResult {
    const [posts, setPosts] = useState<Post[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [nextPage, setNextPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);

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

    // currentUserId/hashtagFilter đọc qua ref bên trong handler SSE để tránh phải reconnect stream
    // (effect bên dưới) mỗi khi 2 giá trị này đổi — chỉ `scope` mới cần reconnect vì stream được server
    // scope theo đúng tham số này, giống hệt GET /feed.
    const currentUserIdRef = useRef<string | null>(null);
    useEffect(() => {
        currentUserIdRef.current = currentUserId;
    }, [currentUserId]);

    const hashtagFilterRef = useRef<string | null>(null);
    useEffect(() => {
        hashtagFilterRef.current = hashtagFilter;
    }, [hashtagFilter]);

    useEffect(() => {
        const handleLiveEvent = (event: import('../types/social').FeedLiveEvent) => {
            if (event.type === 'POST_CREATED') {
                if (!event.post || event.post.author?.id === currentUserIdRef.current) return; // already added optimistically
                const activeHashtag = hashtagFilterRef.current;
                if (activeHashtag && !event.post.hashtags?.includes(activeHashtag)) return;
                const newPost = event.post;
                setPosts((prev) => (prev.some((p) => p.id === newPost.id) ? prev : [newPost, ...prev]));
                return;
            }
            if (event.type === 'POST_LIKED') {
                setPosts((prev) => prev.map((p) => (p.id === event.post_id ? { ...p, like_count: event.like_count ?? p.like_count } : p)));
                return;
            }
            if (event.type === 'COMMENT_ADDED') {
                setPosts((prev) => prev.map((p) => (p.id === event.post_id ? { ...p, comment_count: event.comment_count ?? p.comment_count } : p)));
            }
        };
        const controller = streamFeed(scope, handleLiveEvent);
        return () => controller.abort();
    }, [scope]);

    const loadMore = useCallback(async (): Promise<boolean> => {
        setLoadingMore(true);
        try {
            const res = await getFeed(nextPage, PAGE_SIZE, { scope, hashtag: hashtagFilter });
            const list = res?.data ?? [];
            setPosts((prev) => [...prev, ...list]);
            setNextPage((o) => o + 1);
            setHasMore(list.length === PAGE_SIZE);
            return true;
        } catch {
            return false;
        } finally {
            setLoadingMore(false);
        }
    }, [nextPage, scope, hashtagFilter]);

    const addPost = useCallback((post: Post) => setPosts((prev) => [post, ...prev]), []);
    const removePost = useCallback((postId: string) => setPosts((prev) => prev.filter((p) => p.id !== postId)), []);

    return { posts, loading, error, hasMore, loadingMore, loadFeed, loadMore, addPost, removePost };
}
