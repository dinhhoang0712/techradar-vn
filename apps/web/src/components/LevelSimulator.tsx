import { useState } from 'react';
import type { FormEvent } from 'react';
import './CareerSimulator.css';
import type { LevelMoveSimulationResult, CareerSimulationSalary } from '../types/career';
import { EXPERIENCE_LEVELS } from '../constants/experienceLevels';

function jobMatchDelta(current: number, simulated: number): number {
    if (current <= 0) return simulated > 0 ? 100 : 0;
    return Math.round(((simulated - current) / current) * 100);
}

function rangeMeterStyle(salary: CareerSimulationSalary): { markerLeft: string } {
    const { p25_salary_mvnd: p25, p75_salary_mvnd: p75, median_salary_mvnd: median } = salary;
    if (!(p75 > p25)) return { markerLeft: '50%' };
    const pct = Math.min(100, Math.max(0, ((median - p25) / (p75 - p25)) * 100));
    return { markerLeft: `${pct}%` };
}

interface LevelSimulatorProps {
    onSimulate: (targetLevel: string) => Promise<LevelMoveSimulationResult>;
}

/**
 * "What if I moved to this experience level?" — GET /career/simulate-level. Sibling of
 * CareerSimulator ("what if I learned this technology?"), same tile/meter treatment (dataviz
 * skill: status color only, one ramp for the range meter), minus the trend tile — there is no
 * "trend forecast" concept for a level the way there is for a technology's adoption.
 */
export default function LevelSimulator({ onSimulate }: LevelSimulatorProps) {
    const [targetLevel, setTargetLevel] = useState('');
    const [result, setResult] = useState<LevelMoveSimulationResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!targetLevel) return;
        setLoading(true);
        setError('');
        try {
            const data = await onSimulate(targetLevel);
            setResult(data);
        } catch (err) {
            setResult(null);
            setError((err as Error).message || 'Không thể mô phỏng. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    const delta = result ? jobMatchDelta(result.current_job_matches, result.simulated_job_matches) : 0;

    return (
        <div className="card simulator-card">
            <h2 className="section-title">Mô phỏng lên cấp</h2>
            <p className="simulator-subtitle">
                Thử chọn một cấp độ mục tiêu — xem ngay số job phù hợp và mức lương thị trường ở cấp độ đó,
                với kỹ năng hiện tại của bạn.
            </p>

            <form onSubmit={handleSubmit} className="simulator-form">
                <select
                    className="form-input"
                    value={targetLevel}
                    onChange={(e) => setTargetLevel(e.target.value)}
                >
                    <option value="">Chọn cấp độ mục tiêu</option>
                    {EXPERIENCE_LEVELS.map(l => (
                        <option key={l} value={l}>{l}</option>
                    ))}
                </select>
                <button type="submit" className="btn btn-primary simulator-submit-btn" disabled={loading || !targetLevel}>
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
                        <span className="sim-tile-label">Lương thị trường ({result.target_level})</span>
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
                            <p className="sim-tile-empty">Chưa đủ dữ liệu lương cho cấp độ này</p>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
