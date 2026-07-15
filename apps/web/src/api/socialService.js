import { apiClient } from '../utils/apiClient';

/**
 * Bảng tin: bài viết của bản thân + người đang theo dõi, mới nhất trước.
 * Endpoint: GET /feed
 */
export const getFeed = async (page = 0, size = 20) => {
    return await apiClient(`/feed?page=${page}&size=${size}`, { method: 'GET' });
};

/**
 * Đăng bài mới.
 * Endpoint: POST /posts
 */
export const createPost = async (content) => {
    return await apiClient('/posts', {
        method: 'POST',
        body: JSON.stringify({ content }),
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
 * Lấy danh sách bình luận của một bài viết, cũ nhất trước.
 * Endpoint: GET /posts/{id}/comments
 */
export const getComments = async (postId, page = 0, size = 20) => {
    return await apiClient(`/posts/${encodeURIComponent(postId)}/comments?page=${page}&size=${size}`, {
        method: 'GET',
    });
};

/**
 * Thêm bình luận vào bài viết.
 * Endpoint: POST /posts/{id}/comments
 */
export const addComment = async (postId, content) => {
    return await apiClient(`/posts/${encodeURIComponent(postId)}/comments`, {
        method: 'POST',
        body: JSON.stringify({ content }),
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
