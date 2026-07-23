import {
    ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import type { TooltipContentProps } from 'recharts';
import type { ValueType, NameType } from 'recharts/types/component/DefaultTooltipContent';
import type { Forecast } from '../../types/forecast';

function TrendHistoryTooltip({ active, payload, label }: Partial<TooltipContentProps<ValueType, NameType>>) {
    if (!active || !payload?.length) return null;
    const jobPoint = payload.find(p => p.dataKey === 'job_count');
    const growthPoint = payload.find(p => p.dataKey === 'growth_rate');
    return (
        <div className="chart-tooltip">
            <p className="tooltip-month">{label}</p>
            {jobPoint && (
                <div className="tooltip-row">
                    <span className="tooltip-dot" style={{ background: jobPoint.color }} />
                    <span className="tooltip-tech">Số việc làm</span>
                    <span className="tooltip-jobs">{jobPoint.value?.toLocaleString() ?? '—'}</span>
                </div>
            )}
            {growthPoint && growthPoint.value != null && (
                <div className="tooltip-row">
                    <span className="tooltip-dot" style={{ background: growthPoint.color }} />
                    <span className="tooltip-tech">Tăng trưởng</span>
                    <span className={`tooltip-jobs ${Number(growthPoint.value) >= 0 ? 'up' : 'down'}`}>
                        {Number(growthPoint.value) >= 0 ? '+' : ''}{Number(growthPoint.value).toFixed(1)}%
                    </span>
                </div>
            )}
        </div>
    );
}

interface ForecastPanelProps {
    tech: string;
    loading: boolean;
    forecast: Forecast | null;
    onClose: () => void;
}

// Panel dự báo xu hướng cho 1 công nghệ (GET /forecast): chiều dự báo, lịch sử job/tăng trưởng,
// và các tín hiệu (kèm trọng số) mà mô hình dùng để dự báo.
export default function ForecastPanel({ tech, loading, forecast, onClose }: ForecastPanelProps) {
    return (
        <div className="card forecast-panel" style={{ marginTop: 16 }}>
            <div className="forecast-header">
                <h2 className="section-title">Dự báo xu hướng: <span style={{ color: 'var(--primary)' }}>{tech}</span></h2>
                <button className="forecast-close-btn" onClick={onClose}>✕</button>
            </div>

            {loading ? (
                <div className="forecast-loading">Đang phân tích dữ liệu...</div>
            ) : forecast ? (
                <div className="forecast-content">
                    <div className="forecast-direction-row">
                        <span className={`forecast-direction forecast-direction--${forecast.predicted_direction}`}>
                            {forecast.predicted_direction === 'growing' ? '↑ Tăng trưởng' :
                             forecast.predicted_direction === 'declining' ? '↓ Suy giảm' : '→ Ổn định'}
                        </span>
                        <span className="forecast-confidence">
                            Độ tin cậy: {Math.round((forecast.confidence ?? 0) * 100)}%
                        </span>
                    </div>
                    {forecast.reasoning && (
                        <p className="forecast-reasoning">{forecast.reasoning}</p>
                    )}
                    {forecast.trend_data && forecast.trend_data.length > 0 && (
                        <div className="forecast-trend-history">
                            <p className="forecast-subsection-title">Lịch sử xu hướng (căn cứ dự báo)</p>
                            <ResponsiveContainer width="100%" height={200}>
                                <ComposedChart data={forecast.trend_data} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                    <XAxis dataKey="month" tick={{ fill: 'var(--text-3)', fontSize: 11 }} />
                                    <YAxis yAxisId="jobs" tick={{ fill: 'var(--text-3)', fontSize: 11 }} width={44} />
                                    <YAxis yAxisId="growth" orientation="right" tick={{ fill: 'var(--text-3)', fontSize: 11 }} unit="%" width={44} />
                                    <Tooltip content={<TrendHistoryTooltip />} />
                                    <Legend wrapperStyle={{ paddingTop: 8, fontSize: '0.78rem', color: 'var(--text-2)' }} />
                                    <Bar yAxisId="jobs" dataKey="job_count" name="Số việc làm" fill="var(--primary-glow)" radius={[3, 3, 0, 0]} />
                                    <Line yAxisId="growth" type="monotone" dataKey="growth_rate" name="Tăng trưởng %" stroke="var(--primary)" strokeWidth={2} dot={{ r: 3 }} connectNulls />
                                </ComposedChart>
                            </ResponsiveContainer>
                        </div>
                    )}
                    {forecast.signals && forecast.signals.length > 0 && (
                        <div className="forecast-signals">
                            <p className="forecast-subsection-title">Tín hiệu &amp; trọng số đóng góp vào dự báo</p>
                            {[...forecast.signals]
                                .sort((a, b) => (b.weight ?? 0) - (a.weight ?? 0))
                                .map((s, i) => (
                                    <div key={i} className="forecast-signal">
                                        <div className="forecast-signal-main">
                                            <span className="signal-label">{s.signal}</span>
                                            <span className="signal-value">{typeof s.value === 'number' ? s.value.toFixed(2) : s.value}</span>
                                        </div>
                                        {typeof s.weight === 'number' && (
                                            <div className="signal-weight-row">
                                                <div className="signal-weight-track">
                                                    <div className="signal-weight-fill" style={{ width: `${Math.round(s.weight * 100)}%` }} />
                                                </div>
                                                <span className="signal-weight-label">Trọng số {Math.round(s.weight * 100)}%</span>
                                            </div>
                                        )}
                                    </div>
                                ))}
                        </div>
                    )}
                </div>
            ) : (
                <div className="forecast-loading">Không có dữ liệu dự báo cho công nghệ này.</div>
            )}
        </div>
    );
}
