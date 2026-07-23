import { useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    fetchAdminDashboardStats,
    fetchSocialDashboard,
    fetchJobMarketDashboard,
    fetchPipelineDashboard,
    fetchMessagingDashboard,
    fetchClusteringPipelineRuns,
} from '../../api/adminService';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { useToast } from '../../components/common/toastContext';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, formatShortDate } from '../../utils/adminDashboardFormat';
import SocialTab from '../../components/admin/SocialTab';
import JobsTab from '../../components/admin/JobsTab';
import PipelineTab from '../../components/admin/PipelineTab';
import MessagingTab from '../../components/admin/MessagingTab';
import LiveMetricsPanel from '../../components/admin/LiveMetricsPanel';
import StatCard from '../../components/admin/StatCard';
import type { ClusteringPipelineRun, ClusteringPipelineRunMetrics } from '../../types/admin';
import './AdminDashboard.css';

const TABS = [
    { key: 'overview', label: 'Tổng quan' },
    { key: 'social', label: 'Cộng đồng' },
    { key: 'jobs', label: 'Việc làm & Công nghệ' },
    { key: 'pipeline', label: 'Kafka Pipeline' },
    { key: 'messaging', label: 'Tin nhắn & Thông báo' },
    { key: 'modelQuality', label: 'Chất lượng Model (AI)' },
];

type QualityMetricKey = keyof ClusteringPipelineRunMetrics;

// Chỉ 4 chỉ số đánh giá clustering thật sự (bỏ n_clusters/noise_ratio/wall_seconds —
// mang tính vận hành hơn là chất lượng). higherIsBetter quyết định chiều mũi tên delta.
const QUALITY_METRICS: { key: QualityMetricKey; label: string; higherIsBetter: boolean }[] = [
    { key: 'dbcv', label: 'DBCV', higherIsBetter: true },
    { key: 'silhouette', label: 'Silhouette', higherIsBetter: true },
    { key: 'davies_bouldin', label: 'Davies-Bouldin', higherIsBetter: false },
    { key: 'calinski_harabasz', label: 'Calinski-Harabasz', higherIsBetter: true },
];

