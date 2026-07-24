from unittest.mock import AsyncMock, MagicMock

import pytest

from app.core.retriever_graph import graph_search


@pytest.mark.asyncio
async def test_graph_search_orchestration(monkeypatch):
    """Kiểm tra luồng phối hợp trong graph_search (mock entity extraction & run_query)."""
    # 1. Mock extract_query_entities (Hàm này hiện nằm trong module entity_extractor)
    mock_extract = MagicMock(
        return_value={"technologies": ["Python"], "job_titles": ["Dev"], "companies": ["FPT"], "locations": ["Hà Nội"]}
    )
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
    mock_extract = MagicMock(return_value={"technologies": [], "job_titles": [], "companies": [], "locations": []})
    monkeypatch.setattr("app.core.retriever_graph.extract_query_entities", mock_extract)

    result = await graph_search("Câu hỏi bâng quơ")

    assert result["jobs"] == []
    assert result["companies"] == []
    assert result["entities"] == []


@pytest.mark.asyncio
async def test_graph_search_dedup_jobs(monkeypatch):
    """Kiểm tra logic loại trùng khi gộp jobs từ nhiều nguồn."""
    # 1. Mock entity extraction
    monkeypatch.setattr(
        "app.core.retriever_graph.extract_query_entities",
        MagicMock(return_value={"technologies": ["Python"], "job_titles": ["Dev"], "companies": [], "locations": []}),
    )

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


@pytest.mark.asyncio
async def test_graph_search_uses_provided_extracted_without_calling_extractor(monkeypatch):
    """Regression guard cho Strategy Selector (Phase 1): khi extracted đã được hoist lên gọi
    1 lần ở pipeline.py và truyền vào, graph_search KHÔNG được tự chạy lại NER lần 2."""
    mock_extract = MagicMock(side_effect=AssertionError("extract_query_entities should not be called again"))
    monkeypatch.setattr("app.core.retriever_graph.extract_query_entities", mock_extract)

    mock_run_query = AsyncMock(return_value=[])
    monkeypatch.setattr("app.core.retriever_graph.run_query", mock_run_query)

    extracted = {"technologies": ["Python"], "job_titles": [], "companies": [], "locations": []}
    result = await graph_search("bất kỳ câu gì", extracted=extracted)

    mock_extract.assert_not_called()
    assert result["entities"] == ["Python"]


@pytest.mark.asyncio
async def test_graph_search_extracts_when_extracted_not_provided(monkeypatch):
    """Backward-compat: caller không truyền extracted (VD test cũ) vẫn tự trích như trước."""
    mock_extract = MagicMock(
        return_value={"technologies": ["Java"], "job_titles": [], "companies": [], "locations": []}
    )
    monkeypatch.setattr("app.core.retriever_graph.extract_query_entities", mock_extract)
    monkeypatch.setattr("app.core.retriever_graph.run_query", AsyncMock(return_value=[]))

    result = await graph_search("Java là gì?")

    mock_extract.assert_called_once()
    assert result["entities"] == ["Java"]
