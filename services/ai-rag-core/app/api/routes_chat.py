import json
import uuid

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sse_starlette.sse import EventSourceResponse

from app.api.schemas import ChatMessageItem, ChatRequest, ChatResponse
from app.db.postgres_client import get_session
from app.models.chat import ChatMessage
from app.services.chat_service import handle_chat, handle_chat_stream

router = APIRouter(prefix="/chat", tags=["chat"])


@router.post("", response_model=ChatResponse)
async def chat(
    request: ChatRequest,
    db: AsyncSession = Depends(get_session),
) -> ChatResponse:
    """
    Gửi câu hỏi, nhận câu trả lời từ RAG pipeline (non-stream).

    - `query`: câu hỏi của user
    - `session_id`: UUID phiên hội thoại (None để tạo mới)
    - `user_id`: UUID user đã đăng nhập (None nếu ẩn danh)
    """
    try:
        return await handle_chat(request, db)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/stream")
async def chat_stream(
    request: ChatRequest,
    db: AsyncSession = Depends(get_session),
):
    """
    Streaming version (SSE). Trả 2 loại event:
      - event: token   data: <text chunk>
      - event: done    data: <JSON metadata: answer, session_id, sources, entities, job_titles, query>
    """
    async def event_gen():
        try:
            async for ev in handle_chat_stream(request, db):
                if ev["event"] == "token":
                    yield {"event": "token", "data": ev["data"]}
                elif ev["event"] == "done":
                    yield {"event": "done", "data": json.dumps(ev["data"], default=str, ensure_ascii=False)}
        except Exception as e:
            yield {"event": "error", "data": json.dumps({"detail": str(e)}, ensure_ascii=False)}

    return EventSourceResponse(event_gen())


@router.get("/session/{session_id}/messages", response_model=list[ChatMessageItem])
async def list_session_messages(
    session_id: uuid.UUID,
    db: AsyncSession = Depends(get_session),
) -> list[ChatMessageItem]:
    """Trả lịch sử message của 1 session theo thứ tự thời gian (id tăng dần)."""
    stmt = select(ChatMessage).where(ChatMessage.session_id == session_id).order_by(ChatMessage.created_at.asc())
    result = await db.execute(stmt)
    rows = result.scalars().all()
    return [ChatMessageItem(id=m.id, role=m.role, content=m.content) for m in rows]