export default function AdminDashboard() {
    const [activeTab, setActiveTab] = useState('overview');
    const navigate = useNavigate();
    const notify = useToast();

    // --- Tổng quan ---
    const overview = useAsync(
        async () => {
            const res = await fetchAdminDashboardStats();
            if (!(res && res.status === 'success')) throw new Error('Dữ liệu trả về từ máy chủ không hợp lệ.');
            return {
                totalUsers: res.data?.totalUsers || 0,
                activeSessions: res.data?.activeSessions || 0,
                searchesToday: res.data?.searchesToday || 0,
                topKeywords: Array.isArray(res.data?.topKeywords) ? res.data.topKeywords : [],
                monthlyVisits: Array.isArray(res.data?.monthlyVisits) ? res.data.monthlyVisits : [],
            };
        },
        [],
        {
            onError: (err) => {
                console.error('AdminDashboard error:', err);
                if ((err as Error)?.message?.includes('401')) setTimeout(() => navigate('/login'), 3000);
            },
        },
    );
    const stats = overview.data;
    const loading = overview.loading;
    const overviewErrorText = overview.error
        ? ((overview.error as Error)?.message?.includes('401')
            ? 'Phiên làm việc hết hạn. Vui lòng đăng nhập lại.'
            : 'Không thể tải dữ liệu thống kê. Vui lòng kiểm tra lại kết nối API.')
        : null;

    // --- Cộng đồng / Việc làm / Kafka Pipeline / Tin nhắn / Chất lượng Model — mỗi tab chỉ tải 1
    // lần khi được mở lần đầu (lazy), rồi giữ cache; xem effect bên dưới.
    const social = useAsync(() => fetchSocialDashboard().then(res => res?.data || null), [], {
        lazy: true,
        onError: (err) => {
            console.error('Failed to load social dashboard:', err);
            notify({ title: 'Không tải được dữ liệu Cộng đồng', body: 'Vui lòng thử lại.', variant: 'error' });
        },
    });
    const jobs = useAsync(() => fetchJobMarketDashboard().then(res => res?.data || null), [], {
        lazy: true,
        onError: (err) => {
            console.error('Failed to load job market dashboard:', err);
            notify({ title: 'Không tải được dữ liệu Việc làm & Công nghệ', body: 'Vui lòng thử lại.', variant: 'error' });
        },
    });
    const pipeline = useAsync(() => fetchPipelineDashboard().then(res => res?.data || null), [], {
        lazy: true,
        onError: (err) => {
            console.error('Failed to load pipeline dashboard:', err);
            notify({ title: 'Không tải được dữ liệu Kafka Pipeline', body: 'Vui lòng thử lại.', variant: 'error' });
        },
    });
    const messaging = useAsync(() => fetchMessagingDashboard().then(res => res?.data || null), [], {
        lazy: true,
        onError: (err) => {
            console.error('Failed to load messaging dashboard:', err);
            notify({ title: 'Không tải được dữ liệu Tin nhắn & Thông báo', body: 'Vui lòng thử lại.', variant: 'error' });
        },
    });
    const modelRuns = useAsync(() => fetchClusteringPipelineRuns().then(res => (Array.isArray(res?.data) ? res.data : [])), [], {
        lazy: true,
        onError: (err) => {
            console.error('Failed to load clustering pipeline runs:', err);
            notify({ title: 'Không tải được lịch sử huấn luyện model', body: 'Vui lòng thử lại.', variant: 'error' });
        },
    });

    useEffect(() => {
        if (activeTab === 'social' && !social.data) social.run();
        if (activeTab === 'jobs' && !jobs.data) jobs.run();
        if (activeTab === 'pipeline' && !pipeline.data) pipeline.run();
        if (activeTab === 'messaging' && !messaging.data) messaging.run();
        if (activeTab === 'modelQuality' && !modelRuns.data) modelRuns.run();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeTab]);

    if (loading) {
        return (
            <div className="admin-loading-container">
                <div className="loading-spinner"></div>
                <p>Đang tải dữ liệu hệ thống...</p>
            </div>
        );
    }

    if (overviewErrorText) {
        return (
            <div className="admin-error-container">
                <div className="error-icon">⚠️</div>
                <h2>Đã xảy ra lỗi</h2>
                <p>{overviewErrorText}</p>
                <div className="error-actions">
                    <button className="btn btn-secondary" onClick={() => overview.reload()}>Thử lại</button>
                    <button className="btn btn-primary" onClick={() => navigate('/login')}>Đăng nhập</button>
                </div>
            </div>
        );
    }

    if (!stats) return null;

    return (
        <div className="admin-dashboard">
            <LiveMetricsPanel onOpenPipelineTab={() => setActiveTab('pipeline')} />

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
                        <StatCard icon="👤" label="Tổng User" value={stats.totalUsers} accent="primary" />
                        <StatCard icon="📊" label="Truy cập hôm nay" value={stats.activeSessions} accent="accent" />
                        <StatCard icon="🔍" label="Lượt tìm kiếm" value={stats.searchesToday} accent="green" />
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
                <TabLoading loading={social.loading} data={social.data}>
                    {social.data && <SocialTab social={social.data} />}
                </TabLoading>
            )}

            {activeTab === 'jobs' && (
                <TabLoading loading={jobs.loading} data={jobs.data}>
                    {jobs.data && <JobsTab jobs={jobs.data} />}
                </TabLoading>
            )}

            {activeTab === 'pipeline' && (
                <TabLoading loading={pipeline.loading} data={pipeline.data}>
                    {pipeline.data && <PipelineTab pipeline={pipeline.data} />}
                </TabLoading>
            )}

            {activeTab === 'messaging' && (
                <TabLoading loading={messaging.loading} data={messaging.data}>
                    {messaging.data && <MessagingTab messaging={messaging.data} />}
                </TabLoading>
            )}

            {activeTab === 'modelQuality' && (
                <TabLoading loading={modelRuns.loading} data={modelRuns.data}>
                    {modelRuns.data && <ModelQualityTrend runs={modelRuns.data} />}
                </TabLoading>
            )}
        </div>
    );
}

