import asyncio
import logging
from collections.abc import AsyncIterator
from functools import partial

from app.config import get_settings
from app.core.context_ranker import rerank_context
from app.core.entity_extractor import extract_query_entities
from app.core.generator_stream import generate_stream
from app.core.graph_serializer import triples_to_jsonld
from app.core.prompt_builder import build_messages
from app.core.reranker import rerank
from app.core.retriever import vector_search
from app.core.retriever_graph import graph_search
from app.core.retriever_graph_expand import expand_subgraph
from app.core.retriever_sql import sql_analytics_search
from app.core.retriever_user import build_user_block, get_user_context
from app.core.strategy_selector import RetrievalStrategy, select_strategy
from app.memory.conversation import get_history

logger = logging.getLogger("ai-rag-core.pipeline_stream")

_FALLBACK_ANSWER = "Tôi không tìm thấy thông tin liên quan trong dữ liệu hiện có."
_EMPTY_GRAPH_DATA = {"entities": [], "job_titles": [], "jobs": [], "companies": [], "related_tech": []}


def _strategy_summary(strategy: RetrievalStrategy) -> dict:
    return {
        "use_graph": strategy.use_graph,
        "graph_expansion_depth": strategy.graph_expansion_depth,
        "use_sql_analytics": strategy.use_sql_analytics,
        "matched_signals": strategy.matched_signals,
    }


async def answer_stream(
    query: str,
    user_id: str | None = None,
    session_id: str | None = None,
    db=None,
) -> AsyncIterator[dict]:
    """
    Streaming version của pipeline.answer() — cùng logic Strategy Selector/expansion/unified
    rerank (xem pipeline.py để biết chi tiết), chỉ khác ở bước cuối stream token thay vì
    return 1 lần. Yield dict với 2 loại event:
      - {"event": "token", "data": <str chunk>}
      - {"event": "done",  "data": {"answer", "sources", "entities", "job_titles",
                                     "analytics", "subgraph", "strategy"}}
    """
    settings = get_settings()

    history: list[dict] = []
    if session_id and db is not None:
        try:
            import uuid as _uuid

            history = await get_history(_uuid.UUID(session_id), limit=10, db=db)
        except Exception as e:
            logger.warning("Failed to load conversation history: %s", e)

    if not settings.strategy_selector_enabled:
        async for ev in _answer_stream_legacy(query, user_id, history, settings):
            yield ev
        return

    loop = asyncio.get_event_loop()

    extracted = await loop.run_in_executor(None, extract_query_entities, query)
    strategy = select_strategy(query, extracted)

    tasks: dict[str, object] = {"vector": vector_search(query, top_k=5)}
    if strategy.use_graph:
        tasks["graph"] = graph_search(query, extracted=extracted)
    if strategy.graph_expansion_depth > 0 and settings.graph_expansion_enabled:
        depth = min(strategy.graph_expansion_depth, settings.graph_max_hops)
        tasks["expansion"] = expand_subgraph(strategy.tech_names, strategy.company_names, depth=depth)
    if strategy.use_sql_analytics:
        tasks["sql"] = sql_analytics_search(strategy.tech_names, months=settings.sql_analytics_months)
    if user_id:
        tasks["user"] = get_user_context(user_id)

    values = await asyncio.gather(*tasks.values())
    results = dict(zip(tasks.keys(), values))

    candidates = results.get("vector", [])
    graph_data = results.get("graph", _EMPTY_GRAPH_DATA)
    subgraph_triples = results.get("expansion", [])
    sql_data = results.get("sql", [])
    user_ctx = results.get("user")

    tech_entities = strategy.tech_names or graph_data.get("entities", [])

    if settings.unified_rerank_enabled:
        reranked = await loop.run_in_executor(
            None,
            partial(
                rerank_context,
                query,
                candidates,
                graph_data.get("jobs", []),
                graph_data.get("companies", []),
                sql_data,
                settings,
            ),
        )
        top_articles = reranked["articles"]
        graph_data = {**graph_data, "jobs": reranked["jobs"], "companies": reranked["companies"]}
        sql_data = reranked["analytics"]
    else:
        top_articles = (
            await loop.run_in_executor(None, partial(rerank, query, candidates, settings.rerank_top_k))
            if candidates
            else []
        )

    has_graph_data = bool(graph_data.get("jobs") or graph_data.get("companies"))
    low_confidence = False
    if not top_articles and not has_graph_data and candidates:
        top_articles = sorted(candidates, key=lambda x: x.get("rerank_score", 0), reverse=True)[:3]
        low_confidence = True

    if not top_articles and not graph_data.get("jobs") and not graph_data.get("companies") and not sql_data:
        yield {"event": "token", "data": _FALLBACK_ANSWER}
        yield {
            "event": "done",
            "data": {
                "answer": _FALLBACK_ANSWER,
                "sources": [],
                "entities": tech_entities,
                "job_titles": graph_data.get("job_titles", []),
                "analytics": [],
                "subgraph": None,
                "strategy": _strategy_summary(strategy),
            },
        }
        return

    user_blk = build_user_block(user_ctx) if user_ctx else ""
    messages = build_messages(
        query,
        top_articles,
        graph_data,
        user_block=user_blk,
        low_confidence=low_confidence,
        sql_data=sql_data,
        history=history,
        subgraph_triples=subgraph_triples,
    )

    chunks: list[str] = []
    async for chunk in generate_stream(messages):
        chunks.append(chunk)
        yield {"event": "token", "data": chunk}

    full_answer = "".join(chunks)
    seed_entities = strategy.tech_names + strategy.company_names
    yield {
        "event": "done",
        "data": {
            "answer": full_answer,
            "sources": top_articles,
            "entities": tech_entities,
            "job_titles": graph_data.get("job_titles", []),
            "analytics": sql_data,
            "subgraph": triples_to_jsonld(subgraph_triples, seed_entities) if subgraph_triples else None,
            "strategy": _strategy_summary(strategy),
        },
    }


