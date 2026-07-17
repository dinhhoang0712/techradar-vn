import pytest
import numpy as np
from unittest.mock import MagicMock
from app.core.reranker import rerank # type: ignore # noqa

def test_rerank_basic(monkeypatch):
    """Kiểm tra logic sắp xếp của rerank."""
    mock_model = MagicMock()
    # Mock scores: cả 2 đều trên ngưỡng 0.4, article 2 cao nhất
    mock_model.predict.return_value = np.array([0.5, 0.9])
    monkeypatch.setattr("app.core.reranker.get_reranker", lambda: mock_model)
    
    query = "Học Python"
    candidates = [
        {"title": "Art 1", "content": "Nội dung 1"},
        {"title": "Art 2", "content": "Nội dung 2 Python"}
    ]
    
    result = rerank(query, candidates, top_k=5)
    
    assert len(result) == 2
    assert result[0]["title"] == "Art 2"
    assert result[0]["rerank_score"] == 0.9
    assert result[1]["title"] == "Art 1"

def test_rerank_top_k(monkeypatch):
    """Kiểm tra việc giới hạn số lượng kết quả trả về."""
    mock_model = MagicMock()
    mock_model.predict.return_value = np.array([0.5, 0.6, 0.7])
    monkeypatch.setattr("app.core.reranker.get_reranker", lambda: mock_model)
    
    candidates = [{"title": str(i), "content": "c"} for i in range(3)]
    result = rerank("query", candidates, top_k=2)
    
    assert len(result) == 2
    # Phải lấy top 2 điểm cao nhất (0.7 và 0.6)
    assert result[0]["rerank_score"] == 0.7
    assert result[1]["rerank_score"] == 0.6

def test_rerank_empty_input():
    """Kiểm tra khi input rỗng."""
    result = rerank("query", [])
    assert result == []
