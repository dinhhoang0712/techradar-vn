import {
    Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Legend, Tooltip, ResponsiveContainer
} from 'recharts';
import { buildTechRadarData, CATEGORY_ORDER } from '../../utils/techCategory';

function buildOverlayData(series) {
    const perSeries = series.map(s => buildTechRadarData(s.techStack));
    return CATEGORY_ORDER.map((category, i) => {
        const row = { category };
        series.forEach((s, si) => { row[s.name] = perSeries[si][i]?.value || 0; });
        return row;
    });
}

function RadarTooltip({ active, payload, label }) {
    if (!active || !payload?.length) return null;
    return (
        <div className="chart-tooltip">
            <p className="tooltip-month">{label}</p>
            {payload.map(p => (
                <div key={p.dataKey} className="tooltip-row">
                    <span className="tooltip-dot" style={{ background: p.color }} />
                    <span className="tooltip-tech">{p.name}</span>
                    <span className="tooltip-jobs">{p.value}</span>
                </div>
            ))}
        </div>
    );
}

// "Dấu vân tay công nghệ" của một hoặc nhiều công ty — mỗi trục là 1 nhóm công nghệ
// (Frontend/Backend/Data/Infra.../AI/Khác), giá trị là số lượng tech trong tech_stack thuộc nhóm đó.
// `series`: [{ name, techStack, color }] — truyền 1 phần tử để xem 1 công ty, nhiều phần tử để so sánh.
export default function TechRadarChart({ series, height = 280 }) {
    if (!series?.length) return null;
    const data = buildOverlayData(series);
    const maxValue = Math.max(1, ...data.flatMap(row => series.map(s => row[s.name] || 0)));

    return (
        <ResponsiveContainer width="100%" height={height}>
            <RadarChart data={data} outerRadius="72%">
                <PolarGrid stroke="var(--border)" />
                <PolarAngleAxis dataKey="category" tick={{ fill: 'var(--text-3)', fontSize: 11 }} />
                <PolarRadiusAxis angle={90} tick={{ fill: 'var(--text-3)', fontSize: 10 }} domain={[0, maxValue]} tickCount={4} />
                {series.map(s => (
                    <Radar
                        key={s.name}
                        name={s.name}
                        dataKey={s.name}
                        stroke={s.color}
                        fill={s.color}
                        fillOpacity={series.length > 1 ? 0.15 : 0.35}
                    />
                ))}
                <Tooltip content={<RadarTooltip />} />
                {series.length > 1 && <Legend wrapperStyle={{ fontSize: '0.78rem', color: 'var(--text-2)' }} />}
            </RadarChart>
        </ResponsiveContainer>
    );
}
