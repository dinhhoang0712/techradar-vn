"""
Compute and persist trend_score for Technology nodes.

trend_score reflects how actively a technology is being discussed in recent
articles.  Formula: log1p normalization over a rolling 30-day window.

  score(t) = log(1 + mentions_30d(t)) / log(1 + max_mentions_30d)

Result range: [0.0, 1.0]
  - 1.0  → most-mentioned technology this month
  - 0.0  → no mentions in the last 30 days
  - ~0.5 → roughly 1/3 of max mentions (log scale)

Run this after each import cycle so scores stay fresh.
"""
import logging
import math
from datetime import datetime, timedelta
from typing import Optional

logger = logging.getLogger(__name__)

# Rolling window for trend calculation
_WINDOW_DAYS = 30


def _log1p_normalize(counts: dict[str, int]) -> dict[str, float]:
    """Normalize raw counts using log(1+x) / log(1+max)."""
    if not counts:
        return {}
    max_count = max(counts.values())
    if max_count == 0:
        return {k: 0.0 for k in counts}
    denom = math.log1p(max_count)
    return {k: round(math.log1p(v) / denom, 4) for k, v in counts.items()}


def compute_and_update_trend_scores(
    driver,
    database: str,
    window_days: int = _WINDOW_DAYS,
    cutoff_date: Optional[datetime] = None,
) -> dict[str, float]:
    """
    Query Neo4j for article mention counts, compute trend_score per Technology,
    persist back to the graph, and return the scores map.

    Args:
        driver:      Active neo4j.GraphDatabase.driver instance.
        database:    Neo4j database name.
        window_days: Look-back window in days (default 30).
        cutoff_date: Override the cutoff date (useful for testing).

    Returns:
        dict mapping technology name → computed trend_score.
    """
    from cypher_repo.repository import (
        ANALYTICS_TECH_MENTION_COUNTS,
        ANALYTICS_UPDATE_TECH_TREND_SCORES,
    )

    if cutoff_date is None:
        cutoff_date = datetime.utcnow() - timedelta(days=window_days)

    cutoff_str = cutoff_date.strftime("%Y-%m-%d")
    logger.info("Computing trend scores (window: %d days, cutoff: %s)", window_days, cutoff_str)

    with driver.session(database=database) as session:
        # --- Read phase ---
        result = session.run(ANALYTICS_TECH_MENTION_COUNTS, {"cutoff_date": cutoff_str})
        raw_counts: dict[str, int] = {
            record["tech"]: record["mention_count"]
            for record in result
        }

    if not raw_counts:
        logger.warning("No article mentions found after %s — trend scores unchanged", cutoff_str)
        return {}

    scores = _log1p_normalize(raw_counts)
    logger.info("Computed trend scores for %d technologies (max mentions: %d)",
                len(scores), max(raw_counts.values()))

    # --- Write phase ---
    score_params = [{"name": tech, "score": score} for tech, score in scores.items()]
    with driver.session(database=database) as session:
        session.run(ANALYTICS_UPDATE_TECH_TREND_SCORES, {"scores": score_params})

    logger.info("Updated trend_score for %d Technology nodes", len(scores))

    # Log top-10 for visibility
    top = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:10]
    for tech, score in top:
        logger.info("  trend_score %-30s %.4f  (mentions: %d)",
                    tech, score, raw_counts[tech])

    return scores
