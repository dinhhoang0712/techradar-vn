import MarkdownContent from '../common/MarkdownContent';
import type { TechSummary } from '../../types/summarize';

interface SummaryPanelProps {
    tech: string;
    loading: boolean;
    summary: TechSummary | null;
    error: string;
    onClose: () => void;
}

// Panel tóm tắt tin tức gần đây cho 1 công nghệ (POST /chat/summarize).
export default function SummaryPanel({ tech, loading, summary, error, onClose }: SummaryPanelProps) {
    return (
        <div className="forecast-panel">
            <div className="forecast-header">
                <h2 className="section-title">Tóm tắt tin tức: <span style={{ color: 'var(--primary)' }}>{tech}</span></h2>
                <button className="forecast-close-btn" onClick={onClose}>✕</button>
            </div>

            {loading ? (
                <div className="forecast-loading">Đang tổng hợp tin tức gần đây...</div>
            ) : error ? (
                <div className="forecast-loading" style={{ color: 'var(--danger-light)' }}>{error}</div>
            ) : summary ? (
                <div className="forecast-content">
                    {summary.period && (
                        <span className="forecast-confidence">Giai đoạn: {summary.period} • {summary.sources_used ?? 0} nguồn tin</span>
                    )}
                    {summary.summary && <MarkdownContent className="forecast-reasoning">{summary.summary}</MarkdownContent>}
                    {summary.key_points && summary.key_points.length > 0 && (
                        <div className="forecast-signals">
                            {summary.key_points.map((p, i) => (
                                <div key={i} className="forecast-signal">
                                    <MarkdownContent className="signal-label">{p}</MarkdownContent>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            ) : (
                <div className="forecast-loading">Chưa có đủ tin tức gần đây cho công nghệ này.</div>
            )}
        </div>
    );
}
