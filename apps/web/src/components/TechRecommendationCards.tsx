import './TechRecommendationCards.css';
import MarkdownContent from './common/MarkdownContent';
import type { NextSkill } from '../types/career';

interface TechRecommendationCardsProps {
    recommendations?: NextSkill[];
    basedOn?: string[];
    loading?: boolean;
    title?: string;
    emptyMessage?: string | null;
    highlightSkills?: string[];
}

/**
 * Grid of "next tech to learn" cards (name, ring, reason, growth rate, confidence).
 * Shared between UserProfile (auto-loaded from /recommend) and CareerPage (via the
 * aggregated /career/roadmap response, which also adds `job_matches_needing_it` and
 * `tech_path` — the shortest graph path from the user's first current tech).
 *
 * `highlightSkills` comes from a job-match notification's deep link
 * (JobMatchDispatcher's `/career?highlight=...`) — marks the card(s) whose `tech_name` matched
 * the job that triggered the notification.
 */
export default function TechRecommendationCards({
    recommendations = [],
    basedOn = [],
    loading = false,
    title = 'Công nghệ được gợi ý cho bạn',
    emptyMessage = null,
    highlightSkills = [],
}: TechRecommendationCardsProps) {
    const highlightSet = new Set(highlightSkills.map(s => s.toLowerCase()));
    if (!loading && recommendations.length === 0 && !emptyMessage) {
        return null;
    }

    return (
        <div className="profile-recommendations">
            <h2 className="profile-recs-title">{title}</h2>
            {!loading && basedOn.length > 0 && (
                <p className="profile-recs-based-on">Dựa trên: {basedOn.join(', ')}</p>
            )}
            {loading ? (
                <div className="recs-loading">Đang tải gợi ý...</div>
            ) : recommendations.length === 0 ? (
                <p className="recs-empty">{emptyMessage}</p>
            ) : (
                <div className="recs-grid">
                    {recommendations.map((rec) => {
                        const isHighlighted = highlightSet.has((rec.tech_name || '').toLowerCase());
                        return (
                        <div
                            key={rec.tech_name}
                            className={`rec-card${isHighlighted ? ' rec-card--highlighted' : ''}`}
                        >
                            {isHighlighted && (
                                <div className="rec-highlight-badge">Có job mới phù hợp với kỹ năng này</div>
                            )}
                            <div className="rec-header">
                                <span className="rec-name">{rec.tech_name}</span>
                                {rec.ring && (
                                    <span className={`rec-ring rec-ring--${rec.ring.toLowerCase()}`}>
                                        {rec.ring}
                                    </span>
                                )}
                            </div>
                            {rec.reason && (
                                <MarkdownContent className="rec-reason">{rec.reason}</MarkdownContent>
                            )}
                            <div className="rec-meta">
                                {rec.growth_rate != null && (
                                    <span className={`rec-growth ${rec.growth_rate >= 0 ? 'up' : 'down'}`}>
                                        {rec.growth_rate >= 0 ? '+' : ''}{Number(rec.growth_rate).toFixed(1)}%
                                    </span>
                                )}
                                {(rec.co_occurrence ?? 0) > 0 && (
                                    <span className="rec-cooc">Co-use: {rec.co_occurrence}</span>
                                )}
                                {(rec.job_matches_needing_it ?? 0) > 0 && (
                                    <span className="rec-cooc">{rec.job_matches_needing_it} job cần kỹ năng này</span>
                                )}
                            </div>
                            {rec.confidence != null && (
                                <div className="rec-confidence">
                                    <div className="rec-confidence-track">
                                        <div
                                            className="rec-confidence-fill"
                                            style={{ width: `${Math.round(rec.confidence * 100)}%` }}
                                        />
                                    </div>
                                    <span className="rec-confidence-label">{Math.round(rec.confidence * 100)}% phù hợp</span>
                                </div>
                            )}
                            {(rec.tech_path?.length ?? 0) > 1 && (
                                <div className="rec-path-gps" role="list" aria-label="Lộ trình học">
                                    {rec.tech_path!.map((step, i, arr) => {
                                        const isFirst = i === 0;
                                        const isLast = i === arr.length - 1;
                                        return (
                                            <div
                                                className={`rec-path-step${isFirst ? ' rec-path-step--start' : ''}${isLast ? ' rec-path-step--target' : ''}`}
                                                key={`${step}-${i}`}
                                                role="listitem"
                                                title={step}
                                            >
                                                <span className="rec-path-dot" aria-hidden="true" />
                                                <span className="rec-path-label">{step}</span>
                                                {!isLast && <span className="rec-path-line" aria-hidden="true" />}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
