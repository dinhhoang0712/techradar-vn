import { useState, useEffect, useCallback, useMemo } from 'react';
import Select from 'react-select';
import type { StylesConfig, MultiValue } from 'react-select';
import { getRadarSearch, getRadarTop4, getRadarTop10, streamRadar } from '../api/trendService';
import type { RadarSearchResponse, RadarTop4Item, RadarTop10Item, RadarSnapshotEvent } from '../types/trend';
import { getForecast } from '../api/forecastService';
import type { Forecast } from '../types/forecast';
import { summarizeTech } from '../api/summarizeService';
import type { TechSummary } from '../types/summarize';
import { CHART_PALETTE as PALETTE } from '../utils/chartPalette';
import { useAsync } from '../hooks/useAsync';
import MaintenanceOverlay from '../components/common/MaintenanceOverlay';
import { useToast } from '../components/common/toastContext';
import TrendChart from '../components/trend/TrendChart';
import SummaryPanel from '../components/trend/SummaryPanel';
import ForecastPanel from '../components/trend/ForecastPanel';
import { ApiError } from '../types/api';
import './TrendDashboard.css';

interface TechOption {
    value: string;
    label: string;
    color: string;
}

interface ChartRow {
    month: string;
    rawSort: number;
    [key: string]: string | number;
}

const TIME_OPTIONS = [
    { label: '3 tháng', value: 3 },
    { label: '6 tháng', value: 6 },
    { label: '12 tháng', value: 12 },
    { label: '24 tháng', value: 24 },
];

