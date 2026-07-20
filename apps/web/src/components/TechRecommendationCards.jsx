import './TechRecommendationCards.css';

/**
 * Grid of "next tech to learn" cards (name, ring, reason, growth rate, confidence).
 * Shared between UserProfile (auto-loaded from /recommend) and CareerPage (via the
 * aggregated /career/roadmap response, which also adds `job_matches_needing_it` and
 * `tech_path` — the shortest graph path from the user's first current tech).
 */
export default function TechRecommendationCards({
    recommendations = [],
    basedOn = [],
    loading = false,
    title = 'Công nghệ được gợi ý cho bạn',
    emptyMessage = null,
}) {
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
                    {recommendations.map((rec) => (
                        <div key={rec.tech_name} className="rec-card">
                            <div className="rec-header">
                                <span className="rec-name">{rec.tech_name}</span>
                                {rec.ring && (
                                    <span className={`rec-ring rec-ring--${rec.ring.toLowerCase()}`}>
                                        {rec.ring}
                                    </span>
                                )}
                            </div>
                            {rec.reason && (
                                <p className="rec-reason">{rec.reason}</p>
                            )}
                            <div className="rec-meta">
                                {rec.growth_rate != null && (
                                    <span className={`rec-growth ${rec.growth_rate >= 0 ? 'up' : 'down'}`}>
                                        {rec.growth_rate >= 0 ? '+' : ''}{Number(rec.growth_rate).toFixed(1)}%
                                    </span>
                                )}
                                {rec.co_occurrence > 0 && (
                                    <span className="rec-cooc">Co-use: {rec.co_occurrence}</span>
                                )}
                                {rec.job_matches_needing_it > 0 && (
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
                            {rec.tech_path?.length > 1 && (
                                <p className="rec-path">{rec.tech_path.join(' → ')}</p>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
