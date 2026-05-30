from sqlalchemy import text

from app.db.postgres_client import get_session_factory


async def get_user_context(user_id: str) -> dict | None:
    """
    Lấy profile của user từ Postgres để cá nhân hóa câu trả lời.

    Trả về dict nếu tìm thấy:
    {
        "user_id":      str,
        "full_name":    str | None,
        "job_role":     str | None,
        "technologies": list[str],   # ["Python", "React", ...]
        "location":     str | None,
        "bio":          str | None,
    }
    Trả về None nếu user không tồn tại hoặc chưa có profile.
    """
    factory = get_session_factory()
    try:
        async with factory() as session:
            result = await session.execute(
                text("""
                    SELECT
                        u.id            AS user_id,
                        u.full_name     AS full_name,
                        p.job_role      AS job_role,
                        p.technologies  AS technologies,
                        p.location      AS location,
                        p.bio           AS bio
                    FROM users u
                    LEFT JOIN user_profile p ON p.user_id = u.id
                    WHERE u.id = :user_id
                """),
                {"user_id": user_id},
            )
            row = result.mappings().first()
    except Exception as e:
        # user_profile có thể chưa được tạo, hoặc DB lỗi — không fail RAG
        print(f"[WARN] get_user_context fallback: {e}")
        return None

    if row is None:
        return None

    return {
        "user_id":      str(row["user_id"]),
        "full_name":    row["full_name"],
        "job_role":     row["job_role"],
        "technologies": list(row["technologies"] or []),
        "location":     row["location"],
        "bio":          row["bio"],
    }


def build_user_block(user_context: dict) -> str:
    """
    Format user profile thành text block để nhét vào prompt.

    Ví dụ output:
        Thông tin người dùng:
        - Vai trò: Backend Developer
        - Kỹ năng hiện có: Python, Django, PostgreSQL
        - Địa điểm: Hà Nội
        - Giới thiệu: 3 năm kinh nghiệm, muốn chuyển sang AI/ML
    """
    lines = ["Thông tin người dùng:"]

    if user_context.get("job_role"):
        lines.append(f"- Vai trò: {user_context['job_role']}")

    techs = user_context.get("technologies") or []
    if techs:
        lines.append(f"- Kỹ năng hiện có: {', '.join(techs)}")

    if user_context.get("location"):
        lines.append(f"- Địa điểm: {user_context['location']}")

    if user_context.get("bio"):
        lines.append(f"- Giới thiệu: {user_context['bio']}")

    # Nếu không có thông tin gì ngoài user_id thì trả về rỗng
    if len(lines) == 1:
        return ""

    return "\n".join(lines)