// Biến đổi dữ liệu /compare/search thành mảng chart data: [{ month: 'T1', Java: 10, React: 20 }]
function transformToChartData(apiData: RadarSearchResponse): ChartRow[] {
    if (!apiData?.data || !Array.isArray(apiData.data)) return [];

    const mergedMap: Record<string, ChartRow> = {};
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

function toGrowthData(timelineData: ChartRow[], keywords: string[]): ChartRow[] {
    if (!timelineData.length) return [];

    // Tìm base (giá trị > 0 đầu tiên) cho từng keyword
    const baseVals: Record<string, number | null> = {};
    keywords.forEach(kw => {
        const firstValidRow = timelineData.find(row => Number(row[kw]) > 0);
        baseVals[kw] = firstValidRow ? Number(firstValidRow[kw]) : null;
    });

    return timelineData.map(row => {
        const g: ChartRow = { month: row.month, rawSort: row.rawSort };
        keywords.forEach(kw => {
            const b = baseVals[kw];
            if (b !== null && b > 0) {
                g[kw] = Math.round(((Number(row[kw]) - b) / b) * 100);
            } else {
                g[kw] = 0;
            }
        });
        return g;
    });
}

function roundAxisLimit(value: number): number {
    if (value <= 100) return 100;
    if (value <= 250) return Math.ceil(value / 50) * 50;
    return Math.ceil(value / 100) * 100;
}

function getAxisLimit(value: number): number {
    if (value < 99) return 100;
    return roundAxisLimit(value * 1.15);
}

function roundJobAxisLimit(value: number): number {
    if (value <= 100) return 100;
    if (value <= 500) return Math.ceil(value / 50) * 50;
    if (value <= 1000) return Math.ceil(value / 100) * 100;
    return Math.ceil(value / 500) * 500;
}

function getJobAxisLimit(value: number): number {
    if (value <= 100) return 100;
    return roundJobAxisLimit(value * 1.15);
}

function getPercentAxisDomain(data: ChartRow[], keys: string[]): [number, number] {
    const values = data.flatMap(row => keys.map(key => Number(row[key] || 0)));
    const maxGrowth = Math.max(0, ...values);
    const minGrowth = Math.min(0, ...values);
    const maxValue = getAxisLimit(maxGrowth);
    const minValue = -getAxisLimit(Math.abs(minGrowth));

    return [minValue, maxValue];
}

const selectStyles: StylesConfig<TechOption, true> = {
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

export default function TrendDashboard() {
    const [selectedTechs, setSelectedTechs] = useState<TechOption[]>([]);
    const [timeRange, setTimeRange] = useState(6);
    const [chartMode, setChartMode] = useState<'line' | 'bar' | 'growth'>('line');

    const [forecast, setForecast] = useState<Forecast | null>(null);
    const [loadingForecast, setLoadingForecast] = useState(false);
    const [forecastTech, setForecastTech] = useState<string | null>(null);
    const [summary, setSummary] = useState<TechSummary | null>(null);
    const [loadingSummary, setLoadingSummary] = useState(false);
    const [summaryTech, setSummaryTech] = useState<string | null>(null);
    const [summaryError, setSummaryError] = useState('');
    const notify = useToast();

    // useAsync tự bỏ qua response trả về muộn khi selectedTechs/timeRange đổi nhanh, tránh
    // response cũ ghi đè timelineData mới hơn.
    const overview = useAsync(
        async () => {
            const [t4res, t10res] = await Promise.all([getRadarTop4(), getRadarTop10()]);
            const top10Data = t10res?.data || [];
            return {
                top4Data: t4res?.data || [],
                top10Data,
                techOptions: top10Data.map((item, i) => ({
                    value: item.keyword, label: item.keyword, color: PALETTE[i % PALETTE.length],
                })),
            };
        },
        [],
    );
    // SSE /radar/stream: backend bắn snapshot top4/top10 mới ngay khi ETL rebuild xong, thay vì
    // phải đợi người dùng F5 mới thấy số liệu cập nhật. techOptions vẫn chỉ lấy từ lần fetch đầu
    // để không xáo trộn danh sách đang chọn trong Select.
    const [liveSnapshot, setLiveSnapshot] = useState<RadarSnapshotEvent | null>(null);
    useEffect(() => {
        const controller = streamRadar((snapshot) => setLiveSnapshot(snapshot));
        return () => controller.abort();
    }, []);

    const top4Data: RadarTop4Item[] = liveSnapshot?.top4 ?? overview.data?.top4Data ?? [];
    const top10Data: RadarTop10Item[] = liveSnapshot?.top10 ?? overview.data?.top10Data ?? [];
    const techOptions: TechOption[] = overview.data?.techOptions ?? [];
    const loadingTop = overview.loading;
    const error = overview.error;

    // Chọn sẵn 5 công nghệ đầu tiên khi danh sách gợi ý vừa tải xong lần đầu.
    useEffect(() => {
        if (techOptions.length && selectedTechs.length === 0) setSelectedTechs(techOptions.slice(0, 5));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [techOptions]);

    const { data: timelineData, loading: loadingChart } = useAsync<ChartRow[]>(
        async () => {
            if (selectedTechs.length === 0) return [];
            const keywords = selectedTechs.map(t => t.value);
            const res = await getRadarSearch(keywords, timeRange);
            return transformToChartData(res);
        },
        [selectedTechs, timeRange],
        { initialData: [] },
    );
    const safeTimelineData = useMemo(() => timelineData ?? [], [timelineData]);

    const colorMap = useMemo(() => {
        const map: Record<string, string> = {};
        selectedTechs.forEach((t, i) => { map[t.value] = PALETTE[i % PALETTE.length]; });
        return map;
    }, [selectedTechs]);

    const chartTimelineData = useMemo(() => {
        const sliceStart = Math.max(0, safeTimelineData.length - timeRange);
        return safeTimelineData.slice(sliceStart);
    }, [safeTimelineData, timeRange]);

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
    const jobAxisDomain = useMemo((): [number, number] => {
        const values = chartTimelineData.flatMap(row => activeTechIds.map(id => Number(row[id] || 0)));
        return [0, getJobAxisLimit(Math.max(0, ...values))];
    }, [chartTimelineData, activeTechIds]);

    const handleForecast = useCallback(async (techName: string) => {
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
            setForecast(('data' in res ? res.data : res) ?? null);
        } catch (err) {
            console.error('Lỗi lấy dự báo:', err);
            setForecast(null);
            notify({ title: 'Không thể tạo dự báo lúc này', body: 'Vui lòng thử lại.', variant: 'error' });
        } finally {
            setLoadingForecast(false);
        }
    }, [forecastTech, notify]);

    const handleSummarize = useCallback(async (techName: string) => {
        if (summaryTech === techName) {
            setSummary(null);
            setSummaryTech(null);
            return;
        }
        setSummaryTech(techName);
        setLoadingSummary(true);
        setSummary(null);
        setSummaryError('');
        try {
            const res = await summarizeTech(techName);
            setSummary(('data' in res ? res.data : res) ?? null);
        } catch (err) {
            console.error('Lỗi tóm tắt tin tức:', err);
            setSummaryError('Không thể tạo tóm tắt lúc này.');
        } finally {
            setLoadingSummary(false);
        }
    }, [summaryTech]);

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
            return html2canvas(el, { backgroundColor: '#000000' }).then(canvas => {
                const a = document.createElement('a');
                a.href = canvas.toDataURL('image/png');
                a.download = 'tech_trend_chart.png';
                a.click();
            });
        }).catch((err) => {
            console.error('[TrendDashboard] Export PNG failed:', err);
            notify({ title: 'Không thể xuất ảnh biểu đồ', body: 'Vui lòng thử lại.', variant: 'error' });
        });
    }, [notify]);

    if (loadingTop) return <div className="dashboard-page" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 300 }}><div className="loading-spinner"></div><span style={{ color: 'var(--text-2)', marginLeft: 12 }}>Đang tải dữ liệu...</span></div>;

    if (error) {
        const isMaintenance = error instanceof ApiError && error.message === 'SERVER_MAINTENANCE';
        return (
            <MaintenanceOverlay>
                <div className="dashboard-page flex-center" style={{ height: '100%', flexDirection: 'column', textAlign: 'center' }}>
                    <div className="error-display-box" style={{
                        padding: '60px',
                        background: 'var(--surface)',
                        borderRadius: 'var(--radius-xl)',
                        border: '1px solid var(--border)',
                        boxShadow: 'var(--shadow-lg)'
                    }}>
                        <div className="error-icon-large" style={{ fontSize: '5rem', marginBottom: '24px' }}>
                            {isMaintenance ? '🚧' : '🔌'}
                        </div>
                        <h2 style={{ fontSize: '2rem', marginBottom: '12px', color: 'var(--text)' }}>
                            {isMaintenance ? 'Hệ thống đang bảo trì' : 'Lỗi kết nối Server'}
                        </h2>
                        <p style={{ color: 'var(--text-2)', maxWidth: 450, margin: '16px auto', lineHeight: 1.6, fontSize: '1.1rem' }}>
                            {isMaintenance
                                ? 'Chúng tôi đang tiến hành bảo trì định kỳ. Vui lòng quay lại sau.'
                                : 'Không thể kết nối đến máy chủ TechRadar. Vui lòng kiểm tra lại đường truyền internet hoặc thử lại sau.'}
                        </p>
                        <button className="btn btn-primary" onClick={() => window.location.reload()} style={{ marginTop: '30px', padding: '12px 32px', fontSize: '1rem', fontWeight: 'bold' }}>
                            Thử lại ngay
                        </button>
                    </div>
                </div>
            </MaintenanceOverlay>
        );
    }

    return (
        <div className="dashboard-page">
            {/* Hero */}
            <div className="dashboard-hero">
                <div className="radar-sweep" aria-hidden="true">
                    <span className="radar-ring radar-ring-1" />
                    <span className="radar-ring radar-ring-2" />
                    <span className="radar-ring radar-ring-3" />
                    <span className="radar-sweep-beam" />
                </div>
                <h1 className="dashboard-title">Radar Công nghệ</h1>
                <p className="dashboard-subtitle">
                    Quét xu hướng công nghệ hot nhất trên thị trường tuyển dụng, cập nhật theo thời gian thực.
                </p>
            </div>

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
                        <div className="stat-meta">MoM: {(t.mom_rate ?? 0) > 0 ? '+' : ''}{Number(t.mom_rate ?? 0).toFixed(2)}% • Tháng này: {t.jobs_this_month?.toLocaleString()}</div>
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
                            onChange={(v: MultiValue<TechOption>) => setSelectedTechs([...v])}
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
            <TrendChart
                chartMode={chartMode}
                visibleData={visibleData}
                activeTechIds={activeTechIds}
                colorMap={colorMap}
                growthAxisDomain={growthAxisDomain}
                jobAxisDomain={jobAxisDomain}
                loadingChart={loadingChart}
            />

            {/* Top 10 từ /radar/top10 */}
            <div className="card top10-card" style={{ marginTop: 16 }}>
                <h2 className="section-title">Top 10 Công nghệ Hot nhất</h2>
                <div className="top10-grid">
                    {top10Data.map((t, i) => {
                        const isTopRank = i < 3;
                        return (
                        <div key={t.keyword} className={`top10-item${isTopRank ? ' top10-item--top' : ''}`}>
                            <span className="top10-rank">#{i + 1}</span>
                            <span
                                className={`top10-blip${isTopRank ? ' top10-blip--top' : ''}`}
                                style={{ '--blip-color': PALETTE[i % PALETTE.length] } as React.CSSProperties}
                            >
                                <span className="top10-blip-ping" />
                                <span className="top10-blip-dot" />
                            </span>
                            <span className="top10-name">{t.keyword}</span>
                            <span className="top10-jobs">{t.job_count?.toLocaleString()} jobs</span>
                            <button
                                className={`forecast-btn${forecastTech === t.keyword ? ' active' : ''}`}
                                onClick={() => handleForecast(t.keyword)}
                                title="Dự báo xu hướng"
                            >
                                {forecastTech === t.keyword && loadingForecast ? '...' : '📈'}
                            </button>
                            <button
                                className={`forecast-btn${summaryTech === t.keyword ? ' active' : ''}`}
                                onClick={() => handleSummarize(t.keyword)}
                                title="Tóm tắt tin tức gần đây"
                            >
                                {summaryTech === t.keyword && loadingSummary ? '...' : '📰'}
                            </button>
                        </div>
                        );
                    })}
                </div>
            </div>

            {/* Tóm tắt tin tức (POST /chat/summarize) */}
            {summaryTech && (
                <SummaryPanel
                    tech={summaryTech}
                    loading={loadingSummary}
                    summary={summary}
                    error={summaryError}
                    onClose={() => { setSummary(null); setSummaryTech(null); }}
                />
            )}

            {/* Forecast panel */}
            {forecastTech && (
                <ForecastPanel
                    tech={forecastTech}
                    loading={loadingForecast}
                    forecast={forecast}
                    onClose={() => { setForecast(null); setForecastTech(null); }}
                />
            )}
        </div>
    );
}
