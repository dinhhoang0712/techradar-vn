from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

# Mock các hàm retriever trước khi import answer
import app.core.pipeline as pipeline  # type: ignore # noqa


def _fake_settings(**overrides):
    defaults = dict(
        strategy_selector_enabled=True,
        graph_expansion_enabled=True,
        graph_max_hops=2,
        graph_expansion_limit=100,
        unified_rerank_enabled=True,
        rerank_top_k=5,
        sql_analytics_months=6,
        llm_provider="openai",
        llm_model="gpt-4o-mini",
        eval_enabled=False,
    )
    defaults.update(overrides)
    return SimpleNamespace(**defaults)


def _mock_extract(technologies=None, companies=None, job_titles=None, locations=None):
    return MagicMock(
        return_value={
            "technologies": technologies or [],
            "companies": companies or [],
            "job_titles": job_titles or [],
            "locations": locations or [],
        }
    )


def _passthrough_rerank_context(query, articles, jobs, companies, sql_rows, settings):
    return {"articles": articles, "jobs": jobs, "companies": companies, "analytics": sql_rows}


@pytest.mark.asyncio
async def test_answer_pipeline_success(monkeypatch):
    """Luồng chính (adaptive, có tech entity): graph + expansion đều được gọi, trả đủ field mới."""
    monkeypatch.setattr(pipeline, "get_settings", lambda: _fake_settings())
    monkeypatch.setattr(pipeline, "extract_query_entities", _mock_extract(technologies=["Python"]))

    mock_vector = AsyncMock(return_value=[{"title": "Art 1", "content": "Text", "source": "S1"}])
    monkeypatch.setattr(pipeline, "vector_search", mock_vector)

    mock_graph = AsyncMock(return_value={"jobs": [], "companies": [], "entities": ["Python"], "job_titles": []})
    monkeypatch.setattr(pipeline, "graph_search", mock_graph)

    mock_expand = AsyncMock(return_value=[])
    monkeypatch.setattr(pipeline, "expand_subgraph", mock_expand)

    monkeypatch.setattr(pipeline, "rerank_context", _passthrough_rerank_context)

    mock_gen = AsyncMock(return_value="Đây là câu trả lời từ AI.")
    monkeypatch.setattr(pipeline, "generate", mock_gen)

    result = await pipeline.answer("Python là gì?")

    assert result["answer"] == "Đây là câu trả lời từ AI."
    assert len(result["sources"]) == 1
    assert result["entities"] == ["Python"]
    assert result["strategy"]["use_graph"] is True
    assert result["strategy"]["graph_expansion_depth"] == 1
    mock_graph.assert_awaited_once()
    mock_expand.assert_awaited_once()


@pytest.mark.asyncio
async def test_answer_pipeline_no_data(monkeypatch):
    """Không entity, không vector candidate nào → fallback, graph không được gọi."""
    monkeypatch.setattr(pipeline, "get_settings", lambda: _fake_settings())
    monkeypatch.setattr(pipeline, "extract_query_entities", _mock_extract())

    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[]))
    mock_graph = AsyncMock(return_value={"jobs": [], "companies": [], "entities": [], "job_titles": []})
    monkeypatch.setattr(pipeline, "graph_search", mock_graph)

    result = await pipeline.answer("Câu hỏi không liên quan")

    assert "không tìm thấy thông tin" in result["answer"].lower()
    assert result["sources"] == []
    assert result["subgraph"] is None
    mock_graph.assert_not_awaited()


@pytest.mark.asyncio
async def test_answer_pipeline_skips_graph_when_no_entities_even_with_data(monkeypatch):
    """use_graph=False phải khiến graph_search THỰC SỰ không được gọi, không phải gọi rồi bỏ kết quả."""
    monkeypatch.setattr(pipeline, "get_settings", lambda: _fake_settings())
    monkeypatch.setattr(pipeline, "extract_query_entities", _mock_extract())

    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[{"title": "A", "content": "B"}]))
    mock_graph = AsyncMock(return_value={"jobs": [], "companies": [], "entities": [], "job_titles": []})
    monkeypatch.setattr(pipeline, "graph_search", mock_graph)
    monkeypatch.setattr(pipeline, "rerank_context", _passthrough_rerank_context)
    monkeypatch.setattr(pipeline, "generate", AsyncMock(return_value="Ans"))

    result = await pipeline.answer("Hôm nay trời đẹp không?")

    mock_graph.assert_not_awaited()
    assert result["strategy"]["use_graph"] is False
    assert result["strategy"]["graph_expansion_depth"] == 0


