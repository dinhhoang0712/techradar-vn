import asyncio
from functools import partial
from typing import AsyncIterator

from app.core.retriever import vector_search
from app.core.retriever_graph import graph_search
from app.core.retriever_user import get_user_context, build_user_block
from app.core.reranker import rerank
from app.core.prompt_builder import build_messages
from app.core.generator_stream import generate_stream


_FALLBACK_ANSWER = "Tôi không tìm thấy thông tin liên quan trong dữ liệu hiện có."


async def answer_stream(query: str, user_id: str | None = None) -> AsyncIterator[dict]:
    """
    Streaming version của pipeline.answer().
    Yield dict với 2 loại event:
      - {"event": "token", "data": <str chunk>}
      - {"event": "done",  "data": {"answer", "sources", "entities", "job_titles"}}
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

    # 2b. Nếu graph trống (query mơ hồ) và threshold lọc hết bài
    #     → dùng top-3 bài điểm cao nhất + đánh dấu low_confidence để LLM thận trọng
    has_graph_data = bool(graph_data.get("jobs") or graph_data.get("companies"))
    low_confidence = False
    if not top_articles and not has_graph_data and candidates:
        top_articles   = sorted(
            candidates, key=lambda x: x.get("rerank_score", 0), reverse=True
        )[:3]
        low_confidence = True

    # 3. Fallback: không có data nào
    if not top_articles and not graph_data.get("jobs") and not graph_data.get("companies"):
        yield {"event": "token", "data": _FALLBACK_ANSWER}
        yield {
            "event": "done",
            "data": {
                "answer":     _FALLBACK_ANSWER,
                "sources":    [],
                "entities":   graph_data.get("entities", []),
                "job_titles": graph_data.get("job_titles", []),
            },
        }
        return

    # 4. Build prompt
    user_blk = build_user_block(user_ctx) if user_ctx else ""
    messages = build_messages(
        query, top_articles, graph_data,
        user_block=user_blk, low_confidence=low_confidence,
    )

    # 5. Stream LLM
    chunks: list[str] = []
    async for chunk in generate_stream(messages):
        chunks.append(chunk)
        yield {"event": "token", "data": chunk}

    full_answer = "".join(chunks)
    yield {
        "event": "done",
        "data": {
            "answer":     full_answer,
            "sources":    top_articles,
            "entities":   graph_data.get("entities", []),
            "job_titles": graph_data.get("job_titles", []),
        },
    }
