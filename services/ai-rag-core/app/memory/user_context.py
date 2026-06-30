"""
User Long-term Memory — đọc từ user_profile.preferences_json (PostgreSQL).
Bảng này do Spring Boot Flyway own, ai-rag-core chỉ đọc + cập nhật preferences_json.

Cấu trúc preferences_json gợi ý:
{
  "interested_techs": ["React", "TypeScript"],
  "current_role": "Frontend Developer",
  "experience_years": 3,
  "tech_interactions": {"React": 5, "Kubernetes": 2}
}
"""
import uuid

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


async def get_long_term(user_id: uuid.UUID, db: AsyncSession) -> dict:
    """Lấy preferences_json của user."""
    try:
        result = await db.execute(
            text("SELECT preferences_json FROM user_profile WHERE user_id = :uid"),
            {"uid": str(user_id)},
        )
        row = result.mappings().first()
        if row and row["preferences_json"]:
            prefs = row["preferences_json"]
            return prefs if isinstance(prefs, dict) else {}
    except Exception:
        pass
    return {}


async def increment_tech_interaction(
    user_id: uuid.UUID,
    tech_name: str,
    db: AsyncSession,
) -> None:
    """
    Tăng bộ đếm tech_interactions khi user hỏi về 1 tech.
    Dùng PostgreSQL jsonb update — không tạo record mới nếu chưa có profile.
    """
    try:
        await db.execute(
            text("""
                UPDATE user_profile
                SET preferences_json = jsonb_set(
                    COALESCE(preferences_json, '{}'),
                    ARRAY['tech_interactions', :tech],
                    (COALESCE(
                        (preferences_json -> 'tech_interactions' ->> :tech)::int, 0
                    ) + 1)::text::jsonb
                )
                WHERE user_id = :uid
            """),
            {"uid": str(user_id), "tech": tech_name},
        )
        await db.commit()
    except Exception:
        await db.rollback()


def format_user_memory_block(prefs: dict) -> str:
    """Format user memory thành text cho prompt."""
    if not prefs:
        return ""
    parts = []
    if role := prefs.get("current_role"):
        parts.append(f"Vai trò: {role}")
    if exp := prefs.get("experience_years"):
        parts.append(f"Kinh nghiệm: {exp} năm")
    if techs := prefs.get("interested_techs"):
        parts.append(f"Công nghệ quan tâm: {', '.join(techs)}")
    if top_interacted := sorted(
        prefs.get("tech_interactions", {}).items(),
        key=lambda x: x[1],
        reverse=True,
    )[:5]:
        names = [t[0] for t in top_interacted]
        parts.append(f"Thường hỏi về: {', '.join(names)}")
    return "Thông tin người dùng:\n" + "\n".join(f"- {p}" for p in parts) if parts else ""
