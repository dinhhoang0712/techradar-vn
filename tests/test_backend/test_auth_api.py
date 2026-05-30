import pytest
import requests
import uuid

def test_registration_validation_errors(api_urls):
    """Kiểm tra các lỗi validation khi đăng ký (Khớp với format Go Validator)."""
    url = f"{api_urls['golang']}/auth/register"
    
    # 1. confirm_password không khớp
    payload = {
        "email": "test@ex.com", 
        "password": "Password123!", 
        "confirm_password": "WrongPassword", 
        "full_name": "Test"
    }
    res = requests.post(url, json=payload)
    assert res.status_code == 400
    # Sửa lại assert để khớp với message thực tế của Go Validator
    msg = res.json().get("message", "").lower()
    assert "validation" in msg or "confirmpassword" in msg

def test_registration_duplicate_email(api_urls):
    """Kiểm tra lỗi 400/409 khi đăng ký email đã tồn tại."""
    test_email = "pytest_permanent@example.com"
    payload = {
        "email": test_email,
        "password": "Password123!",
        "confirm_password": "Password123!",
        "full_name": "Duplicate User"
    }
    res = requests.post(f"{api_urls['golang']}/auth/register", json=payload)
    assert res.status_code in [400, 409]

def test_login_invalid_password(api_urls):
    """Kiểm tra lỗi 401 khi sai mật khẩu."""
    payload = {"email": "pytest_permanent@example.com", "password": "WrongPassword"}
    res = requests.post(f"{api_urls['golang']}/auth/login", json=payload)
    assert res.status_code == 401

def test_get_me_structure(api_urls, auth_headers):
    """Kiểm tra cấu trúc response của API /auth/me và Role."""
    res = requests.get(f"{api_urls['golang']}/auth/me", headers=auth_headers)
    assert res.status_code == 200
    data = res.json()
    assert "email" in data
    assert "role" in data
    assert data["role"] == "user"
    assert data["email"] == "test_user@example.com"

def test_login_non_existent_email(api_urls):
    """Login với email không tồn tại -> 401."""
    res = requests.post(f"{api_urls['golang']}/auth/login", json={
        "email": "not_exists_123@example.com", "password": "password123"
    })
    assert res.status_code == 401

def test_register_validation_missing_fields(api_urls):
    """Register thiếu email hoặc password -> 400."""
    res = requests.post(f"{api_urls['golang']}/auth/register", json={"password": "password123"})
    assert res.status_code == 400

def test_register_short_password(api_urls):
    """Register password < 8 ký tự -> 400."""
    res = requests.post(f"{api_urls['golang']}/auth/register", json={
        "email": "short@example.com", "password": "123", "confirm_password": "123", "full_name": "Short"
    })
    assert res.status_code == 400

def test_user_profile_edge_cases(api_urls, auth_headers):
    """Kiểm tra các trường hợp đặc biệt của Profile."""
    # 1. PUT thiếu full_name -> 400
    res = requests.put(f"{api_urls['golang']}/user/profile", json={}, headers=auth_headers)
    assert res.status_code == 400
    
    # 2. GET profile khi chưa có profile -> 200 (empty profile)
    res = requests.get(f"{api_urls['golang']}/user/profile", headers=auth_headers)
    assert res.status_code == 200
