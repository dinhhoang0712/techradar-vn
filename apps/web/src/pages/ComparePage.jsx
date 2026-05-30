import { useState, useEffect, useMemo } from 'react';
import {
    LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
    Legend, ResponsiveContainer, ReferenceLine
} from 'recharts';
import CreatableSelect from 'react-select/creatable';
import { getCompareSearch } from '../api/compareService';
import { getRadarTop10 } from '../api/trendService';
import './ComparePage.css';

const PALETTE = [
    '#6C63FF', '#00D68F', '#FF6584', '#FFC94D', '#54C5F8',
    '#FF8C00', '#7FBA00', '#E040FB', '#FF5252', '#00B4D8'
];

// Để chọn tạm các công nghệ
const DEFAULT_TECHS = [];

function roundAxisLimit(value) {
    if (value <= 100) return 100;
    if (value <= 250) return Math.ceil(value / 50) * 50;
    return Math.ceil(value / 100) * 100;
}

function getAxisLimit(value) {
    if (value < 99) return 100;
    return roundAxisLimit(value * 1.15);
}

function getPercentAxisDomain(data, keys) {
    const values = data.flatMap(row => keys.map(key => Number(row[key] || 0)));
    const maxGrowth = Math.max(0, ...values);
    const minGrowth = Math.min(0, ...values);
    const maxValue = getAxisLimit(maxGrowth);
    const minValue = -getAxisLimit(Math.abs(minGrowth));

    return [minValue, maxValue];
}

const selectStyles = {
    control: (b) => ({ ...b, background: 'var(--surface-2)', borderColor: 'var(--border)', color: 'var(--text)', minHeight: 38, boxShadow: 'none', '&:hover': { borderColor: 'var(--primary)' } }),
    menu: (b) => ({ ...b, background: 'var(--surface-2)', border: '1px solid var(--border)', zIndex: 200 }),
    option: (b, s) => ({ ...b, background: s.isFocused ? 'var(--surface)' : 'transparent', color: 'var(--text)', cursor: 'pointer' }),
    multiValue: (b) => ({ ...b, background: 'var(--primary-glow)', borderRadius: 4 }),
    multiValueLabel: (b) => ({ ...b, color: 'var(--primary-light)' }),
    multiValueRemove: (b) => ({ ...b, color: 'var(--primary-light)', '&:hover': { background: 'var(--primary)', color: '#fff' } }),
    input: (b) => ({ ...b, color: 'var(--text)' }),
    placeholder: (b) => ({ ...b, color: 'var(--text-3)' }),
};

function formatPercent(value) {
    const n = Number(value || 0);
    return `${n >= 0 ? '+' : ''}${n.toFixed(2)}%`;
}

function CompareTooltip({ active, payload, label }) {
    if (!active || !payload?.length) return null;
    return (
        <div className="chart-tooltip">
            <p className="tooltip-month">{label}</p>
            {payload.map(p => (
                <div key={p.dataKey} className="tooltip-row">
                    <span className="tooltip-dot" style={{ background: p.color }} />
                    <span className="tooltip-tech">{p.name}</span>
                    <span className={`tooltip-jobs ${p.value >= 0 ? 'up' : 'down'}`}>{formatPercent(p.value)}</span>
                </div>
            ))}
        </div>
    );
}

