"""
Serialize subgraph-expansion triples thành JSON-LD tối giản cho API response.

Đây là bản @context tự định nghĩa nhỏ gọn, KHÔNG phải một OWL/RDFS ontology đầy đủ — mục
tiêu là hỗ trợ luận điểm "structured, machine-readable output" cho citation/traceability, chứ
không phải semantic-web interop chuẩn. Dùng cho response API, KHÔNG dùng để build prompt LLM
(prompt dùng bản text ở prompt_builder._build_subgraph_block, dễ đọc hơn cho LLM so với
JSON-LD verbose).
"""

_CONTEXT = {
    "@vocab": "https://techradar.vn/ontology#",
    "Technology": "https://techradar.vn/ontology#Technology",
    "Company": "https://techradar.vn/ontology#Company",
    "Job": "https://techradar.vn/ontology#Job",
    "Skill": "https://techradar.vn/ontology#Skill",
    "REQUIRES": "https://techradar.vn/ontology#requires",
    "USES": "https://techradar.vn/ontology#uses",
    "POSTED_BY": "https://techradar.vn/ontology#postedBy",
    "HIRES_FOR": "https://techradar.vn/ontology#postedBy",
    "RELATED_TO": "https://techradar.vn/ontology#relatedTo",
    "MENTIONS": "https://techradar.vn/ontology#mentions",
}


def triples_to_jsonld(triples: list[dict], seed_entities: list[str]) -> dict:
    """
    triples:       list[dict] từ retriever_graph_expand.expand_subgraph()
                   ({subject, subject_type, predicate, object, object_type, hop})
    seed_entities: tên tech/company dùng làm điểm neo mở rộng (strategy.tech_names +
                   strategy.company_names) — giữ lại để biết truy vấn bắt đầu từ đâu.
    """
    graph = [
        {
            "@type": t.get("subject_type"),
            "name": t.get("subject"),
            "relation": t.get("predicate"),
            "target": {"@type": t.get("object_type"), "name": t.get("object")},
            "hop": t.get("hop"),
        }
        for t in triples
    ]

    return {
        "@context": _CONTEXT,
        "@graph": graph,
        "seed_entities": seed_entities,
    }
