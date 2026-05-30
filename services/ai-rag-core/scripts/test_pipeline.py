"""
Test nhanh pipeline.answer() end-to-end trên local Neo4j.

Cách chạy:
    cd src/ai-rag-core
    set -a && source ../../.env && set +a
    USE_LOCAL_NEO4J=true python3 -m scripts.test_pipeline
"""

import asyncio
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.core.retriever import vector_search
from app.core.retriever_graph import graph_search
from app.core.reranker import rerank
from app.core.prompt_builder import build_messages
from app.core.generator import generate
from app.core.entity_extractor import extract_query_entities
from app.db.neo4j_client import close_driver

TEST_QUERIES = [
    "Lương kỹ sư phần mềm ở Việt Nam hiện tại ra sao?",
    "Công việc Python developer lương bao nhiêu?",
    "Tôi muốn tìm việc Data Engineer dùng Kafka và Spark",
    "tôi muốn học lập trình web",
    "FPT tuyển kỹ sư phần mềm không?",
]


def _fmt(seconds: float) -> str:
    return f"{seconds * 1000:.0f}ms"


async def run_timed(query: str) -> dict:
    print(f"\n{'=' * 60}")
    print(f"Query: {query}")
    print("=" * 60)

    # --- Entity extraction (local, sync) ---
    t = time.perf_counter()
    extracted = extract_query_entities(query)
    t_entity = time.perf_counter() - t
    print(f"[1] Entity extraction (local)  : {_fmt(t_entity)}")
    print(f"    → tech     : {extracted['technologies']}")
    print(f"    → job_title: {extracted['job_titles']}")

    # --- Parallel: vector search + graph search ---
    t = time.perf_counter()
    candidates, graph_data = await asyncio.gather(
        vector_search(query, top_k=5),
        graph_search(query),
    )
    t_retrieve = time.perf_counter() - t
    print(f"[2] Retrieval (vector ∥ graph)  : {_fmt(t_retrieve)}")
    print(f"    → vector candidates: {len(candidates)}")
    print(f"    → graph jobs: {len(graph_data.get('jobs', []))}, "
          f"companies: {len(graph_data.get('companies', []))}")

    # --- Rerank ---
    t = time.perf_counter()
    loop = asyncio.get_event_loop()
    top_articles = (
        await loop.run_in_executor(None, lambda: rerank(query, candidates, top_k=5))
        if candidates else []
    )
    t_rerank = time.perf_counter() - t
    print(f"[3] Rerank                      : {_fmt(t_rerank)}")
    print(f"    → top articles: {len(top_articles)}")

    # --- Build prompt ---
    t = time.perf_counter()
    messages = build_messages(query, top_articles, graph_data)
    t_prompt = time.perf_counter() - t
    print(f"[4] Build prompt                : {_fmt(t_prompt)}")

    # --- Generate ---
    t = time.perf_counter()
    answer_text = await generate(messages)
    t_gen = time.perf_counter() - t
    print(f"[5] LLM generate                : {_fmt(t_gen)}")

    total = t_entity + t_retrieve + t_rerank + t_prompt + t_gen
    print(f"\n    TOTAL                      : {_fmt(total)}")

    print(f"\nCâu trả lời:\n{answer_text}")

    print(f"\nNguồn ({len(top_articles)} bài):")
    for j, src in enumerate(top_articles, 1):
        score = src.get("rerank_score", 0)
        title = src.get("title", "N/A")
        date  = str(src.get("published_date", ""))[:10]
        print(f"  [{j}] (score={score:.3f}) {title} — {date}")

    return {
        "answer": answer_text,
        "sources": top_articles,
        "entities": graph_data.get("entities", []),
        "job_titles": graph_data.get("job_titles", []),
        "timings": {
            "entity_ms":   round(t_entity * 1000),
            "retrieve_ms": round(t_retrieve * 1000),
            "rerank_ms":   round(t_rerank * 1000),
            "prompt_ms":   round(t_prompt * 1000),
            "generate_ms": round(t_gen * 1000),
            "total_ms":    round(total * 1000),
        },
    }


async def main():
    print("TEST PIPELINE RAG — local entity extractor (no LLM for entities)")

    all_timings = []
    for query in TEST_QUERIES:
        result = await run_timed(query)
        all_timings.append(result["timings"])

    if len(all_timings) > 1:
        print(f"\n{'=' * 60}")
        print("TIMING SUMMARY (ms)")
        print(f"{'Query':<4} {'Entity':>8} {'Retrieve':>10} {'Rerank':>8} {'Generate':>10} {'Total':>8}")
        for i, t in enumerate(all_timings, 1):
            print(f"  {i:<3} {t['entity_ms']:>8} {t['retrieve_ms']:>10} "
                  f"{t['rerank_ms']:>8} {t['generate_ms']:>10} {t['total_ms']:>8}")

    await close_driver()


if __name__ == "__main__":
    asyncio.run(main())