export default function ComparePage() {
    const [selectedTechs, setSelectedTechs] = useState(DEFAULT_TECHS);
    const [timeRange, setTimeRange] = useState(12);
    const [compareData, setCompareData] = useState([]);
    const [techOptions, setTechOptions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const activeTechIds = selectedTechs.map(t => t.value);

    const colorMap = useMemo(() => {
        const map = {};
        selectedTechs.forEach((t, i) => { map[t.value] = t.color || PALETTE[i % PALETTE.length]; });
        return map;
    }, [selectedTechs]);

    // Lấy danh sách gợi ý từ Backend
    useEffect(() => {
        const fetchOptions = async () => {
            try {
                const res = await getRadarTop10();
                if (res?.data) {
                    const opts = res.data.map((item, i) => ({
                        value: item.keyword,
                        label: item.keyword,
                        color: PALETTE[i % PALETTE.length]
                    }));
                    setTechOptions(opts);
                    setSelectedTechs(prev => prev.length ? prev : opts.slice(0, 2));
                }
            } catch (e) {
                console.warn("Could not fetch options", e);
            }
        };
        fetchOptions();
    }, []);

    useEffect(() => {
        if (selectedTechs.length === 0) {
            setCompareData([]);
            return;
        }

        const fetchCompare = async () => {
            setLoading(true);
            setError('');
            try {
                const keywords = selectedTechs.map(t => t.value);
                const res = await getCompareSearch(keywords, timeRange);
                if (res?.data) {
                    setCompareData(res.data);
                } else {
                    setCompareData([]);
                }
            } catch (err) {
                console.error("Lỗi Compare API:", err);
                setError('Không thể tải dữ liệu so sánh.');
            } finally {
                setLoading(false);
            }
        };
        fetchCompare();
    }, [selectedTechs, timeRange]);

    // Trích xuất thống kê từ dữ liệu backend
    const statsArr = useMemo(() => {
        if (!compareData || !compareData.length) return [];
        return compareData.map((techItem) => {
            return {
                id: techItem.keyword,
                name: techItem.keyword,
                color: colorMap[techItem.keyword] || '#fff',
                yoy: techItem.yoy_rate || 0,
                mom: techItem.mom_rate || 0,
                total: techItem.growth_rate || 0,
            };
        });
    }, [compareData, colorMap]);

    const chartData = useMemo(() => {
        if (!compareData || !compareData.length) return [];
        const mergedMap = {};

        compareData.forEach(techItem => {
            const kw = techItem.keyword;
            const history = techItem.monthly || [];
            
            let baseVal = null;
            history.forEach(point => {
                const m = `T${point.month}/${point.year}`; 
                
                // Cập nhật baseVal là giá trị khác 0 đầu tiên
                if (baseVal === null && point.job_count > 0) {
                    baseVal = point.job_count;
                }
                
                if (!mergedMap[m]) {
                    mergedMap[m] = { month: m, rawSort: point.year * 100 + point.month };
                }
                mergedMap[m][kw] = point.job_count || 0; 
            });
        });

        const sorted = Object.values(mergedMap).sort((a, b) => a.rawSort - b.rawSort);
        const sliceStart = Math.max(0, sorted.length - timeRange);
        const visibleRows = sorted.slice(sliceStart);
        const baseVals = {};

        activeTechIds.forEach(kw => {
            const firstValidRow = visibleRows.find(row => row[kw] > 0);
            baseVals[kw] = firstValidRow ? firstValidRow[kw] : null;
        });

        return visibleRows.map(row => {
            const growthRow = { month: row.month, rawSort: row.rawSort };
            activeTechIds.forEach(kw => {
                const baseVal = baseVals[kw];
                growthRow[kw] = baseVal !== null && baseVal > 0
                    ? Number((((Number(row[kw] || 0) - baseVal) / baseVal) * 100).toFixed(2))
                    : 0;
            });
            return growthRow;
        });
    }, [compareData, timeRange, activeTechIds]);

    const chartAxisDomain = useMemo(() =>
        getPercentAxisDomain(chartData, activeTechIds),
        [chartData, activeTechIds]
    );

    return (
        <div className="compare-page">
            {/* Controls */}
            <div className="card compare-controls">
                <div className="control-group" style={{ flex: 1, maxWidth: 500 }}>
                    <label className="control-label">Nhập tên công nghệ (Nhấn Enter)</label>
                    <CreatableSelect 
                        isMulti 
                        options={techOptions} 
                        value={selectedTechs}
                        onChange={v => { if (v.length <= 5) setSelectedTechs(v); }}
                        styles={selectStyles} 
                        placeholder="Gõ tên công nghệ và nhấn Enter..." 
                        formatCreateLabel={(inputValue) => `Thêm "${inputValue}"`}
                    />
                </div>
                <div className="control-group">
                    <label className="control-label">Thời gian</label>
                    <div className="pill-group">
                        {[3, 6, 12, 24].map(v => (
                            <button key={v} className={`pill${timeRange === v ? ' active' : ''}`}
                                onClick={() => setTimeRange(v)}>{v} tháng</button>
                        ))}
                    </div>
                </div>
            </div>

            {loading && <div style={{ marginTop: 20, color: 'var(--text-2)' }}>Đang tải dữ liệu so sánh...</div>}
            {error && <div style={{ marginTop: 20, color: '#ff6b6b' }}>{error}</div>}

            {/* Stats cards */}
            {!loading && !error && (
                <div className="compare-stats-row" style={{ marginTop: 16 }}>
                    {statsArr.map(s => (
                        <div key={s.id} className="compare-stat-card" style={{ borderColor: s.color + '55' }}>
                            <div className="cs-header">
                                <span className="cs-dot" style={{ background: s.color }} />
                                <span className="cs-name">{s.name}</span>
                            </div>
                            <div className="cs-big">{formatPercent(s.total)}</div>
                            <div className="cs-meta-row">
                                <div className="cs-meta"><span>YoY</span><strong className={s.yoy >= 0 ? 'up' : 'down'}>{formatPercent(s.yoy)}</strong></div>
                                <div className="cs-meta"><span>MoM</span><strong className={s.mom >= 0 ? 'up' : 'down'}>{formatPercent(s.mom)}</strong></div>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Overlay chart */}
            {!loading && !error && chartData.length > 0 && (
                <div className="card" style={{ marginTop: 16 }}>
                    <h2 className="section-title">So sánh Tăng trưởng theo thời gian</h2>
                    <ResponsiveContainer width="100%" height={380}>
                        <LineChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 5 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                            <XAxis dataKey="month" tick={{ fill: 'var(--text-3)', fontSize: 11 }} />
                            <YAxis tick={{ fill: 'var(--text-3)', fontSize: 11 }} domain={chartAxisDomain} />
                            <ReferenceLine y={0} stroke="var(--border-2)" strokeDasharray="4 4" />
                            <Tooltip content={<CompareTooltip />} />
                            <Legend wrapperStyle={{ fontSize: '0.8rem', color: 'var(--text-2)', paddingTop: 12 }} />
                            {activeTechIds.map(id => (
                                <Line key={id} type="monotone" dataKey={id}
                                    stroke={colorMap[id]} strokeWidth={2.5} dot={false}
                                    activeDot={{ r: 5, strokeWidth: 0 }}
                                    name={id}
                                />
                            ))}
                        </LineChart>
                    </ResponsiveContainer>
                </div>
            )}
        </div>
    );
}
