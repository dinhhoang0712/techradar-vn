import pytest
from unittest.mock import AsyncMock

# Mock các hàm retriever trước khi import answer
import app.core.pipeline as pipeline  # type: ignore # noqa

@pytest.mark.asyncio
async def test_answer_pipeline_success(monkeypatch):
    """Kiểm tra luồng chạy chính của RAG answer."""
    
    # Mock Vector Search
    mock_vector = AsyncMock(return_value=[{"title": "Art 1", "content": "Text", "source": "S1"}])
    monkeypatch.setattr(pipeline, "vector_search", mock_vector)
    
    # Mock Graph Search
    mock_graph = AsyncMock(return_value={
        "jobs": [], "companies": [], "entities": ["Python"], "job_titles": []
    })
    monkeypatch.setattr(pipeline, "graph_search", mock_graph)
    
    # Mock Reranker (không async)
    monkeypatch.setattr(pipeline, "rerank", lambda q, c, top_k: c)
    
    # Mock Generator
    mock_gen = AsyncMock(return_value="Đây là câu trả lời từ AI.")
    monkeypatch.setattr(pipeline, "generate", mock_gen)
    
    query = "Hỏi gì đó"
    result = await pipeline.answer(query)
    
    assert result["answer"] == "Đây là câu trả lời từ AI."
    assert len(result["sources"]) == 1
    assert result["entities"] == ["Python"]

@pytest.mark.asyncio
async def test_answer_pipeline_no_data(monkeypatch):
    """Kiểm tra phản hồi khi không tìm thấy dữ liệu bổ trợ nào."""
    
    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[]))
    monkeypatch.setattr(pipeline, "graph_search", AsyncMock(return_value={
        "jobs": [], "companies": [], "entities": [], "job_titles": []
    }))
    
    result = await pipeline.answer("Câu hỏi không liên quan")
    
    assert "không tìm thấy thông tin" in result["answer"].lower()
    assert result["sources"] == []

@pytest.mark.asyncio
async def test_answer_pipeline_with_user_id(monkeypatch):
    """Kiểm tra khi có user_id truyền vào, phải gọi get_user_context."""
    mock_user_ctx = AsyncMock(return_value={"job_role": "Data Scientist", "technologies": ["Spark"]})
    monkeypatch.setattr(pipeline, "get_user_context", mock_user_ctx)
    
    # Mock các thành phần khác
    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[]))
    monkeypatch.setattr(pipeline, "graph_search", AsyncMock(return_value={"jobs": [{"title": "Job 1"}], "entities": []}))
    monkeypatch.setattr(pipeline, "generate", AsyncMock(return_value="Ans"))
    
    result = await pipeline.answer("Hỏi việc", user_id="user-123")
    
    assert result["answer"] == "Ans"
    assert mock_user_ctx.call_count == 1

@pytest.mark.asyncio
async def test_answer_pipeline_partial_graph_data(monkeypatch):
    """Kiểm tra khi vector search rỗng nhưng graph_search có jobs thì vẫn generate (không fallback)."""
    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[]))
    # Có jobs thì không được rơi vào fallback rỗng
    monkeypatch.setattr(pipeline, "graph_search", AsyncMock(return_value={
        "jobs": [{"title": "Software Engineer"}],
        "companies": [],
        "entities": [],
        "job_titles": []
    }))
    mock_gen = AsyncMock(return_value="Có jobs đây")
    monkeypatch.setattr(pipeline, "generate", mock_gen)
    
    result = await pipeline.answer("Tìm việc")
    
    assert result["answer"] == "Có jobs đây"
    assert mock_gen.call_count == 1
