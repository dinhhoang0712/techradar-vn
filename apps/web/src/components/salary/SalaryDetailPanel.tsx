import type { CSSProperties } from 'react';
import { getSalaryByTech } from '../../api/salaryService';
import { useAsync } from '../../hooks/useAsync';
import { SALARY_COLORS, formatM } from '../../utils/salaryFormat';
import type { SalaryTech } from '../../types/salary';

// Panel chi tiết lương của 1 công nghệ — SalaryPage remount panel này (qua `key`) mỗi khi đổi lựa
// chọn, nên tự fetch chi tiết theo `tech` khi mount thay vì nhận dữ liệu sẵn từ ngoài.
export default function SalaryDetailPanel({ tech, onClose }: { tech: SalaryTech; onClose: () => void }) {
    const { data: detail, loading } = useAsync(
        () => getSalaryByTech(tech.tech_name).then(res => res?.data ?? null),
        [tech.tech_name],
    );

    const data = detail ?? tech;

    return (
        <div className="detail-panel">
            <div className="detail-header">
                <h3 className="detail-title">{tech.tech_name}</h3>
                <button className="detail-close" onClick={onClose}>✕</button>
            </div>

            {loading ? (
                <div className="detail-loading"><div className="loading-spinner" /></div>
            ) : (
                <>
                    <div className="detail-stats-grid">
                        <div className="detail-stat">
                            <span className="ds-label">Median</span>
                            <span className="ds-value green">{formatM(data.median_salary_mvnd)} VND</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">Trung bình</span>
                            <span className="ds-value">{formatM(data.avg_salary_mvnd)} VND</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">P25 – P75</span>
                            <span className="ds-value">{formatM(data.p25_salary_mvnd)} – {formatM(data.p75_salary_mvnd)}</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">Min – Max</span>
                            <span className="ds-value">{formatM(data.min_salary_mvnd)} – {formatM(data.max_salary_mvnd)}</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">Tổng jobs</span>
                            <span className="ds-value">{data.total_jobs?.toLocaleString()}</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">Jobs có lương</span>
                            <span className="ds-value">
                                {data.jobs_with_salary?.toLocaleString()}
                                <span className="ds-pct">
                                    ({data.total_jobs ? Math.round(data.jobs_with_salary / data.total_jobs * 100) : 0}%)
                                </span>
                            </span>
                        </div>
                    </div>

                    {data.top_co_techs && data.top_co_techs.length > 0 && (
                        <div className="detail-cotechs">
                            <p className="detail-section-label">Thường yêu cầu cùng</p>
                            <div className="cotech-chips">
                                {data.top_co_techs.map((t, i) => (
                                    <span key={t} className="cotech-chip" style={{ '--chip-color': SALARY_COLORS[i % SALARY_COLORS.length] } as CSSProperties}>
                                        {t}
                                    </span>
                                ))}
                            </div>
                        </div>
                    )}

                    <div className="detail-salary-bar">
                        <p className="detail-section-label">Phân phối lương</p>
                        <div className="salary-bar-track">
                            <div
                                className="salary-bar-range"
                                style={{
                                    left: `${(data.min_salary_mvnd ?? 0) / (data.max_salary_mvnd || 1) * 100}%`,
                                    '--bar-width': `${((data.p75_salary_mvnd ?? 0) - (data.p25_salary_mvnd ?? 0)) / (data.max_salary_mvnd || 1) * 100}%`,
                                } as CSSProperties}
                            />
                            <div
                                className="salary-bar-median"
                                style={{ left: `${data.median_salary_mvnd / (data.max_salary_mvnd || 1) * 100}%` }}
                                title={`Median: ${formatM(data.median_salary_mvnd)}`}
                            />
                        </div>
                        <div className="salary-bar-labels">
                            <span>{formatM(data.min_salary_mvnd)}</span>
                            <span>Median: {formatM(data.median_salary_mvnd)}</span>
                            <span>{formatM(data.max_salary_mvnd)}</span>
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}
