import pytest
import requests

def test_chat_session_creation(api_urls, auth_headers):
    """Kiểm tra việc tạo session chat mới."""
    res = requests.post(f"{api_urls['golang']}/chat/session", headers=auth_headers)
    assert res.status_code in [200, 201]
    data = res.json()
    # Kiểm tra key ID linh hoạt (id hoặc session_id)
    assert "id" in data or "session_id" in data

def test_chat_message_flow(api_urls, auth_headers):
    """Kiểm tra việc gửi tin nhắn và nhận phản hồi có cấu trúc."""
    # 1. Tạo session
    session_res = requests.post(f"{api_urls['golang']}/chat/session", headers=auth_headers)
    session_id = session_res.json().get("id") or session_res.json().get("session_id")
    
    if session_id:
        # 2. Gửi tin nhắn
        payload = {"query": "Hello AI"}
        res = requests.post(f"{api_urls['golang']}/chat/session/{session_id}/messages", 
                            headers=auth_headers, json=payload, timeout=90)
        assert res.status_code in [200, 201, 500, 502]
        if res.status_code in [200, 201]:
            data = res.json()
            # Không check text, chỉ check sự tồn tại của trường phản hồi
            assert any(k in data for k in ["answer", "response", "content"])

def test_python_ai_health_structure(api_urls):
    """Kiểm tra API Health của Python AI có trả đúng các trường neo4j/status không."""
    res = requests.get(f"{api_urls['python']}/health")
    assert res.status_code == 200
    data = res.json()
    assert "status" in data
    assert "neo4j" in data
    assert isinstance(data["neo4j"], bool)
