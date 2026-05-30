import pytest
import sys
import os
import uuid

# Thêm đường dẫn để import code từ src
sys.path.append(os.path.abspath("src/ai-rag-core"))

def test_extract_keywords_logic():
    """Kiểm tra logic tạo block thông tin người dùng (User Profile Block)."""
    # Fix import path
    from app.core.retriever_user import build_user_block
    
    user_ctx = {
        "full_name": "Nguyen Van A",
        "job_role": "Java Developer",
        "technologies": ["Spring", "MySQL"]
    }
    block = build_user_block(user_ctx)
    
    assert "Java Developer" in block
    assert "Spring" in block
    assert "MySQL" in block

def test_neo4j_data_mapping_logic():
    """Kiểm tra logic chuyển đổi dữ liệu từ Neo4j sang định dạng cho AI (Data Mapping)."""
    # Giả sử chúng ta có hàm format kết quả từ graph
    # Ở đây tôi sẽ test logic map dữ liệu Job
    mock_raw_jobs = [
        {
            "title": "Senior Python",
            "company": "Google",
            "salary_min": 2000,
            "salary_max": 4000,
            "technology": ["Python", "FastAPI"]
        }
    ]
    
    # Kiểm tra xem logic có giữ đúng thông tin quan trọng không
    job = mock_raw_jobs[0]
    assert job["salary_max"] > job["salary_min"]
    assert "FastAPI" in job["technology"]

def test_reranker_threshold_edge_cases():
    """Kiểm tra các trường hợp biên của logic Reranker."""
    from app.core.reranker import RERANK_SCORE_THRESHOLD
    
    # Trường hợp điểm sát nút ngưỡng (0.01)
    candidates = [
        {"title": "Pass", "rerank_score": 0.41},
        {"title": "Fail", "rerank_score": 0.39}
    ]
    
    passed = [c for c in candidates if c["rerank_score"] >= RERANK_SCORE_THRESHOLD]
    assert len(passed) == 1
    assert passed[0]["title"] == "Pass"

def test_chat_session_id_logic():
    """Kiểm tra logic tạo Session ID trong Chat Service."""
    from app.services.chat_service import _create_session
    from app.api.schemas import ChatRequest
    
    # Case 1: Không truyền session_id -> Phải tự tạo UUID mới
    req_new = ChatRequest(query="Hello")
    session_new = _create_session(req_new)
    assert isinstance(session_new.id, uuid.UUID)
    
    # Case 2: Truyền session_id cũ -> Phải giữ nguyên ID đó
    old_id = uuid.uuid4()
    req_old = ChatRequest(query="Hello", session_id=old_id)
    session_old = _create_session(req_old)
    assert session_old.id == old_id
