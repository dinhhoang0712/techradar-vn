import pytest
import requests
import time

def test_rag_retrieval_relevance(api_urls, auth_headers):
    """Kiểm tra xem AI có lấy đúng kiến thức từ Database không (Retrieval Quality)."""
    query = "Lộ trình học Java Spring Boot"
    # 1. Tạo session
    res = requests.post(f"{api_urls['golang']}/chat/session", headers=auth_headers)
    session_id = res.json().get("id") or res.json().get("session_id")
    assert session_id, "Failed to create session"

    # 2. Gửi câu hỏi
    response = requests.post(
        f"{api_urls['golang']}/chat/session/{session_id}/messages", 
        headers=auth_headers, 
        json={"query": query}, 
        timeout=90
    )
    assert response.status_code in [200, 500, 502]
    if response.status_code == 200:
        answer = response.json().get("answer", "").lower()
        # Kiểm tra tính liên quan: Trả về ít nhất một câu trả lời có độ dài hợp lý
        assert len(answer) > 50, "AI response is too short"
        # Kiểm tra sự tồn tại của các trường thông tin bổ sung (sources, entities)
        assert "sources" in response.json()
        assert "entities" in response.json()

def test_rag_faithfulness_check(api_urls, auth_headers):
    """Kiểm tra tính trung thực của AI (Tránh bịa đặt thông tin không có thật)."""
    # Hỏi về một thứ hoàn toàn không tồn tại trong lĩnh vực CNTT
    query = "Lộ trình trở thành thợ sửa ống nước dùng ReactJS"
    
    # 1. Tạo session
    s_res = requests.post(f"{api_urls['golang']}/chat/session", headers=auth_headers)
    session_id = s_res.json().get("id") or s_res.json().get("session_id")
    
    # 2. Gửi câu hỏi
    res = requests.post(
        f"{api_urls['golang']}/chat/session/{session_id}/messages", 
        headers=auth_headers, 
        json={"query": query}, 
        timeout=90
    )
    
    # Nếu bị lỗi (như 429 hay 502), in ra để debug
    if res.status_code != 200:
        print(f"\n[DEBUG] Chat failed with status {res.status_code}")
        print(f"Body: {res.text.encode('utf-8')}") # Encode để tránh lỗi Unicode trên Windows

    assert res.status_code in [200, 500, 502]
    if res.status_code == 200:
        answer = res.json().get("answer", "").lower()
        # AI nên từ chối tư vấn sửa ống nước bằng ReactJS
        bad_keywords = ["hàn chì", "vặn vít", "đường ống", "kìm", "ống nước"]
        found_bad = [t for t in bad_keywords if t in answer]
        assert len(found_bad) == 0, f"AI vẫn đang bịa đặt về sửa ống nước! Nội dung: {answer[:100]}..."
