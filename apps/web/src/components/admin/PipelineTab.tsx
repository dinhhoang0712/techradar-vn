import RingGauge from '../common/RingGauge';
import StatCard from './StatCard';
import { formatDateTime, computePipelineSuccessRate } from '../../utils/adminDashboardFormat';
import type { PipelineDashboard } from '../../types/admin';

// Tab "Kafka Pipeline" của AdminDashboard: banner tình trạng đồng bộ + số article/job đã xử lý/lỗi.
export default function PipelineTab({ pipeline }: { pipeline: PipelineDashboard }) {
    const successRate = computePipelineSuccessRate(pipeline);

    return (
        <>
            <div className={`pipeline-banner ${pipeline.last_failure_at ? 'warning' : 'healthy'}`}>
                <RingGauge
                    percent={successRate}
                    size={44}
                    strokeWidth={4}
                    label={`${Math.round(successRate)}%`}
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
                <StatCard icon="✅" label="Article đã xử lý" value={pipeline.articles_processed} accent="green" />
                <StatCard icon="⚠️" label="Article lỗi" value={pipeline.articles_failed} accent={pipeline.articles_failed ? 'danger' : 'green'} />
                <StatCard icon="✅" label="Job đã xử lý" value={pipeline.jobs_processed} accent="green" />
                <StatCard icon="⚠️" label="Job lỗi" value={pipeline.jobs_failed} accent={pipeline.jobs_failed ? 'danger' : 'green'} />
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
    );
}
