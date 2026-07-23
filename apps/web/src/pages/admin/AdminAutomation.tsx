import { useState, useEffect } from 'react';
import {
    triggerAnalyticsRebuild, triggerGraphAnalyticsRebuild,
    triggerCrawlerRun, fetchCrawlerStatus,
    fetchClusteringPipelineStatus, triggerClusteringPipeline,
    fetchDataPlatformJobs, triggerDataPlatformJob,
} from '../../api/adminService';
import type { ClusteringPipelineStatus, CrawlerStatus, DataPlatformJob } from '../../types/admin';
import { ApiError } from '../../types/api';
import { useToast } from '../../components/common/toastContext';
import { usePollingJob } from '../../hooks/usePollingJob';
import JobHistoryModal from '../../components/admin/JobHistoryModal';
import './AdminAutomation.css';

const CRAWL_POLL_INTERVAL_MS = 5000;
const CLUSTER_POLL_INTERVAL_MS = 4000;
const DATA_PLATFORM_POLL_INTERVAL_MS = 5000;
const CLUSTER_STAGES = [
    { key: 'pipelines.stage_01_extract', label: 'Trích xuất' },
    { key: 'pipelines.stage_02_features', label: 'Đặc trưng' },
    { key: 'pipelines.stage_03_train', label: 'Huấn luyện' },
    { key: 'pipelines.stage_04_label', label: 'Gán nhãn AI' },
    { key: 'pipelines.stage_05_writeback', label: 'Ghi kết quả' },
];

interface DataPlatformJobMeta {
    label: string;
    description: string;
}

// gold_pg_etl và retrain_clustering không nằm trong danh sách này — 2 job đó đã có
// nút riêng ("Chạy lại phân tích" và "Huấn luyện lại" ở trên) nên không lặp lại ở đây.
const DATA_PLATFORM_JOB_LABELS: Record<string, DataPlatformJobMeta> = {
    neo4j_article_sync: {
        label: 'Đồng bộ Bài viết → Knowledge Graph',
        description: 'Đẩy bài viết đã xử lý (Silver layer) sang Neo4j, độc lập với đường Kafka realtime hay bị rớt data.',
    },
    neo4j_job_sync: {
        label: 'Đồng bộ Tin tuyển dụng → Knowledge Graph',
        description: 'Đẩy tin tuyển dụng đã xử lý sang Neo4j, độc lập với đường Kafka realtime hay bị rớt data.',
    },
    embed_trigger: {
        label: 'Embed Bài viết mới (RAG)',
        description: 'Trigger ai-rag-core embed các Article mới vào vector store cho tìm kiếm ngữ nghĩa.',
    },
    neo4j_enricher: {
        label: 'Làm giàu Knowledge Graph',
        description: 'Suy ra quan hệ Company–Technology, Technology liên quan nhau, cập nhật trend score.',
    },
    tech_dedup: {
        label: 'Gộp Công nghệ trùng lặp',
        description: 'Gộp các node Technology bị tách do khác cách viết (Go/Golang, K8s/Kubernetes...).',
    },
};
// Thứ tự pipeline THẬT (khớp lịch cron đêm: article_sync → job_sync → embed_trigger →
// enricher → tech_dedup — tech_dedup phải chạy sau enricher, xem docstring của job đó).
// Khai báo tường minh bằng mảng thay vì Object.keys(...) để thứ tự không phụ thuộc ngầm
// vào thứ tự khai báo property phía trên.
const DATA_PLATFORM_JOB_ORDER = ['neo4j_article_sync', 'neo4j_job_sync', 'embed_trigger', 'neo4j_enricher', 'tech_dedup'];
// Mỗi job chỉ nên chạy tay sau khi job đứng trước nó trong pipeline đã chạy thành công —
// tránh vd. gộp công nghệ trùng (tech_dedup) trước khi Knowledge Graph được làm giàu (enricher).
const DATA_PLATFORM_JOB_DEPENDS_ON: Record<string, string | null> = {
    neo4j_article_sync: null,
    neo4j_job_sync: null,
    embed_trigger: 'neo4j_article_sync',
    neo4j_enricher: 'embed_trigger',
    tech_dedup: 'neo4j_enricher',
};

