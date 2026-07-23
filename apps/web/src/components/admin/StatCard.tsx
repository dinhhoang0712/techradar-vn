import type { ReactNode } from 'react';

export type StatAccent = 'primary' | 'accent' | 'green' | 'yellow' | 'danger';

interface StatCardProps {
    icon?: string;
    label: string;
    value: ReactNode;
    caption?: ReactNode;
    accent?: StatAccent;
    onClick?: () => void;
    /** 'lg' (default) for numbers; 'md' for text values (e.g. an algorithm name) that would look
     * oversized at the default 2.5rem. */
    valueSize?: 'lg' | 'md';
}

// Stat tile dùng chung cho mọi tab AdminDashboard (Tổng quan/Cộng đồng/Việc làm/Kafka Pipeline/Tin
// nhắn/Chất lượng Model) — thay cho khối <div className="stat-card"><h3>...</h3><p>...</p></div>
// lặp lại y hệt ở từng tab trước đây. Thêm icon + accent màu theo ngữ nghĩa (xanh lá=tốt, vàng=cảnh
// báo, đỏ=lỗi, tím=cộng đồng...) để các tab bớt đơn điệu; truyền onClick để biến tile thành nút bấm
// điều hướng được (VD "Báo cáo chờ duyệt" → trang kiểm duyệt).
export default function StatCard({ icon, label, value, caption, accent = 'primary', onClick, valueSize = 'lg' }: StatCardProps) {
    const className = `stat-card stat-card--accent-${accent}${onClick ? ' stat-card--clickable' : ''}`;
    const content = (
        <>
            <h3>
                {icon && <span className="stat-card-icon" aria-hidden="true">{icon}</span>}
                {label}
            </h3>
            <p className={`stat-value${valueSize === 'md' ? ' stat-value-text' : ''}`}>{value}</p>
            {caption && <p className="stat-card-caption">{caption}</p>}
        </>
    );

    if (onClick) {
        return (
            <button type="button" className={className} onClick={onClick}>
                {content}
            </button>
        );
    }
    return <div className={className}>{content}</div>;
}
