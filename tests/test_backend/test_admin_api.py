import pytest
import requests

pytestmark = pytest.mark.skip(reason="Chưa có tài khoản Admin thật trong Database để chạy Happy Path")

def test_admin_list_users_pagination(api_urls, real_admin_headers):
    """Kiểm tra phân trang danh sách người dùng (Sửa lỗi logic batch cũ)."""
    res = requests.get(f"{api_urls['golang']}/admin/users", params={"page": 1, "limit": 10}, headers=real_admin_headers)
    assert res.status_code == 200
    json_data = res.json()
    # SỬA LỖI: API trả về {"data": [...]}, không phải list trực tiếp
    assert "data" in json_data
    assert isinstance(json_data["data"], list)

def test_admin_dashboard_user_count(api_urls, real_admin_headers):
    """Kiểm tra API dashboard trả về số nguyên."""
    res = requests.get(f"{api_urls['golang']}/admin/dashboard/user-count", headers=real_admin_headers)
    assert res.status_code == 200
    json_data = res.json()
    assert "data" in json_data
    assert isinstance(json_data["data"], int)

def test_admin_get_settings_structure(api_urls, real_admin_headers):
    """Kiểm tra cấu trúc response của settings."""
    res = requests.get(f"{api_urls['golang']}/admin/settings", headers=real_admin_headers)
    assert res.status_code == 200
    json_data = res.json()
    # Verify structure {"data": {"maintenance_web": ..., ...}}
    assert "data" in json_data
    assert "maintenance_web" in json_data["data"]
    assert "maintenance_mobile" in json_data["data"]
    assert "feature_graph" in json_data["data"]

def test_admin_update_setting_invalid_value(api_urls, real_admin_headers):
    """Update setting với value không hợp lệ (không phải 'true'/'false') -> 400."""
    # SỬA LỖI: API thực tế là /admin/settings/{key}
    res = requests.put(
        f"{api_urls['golang']}/admin/settings/maintenance_web", 
        json={"value": "not-a-boolean"}, 
        headers=real_admin_headers
    )
    assert res.status_code == 400

def test_admin_create_user_duplicate_email(api_urls, real_admin_headers):
    """Tạo user trùng email -> 409."""
    # Giả sử email này đã tồn tại
    payload = {
        "email": "test_user@example.com",
        "password": "password123",
        "full_name": "Duplicate User"
    }
    res = requests.post(f"{api_urls['golang']}/admin/users", json=payload, headers=real_admin_headers)
    assert res.status_code == 409

def test_admin_update_non_existent_user(api_urls, real_admin_headers):
    """Update user không tồn tại -> 404."""
    res = requests.put(f"{api_urls['golang']}/admin/users/999999", json={"full_name": "New Name"}, headers=real_admin_headers)
    assert res.status_code == 404

def test_admin_delete_non_existent_user(api_urls, real_admin_headers):
    """Delete user không tồn tại -> 404."""
    res = requests.delete(f"{api_urls['golang']}/admin/users/999999", headers=real_admin_headers)
    assert res.status_code == 404
