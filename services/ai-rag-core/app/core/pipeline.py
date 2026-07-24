import asyncio
import logging
import time
from functools import partial

from app.config import get_settings
from app.core.context_ranker import rerank_context
from app.core.entity_extractor import extract_query_entities
from app.core.generator import generate
from app.core.graph_serializer import triples_to_jsonld
from app.core.prompt_builder import _build_job_context_block, build_messages
from app.core.reranker import rerank
from app.core.retriever import vector_search
from app.core.retriever_graph import graph_search
from app.core.retriever_graph_expand import expand_subgraph
from app.core.retriever_sql import sql_analytics_search
from app.core.retriever_user import build_user_block, get_user_context
from app.core.strategy_selector import RetrievalStrategy, select_strategy
from app.memory.conversation import get_history
from app.monitoring.metrics import (
    ai_latency_seconds,
    ai_requests_total,
    retrieval_results,
)

logger = logging.getLogger("ai-rag-core.pipeline")

_EMPTY_GRAPH_DATA = {"entities": [], "job_titles": [], "jobs": [], "companies": [], "related_tech": []}
_NO_DATA_ANSWER = "Tôi không tìm thấy thông tin liên quan trong dữ liệu hiện có."


def _strategy_summary(strategy: RetrievalStrategy) -> dict:
    return {
        "use_graph": strategy.use_graph,
        "graph_expansion_depth": strategy.graph_expansion_depth,
        "use_sql_analytics": strategy.use_sql_analytics,
        "matched_signals": strategy.matched_signals,
    }


async def answer(
    query: str,
    user_id: str | None = None,
    session_id: str | None = None,
    db=None,
) -> dict:
    """
    Pipeline RAG end-to-end.

    settings.strategy_selector_enabled=True (mặc định): Strategy Selector (rule-based) quyết
    định nhánh nào chạy — vector luôn bật, graph/graph-expansion/SQL analytics chỉ bật khi có
    tín hiệu entity/intent tương ứng, tất cả chạy song song qua 1 gather duy nhất theo tên
    nhánh (nhánh không được chọn thì KHÔNG được gọi, không phải gọi rồi bỏ kết quả).

    settings.strategy_selector_enabled=False: giữ nguyên hành vi trước khi có Adaptive Hybrid
    Graph RAG (_answer_legacy) — dùng làm baseline so sánh trong evaluate_rag.py.

    Trả về dict:
      {
        "answer":       str,
        "sources":      list[dict],
        "job_context":  str,
        "entities":     list[str],
        "job_titles":   list[str],
        "analytics":    list[dict],
        "subgraph":     dict | None,  # JSON-LD, None nếu expansion không chạy
        "strategy":     dict | None,  # explainability, None ở legacy path
        "query":        str,
      }
    """
    t0 = time.monotonic()
    settings = get_settings()

    history: list[dict] = []
    if session_id and db is not None:
        try:
            import uuid as _uuid

            history = await get_history(_uuid.UUID(session_id), limit=10, db=db)
        except Exception as e:
            logger.warning("Failed to load conversation history: %s", e)

    if not settings.strategy_selector_enabled:
        return await _answer_legacy(query, user_id, history, settings, t0)

    loop = asyncio.get_event_loop()

    # 1. Trích entity 1 lần duy nhất — dùng chung cho Strategy Selector lẫn graph_search,
    #    tránh chạy lại NER (tốn) lần 2.
    extracted = await loop.run_in_executor(None, extract_query_entities, query)
    strategy = select_strategy(query, extracted)

    # 2. Gather theo tên nhánh — nhánh không được chọn thì không được đưa vào gather.
    t_ret = time.monotonic()
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

    ai_latency_seconds.labels(endpoint="chat", stage="retrieval").observe(time.monotonic() - t_ret)
    retrieval_results.labels(source="vector").observe(len(candidates))
    retrieval_results.labels(source="graph").observe(
        len(graph_data.get("jobs", [])) + len(graph_data.get("companies", []))
    )
    retrieval_results.labels(source="sql").observe(len(sql_data))

    tech_entities = strategy.tech_names or graph_data.get("entities", [])

    # 3. Rerank — unified (article+job+company+analytics, riêng từng loại) hoặc chỉ vector
    #    (ablation toggle độc lập với strategy_selector_enabled).
    t_rerank = time.monotonic()
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
    ai_latency_seconds.labels(endpoint="chat", stage="rerank").observe(time.monotonic() - t_rerank)

    # 3b. Graph trống (query mơ hồ) và threshold lọc hết bài → top-3 điểm cao nhất + cảnh báo
    has_graph_data = bool(graph_data.get("jobs") or graph_data.get("companies"))
    low_confidence = False
    if not top_articles and not has_graph_data and candidates:
        top_articles = sorted(candidates, key=lambda x: x.get("rerank_score", 0), reverse=True)[:3]
        low_confidence = True

    # 4. Fallback: không có dữ liệu nào từ bất kỳ nguồn nào
    if not top_articles and not graph_data.get("jobs") and not graph_data.get("companies") and not sql_data:
        ai_requests_total.labels(endpoint="chat", status="fallback", llm_provider=settings.llm_provider).inc()
        return {
            "answer": _NO_DATA_ANSWER,
            "sources": [],
            "job_context": "",
            "entities": tech_entities,
            "job_titles": graph_data.get("job_titles", []),
            "analytics": [],
            "subgraph": None,
            "strategy": _strategy_summary(strategy),
            "query": query,
        }

    # 5. Build prompt (ghép article + job/company + analytics + subgraph + user + history)
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

    # 6. Gọi LLM
    t_llm = time.monotonic()
    answer_text = await generate(messages)
    ai_latency_seconds.labels(endpoint="chat", stage="llm").observe(time.monotonic() - t_llm)

    total_latency = time.monotonic() - t0
    ai_latency_seconds.labels(endpoint="chat", stage="total").observe(total_latency)
    ai_requests_total.labels(endpoint="chat", status="ok", llm_provider=settings.llm_provider).inc()

    # 7. Evaluation (fire-and-forget, không block response)
    if settings.eval_enabled:
        from app.evaluation.ragas_scorer import evaluate as _eval

        contexts = [a.get("content", "") for a in top_articles if a.get("content")]
        asyncio.create_task(
            _eval(
                question=query,
                answer=answer_text,
                contexts=contexts,
                latency_ms=total_latency * 1000,
                model=settings.llm_model,
            )
        )

    seed_entities = strategy.tech_names + strategy.company_names
    return {
        "answer": answer_text,
        "sources": top_articles,
        "job_context": _build_job_context_block(graph_data),
        "entities": tech_entities,
        "job_titles": graph_data.get("job_titles", []),
        "analytics": sql_data,
        "subgraph": triples_to_jsonld(subgraph_triples, seed_entities) if subgraph_triples else None,
        "strategy": _strategy_summary(strategy),
        "query": query,
    }


