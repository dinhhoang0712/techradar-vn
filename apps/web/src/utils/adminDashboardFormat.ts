// Helpers định dạng dùng chung giữa AdminDashboard.jsx và các tab con (PipelineTab, ModelQualityTrend).
export function formatDateTime(iso?: string | null): string {
    if (!iso) return 'Chưa có dữ liệu';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return 'Chưa có dữ liệu';
    return d.toLocaleString('vi-VN');
}

export function formatShortDate(iso?: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
}

export interface PipelineStats {
    articles_processed?: number;
    jobs_processed?: number;
    articles_failed?: number;
    jobs_failed?: number;
    last_failure_at?: string | null;
    last_failure_message?: string;
    last_article_processed_at?: string | null;
    last_job_processed_at?: string | null;
}

// Tỷ lệ xử lý thành công = (article + job đã xử lý) / (article + job đã xử lý + lỗi)
export function computePipelineSuccessRate(pipeline?: PipelineStats | null): number {
    if (!pipeline) return 0;
    const processed = (pipeline.articles_processed || 0) + (pipeline.jobs_processed || 0);
    const failed = (pipeline.articles_failed || 0) + (pipeline.jobs_failed || 0);
    const total = processed + failed;
    if (total <= 0) return 100;
    return (processed / total) * 100;
}
