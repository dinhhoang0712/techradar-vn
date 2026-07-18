import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getNotifications,
    getUnreadCount,
    markNotificationRead,
    markAllNotificationsRead,
    streamNotifications,
} from '../../api/notificationService';
import { useToast } from '../common/toastContext';
import NotifIcon from './notifIcons';
import { timeAgo } from '../../utils/timeAgo';
import './NotificationBell.css';

function exactTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('vi-VN');
}

export default function NotificationBell() {
    const [items, setItems] = useState([]);
    const [unread, setUnread] = useState(0);
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [pulse, setPulse] = useState(false);
    const wrapRef = useRef(null);
    const openRef = useRef(false);
    const navigate = useNavigate();
    const showToast = useToast();

    openRef.current = open;

    const refresh = useCallback(async () => {
        setError(false);
        try {
            const [list, count] = await Promise.all([getNotifications(), getUnreadCount()]);
            setItems(Array.isArray(list) ? list : []);
            setUnread(Number(count) || 0);
        } catch (err) {
            console.warn('[NotificationBell] load failed:', err);
            setError(true);
        } finally {
            setLoading(false);
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
                if (!n.read) {
                    setUnread((c) => c + 1);
                    setPulse(true);
                    setTimeout(() => setPulse(false), 700);
                }
                if (!openRef.current) {
                    showToast({
                        title: n.title,
                        body: n.body,
                        variant: 'info',
                        onClick: () => {
                            if (!n.read) markNotificationRead(n.id).catch(() => {});
                            if (n.link) navigate(n.link);
                        },
                    });
                }
            },
            (err) => console.warn('[NotificationBell] stream error:', err),
        );
        return () => controller.abort();
    }, [refresh, showToast, navigate]);

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
            try {
                await markNotificationRead(n.id);
            } catch {
                setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: false } : x)));
                setUnread((c) => c + 1);
                showToast({ title: 'Không thể đánh dấu đã đọc', body: 'Vui lòng thử lại.', variant: 'error' });
            }
        }
        setOpen(false);
        if (n.link) navigate(n.link);
    };

    const handleMarkAll = async () => {
        const previousItems = items;
        const previousUnread = unread;
        setItems((prev) => prev.map((x) => ({ ...x, read: true })));
        setUnread(0);
        try {
            await markAllNotificationsRead();
        } catch {
            setItems(previousItems);
            setUnread(previousUnread);
            showToast({ title: 'Không thể đánh dấu tất cả đã đọc', body: 'Vui lòng thử lại.', variant: 'error' });
        }
    };

    const handleViewAll = () => {
        setOpen(false);
        navigate('/notifications');
    };

    if (!localStorage.getItem('access_token')) return null;

    return (
        <div className="notif-wrap" ref={wrapRef}>
            <button
                className={`notif-bell${open ? ' active' : ''}${pulse ? ' pulse' : ''}`}
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
                        {loading ? (
                            <div className="notif-skeleton">
                                {[0, 1, 2].map((i) => (
                                    <div className="notif-skel-item" key={i}>
                                        <span className="notif-skel-icon" />
                                        <span className="notif-skel-lines">
                                            <span className="notif-skel-line w-70" />
                                            <span className="notif-skel-line w-40" />
                                        </span>
                                    </div>
                                ))}
                            </div>
                        ) : error ? (
                            <div className="notif-error">
                                <span>Không tải được thông báo.</span>
                                <button className="notif-retry" onClick={() => { setLoading(true); refresh(); }}>
                                    Thử lại
                                </button>
                            </div>
                        ) : items.length === 0 ? (
                            <div className="notif-empty">Chưa có thông báo</div>
                        ) : (
                            items.map((n) => (
                                <button
                                    key={n.id}
                                    className={`notif-item${n.read ? '' : ' unread'}`}
                                    onClick={() => handleItemClick(n)}
                                >
                                    <span className="notif-item-icon-wrap">
                                        <NotifIcon type={n.type} />
                                    </span>
                                    <div className="notif-item-body">
                                        <div className="notif-item-title">{n.title}</div>
                                        {n.body && <div className="notif-item-text">{n.body}</div>}
                                        <div className="notif-item-time" title={exactTime(n.created_at)}>
                                            {timeAgo(n.created_at)}
                                        </div>
                                    </div>
                                    {!n.read && <span className="notif-dot" />}
                                </button>
                            ))
                        )}
                    </div>
                    <div className="notif-foot">
                        <button className="notif-viewall" onClick={handleViewAll}>Xem tất cả</button>
                    </div>
                </div>
            )}
        </div>
    );
}
