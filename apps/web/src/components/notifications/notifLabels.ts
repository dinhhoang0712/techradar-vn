// Ánh xạ loại thông báo (type) sang nhãn tiếng Việt hiển thị (legend/tooltip biểu đồ, danh sách...).
const LABELS: Record<string, string> = {
    NEW_MESSAGE: 'Tin nhắn mới',
    POST_COMMENT: 'Bình luận bài viết',
    COMMENT_REPLY: 'Trả lời bình luận',
    POST_LIKE: 'Lượt thích bài viết',
    NEW_FOLLOWER: 'Người theo dõi mới',
    POST_MENTION: 'Được nhắc đến',
    JOB_MATCH: 'Việc làm phù hợp',
    JOB_MATCH_LEARNING: 'Việc làm phù hợp kỹ năng đang học',
    TREND_ALERT: 'Cảnh báo xu hướng công nghệ',
    CAREER_ALERT: 'Gợi ý lộ trình sự nghiệp',
    ADMIN_ANNOUNCEMENT: 'Thông báo từ quản trị viên',
    ADMIN_JOB_DONE: 'Tác vụ nền hoàn tất',
    ADMIN_JOB_FAILED: 'Tác vụ nền thất bại',
    ADMIN_JOB_REPEATED_FAILURE: 'Tác vụ nền thất bại liên tiếp',
    ADMIN_CRAWL_DONE: 'Thu thập dữ liệu hoàn tất',
    ADMIN_CLUSTERING_DONE: 'Phân cụm công nghệ hoàn tất',
    ADMIN_CLUSTERING_FAILED: 'Phân cụm công nghệ thất bại',
    ADMIN_ANALYTICS_REBUILD_DONE: 'Xây dựng lại phân tích hoàn tất',
    ADMIN_ANALYTICS_REBUILD_FAILED: 'Xây dựng lại phân tích thất bại',
    // Giá trị cũ còn sót lại trong dữ liệu trước khi có quy ước ADMIN_* hiện tại.
    SYSTEM: 'Hệ thống',
};

// Loại chưa biết: chuyển "SOME_TYPE" thành "Some type" thay vì hiển thị nguyên enum viết hoa.
function fallbackLabel(type: string): string {
    const words = type.toLowerCase().replace(/_/g, ' ');
    return words.charAt(0).toUpperCase() + words.slice(1);
}

export function labelOf(type: string): string {
    return LABELS[type.toUpperCase()] ?? fallbackLabel(type);
}
