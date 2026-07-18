import { apiClient } from '../utils/apiClient';

const API_BASE_URL = '/api/v1';

/**
 * Bảng tin: bài viết của bản thân + người đang theo dõi (mặc định), hoặc mọi bài viết công khai
 * (scope='explore'), mới nhất trước. Có thể lọc theo hashtag.
 * Endpoint: GET /feed
 */
export const getFeed = async (page = 0, size = 20, { scope, hashtag } = {}) => {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);
    if (scope) params.set('scope', scope);
    if (hashtag) params.set('hashtag', hashtag);
    return await apiClient(`/feed?${params.toString()}`, { method: 'GET' });
};

/**
 * Bảng tin thời gian thực (SSE) — bài viết mới, số lượt thích/bình luận cập nhật trực tiếp.
 * Cùng "scope" như GET /feed: server tự lọc bài viết mới theo người đang theo dõi.
 * Dùng fetch-based SSE (không phải EventSource) để gắn được header Authorization.
 * Trả về AbortController; gọi .abort() khi unmount hoặc khi scope đổi để mở lại đúng scope mới.
 * Endpoint: GET /feed/stream
 *
 *   onEvent(event)  — { type: 'POST_CREATED'|'POST_LIKED'|'COMMENT_ADDED', post_id, post?, like_count?, comment_count? }
 *   onError(err)    — lỗi hẳn (bỏ qua AbortError; mất kết nối tạm thời tự reconnect, xem RECONNECT_DELAY_MS)
 */
const RECONNECT_DELAY_MS = 3000;

export const streamFeed = (scope, onEvent, onError) => {
    const controller = new AbortController();

    (async () => {
        while (!controller.signal.aborted) {
            try {
                const token = localStorage.getItem('access_token');
                const res = await fetch(`${API_BASE_URL}/feed/stream?scope=${encodeURIComponent(scope || 'following')}`, {
                    headers: {
                        Accept: 'text/event-stream',
                        ...(token ? { Authorization: `Bearer ${token}` } : {}),
                    },
                    signal: controller.signal,
                });
                if (!res.ok || !res.body) {
                    if (res.status === 401) {
                        onError?.(new Error('SSE 401'));
                        return;
                    }
                    throw new Error(`SSE ${res.status}`);
                }

                const reader = res.body.getReader();
                const decoder = new TextDecoder('utf-8');
                let buffer = '';

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break; // server đóng kết nối (vd idle timeout) — reconnect bên dưới
                    buffer += decoder.decode(value, { stream: true });

                    const lines = buffer.split('\n');
                    buffer = lines.pop(); // giữ lại phần chưa hoàn chỉnh

                    for (const line of lines) {
                        if (!line || line.startsWith(':')) continue; // heartbeat comment
                        if (line.startsWith('event:')) continue;     // event type — xử lý ở data
                        if (line.startsWith('data:')) {
                            const raw = line.slice(5).trimStart();
                            try {
                                onEvent(JSON.parse(raw));
                            } catch {
                                /* dòng data không phải JSON — bỏ qua */
                            }
                        }
                    }
                }
            } catch (err) {
                if (err?.name === 'AbortError') return;
                onError?.(err);
            }
            if (controller.signal.aborted) return;
            await new Promise((resolve) => setTimeout(resolve, RECONNECT_DELAY_MS));
        }
    })();

    return controller;
};

/**
 * Đăng bài mới, có thể kèm ảnh (tối đa 4), gắn thẻ công ty, và nhắc (@mention) người dùng khác.
 * Endpoint: POST /posts
 * @param {string} content
 * @param {{images?: {contentType: string, dataBase64: string}[], taggedCompanyId?: string, mentionedUserIds?: string[]}} [opts]
 */
export const createPost = async (content, { images, taggedCompanyId, mentionedUserIds } = {}) => {
    return await apiClient('/posts', {
        method: 'POST',
        body: JSON.stringify({
            content,
            images: images?.map((img) => ({ content_type: img.contentType, data_base64: img.dataBase64 })),
            tagged_company_id: taggedCompanyId,
            mentioned_user_ids: mentionedUserIds,
        }),
    });
};

/**
 * Xoá bài viết của chính mình.
 * Endpoint: DELETE /posts/{id}
 */
export const deletePost = async (id) => {
    return await apiClient(`/posts/${encodeURIComponent(id)}`, { method: 'DELETE' });
};

