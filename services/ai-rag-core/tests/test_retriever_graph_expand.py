from unittest.mock import AsyncMock

import pytest

from app.core.retriever_graph_expand import expand_subgraph


@pytest.mark.asyncio
async def test_expand_subgraph_returns_empty_when_no_names(monkeypatch):
    mock_run_query = AsyncMock(return_value=[{"subject": "should not be called"}])
    monkeypatch.setattr("app.core.retriever_graph_expand.run_query", mock_run_query)

    result = await expand_subgraph([], [], depth=2)

    assert result == []
    mock_run_query.assert_not_called()


@pytest.mark.asyncio
async def test_expand_subgraph_passes_clamped_depth_and_lowercased_names(monkeypatch):
    mock_run_query = AsyncMock(return_value=[{"subject": "Java", "predicate": "RELATED_TO", "object": "Kotlin"}])
    monkeypatch.setattr("app.core.retriever_graph_expand.run_query", mock_run_query)

    result = await expand_subgraph(["Java"], ["FPT"], depth=2)

    assert result == [{"subject": "Java", "predicate": "RELATED_TO", "object": "Kotlin"}]
    cypher, params = mock_run_query.call_args[0]
    assert "*1..2" in cypher
    assert params == {"names": ["java", "fpt"]}


@pytest.mark.asyncio
async def test_expand_subgraph_clamps_depth_to_configured_max_hops(monkeypatch):
    mock_run_query = AsyncMock(return_value=[])
    monkeypatch.setattr("app.core.retriever_graph_expand.run_query", mock_run_query)

    # depth=10 vượt xa settings.graph_max_hops (mặc định 2) và trần cứng 3 — phải bị clamp
    # xuống giá trị an toàn, không bao giờ đưa nguyên depth=10 vào Cypher.
    await expand_subgraph(["Java"], [], depth=10)

    cypher, _ = mock_run_query.call_args[0]
    assert "*1..10" not in cypher
    assert "*1..2" in cypher


@pytest.mark.asyncio
async def test_expand_subgraph_never_goes_below_depth_1(monkeypatch):
    mock_run_query = AsyncMock(return_value=[])
    monkeypatch.setattr("app.core.retriever_graph_expand.run_query", mock_run_query)

    await expand_subgraph(["Java"], [], depth=0)

    cypher, _ = mock_run_query.call_args[0]
    assert "*1..1" in cypher
