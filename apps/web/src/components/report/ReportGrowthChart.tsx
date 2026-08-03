import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ReferenceLine, ResponsiveContainer, Cell } from 'recharts';
import type { TooltipContentProps } from 'recharts';
import type { ValueType, NameType } from 'recharts/types/component/DefaultTooltipContent';
import type { ReportTechRow } from '../../types/report';

function GrowthTooltip({ active, payload }: Partial<TooltipContentProps<ValueType, NameType>>) {
    if (!active || !payload?.length) return null;
    const point = payload[0];
    const value = Number(point.value ?? 0);
    return (
        <div className="chart-tooltip">
            <p className="tooltip-month">{String(point.payload?.name ?? '')}</p>
            <div className="tooltip-row">
                <span className="tooltip-dot" style={{ background: point.color }} />
                <span className="tooltip-tech">Tăng trưởng</span>
                <span className={`tooltip-jobs ${value >= 0 ? 'up' : 'down'}`}>
                    {value >= 0 ? '+' : ''}{value.toFixed(1)}%
                </span>
            </div>
        </div>
    );
}

interface ReportGrowthChartProps {
    techs: ReportTechRow[];
}

// So sánh growth_rate giữa các công nghệ có dữ liệu analytics (bảng chỉ có sparkbar, không đủ để
// so trực quan nhiều dòng cùng lúc) — chỉ vẽ các dòng có growth_rate (nguồn 'analytics').
export default function ReportGrowthChart({ techs }: ReportGrowthChartProps) {
    const data = techs
        .filter(t => t.growth_rate != null)
        .map(t => ({ name: t.name, growth_rate: Number(t.growth_rate) }));

    if (data.length === 0) return null;

    const chartHeight = Math.max(180, data.length * 32);

    return (
        <div className="card report-chart-card">
            <h2 className="section-title">So sánh tăng trưởng</h2>
            <ResponsiveContainer width="100%" height={chartHeight}>
                <BarChart data={data} layout="vertical" margin={{ top: 5, right: 24, left: 10, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
                    <XAxis type="number" tick={{ fill: 'var(--text-3)', fontSize: 11 }} unit="%" />
                    <YAxis
                        type="category"
                        dataKey="name"
                        width={140}
                        tick={{ fill: 'var(--text-2)', fontSize: 11 }}
                    />
                    <ReferenceLine x={0} stroke="var(--border-2)" />
                    <Tooltip content={<GrowthTooltip />} cursor={{ fill: 'var(--surface)' }} />
                    <Bar dataKey="growth_rate" radius={[3, 3, 3, 3]}>
                        {data.map(d => (
                            <Cell key={d.name} fill={d.growth_rate >= 0 ? 'var(--green)' : 'var(--danger)'} />
                        ))}
                    </Bar>
                </BarChart>
            </ResponsiveContainer>
        </div>
    );
}
