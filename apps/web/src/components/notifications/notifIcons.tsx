import type { ReactNode } from 'react';

interface NotifIconDef {
    color: string;
    path: ReactNode;
}

// Ánh xạ loại thông báo (type) sang icon + màu hiển thị. Loại chưa biết dùng icon mặc định.
const ICONS: Record<string, NotifIconDef> = {
    TREND_ALERT: {
        color: 'var(--green)',
        path: (
            <path d="M3 17l6-6 4 4 8-8M15 7h6v6" />
        ),
    },
    NEW_MESSAGE: {
        color: 'var(--primary)',
        path: (
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        ),
    },
    POST_COMMENT: {
        color: 'var(--primary)',
        path: (
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8z" />
        ),
    },
    POST_LIKE: {
        color: 'var(--danger)',
        path: (
            <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z" />
        ),
    },
    NEW_FOLLOWER: {
        color: 'var(--primary-light)',
        path: (
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
        ),
    },
    JOB_MATCH: {
        color: 'var(--green)',
        path: (
            <path d="M20 7h-4V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2zM10 5h4v2h-4z" />
        ),
    },
};

const DEFAULT_ICON: NotifIconDef = {
    color: 'var(--primary-light)',
    path: (
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" />
    ),
};

export default function NotifIcon({ type }: { type: string }) {
    const icon = ICONS[type] || DEFAULT_ICON;
    return (
        <svg
            className="notif-item-icon"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke={icon.color}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
        >
            {icon.path}
        </svg>
    );
}