function ModelQualityTrend({ runs }: { runs: ClusteringPipelineRun[] }) {
    const availableMetrics = QUALITY_METRICS.filter(m => runs.some(r => r.metrics?.[m.key] != null));
    const [selectedKey, setSelectedKey] = useState<QualityMetricKey | undefined>(availableMetrics[0]?.key);
    const metric = availableMetrics.find(m => m.key === selectedKey) || availableMetrics[0];

    if (runs.length === 0) {
        return <div className="flex-center chart-empty" style={{ height: 200 }}>Chưa có lần huấn luyện nào được ghi lại.</div>;
    }
    if (!metric) {
        return <div className="flex-center chart-empty" style={{ height: 200 }}>Các lần chạy chưa có chỉ số chất lượng nào được ghi lại.</div>;
    }

    // API trả về newest-first; biểu đồ đọc trái→phải nên đảo lại thành cũ→mới.
    const chronological = [...runs].reverse();
    const chartData = chronological.map(r => ({
        started_at: r.started_at,
        snapshot_tag: r.snapshot_tag,
        algorithm: r.algorithm,
        value: r.metrics?.[metric.key] ?? null,
    }));

    const withValue = chartData.filter((d): d is typeof d & { value: number } => d.value != null);
    const latest = withValue[withValue.length - 1];
    const previous = withValue[withValue.length - 2];
    const delta = latest && previous ? latest.value - previous.value : null;
    const improved = delta != null && (metric.higherIsBetter ? delta > 0 : delta < 0);
    const worsened = delta != null && (metric.higherIsBetter ? delta < 0 : delta > 0);

    const latestRun = chronological[chronological.length - 1];

    return (
        <>
            <div className="stat-cards">
                <StatCard icon="🔁" label="Số lần huấn luyện" value={runs.length} accent="primary" />
                <StatCard
                    icon="📈"
                    label={`${metric.label} (mới nhất)`}
                    value={latest ? latest.value.toFixed(3) : '—'}
                    accent={improved ? 'green' : worsened ? 'danger' : 'accent'}
                    caption={delta != null && (
                        <span className={`model-quality-delta${improved ? ' good' : worsened ? ' bad' : ''}`}>
                            {delta > 0 ? '▲' : delta < 0 ? '▼' : '–'} {Math.abs(delta).toFixed(3)} so với lần trước
                        </span>
                    )}
                />
                <StatCard icon="🧠" label="Thuật toán hiện tại" value={latestRun?.algorithm || '—'} accent="accent" valueSize="md" />
            </div>

            <div className="chart-card">
                <div className="model-quality-header">
                    <h3>Chất lượng model qua các lần huấn luyện</h3>
                    <div className="pill-group">
                        {availableMetrics.map(m => (
                            <button
                                key={m.key}
                                className={`pill${m.key === metric.key ? ' active' : ''}`}
                                onClick={() => setSelectedKey(m.key)}
                            >
                                {m.label}
                            </button>
                        ))}
                    </div>
                </div>
                <div style={{ width: '100%', height: 340 }}>
                    <ResponsiveContainer>
                        <LineChart data={chartData} margin={{ top: 20, right: 30, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                            <XAxis dataKey="started_at" tickFormatter={formatShortDate} stroke="var(--text-3)" />
                            <YAxis stroke="var(--text-3)" domain={['auto', 'auto']} />
                            <Tooltip
                                contentStyle={{ backgroundColor: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }}
                                labelFormatter={(label) => formatDateTime(label as string)}
                                formatter={(value, _name, props) => [
                                    Number(value).toFixed(3),
                                    `${metric.label} · ${(props.payload as { algorithm?: string })?.algorithm || ''}`,
                                ]}
                            />
                            <Line
                                type="monotone"
                                dataKey="value"
                                name={metric.label}
                                stroke="var(--primary)"
                                strokeWidth={3}
                                connectNulls
                                dot={{ r: 5, strokeWidth: 2 }}
                                activeDot={{ r: 8 }}
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </div>
            </div>
        </>
    );
}

function TabLoading({ loading, data, children }: { loading: boolean; data: unknown; children: ReactNode }) {
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
