import { useState, useEffect } from 'react';
import { fetchAdminSettings, updateAdminSetting, triggerAnalyticsRebuild } from '../../api/adminService';
import { useToast } from '../../components/common/toastContext';
import './AdminSettings.css';

export default function AdminSettings() {
    const [settings, setSettings] = useState({});
    const [loading, setLoading] = useState(true);
    const [rebuilding, setRebuilding] = useState(false);
    const notify = useToast();

    useEffect(() => {
        loadSettings();
    }, []);

    const loadSettings = async () => {
        try {
            const res = await fetchAdminSettings();
            // Map backend keys to frontend state if they differ
            if (res && res.data) {
                const mapped = {
                    isWebMaintenance: res.data.maintenance_web === 'true' || res.data.maintenance_web === true,
                    isAppMaintenance: res.data.maintenance_mobile === 'true' || res.data.maintenance_mobile === true,
                    isGraphEnabled: res.data.feature_graph === 'true' || res.data.feature_graph === true,
                    isChatEnabled: res.data.feature_chat === 'true' || res.data.feature_chat === true,
                    isRagEnabled: res.data.feature_rag === 'true' || res.data.feature_rag === true,
                };
                setSettings(mapped);
            }
        } catch (error) {
            console.error('Failed to load admin settings:', error);
            notify({ title: 'Không tải được cài đặt hệ thống', body: 'Vui lòng tải lại trang.', variant: 'error' });
        } finally {
            setLoading(false);
        }
    };

    const handleToggleSetting = async (frontendKey, currentValue) => {
        try {
            const newValue = !currentValue;
            // Map frontend key back to backend key
            const keyMap = {
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

    const handleRebuildAnalytics = async () => {
        setRebuilding(true);
        try {
            const res = await triggerAnalyticsRebuild();
            const rows = res?.data?.rows_upserted;
            notify({ title: `Đã chạy lại phân tích thành công${rows != null ? ` (${rows} dòng)` : ''}.`, variant: 'success' });
        } catch (error) {
            console.error('Failed to rebuild analytics:', error);
            notify({ title: 'Chạy lại phân tích thất bại. Vui lòng thử lại.', variant: 'error' });
        } finally {
            setRebuilding(false);
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

            <div className="settings-card">
                <div className="setting-info">
                    <h3>Phân tích dữ liệu (ETL)</h3>
                    <p>Chạy lại ngay việc tổng hợp tech_analytics từ Knowledge Graph, thay vì chờ lịch chạy định kỳ.</p>
                </div>
                <button className="btn btn-secondary" onClick={handleRebuildAnalytics} disabled={rebuilding}>
                    {rebuilding ? 'Đang chạy...' : 'Chạy lại phân tích'}
                </button>
            </div>
        </div>
    );
}
