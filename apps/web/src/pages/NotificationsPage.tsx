import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getNotifications,
    getUnreadCount,
    markNotificationRead,
    markAllNotificationsRead,
    streamNotifications,
} from '../api/notificationService';
import type { Notification } from '../types/notification';
import NotifIcon from '../components/notifications/notifIcons';
import { severityOf } from '../components/notifications/notifSeverity';
import { useToast } from '../components/common/toastContext';
import './NotificationsPage.css';

const PAGE_SIZE = 20;

function formatDateTime(iso?: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('vi-VN');
}

export default function NotificationsPage() {
    const [items, setItems] = useState<Notification[]>([]);
    const [offset, setOffset] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [error, setError] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const navigate = useNavigate();
    const notify = useToast();

    const refreshUnreadCount = useCallback(async () => {
        try {
            const count = await getUnreadCount();
            setUnreadCount(Number(count) || 0);
        } catch {
            // giữ nguyên giá trị cũ nếu gọi API thất bại
        }
    }, []);

    const loadFirstPage = useCallback(async () => {
        setLoading(true);
        setError(false);
        try {
            const [list] = await Promise.all([
                getNotifications({ limit: PAGE_SIZE, offset: 0 }),
                refreshUnreadCount(),
            ]);
            setItems(Array.isArray(list) ? list : []);
            setOffset(list.length);
            setHasMore(list.length === PAGE_SIZE);
        } catch (err) {
            console.warn('[NotificationsPage] load failed:', err);
            setError(true);
        } finally {
            setLoading(false);
        }
    }, [refreshUnreadCount]);

    useEffect(() => {
        loadFirstPage();
    }, [loadFirstPage]);

    // Live push — cùng cơ chế với NotificationBell, để danh sách trên trang này
    // tự cập nhật khi có thông báo mới, không cần tải lại trang.
    useEffect(() => {
        const controller = streamNotifications(
            (n) => {
                setItems((prev) => (prev.some((x) => x.id === n.id) ? prev : [n, ...prev]));
                if (!n.read) setUnreadCount((c) => c + 1);
            },
            (err) => console.warn('[NotificationsPage] stream error:', err),
        );
        return () => controller.abort();
    }, []);

    const loadMore = async () => {
        setLoadingMore(true);
        try {
            const list = await getNotifications({ limit: PAGE_SIZE, offset });
            setItems((prev) => [...prev, ...(Array.isArray(list) ? list : [])]);
            setOffset((o) => o + list.length);
            setHasMore(list.length === PAGE_SIZE);
        } catch (err) {
            console.warn('[NotificationsPage] load more failed:', err);
            notify({ title: 'Không tải thêm được thông báo', body: 'Vui lòng thử lại.', variant: 'error' });
        } finally {
            setLoadingMore(false);
        }
    };

    const handleItemClick = async (n: Notification) => {
        if (!n.read) {
            setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
            setUnreadCount((c) => Math.max(0, c - 1));
            try {
                await markNotificationRead(n.id);
            } catch {
                setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: false } : x)));
                setUnreadCount((c) => c + 1);
                notify({ title: 'Không thể đánh dấu đã đọc', body: 'Vui lòng thử lại.', variant: 'error' });
            }
        }
        if (n.link) navigate(n.link);
    };

    const handleMarkAll = async () => {
        const previousItems = items;
        const previousUnread = unreadCount;
        setItems((prev) => prev.map((x) => ({ ...x, read: true })));
        setUnreadCount(0);
        try {
            await markAllNotificationsRead();
        } catch {
            setItems(previousItems);
            setUnreadCount(previousUnread);
            notify({ title: 'Không thể đánh dấu tất cả đã đọc', body: 'Vui lòng thử lại.', variant: 'error' });
        }
    };

    return (
        <div className="notifpage">
            <div className="notifpage-header">
                <div>
                    <h2 className="section-title">Thông báo</h2>
                    <p className="text-2 text-sm">Toàn bộ thông báo hệ thống gửi cho bạn.</p>
                </div>
                {unreadCount > 0 && (
                    <button className="btn btn-secondary" onClick={handleMarkAll}>
                        Đánh dấu tất cả đã đọc
                    </button>
                )}
            </div>

            <div className="notifpage-card card">
                {loading ? (
                    <div className="notifpage-list">
                        {[0, 1, 2, 3].map((i) => (
                            <div className="notifpage-item notifpage-item--skeleton" key={i}>
                                <span className="notifpage-item-icon skeleton" />
                                <div className="notifpage-item-body">
                                    <div className="skeleton notifpage-skel-line notifpage-skel-line--title" />
                                    <div className="skeleton notifpage-skel-line notifpage-skel-line--text" />
                                </div>
                            </div>
                        ))}
                    </div>
                ) : error ? (
                    <div className="notifpage-state">
                        <span>Không tải được thông báo.</span>
                        <button className="btn btn-ghost mt-16" onClick={loadFirstPage}>Thử lại</button>
                    </div>
                ) : items.length === 0 ? (
                    <div className="notifpage-state">Chưa có thông báo nào.</div>
                ) : (
                    <>
                        <div className="notifpage-list">
                            {items.map((n) => (
                                <button
                                    key={n.id}
                                    className={`notifpage-item notifpage-item--${severityOf(n.type)}${n.read ? '' : ' unread'}`}
                                    onClick={() => handleItemClick(n)}
                                >
                                    <span className={`notifpage-item-icon${n.read ? '' : ' gradient-ring active'}`}>
                                        <NotifIcon type={n.type} />
                                    </span>
                                    <div className="notifpage-item-body">
                                        <div className="notifpage-item-title">{n.title}</div>
                                        {n.body && <div className="notifpage-item-text">{n.body}</div>}
                                        <div className="notifpage-item-time">{formatDateTime(n.created_at)}</div>
                                    </div>
                                    {!n.read && <span className="notif-dot" />}
                                </button>
                            ))}
                        </div>
                        {hasMore && (
                            <div className="notifpage-loadmore">
                                <button className="btn btn-ghost" onClick={loadMore} disabled={loadingMore}>
                                    {loadingMore ? 'Đang tải…' : 'Tải thêm'}
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
