"""
Compute and persist demand_score for Technology and Skill nodes.

demand_score reflects how frequently a technology/skill appears in job
postings — a proxy for market demand.  Formula: same log1p normalization
as trend_scorer.

  score(t) = log(1 + job_count(t)) / log(1 + max_job_count)

Result range: [0.0, 1.0]
  - 1.0  → required by the most job postings
  - 0.0  → not required by any job posting
  - ~0.5 → roughly 1/3 of max (log scale)

Run this after each import cycle so scores reflect the latest postings.
"""
import logging
import math

logger = logging.getLogger(__name__)


def _log1p_normalize(counts: dict[str, int]) -> dict[str, float]:
    """Normalize raw counts using log(1+x) / log(1+max)."""
    if not counts:
        return {}
    max_count = max(counts.values())
    if max_count == 0:
        return {k: 0.0 for k in counts}
    denom = math.log1p(max_count)
    return {k: round(math.log1p(v) / denom, 4) for k, v in counts.items()}


def compute_and_update_demand_scores(driver, database: str) -> dict[str, dict[str, float]]:
    """
    Query Neo4j for job requirement counts, compute demand_score per
    Technology and Skill node, persist back to the graph.

    Args:
        driver:   Active neo4j.GraphDatabase.driver instance.
        database: Neo4j database name.

    Returns:
        dict with keys 'technologies' and 'skills', each mapping
        node name → computed demand_score.
    """
    from cypher_repo.repository import (
        ANALYTICS_TECH_DEMAND_COUNTS,
        ANALYTICS_SKILL_DEMAND_COUNTS,
        ANALYTICS_UPDATE_TECH_DEMAND_SCORES,
        ANALYTICS_UPDATE_SKILL_DEMAND_SCORES,
    )

    result: dict[str, dict[str, float]] = {"technologies": {}, "skills": {}}

    # ---- Technology demand ------------------------------------------------
    with driver.session(database=database) as session:
        tech_result = session.run(ANALYTICS_TECH_DEMAND_COUNTS)
        tech_counts: dict[str, int] = {
            r["tech"]: r["job_count"] for r in tech_result
        }

    if tech_counts:
        tech_scores = _log1p_normalize(tech_counts)
        tech_params = [{"name": t, "score": s} for t, s in tech_scores.items()]
        with driver.session(database=database) as session:
            session.run(ANALYTICS_UPDATE_TECH_DEMAND_SCORES, {"scores": tech_params})
        result["technologies"] = tech_scores
        logger.info("Updated demand_score for %d Technology nodes (max jobs: %d)",
                    len(tech_scores), max(tech_counts.values()))
    else:
        logger.warning("No Technology demand data found — scores unchanged")

    # ---- Skill demand -----------------------------------------------------
    with driver.session(database=database) as session:
        skill_result = session.run(ANALYTICS_SKILL_DEMAND_COUNTS)
        skill_counts: dict[str, int] = {
            r["skill"]: r["job_count"] for r in skill_result
        }

    if skill_counts:
        skill_scores = _log1p_normalize(skill_counts)
        # Cypher: UNWIND $scores AS s — alias 's' shadows Skill node variable,
        # so the update query uses 'item' to avoid the collision.
        skill_params = [{"name": sk, "score": sc} for sk, sc in skill_scores.items()]
        with driver.session(database=database) as session:
            session.run(ANALYTICS_UPDATE_SKILL_DEMAND_SCORES, {"scores": skill_params})
        result["skills"] = skill_scores
        logger.info("Updated demand_score for %d Skill nodes (max jobs: %d)",
                    len(skill_scores), max(skill_counts.values()))
    else:
        logger.warning("No Skill demand data found — scores unchanged")

    # Log top-10 technologies
    top = sorted(result["technologies"].items(), key=lambda x: x[1], reverse=True)[:10]
    for tech, score in top:
        logger.info("  demand_score %-30s %.4f  (jobs: %d)",
                    tech, score, tech_counts.get(tech, 0))

    return result
