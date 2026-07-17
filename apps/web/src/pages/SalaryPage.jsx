import { useState, useEffect, useMemo } from 'react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid,
    Tooltip, ResponsiveContainer, Cell, ErrorBar
} from 'recharts';
import { getSalaryTop, getSalaryByTech } from '../api/salaryService';
import './SalaryPage.css';

const SALARY_COLORS = ['#00d68f', '#54C5F8', '#6C63FF', '#ffc94d', '#FF6584'];

function salaryColor(value, max) {
    if (!value || !max) return 'var(--text-3)';
    const ratio = value / max;
    if (ratio > 0.75) return 'var(--green)';
    if (ratio > 0.45) return '#54C5F8';
    return 'var(--yellow)';
}

function formatM(val) {
    if (!val) return '—';
    return `${val.toFixed(1)}M`;
}

function SalaryTooltip({ active, payload }) {
    if (!active || !payload?.length) return null;
    const d = payload[0]?.payload;
    if (!d) return null;
    return (
        <div className="salary-tooltip">
            <p className="tooltip-tech-name">{d.tech_name}</p>
            <div className="tooltip-row">
                <span>Median</span>
                <span className="tooltip-val green">{formatM(d.median_salary_mvnd)}</span>
            </div>
            <div className="tooltip-row">
                <span>Range</span>
                <span className="tooltip-val">{d.salary_range}</span>
            </div>
            <div className="tooltip-row">
                <span>Jobs có lương</span>
                <span className="tooltip-val">{d.jobs_with_salary?.toLocaleString()}</span>
            </div>
        </div>
    );
}

