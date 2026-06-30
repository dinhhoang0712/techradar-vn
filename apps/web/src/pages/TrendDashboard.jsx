import { useState, useEffect, useCallback, useMemo } from 'react';
import {
    LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid,
    Tooltip, Legend, ResponsiveContainer, ReferenceLine
} from 'recharts';
import Select from 'react-select';
import { getRadarSearch, getRadarTop4, getRadarTop10 } from '../api/trendService';
import { getForecast } from '../api/forecastService';
import './TrendDashboard.css';

const PALETTE = [
    '#6C63FF', '#00D68F', '#FF6584', '#FFC94D', '#54C5F8',
    '#FF8C00', '#7FBA00', '#E040FB', '#FF5252', '#00B4D8'
];

const TIME_OPTIONS = [
    { label: '3 tháng', value: 3 },
    { label: '6 tháng', value: 6 },
    { label: '12 tháng', value: 12 },
    { label: '24 tháng', value: 24 },
];

// Biến đổi dữ liệu /compare/search thành mảng chart data: [{ month: 'T1', Java: 10, React: 20 }]
function transformToChartData(apiData) {
    if (!apiData?.data || !Array.isArray(apiData.data)) return [];
    
    const mergedMap = {}; 
    apiData.data.forEach(point => {
        const m = `T${point.month}/${point.year}`;
        if (!mergedMap[m]) {
            mergedMap[m] = { month: m, rawSort: point.year * 100 + point.month };
        }

        Object.entries(point.keywords || {}).forEach(([kw, count]) => {
            mergedMap[m][kw] = count || 0;
        });
    });

    return Object.values(mergedMap).sort((a, b) => a.rawSort - b.rawSort);
}

function toGrowthData(timelineData, keywords) {
    if (!timelineData.length) return [];
    
    // Tìm base (giá trị > 0 đầu tiên) cho từng keyword
    const baseVals = {};
    keywords.forEach(kw => {
        const firstValidRow = timelineData.find(row => row[kw] > 0);
        baseVals[kw] = firstValidRow ? firstValidRow[kw] : null;
    });

    return timelineData.map(row => {
        const g = { month: row.month };
        keywords.forEach(kw => {
            const b = baseVals[kw];
            if (b !== null && b > 0) {
                g[kw] = Math.round(((row[kw] - b) / b) * 100);
            } else {
                g[kw] = 0;
            }
        });
        return g;
    });
}

function roundAxisLimit(value) {
    if (value <= 100) return 100;
    if (value <= 250) return Math.ceil(value / 50) * 50;
    return Math.ceil(value / 100) * 100;
}

function getAxisLimit(value) {
    if (value < 99) return 100;
    return roundAxisLimit(value * 1.15);
}

function roundJobAxisLimit(value) {
    if (value <= 100) return 100;
    if (value <= 500) return Math.ceil(value / 50) * 50;
    if (value <= 1000) return Math.ceil(value / 100) * 100;
    return Math.ceil(value / 500) * 500;
}

function getJobAxisLimit(value) {
    if (value <= 100) return 100;
    return roundJobAxisLimit(value * 1.15);
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
    control: (base) => ({ ...base, background: 'var(--surface-2)', borderColor: 'var(--border)', color: 'var(--text)', minHeight: '38px', boxShadow: 'none', '&:hover': { borderColor: 'var(--primary)' } }),
    menu: (base) => ({ ...base, background: 'var(--surface-2)', border: '1px solid var(--border)', zIndex: 200 }),
    option: (base, state) => ({ ...base, background: state.isFocused ? 'var(--surface)' : 'transparent', color: 'var(--text)', cursor: 'pointer' }),
    multiValue: (base) => ({ ...base, background: 'var(--primary-glow)', borderRadius: 4 }),
    multiValueLabel: (base) => ({ ...base, color: 'var(--primary-light)' }),
    multiValueRemove: (base) => ({ ...base, color: 'var(--primary-light)', '&:hover': { background: 'var(--primary)', color: '#fff' } }),
    input: (base) => ({ ...base, color: 'var(--text)' }),
    singleValue: (base) => ({ ...base, color: 'var(--text)' }),
    placeholder: (base) => ({ ...base, color: 'var(--text-3)' }),
};

