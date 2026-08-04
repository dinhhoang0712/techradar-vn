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

# Cùng enum 6 mức đã dùng cho dp_processed_jobs.level / user_profile.current_level
# (V38__job_level_enum.sql, V39__user_profile_current_level.sql) — thứ tự dùng để tính
# level_distance cho estimated_months.
_LEVEL_ORDER = ["Intern", "Fresher", "Junior", "Middle", "Senior", "Lead"]
# Dưới ngưỡng này, kết quả lọc theo level được coi là quá thưa để tin — phần lớn job hiện
# vẫn có level IS NULL (chỉ VietnamWorks crawl được level thật), nên fallback về danh sách
# không lọc thay vì trả skill_gap gần rỗng.
_MIN_LEVELED_SKILLS = 5


def _load_template(filename: str) -> str:
    return (_PROMPTS_DIR / filename).read_text(encoding="utf-8").strip()


async def _get_user_profile_defaults(user_id: uuid.UUID, db: AsyncSession) -> tuple[list[str], str | None, str | None]:
    """current_skills (từ preferences_json.interested_techs) + current_level + job_role, trong 1
    query — user_profile là bảng share với Java, nên current_level/job_role ghi qua
    PUT /user/profile đọc được thẳng ở đây."""
    try:
        result = await db.execute(
            text("SELECT preferences_json, current_level, job_role FROM user_profile WHERE user_id = :uid"),
            {"uid": str(user_id)},
        )
        row = result.mappings().first()
        if not row:
            return [], None, None
        skills: list[str] = []
        if row["preferences_json"]:
            prefs = row["preferences_json"]
            if isinstance(prefs, dict):
                skills = prefs.get("interested_techs", [])
        return skills, row["current_level"], row["job_role"]
    except Exception as e:
        logger.warning("get_user_profile_defaults failed: %s", e)
        return [], None, None


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


async def _neo4j_role_required_skills(target_role: str, target_level: str | None = None) -> list[str]:
    """Lấy skills mà target role yêu cầu nhiều nhất (từ Job data).

    Khi có target_level, thử lọc theo j.level trước để skill_gap sát với đúng cấp bậc hơn —
    nhưng phần lớn job hiện vẫn có level IS NULL, nên nếu kết quả lọc quá thưa
    (< _MIN_LEVELED_SKILLS), fallback về danh sách không lọc thay vì trả gần như rỗng.
    """
    try:
        if target_level:
            leveled_rows = await run_query(
                """
                MATCH (j:Job)-[:REQUIRES]->(t)
                WHERE toLower(j.title) CONTAINS toLower($role)
                  AND j.level = $level
                  AND (t:Technology OR t:Skill)
                RETURN t.name AS skill, count(*) AS demand
                ORDER BY demand DESC
                LIMIT 15
                """,
                {"role": target_role, "level": target_level},
            )
            leveled_skills = [r["skill"] for r in leveled_rows if r.get("skill")]
            if len(leveled_skills) >= _MIN_LEVELED_SKILLS:
                return leveled_skills

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
    # 1. Lấy current skills + current level + job_role hiện tại (fallback: tra user_profile
    # theo user_id) — job_role dùng để default target_role khi auto-load roadmap không gửi
    # target_role (Java GetCareerRoadmapUseCase chỉ forward user_id).
    current_skills = list(req.current_skills)
    current_level = req.current_level
    profile_job_role = None
    if req.user_id and (not current_skills or not current_level or not req.target_role):
        profile_skills, profile_level, profile_job_role = await _get_user_profile_defaults(req.user_id, db)
        if not current_skills:
            current_skills = profile_skills
        if not current_level:
            current_level = profile_level

    # auto_loaded: caller không gửi target_role rõ ràng (auto-load roadmap, không phải tìm kiếm
    # thủ công trên CareerPage) — default target_role về job_role hiện tại của user (không còn
    # cứng "Senior Software Engineer" cho mọi người), và target_level về chính current_level (gap
    # trong cùng cấp/role, thay vì giả định ai cũng nhắm lên Senior).
    auto_loaded = not req.target_role
    target_role = req.target_role or profile_job_role or "Software Engineer"
    target_level = req.target_level or (current_level if auto_loaded else None)

    # 2. Neo4j: tìm required skills cho target role (ưu tiên đúng target_level nếu đủ dữ liệu)
    required_skills = await _neo4j_role_required_skills(target_role, target_level)

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
    analytics_lines = (
        "\n".join(f"- {name}: {d.get('job_count', 0)} việc làm" for name, d in analytics_map.items())
        or "(Không có dữ liệu)"
    )

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

    # level_distance: khoảng cách vị trí giữa current_level/target_level trong _LEVEL_ORDER —
    # cá nhân hoá estimated_months hơn số lượng skill_gap thuần, khi biết đủ cả 2 mức.
    level_distance = None
    if current_level in _LEVEL_ORDER and target_level in _LEVEL_ORDER:
        level_distance = abs(_LEVEL_ORDER.index(target_level) - _LEVEL_ORDER.index(current_level))

    return CareerResponse(
        target_role=target_role,
        current_skills=current_skills,
        current_level=current_level,
        target_level=target_level,
        skill_gap=skill_gap,
        roadmap=roadmap,
        estimated_months=len(gap_skills) * 2 + (level_distance * 3 if level_distance is not None else 0),
    )
