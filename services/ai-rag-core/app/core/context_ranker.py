"""
Unified reranking cho job/company/analytics — tái dùng reranker.rerank() (vốn chỉ đọc
title/content của mỗi candidate) bằng cách bọc mỗi item thành 1 dict có title/content tổng
hợp từ prompt_builder's per-item formatter, rồi rerank RIÊNG từng loại — không gộp chung 1
batch, vì điểm cross-encoder không so sánh được giữa các loại passage khác nhau (bài viết dài
nhiều đoạn, tin tuyển dụng 1 dòng, thống kê 1 dòng).

Expansion triples (từ retriever_graph_expand) KHÔNG qua bước này — chúng không phải passage
tự nhiên ngôn ngữ, đã neo theo entity trích được, và Cypher DISTINCT+LIMIT đã giới hạn/dedupe
sẵn ở retriever_graph_expand.expand_subgraph().
"""

from app.config import Settings
from app.core.prompt_builder import format_analytics_line, format_company_line, format_job_line
from app.core.reranker import rerank


def _with_passage(item: dict, content: str) -> dict:
    """Shallow copy kèm title/content tổng hợp để rerank() dùng — giữ nguyên field gốc."""
    enriched = dict(item)
    enriched.setdefault("title", enriched.get("title") or enriched.get("name") or "")
    enriched["content"] = content
    return enriched


def _group_sql_by_tech(sql_rows: list[dict]) -> list[dict]:
    """Nhóm sql_rows theo technology_name — 1 candidate rerank / tech, không phải / dòng tháng."""
    by_tech: dict[str, list[dict]] = {}
    for row in sql_rows:
        name = row.get("technology_name") or "Unknown"
        by_tech.setdefault(name, []).append(row)

    return [
        {"technology_name": tech, "rows": rows, "title": tech, "content": format_analytics_line(tech, rows)}
        for tech, rows in by_tech.items()
    ]


def rerank_context(
    query: str,
    articles: list[dict],
    jobs: list[dict],
    companies: list[dict],
    sql_rows: list[dict],
    settings: Settings,
) -> dict:
    """
    Rerank riêng từng loại nguồn, mỗi loại lấy top settings.rerank_top_k, cùng ngưỡng
    RERANK_SCORE_THRESHOLD đã có trong reranker.py. CPU-bound — caller (pipeline.py) tự bọc
    trong run_in_executor giống cách rerank() article hiện tại đang làm, để không block
    event loop.
    """
    top_articles = rerank(query, articles, settings.rerank_top_k) if articles else []

    job_candidates = [_with_passage(j, format_job_line(j)) for j in jobs]
    top_jobs = rerank(query, job_candidates, settings.rerank_top_k) if job_candidates else []

    company_candidates = [_with_passage(c, format_company_line(c)) for c in companies]
    top_companies = rerank(query, company_candidates, settings.rerank_top_k) if company_candidates else []

    analytics_candidates = _group_sql_by_tech(sql_rows) if sql_rows else []
    top_analytics_wrapped = rerank(query, analytics_candidates, settings.rerank_top_k) if analytics_candidates else []
    # Bung lại thành sql rows gốc (bỏ title/content tổng hợp chỉ dùng để rerank)
    top_analytics = [row for wrapped in top_analytics_wrapped for row in wrapped["rows"]]

    return {
        "articles": top_articles,
        "jobs": top_jobs,
        "companies": top_companies,
        "analytics": top_analytics,
    }
