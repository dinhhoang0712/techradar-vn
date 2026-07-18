import { useState, useEffect, useRef } from 'react';
import {
    fetchAdminSettings, updateAdminSetting, triggerAnalyticsRebuild,
    triggerCrawlerRun, fetchCrawlerStatus,
    fetchClusteringPipelineStatus, triggerClusteringPipeline,
    fetchDataPlatformJobs, triggerDataPlatformJob,
} from '../../api/adminService';
import { useToast } from '../../components/common/toastContext';
import './AdminSettings.css';

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

// gold_pg_etl và retrain_clustering không nằm trong danh sách này — 2 job đó đã có
// nút riêng ("Chạy lại phân tích" và "Huấn luyện lại" ở trên) nên không lặp lại ở đây.
const DATA_PLATFORM_JOB_LABELS = {
    neo4j_article_sync: {
        label: 'Đồng bộ Bài viết → Knowledge Graph',
        description: 'Đẩy bài viết đã xử lý (Silver layer) sang Neo4j, độc lập với đường Kafka realtime hay bị rớt data.',
    },
    neo4j_job_sync: {
        label: 'Đồng bộ Tin tuyển dụng → Knowledge Graph',
        description: 'Đẩy tin tuyển dụng đã xử lý sang Neo4j, độc lập với đường Kafka realtime hay bị rớt data.',
    },
    neo4j_enricher: {
        label: 'Làm giàu Knowledge Graph',
        description: 'Suy ra quan hệ Company–Technology, Technology liên quan nhau, cập nhật trend score.',
    },
    tech_dedup: {
        label: 'Gộp Công nghệ trùng lặp',
        description: 'Gộp các node Technology bị tách do khác cách viết (Go/Golang, K8s/Kubernetes...).',
    },
    embed_trigger: {
        label: 'Embed Bài viết mới (RAG)',
        description: 'Trigger ai-rag-core embed các Article mới vào vector store cho tìm kiếm ngữ nghĩa.',
    },
};
const DATA_PLATFORM_JOB_ORDER = Object.keys(DATA_PLATFORM_JOB_LABELS);

