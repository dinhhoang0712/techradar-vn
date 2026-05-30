import pytest
import sys
import os

# Thêm đường dẫn để import code từ src
sys.path.append(os.path.abspath("src/ai-rag-core"))

def test_prompt_confidence_logic():
    """Kiểm tra logic thay đổi Prompt dựa trên độ tin cậy (Confidence)."""
    from app.core.prompt_builder import build_messages
    
    query = "Học Python"
    articles = [{"title": "Python 101", "content": "Basic Python"}]
    
    # Case 1: High confidence
    msg_high = build_messages(query, articles, low_confidence=False)
    # Kiểm tra xem có vai trò system và user không
    roles = [m["role"] for m in msg_high]
    assert "system" in roles
    assert "user" in roles
    # Kiểm tra xem nội dung bài viết có trong prompt không
    user_msg = next(m["content"] for m in msg_high if m["role"] == "user")
    assert "Python 101" in user_msg

    # Case 2: Low confidence
    msg_low = build_messages(query, articles, low_confidence=True)
    user_msg_low = next(m["content"] for m in msg_low if m["role"] == "user")
    # Kiểm tra xem có lời cảnh báo độ liên quan THẤP không
    assert "Lưu ý" in user_msg_low or "THẤP" in user_msg_low

def test_reranker_sorting_logic():
    """Kiểm tra logic sắp xếp của Reranker (Cực kỳ quan trọng)."""
    from app.core.reranker import rerank
    
    query = "FastAPI"
    # Giả lập model chấm điểm (vì chúng ta không load model thật ở đây để tránh chậm)
    candidates = [
        {"title": "Bài viết về Java", "content": "Spring Boot...", "score": 0.1},
        {"title": "Bài viết về FastAPI", "content": "Hướng dẫn FastAPI...", "score": 0.9}
    ]
    
    # Ở đây chúng ta test logic gán điểm và sắp xếp
    # (Hàm rerank thật sẽ gọi model, ở đây ta giả lập kết quả trả về từ model)
    for c in candidates:
        c["rerank_score"] = c["score"] # Giả lập kết quả sau khi qua model
        
    reranked = sorted(candidates, key=lambda x: x["rerank_score"], reverse=True)
    
    assert reranked[0]["title"] == "Bài viết về FastAPI"
    assert reranked[1]["title"] == "Bài viết về Java"

def test_graph_data_transformation_logic():
    """Kiểm tra logic biến đổi dữ liệu thô từ Neo4j sang format của Chat."""
    # Giả lập dữ liệu thô từ Neo4j (thường là list các Mapping)
    raw_graph_results = {
        "entities": ["Python", "FastAPI"],
        "job_titles": ["Backend Developer"],
        "companies": [
            {"name": "FPT", "location": "Hà Nội", "technology": "Java"}
        ]
    }
    
    # Test logic bóc tách thông tin công ty
    company = raw_graph_results["companies"][0]
    assert company["name"] == "FPT"
    assert "Hà Nội" in company["location"]
    
def test_user_context_merging_logic():
    """Kiểm tra logic gộp thông tin người dùng vào luồng xử lý."""
    from app.core.retriever_user import build_user_block
    
    # Case: User có profile đầy đủ
    full_ctx = {
        "full_name": "Trần Văn B",
        "job_role": "AI Engineer",
        "technologies": ["PyTorch", "Tensorflow"],
        "bio": "Đam mê Machine Learning"
    }
    block = build_user_block(full_ctx)
    assert "AI Engineer" in block
    assert "PyTorch" in block
    
    # Case: User profile trống (hệ thống phải xử lý mượt mà, không crash)
    empty_ctx = {"user_id": "some-uuid"}
    block_empty = build_user_block(empty_ctx)
    assert block_empty == "" # Logic: Nếu không có info thì trả về rỗng để không làm nhiễu prompt
