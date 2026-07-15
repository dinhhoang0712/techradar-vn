import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchAdminDashboardStats } from '../../api/adminService';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import './AdminDashboard.css';

export default function AdminDashboard() {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const navigate = useNavigate();

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        if (!loading && stats) return; // Prevent multiple loads if already loading or loaded
        
        try {
            setLoading(true);
            setError(null);
            const res = await fetchAdminDashboardStats();
            if(res && res.status === 'success') {
                // Ensure data is in expected format
                const safeData = {
                    totalUsers: res.data?.totalUsers || 0,
                    activeSessions: res.data?.activeSessions || 0,
                    searchesToday: res.data?.searchesToday || 0,
                    topKeywords: Array.isArray(res.data?.topKeywords) ? res.data.topKeywords : [],
                    monthlyVisits: Array.isArray(res.data?.monthlyVisits) ? res.data.monthlyVisits : []
                };
                setStats(safeData);
            } else {
                setError('Dữ liệu trả về từ máy chủ không hợp lệ.');
            }
        } catch (err) {
            console.error('AdminDashboard error:', err);
            if (err.message && err.message.includes('401')) {
                setError('Phiên làm việc hết hạn. Vui lòng đăng nhập lại.');
                setTimeout(() => navigate('/login'), 3000);
            } else {
                setError('Không thể tải dữ liệu thống kê. Vui lòng kiểm tra lại kết nối API.');
            }
        } finally {
            setLoading(false);
        }
    };

    if (loading) {
        return (
            <div className="admin-loading-container">
                <div className="loading-spinner"></div>
                <p>Đang tải dữ liệu hệ thống...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className="admin-error-container">
                <div className="error-icon">⚠️</div>
                <h2>Đã xảy ra lỗi</h2>
                <p>{error}</p>
                <div className="error-actions">
                    <button className="btn btn-secondary" onClick={() => loadData()}>Thử lại</button>
                    <button className="btn btn-primary" onClick={() => navigate('/login')}>Đăng nhập</button>
                </div>
            </div>
        );
    }

    if (!stats) return null;

    return (
        <div className="admin-dashboard">
            <div className="stat-cards">
                <div className="stat-card">
                    <h3>Tổng User</h3>
                    <p className="stat-value">{stats.totalUsers}</p>
                </div>
                <div className="stat-card">
                    <h3>Truy cập hôm nay</h3>
                    <p className="stat-value">{stats.activeSessions}</p>
                </div>
                <div className="stat-card">
                    <h3>Lượt tìm kiếm</h3>
                    <p className="stat-value">{stats.searchesToday}</p>
                </div>
            </div>

            <div className="dashboard-grid">
                <div className="chart-card">
                    <h3>Lưu lượng truy cập hệ thống</h3>
                    <div style={{ width: '100%', height: 350 }}>
                        {stats.monthlyVisits && stats.monthlyVisits.length > 0 ? (
                            <ResponsiveContainer>
                                <LineChart data={stats.monthlyVisits} margin={{top: 20, right: 30, left: 0, bottom: 0}}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                    <XAxis dataKey="month" stroke="var(--text-3)" />
                                    <YAxis stroke="var(--text-3)" />
                                    <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                    <Legend wrapperStyle={{paddingTop: '20px'}}/>
                                    <Line type="monotone" dataKey="count" name="Người dùng truy cập" stroke="var(--primary)" strokeWidth={3} dot={{r: 5, strokeWidth: 2}} activeDot={{r: 8}} />
                                </LineChart>
                            </ResponsiveContainer>
                        ) : (
                            <div className="flex-center chart-empty" style={{height: '100%'}}>Chưa có dữ liệu biểu đồ</div>
                        )}
                    </div>
                </div>

                <div className="keyword-card">
                    <h3>Top từ khóa tìm kiếm</h3>
                    <ul className="keyword-list">
                        {stats.topKeywords && stats.topKeywords.length > 0 ? (
                            stats.topKeywords.map((kw, idx) => (
                                <li key={idx}>
                                    <span className="rank-badge">{idx + 1}</span> 
                                    <span style={{fontWeight: 500}}>
                                        {typeof kw === 'string' ? kw : (kw?.name || kw?.keyword || JSON.stringify(kw))}
                                    </span>
                                </li>
                            ))
                        ) : (
                            <li className="keyword-empty">Không có từ khóa nào</li>
                        )}
                    </ul>
                </div>
            </div>
        </div>
    );
}
