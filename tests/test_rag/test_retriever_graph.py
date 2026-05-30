import pytest
from unittest.mock import AsyncMock, MagicMock
from app.core.retriever_graph import graph_search

@pytest.mark.asyncio
async def test_graph_search_orchestration(monkeypatch):
    """Kiểm tra luồng phối hợp trong graph_search (mock entity extraction & run_query)."""
    # 1. Mock extract_query_entities (Hàm này hiện nằm trong module entity_extractor)
    mock_extract = MagicMock(return_value={
        "technologies": ["Python"],
        "job_titles": ["Dev"],
        "companies": ["FPT"],
        "locations": ["Hà Nội"]
    })
    # Mock hàm được gọi qua run_in_executor
    monkeypatch.setattr("app.core.retriever_graph.extract_query_entities", mock_extract)
    
    # 2. Mock run_query
    mock_run_query = AsyncMock(return_value=[{"title": "Python Dev", "company": "ABC"}])
    monkeypatch.setattr("app.core.retriever_graph.run_query", mock_run_query)
    
    result = await graph_search("Python Dev")
    
    # Kiểm tra các trường dữ liệu mới
    assert result["entities"] == ["Python"]
    assert len(result["jobs"]) > 0
    assert result["jobs"][0]["title"] == "Python Dev"
    assert result["ner_companies"] == ["FPT"]
    assert result["ner_locations"] == ["Hà Nội"]
    assert mock_run_query.call_count >= 1

@pytest.mark.asyncio
async def test_graph_search_no_entities(monkeypatch):
    """Kiểm tra khi không trích xuất được thực thể nào."""
    mock_extract = MagicMock(return_value={
        "technologies": [], "job_titles": [], "companies": [], "locations": []
    })
    monkeypatch.setattr("app.core.retriever_graph.extract_query_entities", mock_extract)
    
    result = await graph_search("Câu hỏi bâng quơ")
    
    assert result["jobs"] == []
    assert result["companies"] == []
    assert result["entities"] == []

@pytest.mark.asyncio
async def test_graph_search_dedup_jobs(monkeypatch):
    """Kiểm tra logic loại trùng khi gộp jobs từ nhiều nguồn."""
    # 1. Mock entity extraction
    monkeypatch.setattr("app.core.retriever_graph.extract_query_entities", MagicMock(return_value={
        "technologies": ["Python"], "job_titles": ["Dev"], "companies": [], "locations": []
    }))
    
    # 2. Mock run_query trả về jobs trùng title từ các nguồn khác nhau
    async def mock_run_query(cypher, params):
        if "MATCH (j:Job)-[:REQUIRES]->(t)" in cypher:
            return [{"title": "Python Dev", "company": "A"}]
        if "UNWIND $keywords AS kw" in cypher:
            return [{"title": "Python Dev", "company": "B"}] 
        return []
        
    monkeypatch.setattr("app.core.retriever_graph.run_query", mock_run_query)
    
    result = await graph_search("Python Dev")
    
    # Phải bị loại trùng dựa trên title (giữ lại job đầu tiên tìm thấy)
    assert len(result["jobs"]) == 1
    assert result["jobs"][0]["title"] == "Python Dev"
    assert result["jobs"][0]["company"] == "A"