function DataPlatformJobStatus({ job }) {
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

function ClusterPipelineProgress({ status }) {
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

export default function AdminSettings() {
    const [settings, setSettings] = useState({});
    const [loading, setLoading] = useState(true);
    const [rebuilding, setRebuilding] = useState(false);
    const [crawlerStatus, setCrawlerStatus] = useState(null);
    const [triggeringCrawl, setTriggeringCrawl] = useState(false);
    const crawlPollRef = useRef(null);

    const [clusterStatus, setClusterStatus] = useState(null);
    const [triggeringCluster, setTriggeringCluster] = useState(false);
    const clusterPollRef = useRef(null);

    const [dataPlatformJobs, setDataPlatformJobs] = useState([]);
    const [triggeringJobId, setTriggeringJobId] = useState(null);
    const dataPlatformPollRef = useRef(null);

    const notify = useToast();

    useEffect(() => {
        loadSettings();
        loadCrawlerStatus();
        loadClusterStatus();
        loadDataPlatformJobs();
        return () => { stopCrawlPolling(); stopClusterPolling(); stopDataPlatformPolling(); };
        // Mount-only: these loaders are recreated every render, adding them here would refetch on every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const loadSettings = async () => {
        try {
            const res = await fetchAdminSettings();
            // res.data is an array of {key, value} rows (ApiResponse<List<AppSettings>>), not a flat object
            if (res && Array.isArray(res.data)) {
                const byKey = Object.fromEntries(res.data.map((s) => [s.key, s.value]));
                const mapped = {
                    isWebMaintenance: byKey.maintenance_web === 'true' || byKey.maintenance_web === true,
                    isAppMaintenance: byKey.maintenance_mobile === 'true' || byKey.maintenance_mobile === true,
                    isGraphEnabled: byKey.feature_graph === 'true' || byKey.feature_graph === true,
                    isChatEnabled: byKey.feature_chat === 'true' || byKey.feature_chat === true,
                    isRagEnabled: byKey.feature_rag === 'true' || byKey.feature_rag === true,
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

    const stopCrawlPolling = () => {
        if (crawlPollRef.current) {
            clearInterval(crawlPollRef.current);
            crawlPollRef.current = null;
        }
    };

    const startCrawlPolling = () => {
        if (crawlPollRef.current) return;
        crawlPollRef.current = setInterval(async () => {
            try {
                const res = await fetchCrawlerStatus();
                const status = res?.data;
                setCrawlerStatus(status || null);
                if (status?.state !== 'running') {
                    stopCrawlPolling();
                    notify({
                        title: `Radar đã cào xong: ${status?.success_count ?? '?'}/${status?.total ?? '?'} nguồn thành công`,
                        variant: 'success',
                    });
                }
            } catch (error) {
                console.error('Failed to poll crawler status:', error);
            }
        }, CRAWL_POLL_INTERVAL_MS);
    };

    const loadCrawlerStatus = async () => {
        try {
            const res = await fetchCrawlerStatus();
            const status = res?.data;
            setCrawlerStatus(status || null);
            if (status?.state === 'running') startCrawlPolling();
        } catch (error) {
            console.error('Failed to load crawler status:', error);
        }
    };

    const handleTriggerCrawl = async () => {
        setTriggeringCrawl(true);
        try {
            const res = await triggerCrawlerRun();
            if (res?.data?.delivered) {
                notify({ title: res.message || 'Đã kích hoạt Radar, crawler sẽ chạy ngay', variant: 'success' });
                setCrawlerStatus((prev) => ({ ...(prev || {}), state: 'running' }));
                startCrawlPolling();
            } else {
                notify({ title: res?.message || 'Không có crawler nào đang lắng nghe', variant: 'error' });
            }
        } catch (error) {
            console.error('Failed to trigger crawler:', error);
            notify({ title: error.message || 'Kích hoạt Radar thất bại. Vui lòng thử lại.', variant: 'error' });
            // 409 nghĩa là đã có lượt chạy khác — vẫn theo dõi tới khi nó xong.
            if (error.status === 409) startCrawlPolling();
        } finally {
            setTriggeringCrawl(false);
        }
    };

    const isCrawling = triggeringCrawl || crawlerStatus?.state === 'running';

    const stopClusterPolling = () => {
        if (clusterPollRef.current) {
            clearInterval(clusterPollRef.current);
            clusterPollRef.current = null;
        }
    };

    const startClusterPolling = () => {
        if (clusterPollRef.current) return;
        clusterPollRef.current = setInterval(async () => {
            try {
                const res = await fetchClusteringPipelineStatus();
                const status = res?.data;
                setClusterStatus(status || null);
                if (status?.status !== 'running') {
                    stopClusterPolling();
                    if (status?.status === 'success') {
                        notify({ title: `Huấn luyện lại cụm công nghệ thành công (${status.duration_s ?? '?'}s)`, variant: 'success' });
                    } else if (status?.status === 'failed') {
                        notify({ title: 'Huấn luyện lại cụm công nghệ thất bại', body: status.error, variant: 'error' });
                    }
                }
            } catch (error) {
                console.error('Failed to poll clustering pipeline status:', error);
            }
        }, CLUSTER_POLL_INTERVAL_MS);
    };

    const loadClusterStatus = async () => {
        try {
            const res = await fetchClusteringPipelineStatus();
            const status = res?.data;
            setClusterStatus(status || null);
            if (status?.status === 'running') startClusterPolling();
        } catch (error) {
            console.error('Failed to load clustering pipeline status:', error);
        }
    };

    const handleTriggerCluster = async () => {
        setTriggeringCluster(true);
        try {
            await triggerClusteringPipeline();
            notify({ title: 'Đã bắt đầu huấn luyện lại cụm công nghệ', variant: 'success' });
            setClusterStatus((prev) => ({ ...(prev || {}), status: 'running', current_stage: null }));
            startClusterPolling();
        } catch (error) {
            console.error('Failed to trigger clustering pipeline:', error);
            notify({ title: error.message || 'Kích hoạt huấn luyện lại thất bại. Vui lòng thử lại.', variant: 'error' });
            // 409 nghĩa là đã có lượt chạy khác — vẫn theo dõi tới khi nó xong.
            if (error.status === 409) startClusterPolling();
        } finally {
            setTriggeringCluster(false);
        }
    };

    const isClusterRunning = triggeringCluster || clusterStatus?.status === 'running';

    const stopDataPlatformPolling = () => {
        if (dataPlatformPollRef.current) {
            clearInterval(dataPlatformPollRef.current);
            dataPlatformPollRef.current = null;
        }
    };

    const startDataPlatformPolling = () => {
        if (dataPlatformPollRef.current) return;
        dataPlatformPollRef.current = setInterval(async () => {
            try {
                const res = await fetchDataPlatformJobs();
                const jobs = res?.data || [];
                setDataPlatformJobs(jobs);
                if (!jobs.some((j) => j.status === 'running')) stopDataPlatformPolling();
            } catch (error) {
                console.error('Failed to poll data platform jobs:', error);
            }
        }, DATA_PLATFORM_POLL_INTERVAL_MS);
    };

    const loadDataPlatformJobs = async () => {
        try {
            const res = await fetchDataPlatformJobs();
            const jobs = res?.data || [];
            setDataPlatformJobs(jobs);
            if (jobs.some((j) => j.status === 'running')) startDataPlatformPolling();
        } catch (error) {
            console.error('Failed to load data platform jobs:', error);
        }
    };

    const handleTriggerDataPlatformJob = async (jobId) => {
        setTriggeringJobId(jobId);
        try {
            const res = await triggerDataPlatformJob(jobId);
            if (res?.data?.delivered) {
                notify({ title: res.message || 'Đã gửi yêu cầu, job sẽ chạy ngay', variant: 'success' });
                startDataPlatformPolling();
            } else {
                notify({ title: res?.message || 'Không có data-platform nào đang lắng nghe', variant: 'error' });
            }
        } catch (error) {
            console.error(`Failed to trigger data platform job ${jobId}:`, error);
            notify({ title: error.message || 'Kích hoạt thất bại. Vui lòng thử lại.', variant: 'error' });
            // 409 nghĩa là job đã đang chạy (lần trước hoặc lịch cron) — vẫn theo dõi tới khi xong.
            if (error.status === 409) startDataPlatformPolling();
        } finally {
            setTriggeringJobId(null);
        }
    };

    const dataPlatformJobByName = Object.fromEntries(dataPlatformJobs.map((j) => [j.job_name, j]));

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
                    {DATA_PLATFORM_JOB_ORDER.map((jobId) => {
                        const meta = DATA_PLATFORM_JOB_LABELS[jobId];
                        const job = dataPlatformJobByName[jobId];
                        const isRunning = triggeringJobId === jobId || job?.status === 'running';
                        return (
                            <div className="data-platform-job-row" key={jobId}>
                                <div className="setting-info">
                                    <h4>{meta.label}</h4>
                                    <p>{meta.description}</p>
                                    <DataPlatformJobStatus job={job} />
                                </div>
                                <button
                                    className="btn btn-secondary"
                                    onClick={() => handleTriggerDataPlatformJob(jobId)}
                                    disabled={isRunning}
                                >
                                    {isRunning ? 'Đang chạy...' : 'Chạy ngay'}
                                </button>
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}
