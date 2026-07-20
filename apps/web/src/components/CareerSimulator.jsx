import { useState } from 'react';
import './CareerSimulator.css';

const TREND_META = {
    growing: { label: 'Đang tăng trưởng', icon: '▲', tone: 'up' },
    stable: { label: 'Ổn định', icon: '●', tone: 'stable' },
    declining: { label: 'Đang giảm', icon: '▼', tone: 'down' },
};

function jobMatchDelta(current, simulated) {
    if (current <= 0) return simulated > 0 ? 100 : 0;
    return Math.round(((simulated - current) / current) * 100);
}

function rangeMeterStyle(salary) {
    const { p25_salary_mvnd: p25, p75_salary_mvnd: p75, median_salary_mvnd: median } = salary;
    if (!(p75 > p25)) return { markerLeft: '50%' };
    const pct = Math.min(100, Math.max(0, ((median - p25) / (p75 - p25)) * 100));
    return { markerLeft: `${pct}%` };
}

/**
 * "What if I learned this technology?" — GET /career/simulate. Three KPI stat-tiles
 * (job match delta, salary range meter, trend badge + confidence meter), built per the
 * dataviz skill: status color only (never bare color), meters share one ramp track/fill,
 * icon+label always paired with a status color.
 */
export default function CareerSimulator({ suggestions = [], onSimulate }) {
    const [technology, setTechnology] = useState('');
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        const tech = technology.trim();
        if (!tech) return;
        setLoading(true);
        setError('');
        try {
            const data = await onSimulate(tech);
            setResult(data);
        } catch (err) {
            setResult(null);
            setError(err.message || 'Không thể mô phỏng. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    const delta = result ? jobMatchDelta(result.current_job_matches, result.simulated_job_matches) : 0;
    const trend = result?.forecast?.predicted_direction ? TREND_META[result.forecast.predicted_direction] : null;
    const confidence = result?.forecast?.confidence != null ? Math.round(result.forecast.confidence * 100) : null;

    return (
        <div className="card simulator-card">
            <h2 className="section-title">Mô phỏng lộ trình</h2>
            <p className="simulator-subtitle">
                Thử nhập một công nghệ bất kỳ — xem ngay tác động lên số job phù hợp, mức lương thị trường và xu hướng.
            </p>

            <form onSubmit={handleSubmit} className="simulator-form">
                <input
                    type="text"
                    className="form-input"
                    value={technology}
                    onChange={(e) => setTechnology(e.target.value)}
                    placeholder="VD: Kubernetes, Rust, Terraform..."
                    list="simulator-suggestions"
                />
                <datalist id="simulator-suggestions">
                    {suggestions.map((s) => <option key={s} value={s} />)}
                </datalist>
                <button type="submit" className="btn btn-primary simulator-submit-btn" disabled={loading || !technology.trim()}>
                    {loading ? (<><span className="btn-spinner" /> Đang mô phỏng...</>) : 'Mô phỏng'}
                </button>
            </form>

            {error && <div className="career-error">{error}</div>}

            {result && (
                <div className="simulator-tiles">
                    <div className="sim-tile">
                        <span className="sim-tile-label">Job phù hợp</span>
                        <div className="sim-tile-value-row">
                            <span className="sim-tile-before">{result.current_job_matches}</span>
                            <span className="sim-tile-arrow">→</span>
                            <span className="sim-tile-value">{result.simulated_job_matches}</span>
                        </div>
                        <span className={`sim-delta-badge ${delta >= 0 ? 'up' : 'down'}`}>
                            {delta >= 0 ? '▲' : '▼'} {delta >= 0 ? '+' : ''}{delta}%
                        </span>
                    </div>

                    <div className="sim-tile">
                        <span className="sim-tile-label">Lương thị trường ({result.technology})</span>
                        {result.salary ? (
                            <>
                                <div className="sim-tile-value">{result.salary.median_salary_mvnd.toFixed(0)} <small>triệu/tháng</small></div>
                                <div className="sim-range-meter">
                                    <div className="sim-range-track">
                                        <div className="sim-range-marker" style={{ left: rangeMeterStyle(result.salary).markerLeft }} />
                                    </div>
                                    <div className="sim-range-labels">
                                        <span>{result.salary.p25_salary_mvnd.toFixed(0)}tr</span>
                                        <span>{result.salary.p75_salary_mvnd.toFixed(0)}tr</span>
                                    </div>
                                </div>
                            </>
                        ) : (
                            <p className="sim-tile-empty">Chưa đủ dữ liệu lương cho công nghệ này</p>
                        )}
                    </div>

                    <div className="sim-tile">
                        <span className="sim-tile-label">Xu hướng 6 tháng tới</span>
                        {trend ? (
                            <>
                                <span className={`sim-trend-badge sim-trend--${trend.tone}`}>
                                    {trend.icon} {trend.label}
                                </span>
                                {confidence != null && (
                                    <div className="sim-confidence-meter">
                                        <div className="sim-confidence-track">
                                            <div className="sim-confidence-fill" style={{ width: `${confidence}%` }} />
                                        </div>
                                        <span className="sim-confidence-label">{confidence}% tin cậy</span>
                                    </div>
                                )}
                                {result.forecast.reasoning && (
                                    <p className="sim-reasoning">{result.forecast.reasoning}</p>
                                )}
                            </>
                        ) : (
                            <p className="sim-tile-empty">Chưa đủ dữ liệu để dự báo</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
