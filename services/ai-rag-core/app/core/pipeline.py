import asyncio
from functools import partial

from app.core.retriever import vector_search
from app.core.retriever_graph import graph_search
from app.core.retriever_user import get_user_context, build_user_block
from app.core.reranker import rerank
from app.core.prompt_builder import build_messages, _build_job_context_block
from app.core.generator import generate


async def answer(query: str, user_id: str | None = None) -> dict:
    """
    Pipeline RAG end-to-end (3 nguồn song song):
      query → [vector search Article  ∥  graph traversal Job/Company  ∥  user profile]
            → rerank article (top-5)
            → build prompt (article context + job context + user block)
            → LLM → answer

    user_id: UUID string nếu user đã đăng nhập, None nếu anonymous.

    Trả về dict:
      {
        "answer":     str,
        "sources":    list[dict],
        "entities":   list[str],
        "job_titles": list[str],
        "query":      str,
      }
    """
    # 1. Chạy song song: vector search + graph traversal + user profile
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

    # 2. Rerank trong thread pool (CPU-bound, tránh block event loop)
    loop = asyncio.get_event_loop()
    top_articles = (
        await loop.run_in_executor(None, partial(rerank, query, candidates, 5))
        if candidates else []
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

    # 3. Fallback: không có cả article lẫn job data
    if not top_articles and not graph_data.get("jobs") and not graph_data.get("companies"):
        return {
            "answer":     "Tôi không tìm thấy thông tin liên quan trong dữ liệu hiện có.",
            "sources":    [],
            "entities":   graph_data.get("entities", []),
            "job_titles": graph_data.get("job_titles", []),
            "query":      query,
        }

    # 4. Build prompt (ghép cả 3 nguồn)
    user_blk = build_user_block(user_ctx) if user_ctx else ""
    messages = build_messages(
        query, top_articles, graph_data,
        user_block=user_blk, low_confidence=low_confidence,
    )

    # 5. Gọi LLM
    answer_text = await generate(messages)

    return {
        "answer":       answer_text,
        "sources":      top_articles,
        "job_context":  _build_job_context_block(graph_data),  # cho RAGAS eval
        "entities":     graph_data.get("entities", []),
        "job_titles":   graph_data.get("job_titles", []),
        "query":        query,
    }