/**
 * Thích bài viết. Gọi lại nhiều lần không lỗi (idempotent).
 * Endpoint: POST /posts/{id}/like
 */
export const likePost = async (id) => {
    return await apiClient(`/posts/${encodeURIComponent(id)}/like`, { method: 'POST' });
};

/**
 * Bỏ thích bài viết.
 * Endpoint: DELETE /posts/{id}/like
 */
export const unlikePost = async (id) => {
    return await apiClient(`/posts/${encodeURIComponent(id)}/like`, { method: 'DELETE' });
};

/**
 * Lấy danh sách bình luận của một bài viết (danh sách phẳng, cũ nhất trước — mỗi item có
 * parent_id để nhóm lại thành cây bình luận ở phía client, xem utils/comments.js).
 * Endpoint: GET /posts/{id}/comments
 */
export const getComments = async (postId, page = 0, size = 20) => {
    return await apiClient(`/posts/${encodeURIComponent(postId)}/comments?page=${page}&size=${size}`, {
        method: 'GET',
    });
};

/**
 * Thêm bình luận vào bài viết, hoặc trả lời một bình luận cấp cao nhất (parentId).
 * Endpoint: POST /posts/{id}/comments
 * @param {string} postId
 * @param {string} content
 * @param {{parentId?: string, mentionedUserIds?: string[]}} [opts]
 */
export const addComment = async (postId, content, { parentId, mentionedUserIds } = {}) => {
    return await apiClient(`/posts/${encodeURIComponent(postId)}/comments`, {
        method: 'POST',
        body: JSON.stringify({ content, parent_id: parentId, mentioned_user_ids: mentionedUserIds }),
    });
};

/**
 * Báo cáo một bài viết vi phạm để admin xem xét. Gọi lại nhiều lần không tạo báo cáo trùng.
 * Endpoint: POST /posts/{id}/report
 */
export const reportPost = async (id, reason) => {
    return await apiClient(`/posts/${encodeURIComponent(id)}/report`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
    });
};

/**
 * Báo cáo một bình luận vi phạm để admin xem xét.
 * Endpoint: POST /comments/{id}/report
 */
export const reportComment = async (id, reason) => {
    return await apiClient(`/comments/${encodeURIComponent(id)}/report`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
    });
};

/**
 * Thông tin hồ sơ công khai của một người dùng (bio, follower/following, is_following).
 * Endpoint: GET /users/{id}/profile-summary
 */
export const getProfileSummary = async (userId) => {
    return await apiClient(`/users/${encodeURIComponent(userId)}/profile-summary`, { method: 'GET' });
};

/**
 * Danh sách bài viết của một người dùng (dùng cho trang hồ sơ công khai).
 * Endpoint: GET /users/{id}/posts
 */
export const getUserPosts = async (userId, page = 0, size = 20) => {
    return await apiClient(`/users/${encodeURIComponent(userId)}/posts?page=${page}&size=${size}`, {
        method: 'GET',
    });
};

/**
 * Theo dõi một người dùng.
 * Endpoint: POST /users/{id}/follow
 */
export const followUser = async (userId) => {
    return await apiClient(`/users/${encodeURIComponent(userId)}/follow`, { method: 'POST' });
};

/**
 * Bỏ theo dõi một người dùng.
 * Endpoint: DELETE /users/{id}/follow
 */
export const unfollowUser = async (userId) => {
    return await apiClient(`/users/${encodeURIComponent(userId)}/follow`, { method: 'DELETE' });
};

/**
 * Gợi ý người dùng để theo dõi (chưa theo dõi, xếp hạng theo số follower).
 * Endpoint: GET /users/suggested
 */
export const getSuggestedUsers = async (limit = 10) => {
    return await apiClient(`/users/suggested?limit=${limit}`, { method: 'GET' });
};

/**
 * Tìm người dùng theo tên (một phần) — dùng cho ô chọn @mention.
 * Endpoint: GET /users/search
 */
export const searchUsers = async (q, limit = 8) => {
    if (!q || !q.trim()) return { data: [] };
    return await apiClient(`/users/search?q=${encodeURIComponent(q)}&limit=${limit}`, { method: 'GET' });
};

/**
 * Hashtag nổi bật (thịnh hành) trong 7 ngày gần nhất.
 * Endpoint: GET /hashtags/trending
 */
export const getTrendingHashtags = async (limit = 10) => {
    return await apiClient(`/hashtags/trending?limit=${limit}`, { method: 'GET' });
};
