from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

import app.core.pipeline_stream as pipeline_stream
from app.core.pipeline_stream import answer_stream  # type: ignore # noqa


def _fake_settings(**overrides):
    defaults = dict(
        strategy_selector_enabled=True,
        graph_expansion_enabled=True,
        graph_max_hops=2,
        graph_expansion_limit=100,
        unified_rerank_enabled=True,
        rerank_top_k=5,
        sql_analytics_months=6,
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
async def test_answer_stream_success(monkeypatch):
    """Luồng stream thành công (adaptive, có tech entity) với đầy đủ tokens và done event."""
    monkeypatch.setattr(pipeline_stream, "get_settings", lambda: _fake_settings())
    monkeypatch.setattr(pipeline_stream, "extract_query_entities", _mock_extract(technologies=["Python"]))
    monkeypatch.setattr(pipeline_stream, "vector_search", AsyncMock(return_value=[{"title": "A1"}]))
    monkeypatch.setattr(
        pipeline_stream, "graph_search", AsyncMock(return_value={"jobs": [], "companies": [], "entities": ["Python"]})
    )
    monkeypatch.setattr(pipeline_stream, "expand_subgraph", AsyncMock(return_value=[]))
    monkeypatch.setattr(pipeline_stream, "rerank_context", _passthrough_rerank_context)
    monkeypatch.setattr(pipeline_stream, "build_messages", lambda *args, **kwargs: [{"role": "user", "content": "..."}])

    async def mock_generate_stream(messages):
        yield "Chào"
        yield " bạn"

    monkeypatch.setattr(pipeline_stream, "generate_stream", mock_generate_stream)

    events = []
    async for ev in answer_stream("Python là gì?"):
        events.append(ev)

    assert events[0] == {"event": "token", "data": "Chào"}
    assert events[1] == {"event": "token", "data": " bạn"}
    assert events[2]["event"] == "done"
    assert events[2]["data"]["answer"] == "Chào bạn"
    assert events[2]["data"]["entities"] == ["Python"]
    assert events[2]["data"]["strategy"]["use_graph"] is True


@pytest.mark.asyncio
async def test_answer_stream_with_user_id(monkeypatch):
    """Có user_id → phải gọi get_user_context."""
    monkeypatch.setattr(pipeline_stream, "get_settings", lambda: _fake_settings())
    monkeypatch.setattr(pipeline_stream, "extract_query_entities", _mock_extract())

    mock_user_ctx = AsyncMock(return_value={"job_role": "Dev"})
    monkeypatch.setattr(pipeline_stream, "get_user_context", mock_user_ctx)
    monkeypatch.setattr(pipeline_stream, "vector_search", AsyncMock(return_value=[{"title": "A", "content": "B"}]))
    monkeypatch.setattr(pipeline_stream, "rerank_context", _passthrough_rerank_context)
    monkeypatch.setattr(pipeline_stream, "build_messages", lambda *args, **kwargs: [{"role": "user", "content": "..."}])

    async def mock_generate_stream(messages):
        yield "Hi"

    monkeypatch.setattr(pipeline_stream, "generate_stream", mock_generate_stream)

    async for _ in answer_stream("Hi", user_id="u1"):
        pass

    assert mock_user_ctx.call_count == 1


@pytest.mark.asyncio
async def test_answer_stream_fallback_skips_graph_when_no_entities(monkeypatch):
    """Không entity, không data nào → fallback ngay, graph_search không được gọi."""
    monkeypatch.setattr(pipeline_stream, "get_settings", lambda: _fake_settings())
    monkeypatch.setattr(pipeline_stream, "extract_query_entities", _mock_extract())
    monkeypatch.setattr(pipeline_stream, "vector_search", AsyncMock(return_value=[]))
    mock_graph = AsyncMock(return_value={"jobs": [], "companies": []})
    monkeypatch.setattr(pipeline_stream, "graph_search", mock_graph)

    events = []
    async for ev in answer_stream("Query không có data"):
        events.append(ev)

    assert "không tìm thấy thông tin" in events[0]["data"]
    assert events[1]["event"] == "done"
    assert events[1]["data"]["subgraph"] is None
    mock_graph.assert_not_awaited()


@pytest.mark.asyncio
async def test_answer_stream_legacy_path_when_selector_disabled(monkeypatch):
    """strategy_selector_enabled=False → hành vi cũ, strategy/subgraph trả None."""
    monkeypatch.setattr(pipeline_stream, "get_settings", lambda: _fake_settings(strategy_selector_enabled=False))
    mock_extract = _mock_extract()
    monkeypatch.setattr(pipeline_stream, "extract_query_entities", mock_extract)

    monkeypatch.setattr(pipeline_stream, "vector_search", AsyncMock(return_value=[{"title": "A", "content": "B"}]))
    monkeypatch.setattr(pipeline_stream, "graph_search", AsyncMock(return_value={"jobs": [], "companies": []}))
    monkeypatch.setattr(pipeline_stream, "rerank", lambda q, c, top_k: c)
    monkeypatch.setattr(pipeline_stream, "build_messages", lambda *args, **kwargs: [{"role": "user", "content": "..."}])

    async def mock_generate_stream(messages):
        yield "..."

    monkeypatch.setattr(pipeline_stream, "generate_stream", mock_generate_stream)

    events = []
    async for ev in answer_stream("Bất kỳ câu gì"):
        events.append(ev)

    mock_extract.assert_not_called()
    assert events[-1]["data"]["strategy"] is None
    assert events[-1]["data"]["subgraph"] is None
