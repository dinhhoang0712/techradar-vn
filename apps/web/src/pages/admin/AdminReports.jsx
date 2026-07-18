import { useCallback, useEffect, useState } from 'react';
import {
    fetchAdminReports, dismissAdminReport, deleteAdminPost, deleteAdminComment,
    fetchReportAiSuggestion, ADMIN_REPORTS_CHANGED_EVENT,
} from '../../api/adminService';
import Modal from '../../components/common/Modal';
import RingGauge from '../../components/common/RingGauge';
import { useToast } from '../../components/common/toastContext';
import './AdminModeration.css';

const PAGE_SIZE = 20;

function formatDateTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('vi-VN');
}

export default function AdminReports() {
    const [reports, setReports] = useState([]);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(true);
    const [dismissTarget, setDismissTarget] = useState(null);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [suggesting, setSuggesting] = useState({});
    const notify = useToast();

    const loadReports = useCallback(async (targetPage) => {
        try {
            setLoading(true);
            const res = await fetchAdminReports(targetPage, PAGE_SIZE);
            const data = res?.data || [];
            setReports(data);
            setHasMore(data.length === PAGE_SIZE);
        } catch (error) {
            console.error('Failed to load reports:', error);
            notify({ title: 'Không tải được hàng đợi báo cáo', variant: 'error' });
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        loadReports(page);
    }, [page, loadReports]);

    const handleDismiss = async () => {
        try {
            await dismissAdminReport(dismissTarget.id);
            setReports(prev => prev.filter(r => r.id !== dismissTarget.id));
            window.dispatchEvent(new Event(ADMIN_REPORTS_CHANGED_EVENT));
            notify({ title: 'Đã bỏ qua báo cáo', variant: 'success' });
        } catch (error) {
            console.error('Failed to dismiss report:', error);
            notify({ title: 'Không thể xử lý báo cáo', variant: 'error' });
        } finally {
            setDismissTarget(null);
        }
    };

    const handleDeleteContent = async () => {
        try {
            if (deleteTarget.target_type === 'POST') {
                await deleteAdminPost(deleteTarget.post_id);
            } else {
                await deleteAdminComment(deleteTarget.comment_id);
            }
            // Deleting the content cascades away its report server-side too.
            setReports(prev => prev.filter(r => r.id !== deleteTarget.id));
            window.dispatchEvent(new Event(ADMIN_REPORTS_CHANGED_EVENT));
            notify({ title: 'Đã xoá nội dung vi phạm', variant: 'success' });
        } catch (error) {
            console.error('Failed to delete reported content:', error);
            notify({ title: 'Không thể xoá nội dung', variant: 'error' });
        } finally {
            setDeleteTarget(null);
        }
    };

    const handleGetAiSuggestion = async (report, force = false) => {
        setSuggesting(prev => ({ ...prev, [report.id]: true }));
        try {
            const res = await fetchReportAiSuggestion(report.id, force);
            const updated = res?.data;
            if (updated) {
                setReports(prev => prev.map(r => (r.id === report.id ? updated : r)));
            }
        } catch (error) {
            console.error('Failed to get AI suggestion:', error);
            notify({ title: 'Không lấy được gợi ý AI', variant: 'error' });
        } finally {
            setSuggesting(prev => ({ ...prev, [report.id]: false }));
        }
    };

    // AI suggestion is advisory only — pre-select the modal it recommends, never bypass
    // the confirmation step (deleting content is irreversible).
    const handleApplySuggestion = (report) => {
        if (report.ai_suggested_action === 'REMOVE') {
            setDeleteTarget(report);
        } else {
            setDismissTarget(report);
        }
    };

    return (
        <div className="admin-moderation">
            <div className="moderation-header">
                <div className="moderation-title">
                    <h2>Hàng đợi báo cáo</h2>
                    <p>Xem xét các bài viết, bình luận bị người dùng báo cáo vi phạm.</p>
                </div>
            </div>

            <div className="moderation-card card">
                <table className="moderation-table">
                    <thead>
                        <tr>
                            <th>Người báo cáo</th>
                            <th>Loại</th>
                            <th>Nội dung bị báo cáo</th>
                            <th>Lý do</th>
                            <th>Gợi ý AI</th>
                            <th>Thời gian</th>
                            <th>Hành động</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading && (
                            <tr><td colSpan={7} className="moderation-state-cell">Đang tải hàng đợi báo cáo…</td></tr>
                        )}
                        {!loading && reports.length === 0 && (
                            <tr><td colSpan={7} className="moderation-state-cell">Không có báo cáo nào đang chờ xử lý 🎉</td></tr>
                        )}
                        {!loading && reports.map(r => (
                            <tr key={r.id}>
                                <td>{r.reporter_name}</td>
                                <td>
                                    <span className={`report-type-badge ${r.target_type === 'POST' ? 'post' : 'comment'}`}>
                                        {r.target_type === 'POST' ? 'Bài viết' : 'Bình luận'}
                                    </span>
                                </td>
                                <td className="post-content-cell" title={r.target_content || ''}>
                                    {r.target_content || <em>Nội dung đã bị xoá</em>}
                                    <div className="report-target-author">bởi {r.target_author_name || 'không rõ'}</div>
                                </td>
                                <td className="report-reason-cell" title={r.reason}>{r.reason}</td>
                                <td className="ai-suggestion-cell">
                                    {!r.ai_suggested_action && (
                                        <button
                                            className="m-btn view"
                                            disabled={!!suggesting[r.id]}
                                            onClick={() => handleGetAiSuggestion(r)}
                                        >
                                            {suggesting[r.id] ? 'Đang phân tích…' : 'Gợi ý AI'}
                                        </button>
                                    )}
                                    {r.ai_suggested_action && (
                                        <>
                                            <span className={`ai-action-badge ${r.ai_suggested_action === 'REMOVE' ? 'remove' : 'dismiss'}`}>
                                                {r.ai_suggested_action === 'REMOVE' ? 'Nên xoá' : 'Nên bỏ qua'}
                                            </span>
                                            <RingGauge
                                                percent={(r.ai_confidence || 0) * 100}
                                                size={32}
                                                strokeWidth={3}
                                                label={Math.round((r.ai_confidence || 0) * 100)}
                                            />
                                            <span className="ai-suggestion-reason" title={r.ai_suggested_reason || ''}>
                                                {r.ai_suggested_reason}
                                            </span>
                                            <button
                                                className="m-btn view"
                                                disabled={!!suggesting[r.id]}
                                                title="Phân tích lại"
                                                onClick={() => handleGetAiSuggestion(r, true)}
                                            >
                                                🔄
                                            </button>
                                        </>
                                    )}
                                </td>
                                <td className="post-time">{formatDateTime(r.created_at)}</td>
                                <td className="m-actions">
                                    {r.ai_suggested_action && (
                                        <button className="m-btn view" onClick={() => handleApplySuggestion(r)}>Áp dụng gợi ý</button>
                                    )}
                                    <button className="m-btn del" onClick={() => setDeleteTarget(r)}>Xoá nội dung</button>
                                    <button className="m-btn view" onClick={() => setDismissTarget(r)}>Bỏ qua</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="moderation-pagination">
                <button className="btn btn-ghost" disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}>
                    ‹ Trang trước
                </button>
                <span className="pagination-page">Trang {page + 1}</span>
                <button className="btn btn-ghost" disabled={!hasMore || loading} onClick={() => setPage(p => p + 1)}>
                    Trang sau ›
                </button>
            </div>

            {dismissTarget && (
                <Modal title="Bỏ qua báo cáo" onClose={() => setDismissTarget(null)} width="380px">
                    <p className="modal-body-text">
                        Xác nhận bỏ qua báo cáo này (không phát hiện vi phạm)? Nội dung sẽ được giữ nguyên.
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setDismissTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-primary" onClick={handleDismiss}>Bỏ qua</button>
                    </div>
                </Modal>
            )}

            {deleteTarget && (
                <Modal title="Xác nhận xoá nội dung vi phạm" onClose={() => setDeleteTarget(null)} width="420px">
                    <p className="modal-body-text">
                        Xoá {deleteTarget.target_type === 'POST' ? 'bài viết' : 'bình luận'} này của "{deleteTarget.target_author_name}"?
                        Hành động này không thể hoàn tác.
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setDeleteTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={handleDeleteContent}>Xoá</button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