function DataPlatformJobStatus({ job }: { job: DataPlatformJob | undefined }) {
    if (!job || job.status === 'never_run') {
        return <p className="text-3 text-sm">Chưa từng chạy.</p>;
    }
    if (job.status === 'running') {
        return <p className="text-3 text-sm">Đang chạy...</p>;
    }
    if (job.status === 'success') {
        return (
            <p className="text-3 text-sm">
                Lần chạy gần nhất: thành công
                {job.finished_at ? ` lúc ${new Date(job.finished_at).toLocaleString('vi-VN')}` : ''}
                {job.rows_affected != null ? ` (${job.rows_affected} dòng)` : ''}
            </p>
        );
    }
    if (job.status === 'failed') {
        return <p className="cluster-pipeline-error">Lần chạy gần nhất thất bại: {job.error_msg || 'Không rõ lỗi'}</p>;
    }
    return null;
}

function ClusterPipelineProgress({ status }: { status: ClusteringPipelineStatus | null }) {
    if (!status) return null;

    if (status.status === 'running') {
        const currentIdx = CLUSTER_STAGES.findIndex(s => s.key === status.current_stage);
        return (
            <div className="cluster-pipeline-progress">
                <div className="cluster-pipeline-steps">
                    {CLUSTER_STAGES.map((s, idx) => (
                        <div
                            key={s.key}
                            className={`cluster-pipeline-step${idx < currentIdx ? ' done' : ''}${idx === currentIdx ? ' active' : ''}`}
                            title={s.label}
                        />
                    ))}
                </div>
                <p className="text-3 text-sm">
                    Đang chạy: {CLUSTER_STAGES[currentIdx]?.label || 'Đang khởi động…'}
                </p>
            </div>
        );
    }

    if (status.status === 'success' && status.finished_at) {
        return (
            <p className="text-3 text-sm">
                Lần chạy gần nhất: thành công trong {status.duration_s ?? '?'}s — {new Date(status.finished_at).toLocaleString('vi-VN')}
            </p>
        );
    }

    if (status.status === 'failed') {
        return <p className="cluster-pipeline-error">Lần chạy gần nhất thất bại: {status.error || 'Không rõ lỗi'}</p>;
    }

    return null;
}

