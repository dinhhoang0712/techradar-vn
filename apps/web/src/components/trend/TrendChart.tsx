import {
    LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid,
    Tooltip, Legend, ResponsiveContainer, ReferenceLine,
} from 'recharts';
import type { TooltipContentProps } from 'recharts';
import type { ValueType, NameType } from 'recharts/types/component/DefaultTooltipContent';

function CustomTooltip({ active, payload, label }: Partial<TooltipContentProps<ValueType, NameType>>) {
    if (!active || !payload?.length) return null;
    return (
        <div className="chart-tooltip">
            <p className="tooltip-month">{label}</p>
            {payload.map(p => (
                <div key={p.dataKey} className="tooltip-row">
                    <span className="tooltip-dot" style={{ background: p.color }} />
                    <span className="tooltip-tech">{p.name}</span>
                    <span className="tooltip-jobs">{p.value?.toLocaleString()} jobs</span>
                </div>
            ))}
        </div>
    );
}

function GrowthTooltip({ active, payload, label }: Partial<TooltipContentProps<ValueType, NameType>>) {
    if (!active || !payload?.length) return null;
    return (
        <div className="chart-tooltip">
            <p className="tooltip-month">{label}</p>
            {payload.map(p => (
                <div key={p.dataKey} className="tooltip-row">
                    <span className="tooltip-dot" style={{ background: p.color }} />
                    <span className="tooltip-tech">{p.name}</span>
                    <span className={`tooltip-jobs ${Number(p.value) >= 0 ? 'up' : 'down'}`}>
                        {Number(p.value) >= 0 ? '+' : ''}{p.value}%
                    </span>
                </div>
            ))}
        </div>
    );
}

interface TrendChartProps {
    chartMode: 'line' | 'bar' | 'growth';
    visibleData: Record<string, unknown>[];
    activeTechIds: string[];
    colorMap: Record<string, string>;
    growthAxisDomain: [number, number];
    jobAxisDomain: [number, number];
    loadingChart: boolean;
}

// Biểu đồ chính của TrendDashboard: chuyển giữa 3 dạng (line/bar số lượng job, hoặc line tăng
// trưởng %) theo `chartMode`, mỗi công nghệ 1 series theo `colorMap`.
export default function TrendChart({ chartMode, visibleData, activeTechIds, colorMap, growthAxisDomain, jobAxisDomain, loadingChart }: TrendChartProps) {
    return (
        <div className="card" id="main-chart-wrapper" style={{ marginTop: 16 }}>
            <div className="flex-between" style={{ marginBottom: 16 }}>
                <h2 className="section-title">
                    {chartMode === 'growth' ? 'Tăng trưởng % theo thời gian' : 'Số lượng Job Postings theo thời gian'}
                </h2>
                {loadingChart && <span style={{ color: 'var(--text-3)', fontSize: '0.8rem' }}>Đang cập nhật...</span>}
            </div>
            <ResponsiveContainer width="100%" height={360}>
                {chartMode === 'bar' ? (
                    <BarChart data={visibleData} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis dataKey="month" tick={{ fill: 'var(--text-3)', fontSize: 11 }} />
                        <YAxis tick={{ fill: 'var(--text-3)', fontSize: 11 }} domain={jobAxisDomain} />
                        <Tooltip content={<CustomTooltip />} />
                        <Legend wrapperStyle={{ paddingTop: 12, fontSize: '0.8rem', color: 'var(--text-2)' }} />
                        {activeTechIds.map(id => (
                            <Bar key={id} dataKey={id} fill={colorMap[id]} name={id} radius={[3, 3, 0, 0]} />
                        ))}
                    </BarChart>
                ) : (
                    <LineChart data={visibleData} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis dataKey="month" tick={{ fill: 'var(--text-3)', fontSize: 11 }} />
                        <YAxis
                            tick={{ fill: 'var(--text-3)', fontSize: 11 }}
                            unit={chartMode === 'growth' ? '%' : ''}
                            domain={chartMode === 'growth' ? growthAxisDomain : jobAxisDomain}
                        />
                        {chartMode === 'growth' && <ReferenceLine y={0} stroke="var(--border-2)" strokeDasharray="4 4" />}
                        <Tooltip content={chartMode === 'growth' ? <GrowthTooltip /> : <CustomTooltip />} />
                        <Legend wrapperStyle={{ paddingTop: 12, fontSize: '0.8rem', color: 'var(--text-2)' }} />
                        {activeTechIds.map(id => (
                            <Line key={id} type="monotone" dataKey={id}
                                stroke={colorMap[id]} strokeWidth={2} dot={false}
                                activeDot={{ r: 5, strokeWidth: 0 }} name={id}
                            />
                        ))}
                    </LineChart>
                )}
            </ResponsiveContainer>
        </div>
    );
}
