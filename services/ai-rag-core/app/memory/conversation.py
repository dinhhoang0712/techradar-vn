"""
Conversation Memory — sliding window từ bảng chat_message (PostgreSQL).
Bảng này do Spring Boot Flyway own, ai-rag-core chỉ đọc.
Schema: chat_message(id UUID, session_id UUID, role TEXT, content TEXT, created_at TIMESTAMPTZ)
"""
import uuid

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


async def get_history(
    session_id: uuid.UUID,
    limit: int = 10,
    db: AsyncSession = None,
) -> list[dict]:
    """
    Lấy lịch sử hội thoại của 1 session theo thứ tự chronological (oldest → newest).
    Dùng sliding window: lấy `limit` messages gần nhất, rồi reverse.
    """
    if db is None:
        return []
    result = await db.execute(
        text("""
            SELECT role, content, created_at
            FROM chat_message
            WHERE session_id = :sid
            ORDER BY created_at DESC
            LIMIT :lim
        """),
        {"sid": str(session_id), "lim": limit},
    )
    rows = result.mappings().all()
    return [{"role": r["role"], "content": r["content"]} for r in reversed(rows)]


def format_history_block(history: list[dict]) -> str:
    """Format lịch sử thành text block để inject vào prompt."""
    if not history:
        return ""
    lines = []
    for msg in history:
        role = "User" if msg["role"] == "user" else "Assistant"
        lines.append(f"{role}: {msg['content'][:300]}")
    return "Lịch sử hội thoại:\n" + "\n".join(lines)
