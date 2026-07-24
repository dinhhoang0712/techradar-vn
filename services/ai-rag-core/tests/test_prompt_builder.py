from app.core.prompt_builder import build_messages  # type: ignore # noqa


def test_prompt_construction_scenarios():
    """Kiểm tra đa dạng các kịch bản xây dựng prompt: từ đủ context, thiếu dữ liệu đến xử lý văn bản quá dài."""

    # Kịch bản 1: Đầy đủ context (Articles + Graph + User)
    query = "Lương AI?"
    articles = [{"title": "T1", "content": "C1", "source": "S1"}]
    graph = {"jobs": [{"title": "AI"}], "entities": ["AI"]}
    user = "User là Senior."
    msgs_full = build_messages(query, articles, graph, user)
    assert "C1" in msgs_full[1]["content"]
    assert "Senior" in msgs_full[1]["content"]

    # Kịch bản 2: Không có dữ liệu bổ trợ (Fallback)
    msgs_empty = build_messages("Query lạ", [], {}, "")
    assert "Không có bài viết liên quan" in msgs_empty[1]["content"]

    # Kịch bản 3: Tự động cắt ngắn nội dung dài (>800 ký tự)
    long_art = [{"title": "L", "content": "X" * 1000}]
    msgs_trunc = build_messages("Test", long_art)
    assert "X" * 800 + "..." in msgs_trunc[1]["content"]

    # Kịch bản 4: Graph chỉ chứa công nghệ liên quan
    graph_partial = {"related_tech": [{"from_tech": "A", "related_tech": "B"}]}
    msgs_partial = build_messages("A", [], graph_partial)
    assert "Công nghệ liên quan: B" in msgs_partial[1]["content"]


def test_subgraph_block_groups_triples_by_hop_distance():
    """Multi-hop expansion (Phase 2): triple phải được nhóm theo hop, không phải liệt kê phẳng —
    để LLM phân biệt được 'liên quan trực tiếp' với 'mở rộng nhiều bước'."""
    triples = [
        {"subject": "Java", "predicate": "REQUIRES", "object": "Spring", "hop": 1},
        {"subject": "Spring", "predicate": "RELATED_TO", "object": "Kafka", "hop": 2},
    ]
    msgs = build_messages("Java cần học gì?", [], {}, subgraph_triples=triples)
    content = msgs[1]["content"]

    assert "Liên quan trực tiếp" in content
    assert "Java yêu cầu Spring" in content
    assert "Mở rộng 2 bước" in content
    assert "Spring liên quan tới Kafka" in content


def test_subgraph_block_empty_when_no_triples():
    msgs = build_messages("Query", [], {})
    assert "Không có dữ liệu đồ thị mở rộng" in msgs[1]["content"]


def test_job_and_company_blocks_unchanged_after_formatter_extraction():
    """Regression guard: format_job_line/format_company_line được tách ra khỏi
    _build_job_context_block (Phase 3) không được đổi hành vi render."""
    graph = {
        "jobs": [{"title": "Backend Dev", "technology": "Java", "company": "FPT", "location": "Hà Nội"}],
        "companies": [{"name": "FPT", "industry": "IT", "technology": "Java"}],
    }
    msgs = build_messages("Query", [], graph)
    content = msgs[1]["content"]

    assert "Backend Dev (yêu cầu: Java) tại FPT" in content
    assert "FPT (dùng Java)" in content
