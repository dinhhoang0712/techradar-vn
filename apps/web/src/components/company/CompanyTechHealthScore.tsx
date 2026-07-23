import { useEffect, useState } from 'react';
import { getCompanyTechHealthScore } from '../../api/companyService';
import RingGauge from '../common/RingGauge';
import type { CompanyTechHealthScore as CompanyTechHealthScoreData } from '../../types/company';

// Company Tech Health Score — điểm 0-100 dựa trên tech_analytics (dữ liệu thật, đã ETL), không phải
// LLM call, nên fetch ngay khi panel mở thay vì chờ người dùng bấm (khác với CompanyAiInsight).
export default function CompanyTechHealthScore({ companyId }: { companyId: string }) {
    const [loading, setLoading] = useState(true);
    const [data, setData] = useState<CompanyTechHealthScoreData | null>(null);

    useEffect(() => {
        let cancelled = false;
        const fetchScore = () => {
            setLoading(true);
            getCompanyTechHealthScore(companyId)
                .then(res => { if (!cancelled) setData(res?.data ?? null); })
                .catch(() => { if (!cancelled) setData(null); })
                .finally(() => { if (!cancelled) setLoading(false); });
        };
        fetchScore();
        return () => { cancelled = true; };
    }, [companyId]);

    if (loading) {
        return (
            <div className="tech-health-score tech-health-score--loading">
                <div className="loading-spinner" />
            </div>
        );
    }

    if (!data || !data.available) {
        return (
            <div className="tech-health-score tech-health-score--empty">
                <p className="tech-health-empty-hint">Chưa đủ dữ liệu để đánh giá Tech Health Score.</p>
            </div>
        );
    }

    const tone = data.score >= 70 ? 'good' : data.score >= 45 ? 'neutral' : 'risk';

    return (
        <div className={`tech-health-score tech-health-score--${tone}`}>
            <div className="tech-health-score-main">
                <RingGauge percent={data.score} size={72} strokeWidth={7} label={data.score} className={`tech-health-ring tech-health-ring--${tone}`} />
                <div className="tech-health-score-copy">
                    <p className="tech-health-label">{data.label}</p>
                    <p className="tech-health-coverage">
                        Dựa trên {data.tracked_count}/{data.stack_size} công nghệ có dữ liệu xu hướng
                    </p>
                </div>
            </div>

            {(data.strengths.length > 0 || data.watch_outs.length > 0) && (
                <div className="tech-health-highlights">
                    {data.strengths.length > 0 && (
                        <div className="tech-health-highlight-row">
                            <span className="tech-health-highlight-label">Điểm mạnh</span>
                            <div className="tech-health-chips">
                                {data.strengths.map(t => (
                                    <span key={t} className="tech-health-chip tech-health-chip--strength">{t}</span>
                                ))}
                            </div>
                        </div>
                    )}
                    {data.watch_outs.length > 0 && (
                        <div className="tech-health-highlight-row">
                            <span className="tech-health-highlight-label">Cần lưu ý</span>
                            <div className="tech-health-chips">
                                {data.watch_outs.map(t => (
                                    <span key={t} className="tech-health-chip tech-health-chip--risk">{t}</span>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
