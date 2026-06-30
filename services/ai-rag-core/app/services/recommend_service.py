"""
Recommendation Service — gợi ý công nghệ / kỹ năng dựa trên:
  1. Tech user đang dùng (từ user_profile.preferences_json hoặc current_techs input)
  2. Neo4j graph traversal (RELATED_TO co-occurrence)
  3. PostgreSQL tech_analytics (growth_rate tháng gần nhất)
  4. LLM sinh lý giải ngắn cho từng recommendation
"""
import logging
import uuid
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import RecommendItem, RecommendRequest, RecommendResponse
from app.core.generator import generate
from app.db.neo4j_client import run_query
from app.db.postgres_client import get_session_factory

logger = logging.getLogger("ai-rag-core.recommend")

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"


def _load_template(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


async def _get_user_techs(user_id: uuid.UUID, db: AsyncSession) -> list[str]:
    """Lấy tech từ user_profile.preferences_json["interested_techs"]."""
    try:
        result = await db.execute(
            text("SELECT preferences_json FROM user_profile WHERE user_id = :uid"),
            {"uid": str(user_id)},
        )
        row = result.mappings().first()
        if row and row["preferences_json"]:
            prefs = row["preferences_json"]
            if isinstance(prefs, dict):
                return prefs.get("interested_techs", [])
    except Exception as e:
        logger.warning("Failed to get user techs: %s", e)
    return []


async def _graph_related_techs(user_techs: list[str]) -> list[dict]:
    """
    Tìm tech liên quan qua RELATED_TO trong Neo4j.
    Trả về: [{related_tech, co_occurrence, ring}]
    """
    if not user_techs:
        return []
    names_lower = [t.lower() for t in user_techs]
    rows = await run_query(
        """
        MATCH (t:Technology)-[:RELATED_TO]-(t2:Technology)
        WHERE toLower(t.name) IN $names
          AND NOT toLower(t2.name) IN $names
        OPTIONAL MATCH (t2)-[:IN_RING]->(r)
        WITH t2.name AS related_tech, r.name AS ring,
             count(*) AS co_occurrence
        RETURN related_tech, ring, co_occurrence
        ORDER BY co_occurrence DESC
        LIMIT 30
        """,
        {"names": names_lower},
    )
    return rows


async def _get_latest_analytics(tech_names: list[str]) -> dict[str, dict]:
    """Lấy growth_rate tháng gần nhất cho danh sách tech."""
    if not tech_names:
        return {}
    factory = get_session_factory()
    async with factory() as session:
        try:
            result = await session.execute(
                text("""
                    SELECT DISTINCT ON (technology_name)
                        technology_name,
                        job_count,
                        article_count,
                        growth_rate,
                        mom_growth
                    FROM tech_analytics
                    WHERE technology_name = ANY(:names)
                    ORDER BY technology_name, month DESC
                """),
                {"names": tech_names},
            )
            return {
                row["technology_name"]: dict(row._mapping)
                for row in result
            }
        except Exception as e:
            logger.warning("Failed to get analytics for recommendation: %s", e)
            return {}


def _score_and_rank(
    related: list[dict],
    analytics: dict[str, dict],
    limit: int,
) -> list[dict]:
    """
    Weighted score = 0.6 * co_occurrence_norm + 0.4 * growth_rate_norm
    Cả hai được normalize về [0, 1] trước khi tính.
    """
    if not related:
        return []

    max_co = max((r.get("co_occurrence") or 0) for r in related) or 1
    growth_values = [
        analytics.get(r["related_tech"], {}).get("mom_growth") or 0.0
        for r in related
    ]
    max_growth = max(abs(g) for g in growth_values) or 1

    scored = []
    for r in related:
        name = r.get("related_tech") or ""
        co = (r.get("co_occurrence") or 0) / max_co
        analytic = analytics.get(name, {})
        growth = (analytic.get("mom_growth") or 0.0) / max_growth
        score = 0.6 * co + 0.4 * growth
        scored.append({
            **r,
            "growth_rate": analytic.get("growth_rate"),
            "job_count":   analytic.get("job_count"),
            "confidence":  round(score, 3),
        })

    scored.sort(key=lambda x: x["confidence"], reverse=True)
    return scored[:limit]


async def _llm_explain(user_techs: list[str], top_items: list[dict]) -> list[str]:
    """Sinh lý giải ngắn cho từng recommendation bằng LLM."""
    if not top_items:
        return []

    lines = []
    for item in top_items:
        name = item.get("related_tech", "")
        ring = item.get("ring") or "unknown"
        growth = item.get("growth_rate")
        co = item.get("co_occurrence", 0)
        growth_str = f"{growth:+.1f}%" if growth is not None else "không có dữ liệu"
        lines.append(
            f"- {name} (ring: {ring}, co-occurrence: {co}, tăng trưởng: {growth_str})"
        )

    template = _load_template("recommend_template.txt")
    prompt = template.format(
        user_techs=", ".join(user_techs),
        recommendations="\n".join(lines),
    )

    messages = [
        {
            "role": "system",
            "content": (
                "Bạn là chuyên gia tư vấn công nghệ. "
                "Với mỗi công nghệ được đề xuất, hãy viết 1 câu lý giải ngắn gọn bằng tiếng Việt "
                "tại sao nên học/dùng nó dựa trên context. "
                "Chỉ trả về danh sách JSON: [{\"tech\": \"...\", \"reason\": \"...\"}]"
            ),
        },
        {"role": "user", "content": prompt},
    ]

    try:
        raw = await generate(messages)
        import json, re
        match = re.search(r"\[.*\]", raw, re.DOTALL)
        if match:
            return json.loads(match.group())
    except Exception as e:
        logger.warning("LLM explain failed for recommendations: %s", e)

    return [{"tech": item.get("related_tech", ""), "reason": ""} for item in top_items]


async def handle(req: RecommendRequest, db: AsyncSession) -> RecommendResponse:
    # 1. Lấy tech user đang dùng
    user_techs: list[str] = list(req.current_techs)
    if req.user_id and not user_techs:
        user_techs = await _get_user_techs(req.user_id, db)

    if not user_techs:
        return RecommendResponse(recommendations=[], based_on=[])

    # 2. Graph traversal
    related = await _graph_related_techs(user_techs)

    # 3. SQL analytics
    related_names = [r.get("related_tech") for r in related if r.get("related_tech")]
    analytics = await _get_latest_analytics(related_names)

    # 4. Score + rank
    top_items = _score_and_rank(related, analytics, req.limit)

    # 5. LLM explain
    explanations = await _llm_explain(user_techs, top_items)
    explain_map = {e.get("tech", ""): e.get("reason", "") for e in explanations} if isinstance(explanations, list) else {}

    recommendations = [
        RecommendItem(
            tech_name=item.get("related_tech", ""),
            reason=explain_map.get(item.get("related_tech", ""), ""),
            ring=item.get("ring"),
            growth_rate=item.get("growth_rate"),
            co_occurrence=item.get("co_occurrence", 0),
            confidence=item.get("confidence", 0.0),
        )
        for item in top_items
    ]

    return RecommendResponse(recommendations=recommendations, based_on=user_techs)
