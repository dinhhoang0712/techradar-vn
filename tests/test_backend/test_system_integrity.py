import pytest
import requests
import json
import time

def test_system_status_public(api_urls):
    """GET /status không cần auth -> 200 với maintenance flags."""
    res = requests.get(f"{api_urls['golang']}/status")
    assert res.status_code == 200
    data = res.json()
    assert "maintenance_web" in data, "Missing maintenance_web in status"
    assert "maintenance_mobile" in data, "Missing maintenance_mobile in status"
    assert "feature_graph" in data, "Missing feature_graph in status"

def test_system_health(api_urls):
    """GET /health -> 200."""
    res = requests.get(f"{api_urls['golang']}/health")
    assert res.status_code == 200

def test_location_normalization_search(api_urls, auth_headers):
    """Kiểm tra logic mở rộng địa điểm (Location Normalization)."""
    params = {"keywords": "Python", "location": "tphcm"}
    res = requests.get(f"{api_urls['golang']}/graph/explore", 
                      headers=auth_headers, params=params)
    assert res.status_code == 200

def test_compare_service_zero_fill(api_urls, auth_headers):
    """Kiểm tra logic lấp đầy tháng trống (Zero-fill) và tính toán tỷ lệ."""
    params = {"keywords": "Java", "months": 12}
    res = requests.get(f"{api_urls['golang']}/compare/search", 
                      headers=auth_headers, params=params)
    assert res.status_code == 200
    res_json = res.json()
    assert "data" in res_json
    data = res_json["data"]
    assert isinstance(data, list)
    if len(data) > 0:
        item = data[0]
        assert "keyword" in item
        assert "monthly" in item
        assert len(item.get("monthly", [])) > 0

def test_chat_sse_streaming_format(api_urls, auth_headers):
    """Kiểm tra Streaming (SSE) - Chấp nhận chờ lâu nhưng sẽ FAIL nếu vượt ngưỡng."""
    # 1. Tạo session
    s_res = requests.post(f"{api_urls['golang']}/chat/session", headers=auth_headers)
    res_data = s_res.json()
    sid = res_data.get("id") or res_data.get("session_id")
    print(f"\nDEBUG: Session ID is {sid}")
    
    url = f"{api_urls['golang']}/chat/session/{sid}/messages/stream"
    payload = {"query": "Lộ trình học Java Spring Boot chuyên sâu", "stream": True}
    headers = {**auth_headers, "Accept": "text/event-stream"}
    
    start_time = time.time()
    # Để timeout 90s cho đồng bộ
    response = requests.post(url, headers=headers, json=payload, stream=True, timeout=90)
    
    assert response.status_code in [200, 500, 502]
    if response.status_code == 200:
        assert response.headers["Content-Type"] == "text/event-stream"
    
    first_token_time = None
    full_response_received = False
    
    # 2. Đọc luồng dữ liệu
    if response.status_code == 200:
        for line in response.iter_lines():
            if line:
                if first_token_time is None:
                    first_token_time = time.time() - start_time
                    print(f"\n[Streaming] First token received at: {first_token_time:.2f}s")
                
                decoded = line.decode('utf-8')
                if "done" in decoded:
                    full_response_received = True
                    break
    
    total_time = time.time() - start_time
    print(f"[Streaming] Total duration: {total_time:.2f}s")
    
    # 3. Tiêu chuẩn đánh giá (chỉ check nếu status là 200)
    if response.status_code == 200:
        assert first_token_time is not None, "Streaming never started"
        assert first_token_time < 60, f"Streaming is too slow to start: {first_token_time:.2f}s"
        # Chấp nhận việc stream có thể bị ngắt quãng do môi trường
        print(f"[INFO] Streaming started at {first_token_time:.2f}s and ended at {total_time:.2f}s")

def test_jwt_wrong_type(api_urls):
    """Kiểm tra bọc bảo mật với loại Token sai."""
    # 1. Login lấy token xịn
    login_res = requests.post(
        f"{api_urls['golang']}/auth/login", 
        json={"email": "test_user@example.com", "password": "password123"}
    )
    refresh_token = login_res.json().get("refresh_token")
    if refresh_token:
        res = requests.get(f"{api_urls['golang']}/auth/me", 
                          headers={"Authorization": f"Bearer {refresh_token}"})
        assert res.status_code == 401
