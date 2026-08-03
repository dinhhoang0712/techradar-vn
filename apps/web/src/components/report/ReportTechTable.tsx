import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { ReportTechRow } from '../../types/report';

type SortKey = 'none' | 'metric' | 'growth';
type SortDir = 'asc' | 'desc';

interface ReportTechTableProps {
    techs: ReportTechRow[];
    periodLabel?: string;
}

function metricOf(t: ReportTechRow): number {
    return t.source === 'articles' ? (t.mention_count ?? 0) : (t.job_count ?? 0);
}

function buildCsv(techs: ReportTechRow[]): string {
    const escape = (v: string | number) => {
        const s = String(v ?? '');
        return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const header = ['#', 'Công nghệ', 'Cluster', 'Nguồn', 'Số jobs', 'Lượt nhắc', 'Tăng trưởng (%)'];
    const rows = techs.map((t, i) => [
        i + 1,
        t.name,
        t.cluster_label || '',
        t.source === 'articles' ? 'Bài viết' : 'Analytics',
        t.job_count ?? '',
        t.mention_count ?? '',
        t.growth_rate != null ? Number(t.growth_rate).toFixed(1) : '',
    ]);
    return [header, ...rows].map(r => r.map(escape).join(',')).join('\n');
}

// Bảng top công nghệ dùng chung cho chế độ xem đơn (ReportPage) và so sánh 2 kỳ (ReportCompareView) —
// tự quản lý sort/filter/export để 1 nguồn logic duy nhất cho cả 2 nơi dùng.
export default function ReportTechTable({ techs, periodLabel }: ReportTechTableProps) {
    const [sortKey, setSortKey] = useState<SortKey>('none');
    const [sortDir, setSortDir] = useState<SortDir>('desc');
    const [clusterFilter, setClusterFilter] = useState('all');

    const clusters = useMemo(() => {
        const set = new Set<string>();
        techs.forEach(t => { if (t.cluster_label) set.add(t.cluster_label); });
        return Array.from(set).sort();
    }, [techs]);

    const filtered = useMemo(
        () => (clusterFilter === 'all' ? techs : techs.filter(t => t.cluster_label === clusterFilter)),
        [techs, clusterFilter],
    );

    const sorted = useMemo(() => {
        if (sortKey === 'none') return filtered;
        const dir = sortDir === 'asc' ? 1 : -1;
        return [...filtered].sort((a, b) => {
            const va = sortKey === 'metric' ? metricOf(a) : (a.growth_rate ?? -Infinity);
            const vb = sortKey === 'metric' ? metricOf(b) : (b.growth_rate ?? -Infinity);
            return (va - vb) * dir;
        });
    }, [filtered, sortKey, sortDir]);

    const maxJobCount = Math.max(1, ...filtered.filter(t => t.source !== 'articles').map(t => t.job_count || 0));
    const maxMentionCount = Math.max(1, ...filtered.filter(t => t.source === 'articles').map(t => t.mention_count || 0));
    const maxAbsGrowth = Math.max(1, ...filtered.map(t => Math.abs(t.growth_rate || 0)));

    const toggleSort = (key: 'metric' | 'growth') => {
        if (sortKey !== key) { setSortKey(key); setSortDir('desc'); return; }
        setSortDir(d => (d === 'desc' ? 'asc' : 'desc'));
    };

    const sortArrow = (key: 'metric' | 'growth') => {
        if (sortKey !== key) return '';
        return sortDir === 'desc' ? ' ▼' : ' ▲';
    };

    const handleExportCsv = () => {
        const csv = buildCsv(sorted);
        const blob = new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `techradar-top-techs-${periodLabel || 'report'}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    };

    if (techs.length === 0) return null;

    return (
        <div className="card report-table-card">
            <div className="report-table-header">
                <h2 className="section-title">Top {techs.length} công nghệ nổi bật</h2>
                <div className="report-table-header-right">
                    {periodLabel && <span className="report-period-badge">{periodLabel}</span>}
                    {clusters.length > 0 && (
                        <select
                            className="cluster-filter-select"
                            value={clusterFilter}
                            onChange={e => setClusterFilter(e.target.value)}
                            aria-label="Lọc theo cluster"
                        >
                            <option value="all">Tất cả cụm</option>
                            {clusters.map(c => <option key={c} value={c}>{c}</option>)}
                        </select>
                    )}
                    <button type="button" className="btn btn-secondary sm" onClick={handleExportCsv}>
                        Xuất CSV
                    </button>
                </div>
            </div>
            <div className="report-tech-table">
                <div className="report-table-head">
                    <span>#</span>
                    <span>Công nghệ</span>
                    <span>Cluster</span>
                    <button type="button" className="sortable-head" onClick={() => toggleSort('metric')}>
                        Chỉ số{sortArrow('metric')}
                    </button>
                    <button type="button" className="sortable-head" onClick={() => toggleSort('growth')}>
                        Tăng trưởng{sortArrow('growth')}
                    </button>
                </div>
                {sorted.map((t, i) => (
                    <div key={t.name || i} className="report-table-row">
                        <span className="rank">#{i + 1}</span>
                        <span className="tech-name">
                            {t.name}
                            <Link
                                to={`/dashboard?forecastTech=${encodeURIComponent(t.name)}`}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="forecast-link"
                                title={`Xem dự báo xu hướng cho ${t.name}`}
                            >
                                📈
                            </Link>
                        </span>
                        <span className="cluster-label">{t.cluster_label || '—'}</span>
                        <span className="job-count">
                            <span className="cell-value">
                                <span className="metric-source-icon">{t.source === 'articles' ? '📰' : '📊'}</span>
                                {t.source === 'articles'
                                    ? `${t.mention_count?.toLocaleString() ?? '—'} bài viết`
                                    : `${t.job_count?.toLocaleString() ?? '—'} việc làm`}
                            </span>
                            <span className="sparkbar-track">
                                <span
                                    className="sparkbar-fill"
                                    style={{
                                        width: `${(metricOf(t) || 0) / (t.source === 'articles' ? maxMentionCount : maxJobCount) * 100}%`,
                                    }}
                                />
                            </span>
                        </span>
                        <span className={`growth-rate ${(t.growth_rate ?? 0) >= 0 ? 'up' : 'down'}`}>
                            <span className="cell-value">
                                {t.growth_rate != null
                                    ? `${t.growth_rate >= 0 ? '+' : ''}${Number(t.growth_rate).toFixed(1)}%`
                                    : '—'}
                            </span>
                            <span className="sparkbar-track">
                                <span
                                    className={`sparkbar-fill growth ${(t.growth_rate ?? 0) >= 0 ? 'up' : 'down'}`}
                                    style={{ width: `${Math.abs(t.growth_rate || 0) / maxAbsGrowth * 100}%` }}
                                />
                            </span>
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}