function DetailPanel({ tech, onClose }) {
    const [detail, setDetail] = useState(null);
    // Loading starts true and this effect runs once per mount — SalaryPage
    // remounts this component (via `key`) whenever the selected tech changes.
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        getSalaryByTech(tech.tech_name)
            .then(res => setDetail(res?.data ?? null))
            .catch(() => setDetail(null))
            .finally(() => setLoading(false));
    }, [tech]);

    const data = detail ?? tech;

    return (
        <div className="detail-panel">
            <div className="detail-header">
                <h3 className="detail-title">{tech.tech_name}</h3>
                <button className="detail-close" onClick={onClose}>✕</button>
            </div>

            {loading ? (
                <div className="detail-loading"><div className="loading-spinner" /></div>
            ) : (
                <>
                    <div className="detail-stats-grid">
                        <div className="detail-stat">
                            <span className="ds-label">Median</span>
                            <span className="ds-value green">{formatM(data.median_salary_mvnd)} VND</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">Trung bình</span>
                            <span className="ds-value">{formatM(data.avg_salary_mvnd)} VND</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">P25 – P75</span>
                            <span className="ds-value">{formatM(data.p25_salary_mvnd)} – {formatM(data.p75_salary_mvnd)}</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">Min – Max</span>
                            <span className="ds-value">{formatM(data.min_salary_mvnd)} – {formatM(data.max_salary_mvnd)}</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">Tổng jobs</span>
                            <span className="ds-value">{data.total_jobs?.toLocaleString()}</span>
                        </div>
                        <div className="detail-stat">
                            <span className="ds-label">Jobs có lương</span>
                            <span className="ds-value">
                                {data.jobs_with_salary?.toLocaleString()}
                                <span className="ds-pct">
                                    ({data.total_jobs ? Math.round(data.jobs_with_salary / data.total_jobs * 100) : 0}%)
                                </span>
                            </span>
                        </div>
                    </div>

                    {data.top_co_techs?.length > 0 && (
                        <div className="detail-cotechs">
                            <p className="detail-section-label">Thường yêu cầu cùng</p>
                            <div className="cotech-chips">
                                {data.top_co_techs.map((t, i) => (
                                    <span key={t} className="cotech-chip" style={{ '--chip-color': SALARY_COLORS[i % SALARY_COLORS.length] }}>
                                        {t}
                                    </span>
                                ))}
                            </div>
                        </div>
                    )}

                    <div className="detail-salary-bar">
                        <p className="detail-section-label">Phân phối lương</p>
                        <div className="salary-bar-track">
                            <div
                                className="salary-bar-range"
                                style={{
                                    left: `${data.min_salary_mvnd / (data.max_salary_mvnd || 1) * 100}%`,
                                    '--bar-width': `${(data.p75_salary_mvnd - data.p25_salary_mvnd) / (data.max_salary_mvnd || 1) * 100}%`,
                                }}
                            />
                            <div
                                className="salary-bar-median"
                                style={{ left: `${data.median_salary_mvnd / (data.max_salary_mvnd || 1) * 100}%` }}
                                title={`Median: ${formatM(data.median_salary_mvnd)}`}
                            />
                        </div>
                        <div className="salary-bar-labels">
                            <span>{formatM(data.min_salary_mvnd)}</span>
                            <span>Median: {formatM(data.median_salary_mvnd)}</span>
                            <span>{formatM(data.max_salary_mvnd)}</span>
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}

export default function SalaryPage() {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [selected, setSelected] = useState(null);
    const [sortBy, setSortBy] = useState('median');

    useEffect(() => {
        getSalaryTop(40, 1)
            .then(res => setData(res?.data ?? []))
            .catch(err => {
                if (err.message === 'SERVER_MAINTENANCE') setError('MAINTENANCE');
                else if (err.message === 'SERVER_CONNECTION_FAILED') setError('CONNECTION_FAILED');
                else setError('Không thể tải dữ liệu lương. Vui lòng thử lại.');
            })
            .finally(() => setLoading(false));
    }, []);

    const filtered = useMemo(() => {
        let list = data.filter(d => d.jobs_with_salary > 0);
        if (search.trim()) {
            const q = search.trim().toLowerCase();
            list = list.filter(d => d.tech_name.toLowerCase().includes(q));
        }
        if (sortBy === 'median') list = [...list].sort((a, b) => b.median_salary_mvnd - a.median_salary_mvnd);
        else if (sortBy === 'jobs') list = [...list].sort((a, b) => b.total_jobs - a.total_jobs);
        else if (sortBy === 'max') list = [...list].sort((a, b) => b.max_salary_mvnd - a.max_salary_mvnd);
        return list;
    }, [data, search, sortBy]);

    const topMedian = useMemo(() => Math.max(0, ...filtered.map(d => d.median_salary_mvnd)), [filtered]);
    const chartData = useMemo(() => filtered.slice(0, 20), [filtered]);

    if (loading) return (
        <div className="salary-page" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 300 }}>
            <div className="loading-spinner" />
            <span style={{ color: 'var(--text-2)', marginLeft: 12 }}>Đang phân tích dữ liệu lương...</span>
        </div>
    );

    if (error) return (
        <div className="salary-page salary-error">
            <div className="error-box">
                <div style={{ fontSize: '3rem' }}>{error === 'MAINTENANCE' ? '🚧' : '📊'}</div>
                <h2>{error === 'MAINTENANCE' ? 'Hệ thống đang bảo trì' : 'Chưa có dữ liệu lương'}</h2>
                <p>{error === 'MAINTENANCE'
                    ? 'Vui lòng quay lại sau.'
                    : 'Dữ liệu salary insights sẽ xuất hiện sau khi pipeline thu thập đủ job postings có thông tin lương.'
                }</p>
                <button className="btn btn-primary" onClick={() => window.location.reload()}>Thử lại</button>
            </div>
        </div>
    );

    const topThree = filtered.slice(0, 3);

    return (
        <div className="salary-page">
            {/* Header stats */}
            <div className="salary-hero">
                <div>
                    <h1 className="salary-page-title">Salary Insights</h1>
                    <p className="salary-page-sub">Mức lương theo công nghệ — phân tích từ {data.reduce((s, d) => s + d.total_jobs, 0).toLocaleString()} job postings</p>
                </div>
                <div className="salary-top3">
                    {topThree.map((t, i) => (
                        <button type="button" key={t.tech_name} className={`top3-card rank-${i + 1}`} onClick={() => setSelected(t)}>
                            <span className="top3-rank">#{i + 1}</span>
                            <span className="top3-name">{t.tech_name}</span>
                            <span className="top3-salary">{formatM(t.median_salary_mvnd)} VND</span>
                            <span className="top3-range">{t.salary_range}</span>
                        </button>
                    ))}
                </div>
            </div>

            <div className="salary-content">
                {/* Chart */}
                <div className="card salary-chart-card">
                    <h2 className="section-title">Top 20 — Median Salary (triệu VND)</h2>
                    <ResponsiveContainer width="100%" height={320}>
                        <BarChart data={chartData} layout="vertical" margin={{ top: 4, right: 40, left: 80, bottom: 4 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
                            <XAxis type="number" tick={{ fill: 'var(--text-3)', fontSize: 11 }} unit="M" />
                            <YAxis type="category" dataKey="tech_name" tick={{ fill: 'var(--text-2)', fontSize: 11 }} width={78} />
                            <Tooltip content={<SalaryTooltip />} cursor={{ fill: 'rgba(255,255,255,0.04)' }} />
                            <Bar dataKey="median_salary_mvnd" radius={[0, 4, 4, 0]} maxBarSize={18}>
                                {chartData.map((entry) => (
                                    <Cell key={entry.tech_name} fill={salaryColor(entry.median_salary_mvnd, topMedian)} />
                                ))}
                            </Bar>
                        </BarChart>
                    </ResponsiveContainer>
                </div>

                {/* Table + detail */}
                <div className="salary-table-section">
                    {/* Controls */}
                    <div className="salary-controls card">
                        <input
                            className="salary-search"
                            placeholder="Tìm công nghệ..."
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                        />
                        <div className="pill-group">
                            <button className={`pill${sortBy === 'median' ? ' active' : ''}`} onClick={() => setSortBy('median')}>Median</button>
                            <button className={`pill${sortBy === 'max' ? ' active' : ''}`} onClick={() => setSortBy('max')}>Cao nhất</button>
                            <button className={`pill${sortBy === 'jobs' ? ' active' : ''}`} onClick={() => setSortBy('jobs')}>Nhiều jobs</button>
                        </div>
                    </div>

                    <div className="salary-table-wrapper">
                        <div className={`salary-table-area${selected ? ' has-detail' : ''}`}>
                            <table className="salary-table">
                                <thead>
                                    <tr>
                                        <th>#</th>
                                        <th>Công nghệ</th>
                                        <th>Median</th>
                                        <th>Range (P25–P75)</th>
                                        <th>Max</th>
                                        <th>Jobs có lương</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filtered.map((item, i) => (
                                        <tr
                                            key={item.tech_name}
                                            className={`salary-row${selected?.tech_name === item.tech_name ? ' selected' : ''}`}
                                            onClick={() => setSelected(selected?.tech_name === item.tech_name ? null : item)}
                                        >
                                            <td className="rank-cell">{i + 1}</td>
                                            <td className="tech-cell">
                                                <span className="tech-dot" style={{ background: salaryColor(item.median_salary_mvnd, topMedian) }} />
                                                {item.tech_name}
                                            </td>
                                            <td className="salary-cell" style={{ color: salaryColor(item.median_salary_mvnd, topMedian) }}>
                                                {formatM(item.median_salary_mvnd)}
                                            </td>
                                            <td className="range-cell">{item.salary_range}</td>
                                            <td className="max-cell">{formatM(item.max_salary_mvnd)}</td>
                                            <td className="jobs-cell">
                                                {item.jobs_with_salary?.toLocaleString()}
                                                <span className="jobs-pct">
                                                    /{item.total_jobs?.toLocaleString()}
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                    {filtered.length === 0 && (
                                        <tr>
                                            <td colSpan={6} className="empty-row">Không tìm thấy công nghệ nào</td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>

                        {selected && (
                            <DetailPanel key={selected.tech_name} tech={selected} onClose={() => setSelected(null)} />
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}