export default function AdminAutomation() {
    const [loading, setLoading] = useState(true);
    const [rebuilding, setRebuilding] = useState(false);
    const [rebuildingGraphAnalytics, setRebuildingGraphAnalytics] = useState(false);
    const [triggeringCrawl, setTriggeringCrawl] = useState(false);
    const [triggeringCluster, setTriggeringCluster] = useState(false);
    const [triggeringJobId, setTriggeringJobId] = useState<string | null>(null);
    const [historyJobId, setHistoryJobId] = useState<string | null>(null);

    const notify = useToast();

    const crawler = usePollingJob<CrawlerStatus>({
        fetchStatus: () => fetchCrawlerStatus().then(res => res?.data as CrawlerStatus),
        isRunning: (status) => status?.state === 'running',
        onSettled: (status) => notify({
            title: `Radar đã cào xong: ${status?.success_count ?? '?'}/${status?.total ?? '?'} nguồn thành công`,
            variant: 'success',
        }),
        intervalMs: CRAWL_POLL_INTERVAL_MS,
    });
    const crawlerStatus = crawler.status;

    const cluster = usePollingJob<ClusteringPipelineStatus>({
        fetchStatus: () => fetchClusteringPipelineStatus().then(res => res?.data as ClusteringPipelineStatus),
        isRunning: (status) => status?.status === 'running',
        onSettled: (status) => {
            if (status?.status === 'success') {
                notify({ title: `Huấn luyện lại cụm công nghệ thành công (${status.duration_s ?? '?'}s)`, variant: 'success' });
            } else if (status?.status === 'failed') {
                notify({ title: 'Huấn luyện lại cụm công nghệ thất bại', body: status.error, variant: 'error' });
            }
        },
        intervalMs: CLUSTER_POLL_INTERVAL_MS,
    });
    const clusterStatus = cluster.status;

    const dataPlatform = usePollingJob<DataPlatformJob[]>({
        fetchStatus: () => fetchDataPlatformJobs().then(res => res?.data || []),
        isRunning: (jobs) => jobs.some((j) => j.status === 'running'),
        intervalMs: DATA_PLATFORM_POLL_INTERVAL_MS,
    });
    const dataPlatformJobs = dataPlatform.status || [];

    useEffect(() => {
        crawler.load();
        cluster.load();
        dataPlatform.load().finally(() => setLoading(false));
        // Mount-only: load is recreated every render, adding it here would refetch on every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

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

    const handleRebuildGraphAnalytics = async () => {
        setRebuildingGraphAnalytics(true);
        try {
            const res = await triggerGraphAnalyticsRebuild();
            const scored = res?.data?.technologies_scored;
            const communities = res?.data?.communities_found;
            notify({
                title: `Đã chạy lại phân tích đồ thị thành công${scored != null ? ` (${scored} công nghệ, ${communities ?? '?'} cộng đồng)` : ''}.`,
                variant: 'success',
            });
        } catch (error) {
            console.error('Failed to rebuild graph analytics:', error);
            notify({ title: 'Chạy lại phân tích đồ thị thất bại. Vui lòng thử lại.', variant: 'error' });
        } finally {
            setRebuildingGraphAnalytics(false);
        }
    };

    const handleTriggerCrawl = async () => {
        setTriggeringCrawl(true);
        try {
            const res = await triggerCrawlerRun();
            if (res?.data?.delivered) {
                notify({ title: res.message || 'Đã kích hoạt Radar, crawler sẽ chạy ngay', variant: 'success' });
                crawler.setStatus((prev) => ({ ...(prev || {} as CrawlerStatus), state: 'running' }));
                crawler.startPolling();
            } else {
                notify({ title: res?.message || 'Không có crawler nào đang lắng nghe', variant: 'error' });
            }
        } catch (error) {
            console.error('Failed to trigger crawler:', error);
            notify({ title: (error as Error).message || 'Kích hoạt Radar thất bại. Vui lòng thử lại.', variant: 'error' });
            // 409/429 nghĩa là đã có lượt chạy khác hoặc bị debounce — vẫn theo dõi tới khi nó xong.
            if (error instanceof ApiError && (error.status === 409 || error.status === 429)) crawler.startPolling();
        } finally {
            setTriggeringCrawl(false);
        }
    };

    const isCrawling = triggeringCrawl || crawlerStatus?.state === 'running';

    const handleTriggerCluster = async () => {
        setTriggeringCluster(true);
        try {
            await triggerClusteringPipeline();
            notify({ title: 'Đã bắt đầu huấn luyện lại cụm công nghệ', variant: 'success' });
            cluster.setStatus((prev) => ({ ...(prev || {} as ClusteringPipelineStatus), status: 'running', current_stage: null }));
            cluster.startPolling();
        } catch (error) {
            console.error('Failed to trigger clustering pipeline:', error);
            notify({ title: (error as Error).message || 'Kích hoạt huấn luyện lại thất bại. Vui lòng thử lại.', variant: 'error' });
            // 409/429 nghĩa là đã có lượt chạy khác hoặc bị debounce — vẫn theo dõi tới khi nó xong.
            if (error instanceof ApiError && (error.status === 409 || error.status === 429)) cluster.startPolling();
        } finally {
            setTriggeringCluster(false);
        }
    };

    const isClusterRunning = triggeringCluster || clusterStatus?.status === 'running';

    const handleTriggerDataPlatformJob = async (jobId: string) => {
        setTriggeringJobId(jobId);
        try {
            const res = await triggerDataPlatformJob(jobId);
            if (res?.data?.delivered) {
                notify({ title: res.message || 'Đã gửi yêu cầu, job sẽ chạy ngay', variant: 'success' });
                dataPlatform.startPolling();
            } else {
                notify({ title: res?.message || 'Không có data-platform nào đang lắng nghe', variant: 'error' });
            }
        } catch (error) {
            console.error(`Failed to trigger data platform job ${jobId}:`, error);
            notify({ title: (error as Error).message || 'Kích hoạt thất bại. Vui lòng thử lại.', variant: 'error' });
            // 409/429 nghĩa là job đã đang chạy (lần trước, lịch cron, hoặc debounce) — vẫn theo dõi tới khi xong.
            if (error instanceof ApiError && (error.status === 409 || error.status === 429)) dataPlatform.startPolling();
        } finally {
            setTriggeringJobId(null);
        }
    };

    const dataPlatformJobByName = Object.fromEntries(dataPlatformJobs.map((j) => [j.job_name, j]));

    if (loading) return <div className="admin-settings-loading"><div className="loading-spinner" /><span>Đang tải...</span></div>;

    return (
        <div className="admin-automation">
            <div className="settings-header">
                <h2>Vận hành</h2>
                <p>Chạy tay các job nền thay vì chờ lịch định kỳ, và xem lịch sử các lần chạy.</p>
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

            <div className="settings-card">
                <div className="setting-info">
                    <h3>Phân tích đồ thị (Centrality/Community)</h3>
                    <p>Tính lại PageRank, cộng đồng công nghệ (Louvain) và độ trung tâm (degree centrality) bằng Neo4j GDS — dữ liệu này hiển thị ở chế độ "Phân tích đồ thị" trên Knowledge Graph Explorer.</p>
                </div>
                <button className="btn btn-secondary" onClick={handleRebuildGraphAnalytics} disabled={rebuildingGraphAnalytics}>
                    {rebuildingGraphAnalytics ? 'Đang chạy...' : 'Chạy lại phân tích đồ thị'}
                </button>
            </div>

            <div className="settings-card">
                <div className="setting-info">
                    <h3>Thu thập dữ liệu (Crawler)</h3>
                    <p>Kích hoạt Radar quét ngay các nguồn tin tức &amp; việc làm, thay vì chờ lịch cào định kỳ.</p>
                    {crawlerStatus?.state === 'idle' && crawlerStatus.finished_at && (
                        <p className="text-3 text-sm">
                            Lần chạy gần nhất: {crawlerStatus.success_count ?? '?'}/{crawlerStatus.total ?? '?'} nguồn thành công.
                        </p>
                    )}
                </div>
                <button className="btn btn-secondary" onClick={handleTriggerCrawl} disabled={isCrawling}>
                    {isCrawling ? 'Đang chạy...' : 'Kích hoạt Radar'}
                </button>
            </div>

            <div className="settings-card cluster-pipeline-card">
                <div className="setting-info">
                    <h3>Huấn luyện lại Cụm Công nghệ (ML Pipeline)</h3>
                    <p>Chạy lại toàn bộ pipeline HDBSCAN + gán nhãn AI cho các công nghệ, thay vì chờ lịch chạy định kỳ.</p>
                    <ClusterPipelineProgress status={clusterStatus} />
                </div>
                <button className="btn btn-secondary" onClick={handleTriggerCluster} disabled={isClusterRunning}>
                    {isClusterRunning ? 'Đang huấn luyện...' : 'Huấn luyện lại'}
                </button>
            </div>

            <div className="settings-card data-platform-jobs-card">
                <div className="setting-info">
                    <h3>Job Data Platform khác</h3>
                    <p>Các job đồng bộ/làm giàu Knowledge Graph chạy theo lịch đêm (2h–5h30 sáng) — chạy ngay thay vì chờ qua đêm.</p>
                </div>
                <div className="data-platform-jobs-list">
                    {DATA_PLATFORM_JOB_ORDER.map((jobId, index) => {
                        const meta = DATA_PLATFORM_JOB_LABELS[jobId];
                        const job = dataPlatformJobByName[jobId];
                        const isRunning = triggeringJobId === jobId || job?.status === 'running';
                        const dependsOn = DATA_PLATFORM_JOB_DEPENDS_ON[jobId];
                        const upstreamReady = !dependsOn || dataPlatformJobByName[dependsOn]?.status === 'success';
                        const isLast = index === DATA_PLATFORM_JOB_ORDER.length - 1;
                        return (
                            <div className="data-platform-job-row" key={jobId}>
                                <div className="data-platform-job-step" aria-hidden="true">
                                    <span className={`data-platform-job-dot data-platform-job-dot--${job?.status ?? 'never_run'}`}>
                                        {index + 1}
                                    </span>
                                    {!isLast && <span className="data-platform-job-line" />}
                                </div>
                                <div className="setting-info">
                                    <h4>{meta.label}</h4>
                                    <p>{meta.description}</p>
                                    <DataPlatformJobStatus job={job} />
                                    {!upstreamReady && !isRunning && (
                                        <p className="data-platform-job-hint">
                                            Cần chạy thành công "{DATA_PLATFORM_JOB_LABELS[dependsOn!].label}" trước
                                        </p>
                                    )}
                                </div>
                                <div className="data-platform-job-actions">
                                    <button
                                        className="btn btn-secondary"
                                        onClick={() => handleTriggerDataPlatformJob(jobId)}
                                        disabled={isRunning || !upstreamReady}
                                        title={!upstreamReady && !isRunning ? 'Job phía trước trong pipeline chưa chạy thành công' : undefined}
                                    >
                                        {isRunning ? 'Đang chạy...' : 'Chạy ngay'}
                                    </button>
                                    <button className="job-history-btn" onClick={() => setHistoryJobId(jobId)}>
                                        Lịch sử
                                    </button>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {historyJobId && (
                <JobHistoryModal
                    jobId={historyJobId}
                    jobLabel={DATA_PLATFORM_JOB_LABELS[historyJobId].label}
                    onClose={() => setHistoryJobId(null)}
                />
            )}
        </div>
    );
}
