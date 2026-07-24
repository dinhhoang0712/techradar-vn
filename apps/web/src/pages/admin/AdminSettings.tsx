import { useState, useEffect } from 'react';
import { fetchAdminSettings, updateAdminSetting, deleteAdminSetting } from '../../api/adminService';
import type { AdminSettingsMap, AdminSettingRow } from '../../types/admin';
import { useToast } from '../../components/common/toastContext';
import Modal from '../../components/common/Modal';
import './AdminSettings.css';

// 5 khóa đã có công tắc riêng bên dưới — không cho xóa qua danh sách "Cài đặt khác" để tránh
// người dùng vô tình xóa mất một feature flag đang được UI quản lý.
const MANAGED_KEYS = ['maintenance_web', 'maintenance_mobile', 'feature_graph', 'feature_chat', 'feature_rag'];

export default function AdminSettings() {
    const [settings, setSettings] = useState<AdminSettingsMap>({});
    const [otherSettings, setOtherSettings] = useState<AdminSettingRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [deleteTarget, setDeleteTarget] = useState<AdminSettingRow | null>(null);
    const [deleting, setDeleting] = useState(false);

    const notify = useToast();

    useEffect(() => {
        loadSettings();
        // Mount-only: loadSettings is recreated every render, adding it here would refetch on every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const loadSettings = async () => {
        try {
            const res = await fetchAdminSettings();
            // res.data is an array of {key, value} rows (ApiResponse<List<AppSettings>>), not a flat object
            if (res && Array.isArray(res.data)) {
                const byKey = Object.fromEntries(res.data.map((s) => [s.key, s.value]));
                const mapped: AdminSettingsMap = {
                    isWebMaintenance: byKey.maintenance_web === 'true' || byKey.maintenance_web === true,
                    isAppMaintenance: byKey.maintenance_mobile === 'true' || byKey.maintenance_mobile === true,
                    isGraphEnabled: byKey.feature_graph === 'true' || byKey.feature_graph === true,
                    isChatEnabled: byKey.feature_chat === 'true' || byKey.feature_chat === true,
                    isRagEnabled: byKey.feature_rag === 'true' || byKey.feature_rag === true,
                };
                setSettings(mapped);
                setOtherSettings(res.data.filter((row) => !MANAGED_KEYS.includes(row.key)));
            }
        } catch (error) {
            console.error('Failed to load admin settings:', error);
            notify({ title: 'Không tải được cài đặt hệ thống', body: 'Vui lòng tải lại trang.', variant: 'error' });
        } finally {
            setLoading(false);
        }
    };

    const handleToggleSetting = async (frontendKey: keyof AdminSettingsMap, currentValue: boolean | undefined) => {
        try {
            const newValue = !currentValue;
            // Map frontend key back to backend key
            const keyMap: Record<keyof AdminSettingsMap, string> = {
                isWebMaintenance: 'maintenance_web',
                isAppMaintenance: 'maintenance_mobile',
                isGraphEnabled: 'feature_graph',
                isChatEnabled: 'feature_chat',
                isRagEnabled: 'feature_rag'
            };

            const backendKey = keyMap[frontendKey];
            // Send as string "true"/"false" to match Swagger example
            await updateAdminSetting(backendKey, String(newValue));

            setSettings(prev => ({ ...prev, [frontendKey]: newValue }));
        } catch (error) {
            console.error(`Failed to update setting:`, error);
            notify({ title: 'Không thể cập nhật cài đặt. Vui lòng thử lại.', variant: 'error' });
        }
    };

    const handleDeleteSetting = async () => {
        if (!deleteTarget) return;
        setDeleting(true);
        try {
            await deleteAdminSetting(deleteTarget.key);
            setOtherSettings(prev => prev.filter(row => row.key !== deleteTarget.key));
            notify({ title: `Đã xóa cài đặt "${deleteTarget.key}"`, variant: 'success' });
        } catch (error) {
            console.error('Failed to delete setting:', error);
            notify({ title: 'Không thể xóa cài đặt. Vui lòng thử lại.', variant: 'error' });
        } finally {
            setDeleting(false);
            setDeleteTarget(null);
        }
    };

    if (loading) return <div className="admin-settings-loading"><div className="loading-spinner" /><span>Đang tải cài đặt...</span></div>;

    return (
        <div className="admin-settings">
            <div className="settings-header">
                <h2>Cài đặt Hệ thống</h2>
                <p>Điều khiển các cờ trạng thái (Feature Flags) của ứng dụng qua API.</p>
            </div>

            <div className="settings-card danger-zone">
                <div className="setting-info">
                    <h3>Chế độ Bảo trì Website</h3>
                    <p>Đóng toàn bộ màn hình truy cập của người dùng Web.</p>
                </div>
                <label className="switch danger">
                    <input type="checkbox" checked={settings.isWebMaintenance || false} onChange={() => handleToggleSetting('isWebMaintenance', settings.isWebMaintenance)} />
                    <span className="slider"></span>
                </label>
            </div>

            <div className="settings-card danger-zone">
                <div className="setting-info">
                    <h3>Chế độ Bảo trì App Mobile</h3>
                    <p>Chặn truy cập đối với phiên bản ứng dụng di động.</p>
                </div>
                <label className="switch danger">
                    <input type="checkbox" checked={settings.isAppMaintenance || false} onChange={() => handleToggleSetting('isAppMaintenance', settings.isAppMaintenance)} />
                    <span className="slider"></span>
                </label>
            </div>

            <div className="settings-card">
                <div className="setting-info">
                    <h3>Tính năng Graph Explorer</h3>
                    <p>Bật/tắt nút và luồng truy xuất dữ liệu Knowledge Graph.</p>
                </div>
                <label className="switch">
                    <input type="checkbox" checked={settings.isGraphEnabled !== false} onChange={() => handleToggleSetting('isGraphEnabled', settings.isGraphEnabled)} />
                    <span className="slider"></span>
                </label>
            </div>

            <div className="settings-card">
                <div className="setting-info">
                    <h3>Tính năng AI RAG</h3>
                    <p>Bật/tắt các tính năng AI sử dụng hệ thống RAG.</p>
                </div>
                <label className="switch">
                    <input type="checkbox" checked={settings.isRagEnabled !== false} onChange={() => handleToggleSetting('isRagEnabled', settings.isRagEnabled)} />
                    <span className="slider"></span>
                </label>
            </div>

            {otherSettings.length > 0 && (
                <div className="settings-card">
                    <div className="setting-info">
                        <h3>Cài đặt khác</h3>
                        <p>Các cài đặt khác không thuộc 5 cờ trạng thái quản lý ở trên. Có thể xóa nếu là dữ liệu thừa hoặc gõ sai key.</p>
                        <ul className="other-settings-list">
                            {otherSettings.map(row => (
                                <li key={row.key} className="other-settings-row">
                                    <span><strong>{row.key}</strong>: {String(row.value)}</span>
                                    <button className="btn btn-ghost" onClick={() => setDeleteTarget(row)}>Xóa</button>
                                </li>
                            ))}
                        </ul>
                    </div>
                </div>
            )}

            {deleteTarget && (
                <Modal title="Xác nhận xóa cài đặt" onClose={() => setDeleteTarget(null)} width="380px">
                    <p className="modal-body-text">
                        Xóa cài đặt "{deleteTarget.key}"? Hành động này không thể hoàn tác.
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setDeleteTarget(null)} disabled={deleting}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={handleDeleteSetting} disabled={deleting}>
                            {deleting ? 'Đang xóa...' : 'Xóa'}
                        </button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