async def _answer_legacy(
    query: str,
    user_id: str | None,
    history: list[dict],
    settings,
    t0: float,
) -> dict:
    """
    Hành vi trước khi có Adaptive Hybrid Graph RAG: luôn chạy vector+graph song song, SQL
    analytics chạy TUẦN TỰ sau graph (dùng entity graph_search tự trích), chỉ rerank vector.
    Giữ nguyên để làm baseline ablation trong evaluate_rag.py — không dùng cho traffic thật
    khi strategy_selector_enabled=True (mặc định).
    """
    loop = asyncio.get_event_loop()

    t_ret = time.monotonic()
    gather_tasks = [vector_search(query, top_k=5), graph_search(query)]
    if user_id:
        gather_tasks.append(get_user_context(user_id))
        candidates, graph_data, user_ctx = await asyncio.gather(*gather_tasks)
    else:
        candidates, graph_data = await asyncio.gather(*gather_tasks)
        user_ctx = None

    ai_latency_seconds.labels(endpoint="chat", stage="retrieval").observe(time.monotonic() - t_ret)
    retrieval_results.labels(source="vector").observe(len(candidates))
    retrieval_results.labels(source="graph").observe(
        len(graph_data.get("jobs", [])) + len(graph_data.get("companies", []))
    )

    tech_entities = graph_data.get("entities", [])
    sql_data: list[dict] = []
    if tech_entities:
        try:
            sql_data = await sql_analytics_search(tech_entities, months=settings.sql_analytics_months)
        except Exception as e:
            logger.warning("SQL analytics search failed, skipping: %s", e)
    retrieval_results.labels(source="sql").observe(len(sql_data))

    t_rerank = time.monotonic()
    top_articles = await loop.run_in_executor(None, partial(rerank, query, candidates, 5)) if candidates else []
    ai_latency_seconds.labels(endpoint="chat", stage="rerank").observe(time.monotonic() - t_rerank)

    has_graph_data = bool(graph_data.get("jobs") or graph_data.get("companies"))
    low_confidence = False
    if not top_articles and not has_graph_data and candidates:
        top_articles = sorted(candidates, key=lambda x: x.get("rerank_score", 0), reverse=True)[:3]
        low_confidence = True

    if not top_articles and not graph_data.get("jobs") and not graph_data.get("companies") and not sql_data:
        ai_requests_total.labels(endpoint="chat", status="fallback", llm_provider=settings.llm_provider).inc()
        return {
            "answer": _NO_DATA_ANSWER,
            "sources": [],
            "job_context": "",
            "entities": tech_entities,
            "job_titles": graph_data.get("job_titles", []),
            "analytics": [],
            "subgraph": None,
            "strategy": None,
            "query": query,
        }

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

    t_llm = time.monotonic()
    answer_text = await generate(messages)
    ai_latency_seconds.labels(endpoint="chat", stage="llm").observe(time.monotonic() - t_llm)

    total_latency = time.monotonic() - t0
    ai_latency_seconds.labels(endpoint="chat", stage="total").observe(total_latency)
    ai_requests_total.labels(endpoint="chat", status="ok", llm_provider=settings.llm_provider).inc()

    if settings.eval_enabled:
        from app.evaluation.ragas_scorer import evaluate as _eval

        contexts = [a.get("content", "") for a in top_articles if a.get("content")]
        asyncio.create_task(
            _eval(
                question=query,
                answer=answer_text,
                contexts=contexts,
                latency_ms=total_latency * 1000,
                model=settings.llm_model,
            )
        )

    return {
        "answer": answer_text,
        "sources": top_articles,
        "job_context": _build_job_context_block(graph_data),
        "entities": tech_entities,
        "job_titles": graph_data.get("job_titles", []),
        "analytics": sql_data,
        "subgraph": None,
        "strategy": None,
        "query": query,
    }
