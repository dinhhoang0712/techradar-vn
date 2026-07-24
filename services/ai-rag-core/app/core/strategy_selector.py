"""
Rule-based Strategy Selector cho pipeline RAG.

Quyết định nhánh retrieval nào cần chạy cho 1 câu hỏi, dựa trên entity đã trích (từ
entity_extractor.extract_query_entities) và intent pattern trên câu hỏi gốc — không dùng
LLM, để giữ chi phí/latency bằng 0 và kết quả giải thích được (matched_signals).

Vector search luôn bật (baseline). Graph/SQL/expansion chỉ bật khi có tín hiệu rõ ràng, và
việc "bật" ở đây có nghĩa là nhánh đó thực sự không được đưa vào gather ở pipeline.py — không
phải chạy rồi bỏ kết quả.
"""

from dataclasses import dataclass, field

from app.core.entity_extractor import has_analytics_intent, has_multihop_intent
from app.core.retriever_graph import normalize_tech_entities


@dataclass
class RetrievalStrategy:
    use_vector: bool = True  # luôn bật — baseline, không bao giờ bị gate
    use_graph: bool = False
    graph_expansion_depth: int = 0  # 0 = tắt, 1 = mở rộng nông, 2 = mở rộng sâu (so sánh/hệ sinh thái)
    use_sql_analytics: bool = False
    tech_names: list[str] = field(default_factory=list)
    company_names: list[str] = field(default_factory=list)
    matched_signals: dict[str, list[str]] = field(default_factory=dict)


def select_strategy(query: str, extracted: dict) -> RetrievalStrategy:
    """
    query:     câu hỏi gốc của user (dùng để match intent pattern, KHÔNG dùng lại entity đã
               trích để tránh trùng lặp — entity quyết định "có gì để tra cứu", intent pattern
               trên câu hỏi gốc quyết định "tra cứu sâu tới đâu").
    extracted: kết quả extract_query_entities(query) — {"technologies", "job_titles",
               "companies", "locations"}. Được hoist lên gọi 1 lần ở pipeline.py, truyền vào
               đây thay vì gọi lại NER/regex lần 2.
    """
    tech_names = normalize_tech_entities(extracted.get("technologies", []))
    company_names = extracted.get("companies", [])
    job_titles = extracted.get("job_titles", [])
    locations = extracted.get("locations", [])

    has_entities = bool(tech_names or company_names or job_titles or locations)

    expansion_depth = 0
    if has_entities:
        expansion_depth = 2 if has_multihop_intent(query) else 1

    use_sql_analytics = bool(tech_names) and has_analytics_intent(query)

    return RetrievalStrategy(
        use_vector=True,
        use_graph=has_entities,
        graph_expansion_depth=expansion_depth,
        use_sql_analytics=use_sql_analytics,
        tech_names=tech_names,
        company_names=company_names,
        matched_signals={
            "technologies": tech_names,
            "companies": company_names,
            "job_titles": job_titles,
            "locations": locations,
        },
    )
