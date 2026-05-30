import pytest
from unittest.mock import AsyncMock
from app.core.retriever import vector_search # type: ignore # noqa

@pytest.mark.asyncio
async def test_vector_search_success(monkeypatch):
    """Kiểm tra luồng gọi vector search (mock embed và run_query)."""
    # Mock embed_query
    mock_embed = monkeypatch.setattr("app.core.retriever.embed_query", lambda x: [0.1] * 768)
    
    # Mock run_query
    mock_data = [
        {"id": "1", "title": "Art 1", "score": 0.95},
        {"id": "2", "title": "Art 2", "score": 0.88}
    ]
    mock_run_query = AsyncMock(return_value=mock_data)
    monkeypatch.setattr("app.core.retriever.run_query", mock_run_query)
    
    result = await vector_search("Tìm việc Python")
    
    assert len(result) == 2
    assert result[0]["title"] == "Art 1"
    assert mock_run_query.call_count == 1
    
    # Kiểm tra tham số truyền vào run_query
    args, _ = mock_run_query.call_args
    assert args[1]["top_k"] == 10
    assert "db.index.vector.queryNodes" in args[0]
