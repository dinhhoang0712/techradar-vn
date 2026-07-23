import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { streamAdminLiveMetrics } from '../../api/adminService';
import { getUnreadCount } from '../../api/notificationService';
import { formatDateTime } from '../../utils/adminDashboardFormat';
import type { AdminLiveMetrics } from '../../types/admin';

const JOB_FAILURE_POLL_INTERVAL_MS = 20000;

const STATE_LABEL: Record<string, string> = {
    running: 'Đang xử lý',
    idle: 'Rảnh',
    unknown: 'Không rõ',
};

interface LiveMetricsPanelProps {
    /** Switches AdminDashboard to the "Kafka Pipeline" tab when the pipeline tile is clicked. */
    onOpenPipelineTab?: () => void;
}

// Panel "Giám sát thời gian thực" — luôn hiển thị phía trên các tab (không phụ thuộc tab đang mở),
// vì đây là điểm nhấn chính của AdminDashboard theo yêu cầu "UI phải đẹp và nổi bật". Nhận snapshot
// mới mỗi 5s qua GET /admin/dashboard/live-metrics/stream (SSE) — xem streamAdminLiveMetrics.
export default function LiveMetricsPanel({ onOpenPipelineTab }: LiveMetricsPanelProps) {
    const [metrics, setMetrics] = useState<AdminLiveMetrics | null>(null);
    const [connected, setConnected] = useState(false);
    // Per-admin, không nằm trong snapshot SSE dùng chung (live-metrics là system-wide) — tự poll
    // riêng qua GET /notifications/unread-count?type=... thay vì gộp vào stream chung.
    const [jobFailureCount, setJobFailureCount] = useState<number | null>(null);
    const navigate = useNavigate();

    useEffect(() => {
        const controller = streamAdminLiveMetrics(
            (snapshot) => {
                setMetrics(snapshot);
                setConnected(true);
            },
            () => setConnected(false),
        );
        return () => controller.abort();
    }, []);

    useEffect(() => {
        const loadJobFailureCount = () => {
            getUnreadCount('ADMIN_JOB_REPEATED_FAILURE')
                .then(setJobFailureCount)
                .catch(() => {});
        };
        loadJobFailureCount();
        const interval = setInterval(loadJobFailureCount, JOB_FAILURE_POLL_INTERVAL_MS);
        return () => clearInterval(interval);
    }, []);

    const crawlerState = metrics?.crawler.state ?? 'unknown';
    const radarState = metrics?.radar.state ?? 'idle';
    const pendingReports = metrics?.pending_reports ?? 0;
    const pipelineHealthy = !metrics?.pipeline_health?.last_failure_at;

    return (
        <div className="live-metrics-panel">
            <div className="live-metrics-header">
                <h2>
                    <span className="live-metrics-icon" aria-hidden="true">📡</span>
                    Giám sát thời gian thực
                </h2>
                <span className={`live-badge${connected ? ' connected' : ''}`}>
                    <span className="live-badge-dot" />
                    {connected ? 'LIVE' : 'Đang kết nối...'}
                </span>
            </div>

            <div className="live-metrics-grid">
                <div className="live-metric-tile">
                    <div className="live-metric-label">
                        <span className="live-metric-icon" aria-hidden="true">📰</span> Bài viết đã cào
                    </div>
                    <div className="live-metric-value">
                        {metrics ? `${metrics.crawler.success_count ?? 0}/${metrics.crawler.total ?? 0}` : '—'}
                    </div>
                    <div className="live-metric-caption">
                        <span className={`state-pill state-${crawlerState}`}>{STATE_LABEL[crawlerState] ?? crawlerState}</span>
                        {metrics?.crawler.finished_at && <span className="live-metric-caption-time"> {formatDateTime(metrics.crawler.finished_at)}</span>}
                    </div>
                </div>

                <div className="live-metric-tile">
                    <div className="live-metric-label">
                        <span className="live-metric-icon" aria-hidden="true">✨</span> Công nghệ mới
                    </div>
                    <div className="live-metric-value">{metrics ? metrics.new_technologies_this_month : '—'}</div>
                    <div className="live-metric-caption">Xuất hiện lần đầu trong tháng này</div>
                </div>

                <div className="live-metric-tile">
                    <div className="live-metric-label">
                        <span className="live-metric-icon" aria-hidden="true">🛰️</span> Radar
                    </div>
                    <div className="live-metric-value live-metric-value-text">
                        <span className={`state-pill state-pill-lg state-${radarState}`}>
                            {radarState === 'running' && <span className="state-pill-ping" aria-hidden="true" />}
                            {STATE_LABEL[radarState] ?? radarState}
                        </span>
                    </div>
                    <div className="live-metric-caption">
                        {metrics?.radar.finished_at ? `Lần chạy gần nhất: ${formatDateTime(metrics.radar.finished_at)}` : 'Chưa từng chạy'}
                    </div>
                </div>

                <div className="live-metric-tile">
                    <div className="live-metric-label">
                        <span className="live-metric-icon" aria-hidden="true">🤖</span> Request AI hôm nay
                    </div>
                    <div className="live-metric-value">{metrics ? metrics.ai_requests_today : '—'}</div>
                    <div className="live-metric-caption">Forecast · Summarize · Interview · Career...</div>
                </div>

                <button
                    type="button"
                    className={`live-metric-tile live-metric-tile--clickable${pendingReports > 0 ? ' live-metric-tile--alert' : ''}`}
                    onClick={() => navigate('/admin/reports')}
                >
                    <div className="live-metric-label">
                        <span className="live-metric-icon" aria-hidden="true">🚩</span> Báo cáo chờ duyệt
                    </div>
                    <div className={`live-metric-value${pendingReports > 0 ? ' live-metric-value--danger' : ''}`}>
                        {metrics ? pendingReports : '—'}
                    </div>
                    <div className="live-metric-caption">
                        {pendingReports > 0 ? 'Nhấn để xem và xử lý →' : 'Không có báo cáo nào chờ duyệt'}
                    </div>
                </button>

                <button
                    type="button"
                    className="live-metric-tile live-metric-tile--clickable"
                    onClick={onOpenPipelineTab}
                >
                    <div className="live-metric-label">
                        <span className="live-metric-icon" aria-hidden="true">⚙️</span> Kafka Pipeline
                    </div>
                    <div className="live-metric-value live-metric-value-text">
                        <span className={`state-pill state-pill-lg ${pipelineHealthy ? 'state-healthy' : 'state-error'}`}>
                            {!pipelineHealthy && <span className="state-pill-ping state-pill-ping--error" aria-hidden="true" />}
                            {pipelineHealthy ? 'Ổn định' : 'Có lỗi'}
                        </span>
                    </div>
                    <div className="live-metric-caption">
                        {metrics?.pipeline_health?.last_failure_at
                            ? `Lỗi gần nhất: ${formatDateTime(metrics.pipeline_health.last_failure_at)}`
                            : 'Không có lỗi đồng bộ gần đây'}
                    </div>
                </button>

                <button
                    type="button"
                    className={`live-metric-tile live-metric-tile--clickable${(jobFailureCount ?? 0) > 0 ? ' live-metric-tile--alert' : ''}`}
                    onClick={() => navigate('/admin/automation')}
                >
                    <div className="live-metric-label">
                        <span className="live-metric-icon" aria-hidden="true">🔧</span> Job gặp vấn đề
                    </div>
                    <div className={`live-metric-value${(jobFailureCount ?? 0) > 0 ? ' live-metric-value--danger' : ''}`}>
                        {jobFailureCount ?? '—'}
                    </div>
                    <div className="live-metric-caption">
                        {(jobFailureCount ?? 0) > 0 ? 'Job lỗi liên tục — nhấn để xem →' : 'Không có job nào lỗi lặp lại'}
                    </div>
                </button>
            </div>
        </div>
    );
}
