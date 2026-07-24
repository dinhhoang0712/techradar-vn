import { useEffect, useState } from 'react';
import { NavLink, Link, useNavigate } from 'react-router-dom';
import { logoutUser } from '../../api/authService';
import {
    fetchSocialDashboard, ADMIN_REPORTS_CHANGED_EVENT,
    fetchTechAliasReviewCount, KG_REVIEW_CHANGED_EVENT,
} from '../../api/adminService';
import Modal from '../common/Modal';
import './AdminSidebar.css';

interface NavItem {
    to: string;
    label: string;
    badgeKey?: 'reports' | 'kgReview';
}

const NAV_ITEMS: NavItem[] = [
    { to: '/admin/dashboard', label: 'Dashboard' },
    { to: '/admin/moderation', label: 'Kiểm duyệt nội dung' },
    { to: '/admin/reports', label: 'Báo cáo vi phạm', badgeKey: 'reports' },
    { to: '/admin/kg-review', label: 'Duyệt Knowledge Graph', badgeKey: 'kgReview' },
    { to: '/admin/users', label: 'Quản lý người dùng' },
    { to: '/admin/cms', label: 'Quản lý dữ liệu (CMS)' },
    { to: '/admin/clusters', label: 'Cụm Công nghệ (AI)' },
    { to: '/admin/automation', label: 'Vận hành' },
    { to: '/admin/settings', label: 'Cài đặt hệ thống' },
    { to: '/admin/audit-log', label: 'Nhật ký thao tác' },
];

interface AdminSidebarProps {
    collapsed: boolean;
    onToggle: () => void;
}

export default function AdminSidebar({ collapsed, onToggle }: AdminSidebarProps) {
    const navigate = useNavigate();
    const [confirmingLogout, setConfirmingLogout] = useState(false);
    const [pendingReports, setPendingReports] = useState(0);
    const [pendingKgReview, setPendingKgReview] = useState(0);

    useEffect(() => {
        const loadPendingReports = () => {
            fetchSocialDashboard()
                .then(res => setPendingReports(res?.data?.pending_reports || 0))
                .catch(() => {});
        };
        loadPendingReports();
        const interval = setInterval(loadPendingReports, 30000); // Cùng nhịp với AppContext polling /status
        window.addEventListener(ADMIN_REPORTS_CHANGED_EVENT, loadPendingReports);
        return () => {
            clearInterval(interval);
            window.removeEventListener(ADMIN_REPORTS_CHANGED_EVENT, loadPendingReports);
        };
    }, []);

    useEffect(() => {
        // Chỉ đếm dp_tech_alias_review_queue (Postgres COUNT(*) rẻ) — KHÔNG đếm Company
        // near-duplicate (quét Neo4j on-demand, tốn hơn nhiều), xem KgReviewAdminController.
        const loadPendingKgReview = () => {
            fetchTechAliasReviewCount()
                .then(res => setPendingKgReview(res?.data?.pending || 0))
                .catch(() => {});
        };
        loadPendingKgReview();
        const interval = setInterval(loadPendingKgReview, 30000);
        window.addEventListener(KG_REVIEW_CHANGED_EVENT, loadPendingKgReview);
        return () => {
            clearInterval(interval);
            window.removeEventListener(KG_REVIEW_CHANGED_EVENT, loadPendingKgReview);
        };
    }, []);

    const badgeCount: Record<string, number> = { reports: pendingReports, kgReview: pendingKgReview };

    const confirmLogout = async () => {
        try {
            await logoutUser();
        } catch (error) {
            console.error('Logout API failed:', error);
        } finally {
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            navigate('/login');
        }
    };

    return (
        <aside className={`sidebar${collapsed ? ' collapsed' : ''}`}>
            <div className="sidebar-header">
                {!collapsed && (
                    <div className="sidebar-brand">
                        <span className="brand-name">Admin Portal</span>
                    </div>
                )}
                <button className="collapse-btn" onClick={onToggle} title="Toggle sidebar">
                    {collapsed ? '›' : '‹'}
                </button>
            </div>

            <nav className="sidebar-nav">
                {NAV_ITEMS.map(item => {
                    const count = item.badgeKey ? badgeCount[item.badgeKey] : 0;
                    return (
                        <NavLink
                            key={item.to}
                            to={item.to}
                            className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
                            title={collapsed ? item.label : ''}
                        >
                            {!collapsed && <span className="nav-label">{item.label}</span>}
                            {count > 0 && <span className="nav-badge">{count}</span>}
                        </NavLink>
                    );
                })}
            </nav>

            <div className="sidebar-footer">
                <Link to="/dashboard" className="nav-item" title={collapsed ? 'Về trang người dùng' : ''}>
                    {!collapsed && <span className="nav-label">← Về trang người dùng</span>}
                </Link>
                <button className="logout-btn" onClick={() => setConfirmingLogout(true)}>
                    {!collapsed && <span className="nav-label">Đăng xuất</span>}
                </button>
            </div>

            {confirmingLogout && (
                <Modal title="Xác nhận đăng xuất" onClose={() => setConfirmingLogout(false)} width="380px">
                    <p className="modal-body-text">Bạn có chắc chắn muốn đăng xuất?</p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setConfirmingLogout(false)}>Hủy bỏ</button>
                        <button className="btn btn-primary" onClick={confirmLogout}>Đăng xuất</button>
                    </div>
                </Modal>
            )}
        </aside>
    );
}
