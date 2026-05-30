import pytest
import requests
import os
from dotenv import load_dotenv

load_dotenv()

GOLANG_API_URL = "http://127.0.0.1:8080/api/v1"
PYTHON_AI_URL = "http://127.0.0.1:8000"

@pytest.fixture(scope="session")
def api_urls():
    return {
        "golang": GOLANG_API_URL,
        "python": PYTHON_AI_URL
    }

@pytest.fixture(scope="session")
def auth_headers(api_urls):
    """Headers cho người dùng thường."""
    email = "test_user@example.com"
    password = "password123"
    requests.post(f"{api_urls['golang']}/auth/register", json={
        "email": email, "password": password, "confirm_password": password, "full_name": "Test User"
    })
    res = requests.post(f"{api_urls['golang']}/auth/login", json={
        "email": email, "password": password
    })
    token = res.json().get("access_token", "")
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

@pytest.fixture(scope="session")
def admin_headers(auth_headers):
    """Dùng user thường để test bảo mật (mong đợi 403 Forbidden)."""
    return auth_headers

@pytest.fixture(scope="session")
def real_admin_headers(api_urls):
    """Headers cho Admin thật. Cần có account admin trong DB."""
    # Giả sử ta dùng account admin mặc định hoặc một account đặc biệt
    email = "admin@techpulse.vn"
    password = "adminpassword123"
    res = requests.post(f"{api_urls['golang']}/auth/login", json={
        "email": email, "password": password
    })
    token = res.json().get("access_token", "")
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
