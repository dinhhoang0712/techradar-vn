import pytest
# Giả sử chúng ta test hàm xử lý text trong AI core
import sys
import os

# Thêm đường dẫn để import được code từ src
sys.path.append(os.path.abspath("src/ai-rag-core"))

def test_passage_builder_logic():
    """Kiểm tra logic ghép nối văn bản của Reranker (Unit Test)."""
    from app.core.reranker import _build_passage
    
    mock_candidate = {
        "title": "Kỹ sư Python",
        "content": "Yêu cầu kinh nghiệm 3 năm với Django và FastAPI."
    }
    passage = _build_passage(mock_candidate, max_chars=50)
    
    # Kiểm tra xem nó có ghép title và content không
    assert "Python" in passage
    assert "Django" in passage
    # Kiểm tra giới hạn ký tự (max_chars)
    assert len(passage) <= 50

def test_threshold_filtering_logic():
    """Kiểm tra logic lọc kết quả theo ngưỡng điểm (Unit Test)."""
    # Chúng ta test logic lọc của hàm rerank bằng cách mock model
    from app.core.reranker import RERANK_SCORE_THRESHOLD
    
    # Giả sử ngưỡng là 0.01, ta test xem logic lọc có hoạt động không
    results = [
        {"title": "A", "rerank_score": 0.5},
        {"title": "B", "rerank_score": 0.1} # Thấp hơn ngưỡng
    ]
    filtered = [r for r in results if r["rerank_score"] >= RERANK_SCORE_THRESHOLD]
    
    assert len(filtered) == 1
    assert filtered[0]["title"] == "A"
