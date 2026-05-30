import pytest
from unittest.mock import AsyncMock, MagicMock
from app.core.pipeline_stream import answer_stream # type: ignore # noqa

@pytest.mark.asyncio
async def test_answer_stream_success(monkeypatch):
    """Kiểm tra luồng stream thành công với đầy đủ tokens và done event."""
    monkeypatch.setattr("app.core.pipeline_stream.vector_search", AsyncMock(return_value=[{"title": "A1"}]))
    monkeypatch.setattr("app.core.pipeline_stream.graph_search", AsyncMock(return_value={"jobs": [], "entities": ["Python"]}))
    monkeypatch.setattr("app.core.pipeline_stream.rerank", lambda query, candidates, top_k: candidates)
    monkeypatch.setattr("app.core.pipeline_stream.build_messages", lambda *args, **kwargs: [{"role": "user", "content": "..."}])
    
    # Sử dụng async generator thật để mock
    async def mock_generate_stream(messages):
        yield "Chào"
        yield " bạn"
    monkeypatch.setattr("app.core.pipeline_stream.generate_stream", mock_generate_stream)
    
    events = []
    async for ev in answer_stream("Hỏi Python"):
        events.append(ev)
        
    assert events[0] == {"event": "token", "data": "Chào"}
    assert events[1] == {"event": "token", "data": " bạn"}
    assert events[2]["event"] == "done"
    assert events[2]["data"]["answer"] == "Chào bạn"
    assert events[2]["data"]["entities"] == ["Python"]

@pytest.mark.asyncio
async def test_answer_stream_with_user_id(monkeypatch):
    """Kiểm tra stream khi có user_id để cá nhân hóa."""
    mock_user_ctx = AsyncMock(return_value={"job_role": "Dev"})
    monkeypatch.setattr("app.core.pipeline_stream.get_user_context", mock_user_ctx)
    monkeypatch.setattr("app.core.pipeline_stream.vector_search", AsyncMock(return_value=[]))
    monkeypatch.setattr("app.core.pipeline_stream.graph_search", AsyncMock(return_value={"jobs": []}))
    
    async def mock_generate_stream(messages):
        yield "Hi"
    monkeypatch.setattr("app.core.pipeline_stream.generate_stream", mock_generate_stream)
    
    async for _ in answer_stream("Hi", user_id="u1"):
        pass
        
    assert mock_user_ctx.call_count == 1

@pytest.mark.asyncio
async def test_answer_stream_fallback(monkeypatch):
    """Kiểm tra fallback khi không tìm thấy thông tin nào."""
    monkeypatch.setattr("app.core.pipeline_stream.vector_search", AsyncMock(return_value=[]))
    monkeypatch.setattr("app.core.pipeline_stream.graph_search", AsyncMock(return_value={"jobs": [], "companies": []}))
    
    async def mock_generate_stream(messages):
        yield "..."
    monkeypatch.setattr("app.core.pipeline_stream.generate_stream", mock_generate_stream)
    
    events = []
    async for ev in answer_stream("Query không có data"):
        events.append(ev)
        
    assert "không tìm thấy thông tin" in events[0]["data"]
    assert events[1]["event"] == "done"
