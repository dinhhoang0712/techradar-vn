import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    fetchAdminDashboardStats,
    fetchSocialDashboard,
    fetchJobMarketDashboard,
    fetchPipelineDashboard,
    fetchMessagingDashboard,
} from '../../api/adminService';
import {
    LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
    XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { useToast } from '../../components/common/toastContext';
import RingGauge from '../../components/common/RingGauge';
import './AdminDashboard.css';

const TABS = [
    { key: 'overview', label: 'Tổng quan' },
    { key: 'social', label: 'Cộng đồng' },
    { key: 'jobs', label: 'Việc làm & Công nghệ' },
    { key: 'pipeline', label: 'Kafka Pipeline' },
    { key: 'messaging', label: 'Tin nhắn & Thông báo' },
];

const PIE_COLORS = ['var(--primary)', 'var(--green)', 'var(--yellow)', 'var(--danger-light)', 'var(--accent)'];

function formatDateTime(iso) {
    if (!iso) return 'Chưa có dữ liệu';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return 'Chưa có dữ liệu';
    return d.toLocaleString('vi-VN');
}

// Tỷ lệ xử lý thành công = (article + job đã xử lý) / (article + job đã xử lý + lỗi)
function computePipelineSuccessRate(pipeline) {
    if (!pipeline) return 0;
    const processed = (pipeline.articles_processed || 0) + (pipeline.jobs_processed || 0);
    const failed = (pipeline.articles_failed || 0) + (pipeline.jobs_failed || 0);
    const total = processed + failed;
    if (total <= 0) return 100;
    return (processed / total) * 100;
}

export default function AdminDashboard() {
    const [activeTab, setActiveTab] = useState('overview');
    const navigate = useNavigate();
    const notify = useToast();

    // --- Tổng quan (existing) ---
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // --- Cộng đồng ---
    const [social, setSocial] = useState(null);
    const [socialLoading, setSocialLoading] = useState(false);

    // --- Việc làm & Công nghệ ---
    const [jobs, setJobs] = useState(null);
    const [jobsLoading, setJobsLoading] = useState(false);

    // --- Kafka Pipeline ---
    const [pipeline, setPipeline] = useState(null);
    const [pipelineLoading, setPipelineLoading] = useState(false);

    // --- Tin nhắn & Thông báo ---
    const [messaging, setMessaging] = useState(null);
    const [messagingLoading, setMessagingLoading] = useState(false);

    useEffect(() => {
        loadOverview();
        // Mount-only: loadOverview is recreated every render, adding it here would refetch on every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        if (activeTab === 'social' && !social) loadSocial();
        if (activeTab === 'jobs' && !jobs) loadJobs();
        if (activeTab === 'pipeline' && !pipeline) loadPipeline();
        if (activeTab === 'messaging' && !messaging) loadMessaging();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeTab]);

    const loadOverview = async () => {
        if (!loading && stats) return; // Prevent multiple loads if already loading or loaded

        try {
            setLoading(true);
            setError(null);
            const res = await fetchAdminDashboardStats();
            if (res && res.status === 'success') {
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

    const loadSocial = useCallback(async () => {
        try {
            setSocialLoading(true);
            const res = await fetchSocialDashboard();
            setSocial(res?.data || null);
        } catch (err) {
            console.error('Failed to load social dashboard:', err);
            notify({ title: 'Không tải được dữ liệu Cộng đồng', body: 'Vui lòng thử lại.', variant: 'error' });
        } finally {
            setSocialLoading(false);
        }
    }, [notify]);

    const loadJobs = useCallback(async () => {
        try {
            setJobsLoading(true);
            const res = await fetchJobMarketDashboard();
            setJobs(res?.data || null);
        } catch (err) {
            console.error('Failed to load job market dashboard:', err);
            notify({ title: 'Không tải được dữ liệu Việc làm & Công nghệ', body: 'Vui lòng thử lại.', variant: 'error' });
        } finally {
            setJobsLoading(false);
        }
    }, [notify]);

    const loadPipeline = useCallback(async () => {
        try {
            setPipelineLoading(true);
            const res = await fetchPipelineDashboard();
            setPipeline(res?.data || null);
        } catch (err) {
            console.error('Failed to load pipeline dashboard:', err);
            notify({ title: 'Không tải được dữ liệu Kafka Pipeline', body: 'Vui lòng thử lại.', variant: 'error' });
        } finally {
            setPipelineLoading(false);
        }
    }, [notify]);

    const loadMessaging = useCallback(async () => {
        try {
            setMessagingLoading(true);
            const res = await fetchMessagingDashboard();
            setMessaging(res?.data || null);
        } catch (err) {
            console.error('Failed to load messaging dashboard:', err);
            notify({ title: 'Không tải được dữ liệu Tin nhắn & Thông báo', body: 'Vui lòng thử lại.', variant: 'error' });
        } finally {
            setMessagingLoading(false);
        }
    }, [notify]);

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
                    <button className="btn btn-secondary" onClick={() => loadOverview()}>Thử lại</button>
                    <button className="btn btn-primary" onClick={() => navigate('/login')}>Đăng nhập</button>
                </div>
            </div>
        );
    }

    if (!stats) return null;

    const pipelineSuccessRate = pipeline ? computePipelineSuccessRate(pipeline) : 0;

    return (
        <div className="admin-dashboard">
            <div className="dashboard-tabs">
                {TABS.map(tab => (
                    <button
                        key={tab.key}
                        className={`dashboard-tab${activeTab === tab.key ? ' active' : ''}`}
                        onClick={() => setActiveTab(tab.key)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {activeTab === 'overview' && (
                <>
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
                                        <LineChart data={stats.monthlyVisits} margin={{ top: 20, right: 30, left: 0, bottom: 0 }}>
                                            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                            <XAxis dataKey="month" stroke="var(--text-3)" />
                                            <YAxis stroke="var(--text-3)" />
                                            <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                            <Legend wrapperStyle={{ paddingTop: '20px' }} />
                                            <Line type="monotone" dataKey="count" name="Người dùng truy cập" stroke="var(--primary)" strokeWidth={3} dot={{ r: 5, strokeWidth: 2 }} activeDot={{ r: 8 }} />
                                        </LineChart>
                                    </ResponsiveContainer>
                                ) : (
                                    <div className="flex-center chart-empty" style={{ height: '100%' }}>Chưa có dữ liệu biểu đồ</div>
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
                                            <span style={{ fontWeight: 500 }}>
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
                </>
            )}

            {activeTab === 'social' && (
                <TabLoading loading={socialLoading} data={social}>
                    {social && (
                        <>
                            <div className="stat-cards">
                                <div className="stat-card">
                                    <h3>Tổng bài viết</h3>
                                    <p className="stat-value">{social.total_posts}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Bài viết hôm nay</h3>
                                    <p className="stat-value">{social.posts_today}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Tổng bình luận</h3>
                                    <p className="stat-value">{social.total_comments}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Tổng lượt thích</h3>
                                    <p className="stat-value">{social.total_likes}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Tổng lượt follow</h3>
                                    <p className="stat-value">{social.total_follows}</p>
                                </div>
                            </div>

                            <div className="chart-card">
                                <h3>Top người dùng hoạt động nhiều nhất</h3>
                                <div style={{ width: '100%', height: 360 }}>
                                    {social.top_posters && social.top_posters.length > 0 ? (
                                        <ResponsiveContainer>
                                            <BarChart data={social.top_posters} layout="vertical" margin={{ top: 10, right: 30, left: 20, bottom: 0 }}>
                                                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                                <XAxis type="number" stroke="var(--text-3)" allowDecimals={false} />
                                                <YAxis type="category" dataKey="full_name" stroke="var(--text-3)" width={140} />
                                                <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                                <Bar dataKey="post_count" name="Số bài viết" fill="var(--primary)" radius={[0, 6, 6, 0]} />
                                            </BarChart>
                                        </ResponsiveContainer>
                                    ) : (
                                        <div className="flex-center chart-empty" style={{ height: '100%' }}>Chưa có dữ liệu</div>
                                    )}
                                </div>
                            </div>
                        </>
                    )}
                </TabLoading>
            )}

            {activeTab === 'jobs' && (
                <TabLoading loading={jobsLoading} data={jobs}>
                    {jobs && (
                        <>
                            <div className="stat-cards">
                                <div className="stat-card">
                                    <h3>Job đã được index</h3>
                                    <p className="stat-value">{jobs.total_jobs_indexed}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Cảnh báo việc làm phù hợp đã gửi</h3>
                                    <p className="stat-value">{jobs.job_match_alerts_sent}</p>
                                </div>
                            </div>

                            <div className="chart-card">
                                <h3>Top công nghệ được yêu cầu nhiều nhất</h3>
                                <div style={{ width: '100%', height: 380 }}>
                                    {jobs.top_technologies && jobs.top_technologies.length > 0 ? (
                                        <ResponsiveContainer>
                                            <BarChart data={jobs.top_technologies} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
                                                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                                <XAxis dataKey="name" stroke="var(--text-3)" interval={0} angle={-30} textAnchor="end" height={70} />
                                                <YAxis stroke="var(--text-3)" allowDecimals={false} />
                                                <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                                <Bar dataKey="job_count" name="Số lượng job yêu cầu" fill="var(--accent)" radius={[6, 6, 0, 0]} />
                                            </BarChart>
                                        </ResponsiveContainer>
                                    ) : (
                                        <div className="flex-center chart-empty" style={{ height: '100%' }}>Chưa có dữ liệu</div>
                                    )}
                                </div>
                            </div>
                        </>
                    )}
                </TabLoading>
            )}

            {activeTab === 'pipeline' && (
                <TabLoading loading={pipelineLoading} data={pipeline}>
                    {pipeline && (
                        <>
                            <div className={`pipeline-banner ${pipeline.last_failure_at ? 'warning' : 'healthy'}`}>
                                <RingGauge
                                    percent={pipelineSuccessRate}
                                    size={44}
                                    strokeWidth={4}
                                    label={`${Math.round(pipelineSuccessRate)}%`}
                                    className="pipeline-banner-gauge"
                                />
                                <div>
                                    <strong>{pipeline.last_failure_at ? 'Có lỗi đồng bộ gần đây' : 'Pipeline đang hoạt động ổn định'}</strong>
                                    {pipeline.last_failure_at && (
                                        <p>
                                            Lần lỗi gần nhất: {formatDateTime(pipeline.last_failure_at)}
                                            {pipeline.last_failure_message ? ` — ${pipeline.last_failure_message}` : ''}
                                        </p>
                                    )}
                                </div>
                            </div>

                            <div className="stat-cards">
                                <div className="stat-card">
                                    <h3>Article đã xử lý</h3>
                                    <p className="stat-value">{pipeline.articles_processed}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Article lỗi</h3>
                                    <p className="stat-value">{pipeline.articles_failed}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Job đã xử lý</h3>
                                    <p className="stat-value">{pipeline.jobs_processed}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Job lỗi</h3>
                                    <p className="stat-value">{pipeline.jobs_failed}</p>
                                </div>
                            </div>

                            <div className="chart-card">
                                <h3>Thời gian đồng bộ gần nhất</h3>
                                <div className="pipeline-timestamps">
                                    <div className="pipeline-timestamp-row">
                                        <span>Article gần nhất</span>
                                        <strong>{formatDateTime(pipeline.last_article_processed_at)}</strong>
                                    </div>
                                    <div className="pipeline-timestamp-row">
                                        <span>Job gần nhất</span>
                                        <strong>{formatDateTime(pipeline.last_job_processed_at)}</strong>
                                    </div>
                                </div>
                            </div>
                        </>
                    )}
                </TabLoading>
            )}

            {activeTab === 'messaging' && (
                <TabLoading loading={messagingLoading} data={messaging}>
                    {messaging && (
                        <>
                            <div className="stat-cards">
                                <div className="stat-card">
                                    <h3>Tổng cuộc trò chuyện</h3>
                                    <p className="stat-value">{messaging.total_conversations}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Tổng tin nhắn</h3>
                                    <p className="stat-value">{messaging.total_messages}</p>
                                </div>
                                <div className="stat-card">
                                    <h3>Tin nhắn hôm nay</h3>
                                    <p className="stat-value">{messaging.messages_today}</p>
                                </div>
                            </div>

                            <div className="chart-card">
                                <h3>Thông báo theo loại</h3>
                                <div style={{ width: '100%', height: 340 }}>
                                    {messaging.notifications_by_type && messaging.notifications_by_type.length > 0 ? (
                                        <ResponsiveContainer>
                                            <PieChart>
                                                <Pie
                                                    data={messaging.notifications_by_type}
                                                    dataKey="count"
                                                    nameKey="type"
                                                    cx="50%"
                                                    cy="50%"
                                                    innerRadius={70}
                                                    outerRadius={110}
                                                    paddingAngle={3}
                                                >
                                                    {messaging.notifications_by_type.map((entry, idx) => (
                                                        <Cell key={entry.type} fill={PIE_COLORS[idx % PIE_COLORS.length]} />
                                                    ))}
                                                </Pie>
                                                <Tooltip contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                                                <Legend />
                                            </PieChart>
                                        </ResponsiveContainer>
                                    ) : (
                                        <div className="flex-center chart-empty" style={{ height: '100%' }}>Chưa có dữ liệu</div>
                                    )}
                                </div>
                            </div>
                        </>
                    )}
                </TabLoading>
            )}
        </div>
    );
}

function TabLoading({ loading, data, children }) {
    if (loading && !data) {
        return (
            <div className="admin-loading-container" style={{ minHeight: 240 }}>
                <div className="loading-spinner"></div>
                <p>Đang tải dữ liệu...</p>
            </div>
        );
    }
    return children;
}
