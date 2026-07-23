import { useEffect, useState } from 'react';
import Modal from '../common/Modal';
import { fetchDataPlatformJobHistory } from '../../api/adminService';
import type { DataPlatformJobRun } from '../../types/admin';
import './JobHistoryModal.css';

interface JobHistoryModalProps {
    jobId: string;
    jobLabel: string;
    onClose: () => void;
}

function formatDuration(durationS: number | null): string {
    if (durationS == null) return '—';
    if (durationS < 60) return `${Math.round(durationS)}s`;
    const minutes = Math.floor(durationS / 60);
    const seconds = Math.round(durationS % 60);
    return `${minutes}m ${seconds}s`;
}

export default function JobHistoryModal({ jobId, jobLabel, onClose }: JobHistoryModalProps) {
    const [runs, setRuns] = useState<DataPlatformJobRun[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchDataPlatformJobHistory(jobId)
            .then((res) => setRuns(res?.data || []))
            .catch(() => setError('Không tải được lịch sử chạy. Vui lòng thử lại.'))
            .finally(() => setLoading(false));
    }, [jobId]);

    return (
        <Modal title={`Lịch sử: ${jobLabel}`} onClose={onClose} width="560px">
            {loading ? (
                <p className="text-3 text-sm">Đang tải...</p>
            ) : error ? (
                <p className="cluster-pipeline-error">{error}</p>
            ) : runs.length === 0 ? (
                <p className="text-3 text-sm">Chưa có lần chạy nào được ghi nhận.</p>
            ) : (
                <div className="job-history-list">
                    {runs.map((run) => (
                        <div className="job-history-row" key={run.id}>
                            <span className={`job-history-badge job-history-badge--${run.status}`}>
                                {run.status === 'success' ? 'Thành công' : run.status === 'failed' ? 'Thất bại' : run.status}
                            </span>
                            <div className="job-history-details">
                                <p className="job-history-time">
                                    {run.started_at ? new Date(run.started_at).toLocaleString('vi-VN') : '—'}
                                    <span className="job-history-duration"> · {formatDuration(run.duration_s)}</span>
                                </p>
                                {run.status === 'success' && run.rows_affected != null && (
                                    <p className="text-3 text-sm">{run.rows_affected} dòng đã xử lý</p>
                                )}
                                {run.status === 'failed' && run.error_msg && (
                                    <p className="cluster-pipeline-error">{run.error_msg}</p>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </Modal>
    );
}
