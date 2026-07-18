// Shared relative-time formatter ("vừa xong" / "N phút trước" / ...) — used by PostCard,
// NotificationBell and MessagesPage so they stay in sync instead of each keeping its own copy.
export function timeAgo(iso) {
    if (!iso) return '';
    const then = new Date(iso).getTime();
    if (Number.isNaN(then)) return '';
    const diff = Math.max(0, Date.now() - then) / 1000;
    if (diff < 60) return 'vừa xong';
    if (diff < 3600) return `${Math.floor(diff / 60)} phút trước`;
    if (diff < 86400) return `${Math.floor(diff / 3600)} giờ trước`;
    return `${Math.floor(diff / 86400)} ngày trước`;
}
