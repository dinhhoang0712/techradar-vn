import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getNotifications,
    getUnreadCount,
    markNotificationRead,
    markAllNotificationsRead,
    streamNotifications,
} from '../../api/notificationService';
import './NotificationBell.css';

function timeAgo(iso) {
    if (!iso) return '';
    const then = new Date(iso).getTime();
    if (Number.isNaN(then)) return '';
    const diff = Math.max(0, Date.now() - then) / 1000;
    if (diff < 60) return 'vừa xong';
    if (diff < 3600) return `${Math.floor(diff / 60)} phút trước`;
    if (diff < 86400) return `${Math.floor(diff / 3600)} giờ trước`;
    return `${Math.floor(diff / 86400)} ngày trước`;
}

export default function NotificationBell() {
    const [items, setItems] = useState([]);
    const [unread, setUnread] = useState(0);
    const [open, setOpen] = useState(false);
    const wrapRef = useRef(null);
    const navigate = useNavigate();

    const refresh = useCallback(async () => {
        try {
            const [list, count] = await Promise.all([getNotifications(), getUnreadCount()]);
            setItems(Array.isArray(list) ? list : []);
            setUnread(Number(count) || 0);
        } catch (err) {
            console.warn('[NotificationBell] load failed:', err);
        }
    }, []);

    // Initial load + realtime SSE stream.
    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (!token) return undefined;

        refresh();
        const controller = streamNotifications(
            (n) => {
                setItems((prev) => [n, ...prev].slice(0, 50));
                if (!n.read) setUnread((c) => c + 1);
            },
            (err) => console.warn('[NotificationBell] stream error:', err),
        );
        return () => controller.abort();
    }, [refresh]);

    // Close dropdown on outside click.
    useEffect(() => {
        function onClick(e) {
            if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
        }
        document.addEventListener('mousedown', onClick);
        return () => document.removeEventListener('mousedown', onClick);
    }, []);

    const handleItemClick = async (n) => {
        if (!n.read) {
            setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
            setUnread((c) => Math.max(0, c - 1));
            try { await markNotificationRead(n.id); } catch { /* optimistic */ }
        }
        setOpen(false);
        if (n.link) navigate(n.link);
    };

    const handleMarkAll = async () => {
        setItems((prev) => prev.map((x) => ({ ...x, read: true })));
        setUnread(0);
        try { await markAllNotificationsRead(); } catch { /* optimistic */ }
    };

    if (!localStorage.getItem('access_token')) return null;

    return (
        <div className="notif-wrap" ref={wrapRef}>
            <button
                className={`notif-bell${open ? ' active' : ''}`}
                title="Thông báo"
                aria-label="Thông báo"
                onClick={() => setOpen((o) => !o)}
            >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                    <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>
                {unread > 0 && <span className="notif-badge">{unread > 99 ? '99+' : unread}</span>}
            </button>

            {open && (
                <div className="notif-dropdown">
                    <div className="notif-head">
                        <span>Thông báo</span>
                        {unread > 0 && (
                            <button className="notif-markall" onClick={handleMarkAll}>
                                Đánh dấu đã đọc
                            </button>
                        )}
                    </div>
                    <div className="notif-list">
                        {items.length === 0 ? (
                            <div className="notif-empty">Chưa có thông báo</div>
                        ) : (
                            items.map((n) => (
                                <button
                                    key={n.id}
                                    className={`notif-item${n.read ? '' : ' unread'}`}
                                    onClick={() => handleItemClick(n)}
                                >
                                    {!n.read && <span className="notif-dot" />}
                                    <div className="notif-item-body">
                                        <div className="notif-item-title">{n.title}</div>
                                        {n.body && <div className="notif-item-text">{n.body}</div>}
                                        <div className="notif-item-time">{timeAgo(n.created_at)}</div>
                                    </div>
                                </button>
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
