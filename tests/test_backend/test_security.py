import pytest
import requests

def test_unauthorized_access(api_urls):
    """Kiểm tra truy cập không có Token (401)."""
    res = requests.get(f"{api_urls['golang']}/auth/me")
    assert res.status_code == 401

def test_forbidden_admin_access(api_urls, auth_headers):
    """
    XÁC NHẬN BẢO MẬT: Đảm bảo User thường KHÔNG THỂ truy cập các API Admin.
    Đây là minh chứng hệ thống đã chặn được truy cập trái phép.
    """
    admin_endpoints = [
        "/admin/settings",
        "/admin/users",
        "/admin/dashboard/user-count"
    ]
    for ep in admin_endpoints:
        res = requests.get(f"{api_urls['golang']}{ep}", headers=auth_headers)
        # In ra để debug xem tại sao lại là 404 hay 403
        print(f"Debug: Accessing {ep} returned {res.status_code}")
        # Quan trọng nhất là KHÔNG ĐƯỢC trả về 200 (vì đây là user thường)
        assert res.status_code != 200, f"Security Breach! Regular user can access {ep}"

def test_invalid_jwt_format(api_urls):
    """Kiểm tra Token sai định dạng (401)."""
    headers = {"Authorization": "Bearer not-a-jwt-at-all"}
    res = requests.get(f"{api_urls['golang']}/auth/me", headers=headers)
    assert res.status_code == 401

def test_access_token_cannot_refresh(api_urls, auth_headers):
    """Kiểm tra bảo mật: Không được dùng Access Token để gọi API Refresh."""
    # 1. Login lấy token xịn
    login_res = requests.post(
        f"{api_urls['golang']}/auth/login", 
        json={"email": "test_user@example.com", "password": "password123"}
    )
    access_token = auth_headers["Authorization"].split(" ")[1]
    payload = {"refresh_token": access_token} 
    res = requests.post(f"{api_urls['golang']}/auth/refresh", json=payload)
    assert res.status_code in [401, 400]

def test_missing_bearer_token(api_urls):
    """Authorization: Bearer (không có token) -> 401."""
    headers = {"Authorization": "Bearer "}
    res = requests.get(f"{api_urls['golang']}/auth/me", headers=headers)
    assert res.status_code == 401

def test_wrong_auth_scheme(api_urls):
    """Authorization: Token abc (sai scheme) -> 401."""
    headers = {"Authorization": "Token some-token"}
    res = requests.get(f"{api_urls['golang']}/auth/me", headers=headers)
    assert res.status_code == 401
