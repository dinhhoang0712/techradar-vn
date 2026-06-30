"""
Career Assistant Service — tư vấn lộ trình học tập / career path.
  1. Lấy user skills từ user_profile.preferences_json
  2. Neo4j: tìm skill gap path (shortestPath đến target role)
  3. PostgreSQL: job demand cho skills trên path
  4. LLM: sinh roadmap cụ thể
"""
import logging
import uuid
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas import CareerRequest, CareerResponse, CareerStep
from app.core.generator import generate
from app.core.retriever_sql import sql_analytics_search
from app.db.neo4j_client import run_query

logger = logging.getLogger("ai-rag-core.career")

_PROMPTS_DIR = Path(__file__).parent.parent / "prompts"


def _load_template(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


async def _get_user_skills(user_id: uuid.UUID, db: AsyncSession) -> list[str]:
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
        logger.warning("get_user_skills failed: %s", e)
    return []


async def _neo4j_skill_path(current_skills: list[str], target_role: str) -> list[dict]:
    """Tìm skill path từ current skills đến target role trong Neo4j."""
    if not current_skills or not target_role:
        return []
    names_lower = [s.lower() for s in current_skills]
    try:
        rows = await run_query(
            """
            UNWIND $skills AS skill
            MATCH (s:Skill)
            WHERE toLower(s.name) = skill
            MATCH (r:Role)
            WHERE toLower(r.name) CONTAINS toLower($role)
            MATCH path = shortestPath((s)-[:LEADS_TO*..8]->(r))
            WITH [n IN nodes(path) | n.name] AS skill_path
            RETURN skill_path
            LIMIT 5
            """,
            {"skills": names_lower, "role": target_role},
        )
        return rows
    except Exception as e:
        logger.warning("Neo4j skill path failed: %s", e)
        return []


async def _neo4j_role_required_skills(target_role: str) -> list[str]:
    """Lấy skills mà target role yêu cầu nhiều nhất (từ Job data)."""
    try:
        rows = await run_query(
            """
            MATCH (j:Job)-[:REQUIRES]->(t)
            WHERE toLower(j.title) CONTAINS toLower($role)
              AND (t:Technology OR t:Skill)
            RETURN t.name AS skill, count(*) AS demand
            ORDER BY demand DESC
            LIMIT 15
            """,
            {"role": target_role},
        )
        return [r["skill"] for r in rows if r.get("skill")]
    except Exception as e:
        logger.warning("Role required skills failed: %s", e)
        return []


async def handle(req: CareerRequest, db: AsyncSession) -> CareerResponse:
    # 1. Lấy current skills
    current_skills = list(req.current_skills)
    if req.user_id and not current_skills:
        current_skills = await _get_user_skills(req.user_id, db)

    target_role = req.target_role or "Senior Software Engineer"

    # 2. Neo4j: tìm required skills cho target role
    required_skills = await _neo4j_role_required_skills(target_role)

    # 3. Tính skill gap
    current_lower = {s.lower() for s in current_skills}
    gap_skills = [s for s in required_skills if s.lower() not in current_lower][:10]

    # 4. SQL analytics cho gap skills
    sql_data = []
    if gap_skills:
        sql_data = await sql_analytics_search(gap_skills, months=3)
    analytics_map = {r["technology_name"]: r for r in sql_data}

    # 5. LLM roadmap
    skill_gap_lines = "\n".join(f"- {s}" for s in gap_skills) or "(Không tìm thấy)"
    analytics_lines = "\n".join(
        f"- {name}: {d.get('job_count', 0)} việc làm"
        for name, d in analytics_map.items()
    ) or "(Không có dữ liệu)"

    template = _load_template("career_template.txt")
    prompt = template.format(
        current_skills=", ".join(current_skills) or "chưa có",
        target_role=target_role,
        skill_gap_data=skill_gap_lines,
        analytics_data=analytics_lines,
    )

    messages = [
        {"role": "system", "content": "Bạn là chuyên gia tư vấn nghề nghiệp IT tại Việt Nam."},
        {"role": "user", "content": prompt},
    ]

    roadmap = await generate(messages)

    skill_gap = [
        CareerStep(
            skill=skill,
            priority=i + 1,
            reason="Được yêu cầu nhiều trong các tin tuyển dụng",
            job_demand=analytics_map.get(skill, {}).get("job_count"),
        )
        for i, skill in enumerate(gap_skills[:5])
    ]

    return CareerResponse(
        target_role=target_role,
        current_skills=current_skills,
        skill_gap=skill_gap,
        roadmap=roadmap,
        estimated_months=len(gap_skills) * 2,
    )