@pytest.mark.asyncio
async def test_answer_pipeline_with_user_id(monkeypatch):
    """Có user_id truyền vào → phải gọi get_user_context."""
    monkeypatch.setattr(pipeline, "get_settings", lambda: _fake_settings())
    monkeypatch.setattr(pipeline, "extract_query_entities", _mock_extract())

    mock_user_ctx = AsyncMock(return_value={"job_role": "Data Scientist", "technologies": ["Spark"]})
    monkeypatch.setattr(pipeline, "get_user_context", mock_user_ctx)

    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[{"title": "A", "content": "B"}]))
    monkeypatch.setattr(pipeline, "rerank_context", _passthrough_rerank_context)
    monkeypatch.setattr(pipeline, "generate", AsyncMock(return_value="Ans"))

    result = await pipeline.answer("Hỏi việc", user_id="user-123")

    assert result["answer"] == "Ans"
    assert mock_user_ctx.call_count == 1


@pytest.mark.asyncio
async def test_answer_pipeline_partial_graph_data(monkeypatch):
    """Vector rỗng nhưng graph_search có jobs → vẫn generate, không rơi vào fallback."""
    monkeypatch.setattr(pipeline, "get_settings", lambda: _fake_settings())
    monkeypatch.setattr(pipeline, "extract_query_entities", _mock_extract(job_titles=["Software Engineer"]))

    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[]))
    monkeypatch.setattr(
        pipeline,
        "graph_search",
        AsyncMock(
            return_value={"jobs": [{"title": "Software Engineer"}], "companies": [], "entities": [], "job_titles": []}
        ),
    )
    monkeypatch.setattr(pipeline, "expand_subgraph", AsyncMock(return_value=[]))
    monkeypatch.setattr(pipeline, "rerank_context", _passthrough_rerank_context)
    mock_gen = AsyncMock(return_value="Có jobs đây")
    monkeypatch.setattr(pipeline, "generate", mock_gen)

    result = await pipeline.answer("Tìm việc")

    assert result["answer"] == "Có jobs đây"
    assert mock_gen.call_count == 1


@pytest.mark.asyncio
async def test_answer_pipeline_legacy_path_when_selector_disabled(monkeypatch):
    """strategy_selector_enabled=False → hành vi cũ: luôn graph, SQL tuần tự, chỉ rerank vector,
    strategy/subgraph trả None (dùng làm baseline ablation)."""
    monkeypatch.setattr(pipeline, "get_settings", lambda: _fake_settings(strategy_selector_enabled=False))

    mock_extract = _mock_extract()
    monkeypatch.setattr(pipeline, "extract_query_entities", mock_extract)

    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[{"title": "A", "content": "B"}]))
    mock_graph = AsyncMock(return_value={"jobs": [], "companies": [], "entities": [], "job_titles": []})
    monkeypatch.setattr(pipeline, "graph_search", mock_graph)
    monkeypatch.setattr(pipeline, "rerank", lambda q, c, top_k: c)
    monkeypatch.setattr(pipeline, "generate", AsyncMock(return_value="Ans"))

    result = await pipeline.answer("Bất kỳ câu hỏi gì")

    # Legacy path luôn gọi graph_search — không dùng Strategy Selector nên không tính entity trước.
    mock_graph.assert_awaited_once()
    mock_extract.assert_not_called()
    assert result["strategy"] is None
    assert result["subgraph"] is None


@pytest.mark.asyncio
async def test_answer_pipeline_unified_rerank_disabled_uses_single_rerank(monkeypatch):
    """unified_rerank_enabled=False (nhưng strategy_selector_enabled=True) → chỉ rerank vector,
    không gọi rerank_context — 1 hàng riêng trong ma trận ablation."""
    monkeypatch.setattr(pipeline, "get_settings", lambda: _fake_settings(unified_rerank_enabled=False))
    monkeypatch.setattr(pipeline, "extract_query_entities", _mock_extract())

    monkeypatch.setattr(pipeline, "vector_search", AsyncMock(return_value=[{"title": "A", "content": "B"}]))

    def _explode(*a, **k):
        raise AssertionError("rerank_context should not be called when unified_rerank_enabled=False")

    monkeypatch.setattr(pipeline, "rerank_context", _explode)
    monkeypatch.setattr(pipeline, "rerank", lambda q, c, top_k: c)
    monkeypatch.setattr(pipeline, "generate", AsyncMock(return_value="Ans"))

    result = await pipeline.answer("Câu hỏi bất kỳ")

    assert result["answer"] == "Ans"