async def _answer_stream_legacy(
    query: str,
    user_id: str | None,
    history: list[dict],
    settings,
) -> AsyncIterator[dict]:
    """Hành vi trước khi có Adaptive Hybrid Graph RAG — xem pipeline._answer_legacy()."""
    gather_tasks = [vector_search(query, top_k=5), graph_search(query)]
    if user_id:
        gather_tasks.append(get_user_context(user_id))
        candidates, graph_data, user_ctx = await asyncio.gather(*gather_tasks)
    else:
        candidates, graph_data = await asyncio.gather(*gather_tasks)
        user_ctx = None

    tech_entities = graph_data.get("entities", [])
    sql_data: list[dict] = []
    if tech_entities:
        try:
            sql_data = await sql_analytics_search(tech_entities, months=settings.sql_analytics_months)
        except Exception as e:
            logger.warning("SQL analytics search failed, skipping: %s", e)

    loop = asyncio.get_event_loop()
    top_articles = await loop.run_in_executor(None, partial(rerank, query, candidates, 5)) if candidates else []

    has_graph_data = bool(graph_data.get("jobs") or graph_data.get("companies"))
    low_confidence = False
    if not top_articles and not has_graph_data and candidates:
        top_articles = sorted(candidates, key=lambda x: x.get("rerank_score", 0), reverse=True)[:3]
        low_confidence = True

    if not top_articles and not graph_data.get("jobs") and not graph_data.get("companies") and not sql_data:
        yield {"event": "token", "data": _FALLBACK_ANSWER}
        yield {
            "event": "done",
            "data": {
                "answer": _FALLBACK_ANSWER,
                "sources": [],
                "entities": tech_entities,
                "job_titles": graph_data.get("job_titles", []),
                "analytics": [],
                "subgraph": None,
                "strategy": None,
            },
        }
        return

    user_blk = build_user_block(user_ctx) if user_ctx else ""
    messages = build_messages(
        query,
        top_articles,
        graph_data,
        user_block=user_blk,
        low_confidence=low_confidence,
        sql_data=sql_data,
        history=history,
    )

    chunks: list[str] = []
    async for chunk in generate_stream(messages):
        chunks.append(chunk)
        yield {"event": "token", "data": chunk}

    full_answer = "".join(chunks)
    yield {
        "event": "done",
        "data": {
            "answer": full_answer,
            "sources": top_articles,
            "entities": tech_entities,
            "job_titles": graph_data.get("job_titles", []),
            "analytics": sql_data,
            "subgraph": None,
            "strategy": None,
        },
    }
