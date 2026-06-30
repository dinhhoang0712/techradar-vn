import asyncio
import uuid
from typing import AsyncIterator

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.pipeline import answer
from app.core.pipeline_stream import answer_stream
from app.models.chat import ChatSession, ChatMessage
from app.api.schemas import ChatRequest, ChatResponse, SourceItem
from app.memory.user_context import increment_tech_interaction


async def _track_interactions(user_id: uuid.UUID, entities: list[str]) -> None:
    """Fire-and-forget: tăng bộ đếm tech_interactions với fresh session."""
    from app.db.postgres_client import get_session_factory
    factory = get_session_factory()
    async with factory() as session:
        for tech in entities[:5]:
            await increment_tech_interaction(user_id, tech, session)


async def handle_chat(request: ChatRequest, db: AsyncSession) -> ChatResponse:
    """
    Xử lý một lượt chat:
      1. Tạo hoặc lấy ChatSession
      2. Gọi RAG pipeline
      3. Lưu cặp (user message, assistant message) vào Postgres
      4. Trả về ChatResponse
    """
    # 1. Session
    session_id = request.session_id
    if session_id:
        stmt = select(ChatSession).where(ChatSession.id == session_id)
        chat_session = (await db.execute(stmt)).scalar_one_or_none()
        if chat_session is None:
            chat_session = _create_session(request)
            db.add(chat_session)
    else:
        chat_session = _create_session(request)
        db.add(chat_session)
        session_id = chat_session.id

    # 2. Lưu user message
    user_msg = ChatMessage(
        session_id=session_id,
        role="user",
        content=request.query,
    )
    db.add(user_msg)

    # 3. Gọi pipeline (kèm session_id để load conversation history)
    result = await answer(
        query=request.query,
        user_id=str(request.user_id) if request.user_id else None,
        session_id=str(session_id),
        db=db,
    )

    # 4. Lưu assistant message
    assistant_msg = ChatMessage(
        session_id=session_id,
        role="assistant",
        content=result["answer"],
    )
    db.add(assistant_msg)
    await db.commit()

    # 5. Cập nhật tech interaction counter (fire-and-forget)
    if request.user_id and result.get("entities"):
        asyncio.create_task(_track_interactions(request.user_id, result["entities"]))

    # 6. Build response
    sources = [
        SourceItem(
            title=s.get("title"),
            published_date=str(s.get("published_date") or "")[:10] or None,
            source=s.get("source"),
            rerank_score=s.get("rerank_score"),
        )
        for s in result.get("sources", [])
    ]

    return ChatResponse(
        answer=result["answer"],
        session_id=session_id,
        sources=sources,
        entities=result.get("entities", []),
        job_titles=result.get("job_titles", []),
        query=request.query,
    )


def _create_session(request: ChatRequest) -> ChatSession:
    # Normally the backend already created this session (it owns chat_session). This is only a
    # fallback when the session row is missing. user_id stays NULL for anonymous — a phantom
    # UUID(int=0) would violate the chat_session.user_id -> users(id) FK and 500 the request.
    title = request.query[:80] if request.query else "Cuộc hội thoại mới"
    return ChatSession(
        id=request.session_id or uuid.uuid4(),
        user_id=request.user_id or None,
        title=title,
    )


async def handle_chat_stream(
    request: ChatRequest, db: AsyncSession
) -> AsyncIterator[dict]:
    """
    Streaming version của handle_chat. Yield events:
      - {"event": "token", "data": <chunk>}
      - {"event": "done",  "data": {"answer", "session_id", "sources", "entities", "job_titles", "query"}}
    """
    # 1. Session
    session_id = request.session_id
    if session_id:
        stmt = select(ChatSession).where(ChatSession.id == session_id)
        chat_session = (await db.execute(stmt)).scalar_one_or_none()
        if chat_session is None:
            chat_session = _create_session(request)
            db.add(chat_session)
    else:
        chat_session = _create_session(request)
        db.add(chat_session)
        session_id = chat_session.id

    # 2. Lưu user message
    user_msg = ChatMessage(
        session_id=session_id,
        role="user",
        content=request.query,
    )
    db.add(user_msg)
    await db.flush()  # đảm bảo session/user message có trước khi stream

    # 3. Stream pipeline + relay events
    full_answer = ""
    final_payload: dict | None = None
    async for ev in answer_stream(
        query=request.query,
        user_id=str(request.user_id) if request.user_id else None,
        session_id=str(session_id),
        db=db,
    ):
        if ev["event"] == "token":
            yield ev
        elif ev["event"] == "done":
            final_payload = ev["data"]
            full_answer = final_payload.get("answer", "")

    # 4. Lưu assistant message
    assistant_msg = ChatMessage(
        session_id=session_id,
        role="assistant",
        content=full_answer,
    )
    db.add(assistant_msg)
    await db.commit()

    # 5. Build done event với session_id + query
    sources = [
        {
            "title":          s.get("title"),
            "published_date": str(s.get("published_date") or "")[:10] or None,
            "source":         s.get("source"),
            "rerank_score":   s.get("rerank_score"),
        }
        for s in (final_payload or {}).get("sources", [])
    ]

    yield {
        "event": "done",
        "data": {
            "answer":     full_answer,
            "session_id": str(session_id),
            "sources":    sources,
            "entities":   (final_payload or {}).get("entities", []),
            "job_titles": (final_payload or {}).get("job_titles", []),
            "query":      request.query,
        },
    }
