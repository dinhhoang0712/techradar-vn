import { useCallback, useEffect, useState } from 'react';
import { fetchAuditLog } from '../../api/adminService';
import type { AuditLogEntry } from '../../types/admin';
import { useToast } from '../../components/common/toastContext';
import './AdminModeration.css';

const PAGE_SIZE = 50;

function formatDateTime(iso?: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('vi-VN');
}

function actionLabel(action: string): string {
    const labels: Record<string, string> = {
        USER_CREATE: 'Tạo người dùng',
        USER_UPDATE: 'Cập nhật người dùng',
        USER_DELETE: 'Xóa người dùng',
        POST_DELETE: 'Xóa bài viết',
        COMMENT_DELETE: 'Xóa bình luận',
        REPORT_DISMISS: 'Bỏ qua báo cáo',
        CLUSTER_LABEL_OVERRIDE: 'Đổi nhãn cụm công nghệ',
        CLUSTERING_PIPELINE_TRIGGER: 'Kích hoạt huấn luyện lại cụm',
        CRAWLER_TRIGGER: 'Kích hoạt crawler',
        DATA_PLATFORM_JOB_TRIGGER: 'Kích hoạt job dữ liệu',
        ANALYTICS_REBUILD: 'Rebuild phân tích xu hướng',
        GRAPH_ANALYTICS_REBUILD: 'Rebuild phân tích đồ thị',
        NOTIFICATION_SEND: 'Gửi thông báo',
    };
    return labels[action] || action;
}

export default function AdminAuditLog() {
    const [entries, setEntries] = useState<AuditLogEntry[]>([]);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(true);
    const notify = useToast();

    const loadEntries = useCallback(async (targetPage: number) => {
        try {
            setLoading(true);
            const res = await fetchAuditLog(targetPage, PAGE_SIZE);
            const data = res?.data || [];
            setEntries(data);
            setHasMore(data.length === PAGE_SIZE);
        } catch (error) {
            console.error('Failed to load audit log:', error);
            notify({ title: 'Không tải được nhật ký thao tác', variant: 'error' });
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        loadEntries(page);
    }, [page, loadEntries]);

    return (
        <div className="admin-moderation">
            <div className="moderation-header">
                <div className="moderation-title">
                    <h2>Nhật ký thao tác</h2>
                    <p>Lịch sử các hành động quản trị: quản lý người dùng, kiểm duyệt nội dung, vận hành pipeline.</p>
                </div>
            </div>

            <div className="moderation-card card">
                <table className="moderation-table">
                    <thead>
                        <tr>
                            <th>Thời gian</th>
                            <th>Người thực hiện</th>
                            <th>Hành động</th>
                            <th>Đối tượng</th>
                            <th>Chi tiết</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading && (
                            <tr><td colSpan={5} className="moderation-state-cell">Đang tải nhật ký…</td></tr>
                        )}
                        {!loading && entries.length === 0 && (
                            <tr><td colSpan={5} className="moderation-state-cell">Chưa có thao tác nào được ghi nhận</td></tr>
                        )}
                        {!loading && entries.map(entry => (
                            <tr key={entry.id}>
                                <td className="post-time">{formatDateTime(entry.created_at)}</td>
                                <td>{entry.actor_email || entry.actor_id}</td>
                                <td>{actionLabel(entry.action)}</td>
                                <td>
                                    {entry.target_type && (
                                        <span>{entry.target_type}{entry.target_id ? ` #${entry.target_id.slice(0, 8)}` : ''}</span>
                                    )}
                                </td>
                                <td className="report-reason-cell" title={entry.details}>{entry.details || ''}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="moderation-pagination">
                <button className="btn btn-ghost" disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}>‹ Trang trước</button>
                <span className="pagination-page">Trang {page + 1}</span>
                <button className="btn btn-ghost" disabled={!hasMore || loading} onClick={() => setPage(p => p + 1)}>Trang sau ›</button>
            </div>
        </div>
    );
}
