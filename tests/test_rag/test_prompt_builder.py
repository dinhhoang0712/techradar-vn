import pytest
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
