import { useState } from 'react';
import { generateReport } from '../api/reportService';
import './ReportPage.css';

const PERIOD_PRESETS = (() => {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1; // 1-12
    const quarter = Math.ceil(month / 3);
    const presets = [];
    // Thêm quý hiện tại và 3 quý trước
    for (let i = 0; i < 4; i++) {
        let q = quarter - i;
        let y = year;
        while (q < 1) { q += 4; y -= 1; }
        presets.push({ label: `Q${q} ${y}`, value: `${y}-Q${q}` });
    }
    // Thêm năm hiện tại và năm trước
    presets.push({ label: `Năm ${year}`, value: `${year}` });
    presets.push({ label: `Năm ${year - 1}`, value: `${year - 1}` });
    return presets;
})();

const TOP_N_OPTIONS = [5, 10, 15, 20];

export default function ReportPage() {
    const [period, setPeriod] = useState(PERIOD_PRESETS[0].value);
    const [topN, setTopN] = useState(10);
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleGenerate = async (e) => {
        e.preventDefault();
        if (!period.trim()) return;
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const res = await generateReport(period.trim(), topN, 'markdown');
            setResult(res?.data ?? res);
        } catch (err) {
            setError(err.message || 'Không thể tạo báo cáo. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    const handleDownload = () => {
        if (!result?.report) return;
        const blob = new Blob([result.report], { type: 'text/markdown' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `techradar-report-${result.period || period}.md`;
        a.click();
        URL.revokeObjectURL(url);
    };

    return (
        <div className="report-page">
            <div className="report-hero">
                <h1 className="report-title">Báo cáo xu hướng công nghệ</h1>
                <p className="report-subtitle">
                    Phân tích tổng hợp các công nghệ nổi bật theo quý / năm
                </p>
            </div>

            <div className="report-controls card">
                <form onSubmit={handleGenerate} className="report-form">
                    <div className="report-control-group">
                        <label className="form-label">Kỳ báo cáo</label>
                        <div className="period-presets">
                            {PERIOD_PRESETS.map(p => (
                                <button
                                    key={p.value}
                                    type="button"
                                    className={`period-chip${period === p.value ? ' active' : ''}`}
                                    onClick={() => setPeriod(p.value)}
                                >
                                    {p.label}
                                </button>
                            ))}
                        </div>
                        <input
                            type="text"
                            className="form-input period-input"
                            value={period}
                            onChange={e => setPeriod(e.target.value)}
                            placeholder="VD: 2024-Q4, 2024-12, 2024"
                        />
                    </div>

                    <div className="report-control-group">
                        <label className="form-label">Top N công nghệ</label>
                        <div className="period-presets">
                            {TOP_N_OPTIONS.map(n => (
                                <button
                                    key={n}
                                    type="button"
                                    className={`period-chip${topN === n ? ' active' : ''}`}
                                    onClick={() => setTopN(n)}
                                >
                                    Top {n}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="report-actions">
                        <button
                            type="submit"
                            className="btn btn-primary"
                            disabled={loading || !period.trim()}
                        >
                            {loading ? (
                                <><span className="btn-spinner" /> Đang tạo báo cáo...</>
                            ) : 'Tạo báo cáo'}
                        </button>
                        {result?.report && (
                            <button
                                type="button"
                                className="btn btn-secondary"
                                onClick={handleDownload}
                            >
                                Tải xuống (.md)
                            </button>
                        )}
                    </div>
                </form>

                {error && <div className="report-error">{error}</div>}
            </div>

            {result && (
                <div className="report-result">
                    {/* Top techs table */}
                    {result.top_techs?.length > 0 && (
                        <div className="card report-table-card">
                            <div className="report-table-header">
                                <h2 className="section-title">Top {result.top_techs.length} công nghệ nổi bật</h2>
                                <span className="report-period-badge">{result.period}</span>
                            </div>
                            <div className="report-tech-table">
                                <div className="report-table-head">
                                    <span>#</span>
                                    <span>Công nghệ</span>
                                    <span>Cluster</span>
                                    <span>Số jobs</span>
                                    <span>Tăng trưởng</span>
                                </div>
                                {result.top_techs.map((t, i) => (
                                    <div key={t.name || i} className="report-table-row">
                                        <span className="rank">#{i + 1}</span>
                                        <span className="tech-name">{t.name}</span>
                                        <span className="cluster-label">{t.cluster_label || '—'}</span>
                                        <span className="job-count">{t.job_count?.toLocaleString() || '—'}</span>
                                        <span className={`growth-rate ${(t.growth_rate ?? 0) >= 0 ? 'up' : 'down'}`}>
                                            {t.growth_rate != null
                                                ? `${t.growth_rate >= 0 ? '+' : ''}${Number(t.growth_rate).toFixed(1)}%`
                                                : '—'}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Markdown report */}
                    {result.report && (
                        <div className="card report-content-card">
                            <div className="report-content-header">
                                <h2 className="section-title">Nội dung báo cáo</h2>
                                {result.generated_at && (
                                    <span className="report-generated-at">
                                        Tạo lúc: {new Date(result.generated_at).toLocaleString('vi-VN')}
                                    </span>
                                )}
                            </div>
                            <div
                                className="report-markdown"
                                dangerouslySetInnerHTML={{
                                    __html: result.report
                                        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                                        .replace(/^### (.*)/gm, '<h3>$1</h3>')
                                        .replace(/^## (.*)/gm, '<h2>$1</h2>')
                                        .replace(/^# (.*)/gm, '<h1>$1</h1>')
                                        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                                        .replace(/\*(.*?)\*/g, '<em>$1</em>')
                                        .replace(/^- (.*)/gm, '<li>$1</li>')
                                        .replace(/^(\d+)\. (.*)/gm, '<li>$2</li>')
                                        .replace(/\n\n/g, '</p><p>')
                                        .replace(/\n/g, '<br/>')
                                        .replace(/`([^`]+)`/g, '<code>$1</code>')
                                }}
                            />
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
