import asyncio
import logging
import time
from functools import partial

from app.config import get_settings
from app.core.retriever import vector_search
from app.core.retriever_graph import graph_search
from app.core.retriever_sql import sql_analytics_search
from app.core.retriever_user import get_user_context, build_user_block
from app.core.reranker import rerank
from app.core.prompt_builder import build_messages, _build_job_context_block
from app.core.generator import generate
from app.memory.conversation import get_history
from app.monitoring.metrics import (
    ai_requests_total,
    ai_latency_seconds,
    retrieval_results,
)

logger = logging.getLogger("ai-rag-core.pipeline")


async def answer(
    query: str,
    user_id: str | None = None,
    session_id: str | None = None,
    db=None,
) -> dict:
    """
    Pipeline RAG end-to-end (4 nguồn song song):
      query → [vector search Article  ∥  graph traversal Job/Company
                ∥  SQL analytics (tech_analytics)  ∥  user profile]
            → rerank article (top-5)
            → build prompt (article + job + analytics + user block + history)
            → LLM → answer

    user_id:    UUID string nếu user đã đăng nhập, None nếu anonymous.
    session_id: UUID string phiên hội thoại, dùng để load conversation history.
    db:         AsyncSession — cần thiết cho conversation history.

    Trả về dict:
      {
        "answer":     str,
        "sources":    list[dict],
        "entities":   list[str],
        "job_titles": list[str],
        "query":      str,
      }
    """
    t0 = time.monotonic()
    settings = get_settings()

    # 0. Load conversation history (sliding window, tối đa 10 turns)
    history: list[dict] = []
    if session_id and db is not None:
        try:
            import uuid as _uuid
            history = await get_history(_uuid.UUID(session_id), limit=10, db=db)
        except Exception as e:
            logger.warning("Failed to load conversation history: %s", e)

    # 1. Chạy song song: vector search + graph traversal + user profile
    t_ret = time.monotonic()
    gather_tasks = [
        vector_search(query, top_k=5),
        graph_search(query),
    ]
    if user_id:
        gather_tasks.append(get_user_context(user_id))
        candidates, graph_data, user_ctx = await asyncio.gather(*gather_tasks)
    else:
        candidates, graph_data = await asyncio.gather(*gather_tasks)
        user_ctx = None

    ai_latency_seconds.labels(endpoint="chat", stage="retrieval").observe(
        time.monotonic() - t_ret
    )
    retrieval_results.labels(source="vector").observe(len(candidates))
    retrieval_results.labels(source="graph").observe(
        len(graph_data.get("jobs", [])) + len(graph_data.get("companies", []))
    )

    # 1b. SQL analytics: lấy data cho các entity đã trích được từ graph search
    tech_entities = graph_data.get("entities", [])
    sql_data: list[dict] = []
    if tech_entities:
        try:
            sql_data = await sql_analytics_search(tech_entities, months=6)
        except Exception as e:
            logger.warning("SQL analytics search failed, skipping: %s", e)
    retrieval_results.labels(source="sql").observe(len(sql_data))

    # 2. Rerank trong thread pool (CPU-bound, tránh block event loop)
    t_rerank = time.monotonic()
    loop = asyncio.get_event_loop()
    top_articles = (
        await loop.run_in_executor(None, partial(rerank, query, candidates, 5))
        if candidates else []
    )
    ai_latency_seconds.labels(endpoint="chat", stage="rerank").observe(
        time.monotonic() - t_rerank
    )

    # 2b. Nếu graph trống (query mơ hồ, không có entity) và threshold lọc hết bài
    #     → dùng top-3 bài điểm cao nhất + đánh dấu low_confidence để LLM thận trọng
    has_graph_data = bool(graph_data.get("jobs") or graph_data.get("companies"))
    low_confidence = False
    if not top_articles and not has_graph_data and candidates:
        top_articles   = sorted(
            candidates, key=lambda x: x.get("rerank_score", 0), reverse=True
        )[:3]
        low_confidence = True

    # 3. Fallback: không có cả article lẫn job data lẫn analytics
    if not top_articles and not graph_data.get("jobs") and not graph_data.get("companies") and not sql_data:
        ai_requests_total.labels(
            endpoint="chat", status="fallback", llm_provider=settings.llm_provider
        ).inc()
        return {
            "answer":     "Tôi không tìm thấy thông tin liên quan trong dữ liệu hiện có.",
            "sources":    [],
            "entities":   tech_entities,
            "job_titles": graph_data.get("job_titles", []),
            "query":      query,
        }

    # 4. Build prompt (ghép cả 4 nguồn + conversation history)
    user_blk = build_user_block(user_ctx) if user_ctx else ""
    messages = build_messages(
        query, top_articles, graph_data,
        user_block=user_blk,
        low_confidence=low_confidence,
        sql_data=sql_data,
        history=history,
    )

    # 5. Gọi LLM
    t_llm = time.monotonic()
    answer_text = await generate(messages)
    llm_latency = time.monotonic() - t_llm
    ai_latency_seconds.labels(endpoint="chat", stage="llm").observe(llm_latency)

    total_latency = time.monotonic() - t0
    ai_latency_seconds.labels(endpoint="chat", stage="total").observe(total_latency)
    ai_requests_total.labels(
        endpoint="chat", status="ok", llm_provider=settings.llm_provider
    ).inc()

    # 6. Evaluation (fire-and-forget, không block response)
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
        "answer":       answer_text,
        "sources":      top_articles,
        "job_context":  _build_job_context_block(graph_data),
        "entities":     tech_entities,
        "job_titles":   graph_data.get("job_titles", []),
        "analytics":    sql_data,
        "query":        query,
    }