function CustomTooltip({ active, payload, label }) {
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

function GrowthTooltip({ active, payload, label }) {
    if (!active || !payload?.length) return null;
    return (
        <div className="chart-tooltip">
            <p className="tooltip-month">{label}</p>
            {payload.map(p => (
                <div key={p.dataKey} className="tooltip-row">
                    <span className="tooltip-dot" style={{ background: p.color }} />
                    <span className="tooltip-tech">{p.name}</span>
                    <span className={`tooltip-jobs ${p.value >= 0 ? 'up' : 'down'}`}>
                        {p.value >= 0 ? '+' : ''}{p.value}%
                    </span>
                </div>
            ))}
        </div>
    );
}

export default function TrendDashboard() {
    const [techOptions, setTechOptions] = useState([]);
    const [selectedTechs, setSelectedTechs] = useState([]);
    const [timeRange, setTimeRange] = useState(6);
    const [chartMode, setChartMode] = useState('line');

    const [top4Data, setTop4Data] = useState([]);
    const [top10Data, setTop10Data] = useState([]);
    const [timelineData, setTimelineData] = useState([]);
    const [loadingTop, setLoadingTop] = useState(true);
    const [loadingChart, setLoadingChart] = useState(false);
    const [error, setError] = useState('');
    const [forecast, setForecast] = useState(null);
    const [loadingForecast, setLoadingForecast] = useState(false);
    const [forecastTech, setForecastTech] = useState(null);

    const colorMap = useMemo(() => {
        const map = {};
        selectedTechs.forEach((t, i) => { map[t.value] = PALETTE[i % PALETTE.length]; });
        return map;
    }, [selectedTechs]);

    const chartTimelineData = useMemo(() => {
        const sliceStart = Math.max(0, timelineData.length - timeRange);
        return timelineData.slice(sliceStart);
    }, [timelineData, timeRange]);

    const growthData = useMemo(() =>
        toGrowthData(chartTimelineData, selectedTechs.map(t => t.value)),
        [chartTimelineData, selectedTechs]
    );

    const visibleData = chartMode === 'growth' ? growthData : chartTimelineData;
    const activeTechIds = selectedTechs.map(t => t.value);
    const growthAxisDomain = useMemo(() =>
        getPercentAxisDomain(growthData, activeTechIds),
        [growthData, activeTechIds]
    );
    const jobAxisDomain = useMemo(() => {
        const values = chartTimelineData.flatMap(row => activeTechIds.map(id => Number(row[id] || 0)));
        return [0, getJobAxisLimit(Math.max(0, ...values))];
    }, [chartTimelineData, activeTechIds]);

    // Load top4 + top10 on mount
    useEffect(() => {
        const init = async () => {
            setLoadingTop(true);
            try {
                const [t4res, t10res] = await Promise.all([getRadarTop4(), getRadarTop10()]);
                if (t4res?.data) setTop4Data(t4res.data);
                if (t10res?.data) {
                    setTop10Data(t10res.data);
                    const opts = t10res.data.map((item, i) => ({
                        value: item.keyword,
                        label: item.keyword,
                        color: PALETTE[i % PALETTE.length]
                    }));
                    setTechOptions(opts);
                    setSelectedTechs(opts.slice(0, 5));
                }
            } catch (err) {
                if (err.message === 'SERVER_MAINTENANCE') {
                    setError('MAINTENANCE');
                } else if (err.message === 'SERVER_CONNECTION_FAILED') {
                    setError('CONNECTION_FAILED');
                } else {
                    setError('Không thể kết nối đến server. Vui lòng thử lại sau.');
                }
            } finally {
                setLoadingTop(false);
            }
        };
        init();
    }, []);

    // Load chart data when selection or time range changes
    useEffect(() => {
        if (selectedTechs.length === 0) return;
        const keywords = selectedTechs.map(t => t.value);
        const fetch = async () => {
            setLoadingChart(true);
            try {
                const res = await getRadarSearch(keywords, timeRange);
                setTimelineData(transformToChartData(res));
            } catch {
                setTimelineData([]);
            } finally {
                setLoadingChart(false);
            }
        };
        fetch();
    }, [selectedTechs, timeRange]);

    const handleForecast = useCallback(async (techName) => {
        if (forecastTech === techName) {
            setForecast(null);
            setForecastTech(null);
            return;
        }
        setForecastTech(techName);
        setLoadingForecast(true);
        setForecast(null);
        try {
            const res = await getForecast(techName, 6);
            setForecast(res?.data ?? res);
        } catch {
            setForecast(null);
        } finally {
            setLoadingForecast(false);
        }
    }, [forecastTech]);

    const handleExportCSV = useCallback(() => {
        const headers = ['Month', ...activeTechIds];
        const rows = visibleData.map(row => [row.month, ...activeTechIds.map(id => row[id] ?? 0)]);
        const csv = [headers, ...rows].map(r => r.join(',')).join('\n');
        const blob = new Blob([csv], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = 'tech_trend_export.csv'; a.click();
        URL.revokeObjectURL(url);
    }, [visibleData, activeTechIds]);

    const handleExportPNG = useCallback(() => {
        import('html2canvas').then(({ default: html2canvas }) => {
            const el = document.getElementById('main-chart-wrapper');
            if (!el) return;
            html2canvas(el, { backgroundColor: '#000000' }).then(canvas => {
                const a = document.createElement('a');
                a.href = canvas.toDataURL('image/png');
                a.download = 'tech_trend_chart.png';
                a.click();
            });
        });
    }, []);

    if (loadingTop) return <div className="dashboard-page" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 300 }}><div className="loading-spinner"></div><span style={{ color: 'var(--text-2)', marginLeft: 12 }}>Đang tải dữ liệu...</span></div>;
    
    if (error) {
        return (
            <div className="dashboard-page flex-center" style={{ 
                position: 'fixed',
                top: 0,
                left: 0,
                width: '100vw',
                height: '100vh',
                zIndex: 9999,
                background: '#000', // Che phủ hoàn toàn nền
                flexDirection: 'column', 
                textAlign: 'center' 
            }}>
                <div className="error-display-box" style={{ 
                    padding: '60px', 
                    background: '#111', 
                    borderRadius: '24px', 
                    border: '1px solid #333',
                    boxShadow: '0 20px 50px rgba(0,0,0,0.5)'
                }}>
                    <div className="error-icon-large" style={{ fontSize: '5rem', marginBottom: '24px' }}>
                        {error === 'MAINTENANCE' ? '🚧' : '🔌'}
                    </div>
                    <h2 style={{ fontSize: '2rem', marginBottom: '12px', color: '#fff' }}>
                        {error === 'MAINTENANCE' ? 'Hệ thống đang bảo trì' : 'Lỗi kết nối Server'}
                    </h2>
                    <p style={{ color: '#888', maxWidth: 450, margin: '16px auto', lineHeight: 1.6, fontSize: '1.1rem' }}>
                        {error === 'MAINTENANCE' 
                            ? 'Chúng tôi đang tiến hành bảo trì định kỳ. Vui lòng quay lại sau.' 
                            : 'Không thể kết nối đến máy chủ TechRadar. Vui lòng kiểm tra lại đường truyền internet hoặc thử lại sau.'}
                    </p>
                    <button className="btn btn-primary" onClick={() => window.location.reload()} style={{ marginTop: '30px', padding: '12px 32px', fontSize: '1rem', fontWeight: 'bold' }}>
                        Thử lại ngay
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="dashboard-page">
            {/* Top Stats từ /radar/top4 */}
            <div className="stats-row">
                {top4Data.map((t, i) => (
                    <div key={t.industry || i} className="stat-card">
                        <div className="stat-header">
                            <span className="stat-name">{t.industry}</span>
                            <span className={`badge ${t.growth_rate > 30 ? 'badge-up' : t.growth_rate < 0 ? 'badge-down' : 'badge-flat'}`}>
                                {t.growth_rate > 0 ? '+' : ''}{Number(t.growth_rate).toFixed(2)}%
                            </span>
                        </div>
                        <div className="stat-jobs">{t.job_count?.toLocaleString()} <span>jobs</span></div>
                        <div className="stat-meta">MoM: {t.mom_rate > 0 ? '+' : ''}{Number(t.mom_rate).toFixed(2)}% • Tháng này: {t.jobs_this_month?.toLocaleString()}</div>
                    </div>
                ))}
            </div>

            {/* Controls */}
            <div className="card dashboard-controls">
                <div className="controls-left">
                    <div className="control-group">
                        <label className="control-label">Công nghệ</label>
                        <Select
                            isMulti
                            options={techOptions}
                            value={selectedTechs}
                            onChange={setSelectedTechs}
                            styles={selectStyles}
                            placeholder="Chọn công nghệ..."
                            closeMenuOnSelect={false}
                        />
                    </div>
                    <div className="control-group">
                        <label className="control-label">Thời gian</label>
                        <div className="pill-group">
                            {TIME_OPTIONS.map(opt => (
                                <button key={opt.value} className={`pill${timeRange === opt.value ? ' active' : ''}`} onClick={() => setTimeRange(opt.value)}>{opt.label}</button>
                            ))}
                        </div>
                    </div>
                    <div className="control-group">
                        <label className="control-label">Dạng biểu đồ</label>
                        <div className="pill-group">
                            <button className={`pill${chartMode === 'line' ? ' active' : ''}`} onClick={() => setChartMode('line')}>Line</button>
                            <button className={`pill${chartMode === 'bar' ? ' active' : ''}`} onClick={() => setChartMode('bar')}>Bar</button>
                            <button className={`pill${chartMode === 'growth' ? ' active' : ''}`} onClick={() => setChartMode('growth')}>Tăng trưởng %</button>
                        </div>
                    </div>
                </div>
                <div className="controls-right">
                    <button className="btn btn-secondary" onClick={handleExportPNG}>Export PNG</button>
                    <button className="btn btn-primary" onClick={handleExportCSV}>Export CSV</button>
                </div>
            </div>

            {/* Main Chart */}
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

            {/* Top 10 từ /radar/top10 */}
            <div className="card top10-card" style={{ marginTop: 16 }}>
                <h2 className="section-title">Top 10 Công nghệ Hot nhất</h2>
                <div className="top10-grid">
                    {top10Data.map((t, i) => (
                        <div key={t.keyword} className="top10-item">
                            <span className="top10-rank">#{i + 1}</span>
                            <span className="top10-dot" style={{ background: PALETTE[i % PALETTE.length] }} />
                            <span className="top10-name">{t.keyword}</span>
                            <span className="top10-jobs">{t.job_count?.toLocaleString()} jobs</span>
                            <button
                                className={`forecast-btn${forecastTech === t.keyword ? ' active' : ''}`}
                                onClick={() => handleForecast(t.keyword)}
                                title="Dự báo xu hướng"
                            >
                                {forecastTech === t.keyword && loadingForecast ? '...' : '📈'}
                            </button>
                        </div>
                    ))}
                </div>
            </div>

            {/* Forecast panel */}
            {forecastTech && (
                <div className="card forecast-panel" style={{ marginTop: 16 }}>
                    <div className="forecast-header">
                        <h2 className="section-title">Dự báo xu hướng: <span style={{ color: 'var(--primary)' }}>{forecastTech}</span></h2>
                        <button className="forecast-close-btn" onClick={() => { setForecast(null); setForecastTech(null); }}>✕</button>
                    </div>

                    {loadingForecast ? (
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
                            {forecast.signals?.length > 0 && (
                                <div className="forecast-signals">
                                    {forecast.signals.map((s, i) => (
                                        <div key={i} className="forecast-signal">
                                            <span className="signal-label">{s.signal}</span>
                                            <span className="signal-value">{typeof s.value === 'number' ? s.value.toFixed(2) : s.value}</span>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    ) : (
                        <div className="forecast-loading">Không có dữ liệu dự báo cho công nghệ này.</div>
                    )}
                </div>
            )}
        </div>
    );
}
