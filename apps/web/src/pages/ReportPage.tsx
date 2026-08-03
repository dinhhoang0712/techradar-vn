import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { generateReport } from '../api/reportService';
import MarkdownContent from '../components/common/MarkdownContent';
import CopyButton from '../components/common/CopyButton';
import ReportTechTable from '../components/report/ReportTechTable';
import ReportGrowthChart from '../components/report/ReportGrowthChart';
import { useReportHistory } from '../hooks/useReportHistory';
import { exportElementToPdf } from '../utils/exportPdf';
import type { ReportResult, ReportTechRow } from '../types/report';
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

function computeDelta(base: ReportTechRow[], compare: ReportTechRow[]) {
    const baseNames = new Set(base.map(t => t.name));
    const compareNames = new Set(compare.map(t => t.name));
    return {
        newInCompare: compare.filter(t => !baseNames.has(t.name)).map(t => t.name),
        droppedFromBase: base.filter(t => !compareNames.has(t.name)).map(t => t.name),
    };
}

function formatSavedAt(iso: string): string {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('vi-VN');
}

export default function ReportPage() {
    const [searchParams] = useSearchParams();
    const [period, setPeriod] = useState(() => searchParams.get('period') || PERIOD_PRESETS[0].value);
    const [topN, setTopN] = useState(() => {
        const n = Number(searchParams.get('topN'));
        return TOP_N_OPTIONS.includes(n) ? n : 10;
    });
    const [result, setResult] = useState<ReportResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [exportingPdf, setExportingPdf] = useState(false);
    const reportResultRef = useRef<HTMLDivElement>(null);

    // So sánh với 1 kỳ khác — gọi lại /report cho kỳ thứ 2 song song, không cần đổi BE.
    const [compareEnabled, setCompareEnabled] = useState(false);
    const [comparePeriod, setComparePeriod] = useState('');
    const [compareResult, setCompareResult] = useState<ReportResult | null>(null);
    const [compareLoading, setCompareLoading] = useState(false);
    const [compareError, setCompareError] = useState('');

    const { entries: history, addEntry: addHistoryEntry, removeEntry: removeHistoryEntry } = useReportHistory();
    const [historyOpen, setHistoryOpen] = useState(false);

    const runGenerate = useCallback(async (p: string, n: number) => {
        if (!p.trim()) return;
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const res = await generateReport(p.trim(), n, 'markdown');
            const data = ('data' in res ? res.data : res) ?? null;
            setResult(data);
            if (data) addHistoryEntry(p.trim(), n, data);
        } catch (err) {
            setError((err as Error).message || 'Không thể tạo báo cáo. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    }, [addHistoryEntry]);

    const runCompareGenerate = useCallback(async (p: string, n: number) => {
        if (!p.trim()) return;
        setCompareLoading(true);
        setCompareError('');
        setCompareResult(null);
        try {
            const res = await generateReport(p.trim(), n, 'markdown');
            const data = ('data' in res ? res.data : res) ?? null;
            setCompareResult(data);
            if (data) addHistoryEntry(p.trim(), n, data);
        } catch (err) {
            setCompareError((err as Error).message || 'Không thể tạo báo cáo so sánh.');
        } finally {
            setCompareLoading(false);
        }
    }, [addHistoryEntry]);

    // Prefill từ query string (?period=&topN=&autorun=1) khi mở qua link chia sẻ — chỉ chạy 1 lần lúc mount.
    const autoRunDone = useRef(false);
    useEffect(() => {
        if (autoRunDone.current) return;
        autoRunDone.current = true;
        if (searchParams.get('autorun') === '1' && searchParams.get('period')) {
            runGenerate(period, topN);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleGenerate = (e: FormEvent) => {
        e.preventDefault();
        runGenerate(period, topN);
        if (compareEnabled && comparePeriod.trim() && comparePeriod.trim() !== period.trim()) {
            runCompareGenerate(comparePeriod, topN);
        } else {
            setCompareResult(null);
        }
    };

    const handleRestoreHistory = (entry: (typeof history)[number]) => {
        setPeriod(entry.period);
        setTopN(entry.topN);
        setResult(entry.result);
        setCompareResult(null);
        setHistoryOpen(false);
    };

    const shareUrl = `${window.location.origin}${window.location.pathname}?period=${encodeURIComponent(period)}&topN=${topN}&autorun=1`;

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

    // Chụp toàn bộ .report-result (bảng + nội dung markdown) thành PDF nhiều trang — xem
    // utils/exportPdf.ts (dùng chung với InterviewPage).
    const handleExportPDF = async () => {
        const el = reportResultRef.current;
        if (!el) return;
        setExportingPdf(true);
        try {
            await exportElementToPdf(el, `techradar-report-${result?.period || period}.pdf`);
        } catch (err) {
            console.error('[ReportPage] Export PDF failed:', err);
            setError('Không thể xuất PDF. Vui lòng thử lại.');
        } finally {
            setExportingPdf(false);
        }
    };

    const topTechs = result?.top_techs || [];
    const delta = compareResult ? computeDelta(topTechs, compareResult.top_techs || []) : null;

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
                                    className={`chip period-chip${period === p.value ? ' active' : ''}`}
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
                                    className={`chip period-chip${topN === n ? ' active' : ''}`}
                                    onClick={() => setTopN(n)}
                                >
                                    Top {n}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="report-control-group">
                        <label className="compare-toggle-label">
                            <input
                                type="checkbox"
                                checked={compareEnabled}
                                onChange={e => setCompareEnabled(e.target.checked)}
                            />
                            So sánh với kỳ khác
                        </label>
                        {compareEnabled && (
                            <>
                                <div className="period-presets">
                                    {PERIOD_PRESETS.filter(p => p.value !== period).map(p => (
                                        <button
                                            key={p.value}
                                            type="button"
                                            className={`chip period-chip${comparePeriod === p.value ? ' active' : ''}`}
                                            onClick={() => setComparePeriod(p.value)}
                                        >
                                            {p.label}
                                        </button>
                                    ))}
                                </div>
                                <input
                                    type="text"
                                    className="form-input period-input"
                                    value={comparePeriod}
                                    onChange={e => setComparePeriod(e.target.value)}
                                    placeholder="Kỳ so sánh, VD: 2024-Q3"
                                />
                            </>
                        )}
                    </div>

                    <div className="report-actions">
                        <button
                            type="submit"
                            className="btn btn-primary"
                            disabled={loading || compareLoading || !period.trim()}
                        >
                            {loading ? (
                                <><span className="btn-spinner" /> Đang tạo báo cáo...</>
                            ) : 'Tạo báo cáo'}
                        </button>
                        <CopyButton text={shareUrl} label="Sao chép liên kết" />
                        <div className="history-dropdown-wrap">
                            <button
                                type="button"
                                className="btn btn-secondary"
                                disabled={history.length === 0}
                                onClick={() => setHistoryOpen(o => !o)}
                            >
                                Lịch sử {history.length > 0 && `(${history.length})`}
                            </button>
                            {historyOpen && history.length > 0 && (
                                <div className="history-dropdown">
                                    {history.map(entry => (
                                        <div key={entry.id} className="history-item">
                                            <button
                                                type="button"
                                                className="history-item-main"
                                                onClick={() => handleRestoreHistory(entry)}
                                            >
                                                <span className="history-item-period">{entry.period} · Top {entry.topN}</span>
                                                <span className="history-item-time">{formatSavedAt(entry.savedAt)}</span>
                                            </button>
                                            <button
                                                type="button"
                                                className="history-item-remove"
                                                title="Xoá khỏi lịch sử"
                                                onClick={() => removeHistoryEntry(entry.id)}
                                            >
                                                ×
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                        {result?.report && (
                            <>
                                <button
                                    type="button"
                                    className="btn btn-secondary"
                                    onClick={handleDownload}
                                >
                                    Tải xuống (.md)
                                </button>
                                <button
                                    type="button"
                                    className="btn btn-secondary"
                                    onClick={handleExportPDF}
                                    disabled={exportingPdf}
                                >
                                    {exportingPdf ? <><span className="btn-spinner" /> Đang xuất PDF...</> : 'Xuất PDF'}
                                </button>
                            </>
                        )}
                    </div>
                </form>

                {error && <div className="report-error">{error}</div>}
                {compareError && <div className="report-error">{compareError}</div>}
            </div>

            {result && (
                <div className="report-result" ref={reportResultRef}>
                    {compareResult && (
                        <div className="card report-compare-card">
                            <h2 className="section-title">So sánh {result.period} với {compareResult.period}</h2>
                            {delta && (delta.newInCompare.length > 0 || delta.droppedFromBase.length > 0) && (
                                <div className="compare-delta-strip">
                                    {delta.newInCompare.length > 0 && (
                                        <div className="compare-delta-group">
                                            <span className="compare-delta-label new">Mới lọt Top ở {compareResult.period}</span>
                                            <div className="compare-delta-chips">
                                                {delta.newInCompare.map(name => (
                                                    <span key={name} className="chip compare-chip new">{name}</span>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                    {delta.droppedFromBase.length > 0 && (
                                        <div className="compare-delta-group">
                                            <span className="compare-delta-label dropped">Rớt khỏi Top ở {compareResult.period}</span>
                                            <div className="compare-delta-chips">
                                                {delta.droppedFromBase.map(name => (
                                                    <span key={name} className="chip compare-chip dropped">{name}</span>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                            <div className="compare-columns">
                                <ReportTechTable techs={topTechs} periodLabel={result.period} />
                                <ReportTechTable techs={compareResult.top_techs || []} periodLabel={compareResult.period} />
                            </div>
                        </div>
                    )}

                    {!compareResult && topTechs.length > 0 && (
                        <ReportTechTable techs={topTechs} periodLabel={result.period} />
                    )}

                    <ReportGrowthChart techs={topTechs} />

                    {/* Markdown report */}
                    {result.report && (
                        <div className="card report-content-card report-generated-doc">
                            <div className="report-content-header">
                                <h2 className="section-title">Nội dung báo cáo</h2>
                                <div className="report-content-header-right">
                                    {result.generated_at && (
                                        <span className="report-generated-at">
                                            Tạo lúc: {new Date(result.generated_at).toLocaleString('vi-VN')}
                                        </span>
                                    )}
                                    <CopyButton text={result.report} label="Copy báo cáo" />
                                </div>
                            </div>
                            <MarkdownContent className="report-markdown">{result.report}</MarkdownContent>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
