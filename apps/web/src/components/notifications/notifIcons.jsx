// Ánh xạ loại thông báo (type) sang icon + màu hiển thị. Loại chưa biết dùng icon mặc định.
const ICONS = {
    TREND_ALERT: {
        color: 'var(--green)',
        path: (
            <path d="M3 17l6-6 4 4 8-8M15 7h6v6" />
        ),
    },
};

const DEFAULT_ICON = {
    color: 'var(--primary-light)',
    path: (
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" />
    ),
};

export default function NotifIcon({ type }) {
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
