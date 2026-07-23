import { useState, useMemo } from 'react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid,
    Tooltip, ResponsiveContainer, Cell
} from 'recharts';
import type { TooltipContentProps } from 'recharts';
import type { ValueType, NameType } from 'recharts/types/component/DefaultTooltipContent';
import { getSalaryTop } from '../api/salaryService';
import { useAsync } from '../hooks/useAsync';
import { salaryColor, formatM } from '../utils/salaryFormat';
import SalaryDetailPanel from '../components/salary/SalaryDetailPanel';
import type { SalaryTech } from '../types/salary';
import { ApiError } from '../types/api';
import './SalaryPage.css';

function SalaryTooltip({ active, payload }: Partial<TooltipContentProps<ValueType, NameType>>) {
    if (!active || !payload?.length) return null;
    const d = payload[0]?.payload as SalaryTech | undefined;
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

export default function SalaryPage() {
    const [search, setSearch] = useState('');
    const [selected, setSelected] = useState<SalaryTech | null>(null);
    const [sortBy, setSortBy] = useState('median');

    const { data, loading, error } = useAsync<SalaryTech[]>(
        () => getSalaryTop(40, 1).then(res => res?.data ?? []),
        [],
        { initialData: [] },
    );
    const safeData = useMemo(() => data ?? [], [data]);

    const filtered = useMemo(() => {
        let list = safeData.filter(d => d.jobs_with_salary > 0);
        if (search.trim()) {
            const q = search.trim().toLowerCase();
            list = list.filter(d => d.tech_name.toLowerCase().includes(q));
        }
        if (sortBy === 'median') list = [...list].sort((a, b) => b.median_salary_mvnd - a.median_salary_mvnd);
        else if (sortBy === 'jobs') list = [...list].sort((a, b) => b.total_jobs - a.total_jobs);
        else if (sortBy === 'max') list = [...list].sort((a, b) => (b.max_salary_mvnd ?? 0) - (a.max_salary_mvnd ?? 0));
        return list;
    }, [safeData, search, sortBy]);

    const topMedian = useMemo(() => Math.max(0, ...filtered.map(d => d.median_salary_mvnd)), [filtered]);
    const chartData = useMemo(() => filtered.slice(0, 20), [filtered]);

    if (loading) return (
        <div className="salary-page" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 300 }}>
            <div className="loading-spinner" />
            <span style={{ color: 'var(--text-2)', marginLeft: 12 }}>Đang phân tích dữ liệu lương...</span>
        </div>
    );

    if (error) {
        const isMaintenance = error instanceof ApiError && error.message === 'SERVER_MAINTENANCE';
        return (
            <div className="salary-page salary-error">
                <div className="error-box">
                    <div style={{ fontSize: '3rem' }}>{isMaintenance ? '🚧' : '📊'}</div>
                    <h2>{isMaintenance ? 'Hệ thống đang bảo trì' : 'Chưa có dữ liệu lương'}</h2>
                    <p>{isMaintenance
                        ? 'Vui lòng quay lại sau.'
                        : 'Dữ liệu salary insights sẽ xuất hiện sau khi pipeline thu thập đủ job postings có thông tin lương.'
                    }</p>
                    <button className="btn btn-primary" onClick={() => window.location.reload()}>Thử lại</button>
                </div>
            </div>
        );
    }

    const topThree = filtered.slice(0, 3);

    return (
        <div className="salary-page">
            {/* Header stats */}
            <div className="salary-hero">
                <div>
                    <h1 className="salary-page-title">Salary Insights</h1>
                    <p className="salary-page-sub">Mức lương theo công nghệ — phân tích từ {safeData.reduce((s, d) => s + d.total_jobs, 0).toLocaleString()} job postings</p>
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
                            <SalaryDetailPanel key={selected.tech_name} tech={selected} onClose={() => setSelected(null)} />
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
