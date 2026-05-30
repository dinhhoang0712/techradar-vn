import pytest
import requests
import time

pytestmark = pytest.mark.skip(reason="Yêu cầu quyền Admin để thay đổi settings hệ thống")

def test_maintenance_mode_behavior(api_urls, real_admin_headers, auth_headers):
    """Kiểm tra logic bảo trì (Maintenance Mode)."""
    # 1. BẬT bảo trì web qua Admin API
    requests.put(f"{api_urls['golang']}/admin/settings/maintenance_web", 
                 json={"value": "true"}, 
                 headers=real_admin_headers)
    
    # Đợi một chút để cache hoặc DB cập nhật (nếu có)
    time.sleep(1)
    
    try:
        # 2. Gọi một API bất kỳ bằng user thường -> mong đợi 503
        res = requests.get(f"{api_urls['golang']}/auth/me", headers=auth_headers)
        assert res.status_code == 503
        
    finally:
        # 3. TẮT bảo trì để không làm hỏng các bài test khác
        requests.put(f"{api_urls['golang']}/admin/settings/maintenance_web", 
                     json={"value": "false"}, 
                     headers=real_admin_headers)

def test_feature_flag_graph_behavior(api_urls, real_admin_headers, auth_headers):
    """Kiểm tra logic Feature Flag cho Graph."""
    # 1. TẮT tính năng Graph
    requests.put(f"{api_urls['golang']}/admin/settings/feature_graph", 
                 json={"value": "false"}, 
                 headers=real_admin_headers)
    
    time.sleep(1)
    
    try:
        # 2. Truy cập API Graph -> mong đợi 403 (Forbidden)
        res = requests.get(f"{api_urls['golang']}/graph/explore", 
                          params={"keywords": "Java"}, headers=auth_headers)
        assert res.status_code == 403
        
    finally:
        # 3. BẬT lại tính năng Graph
        requests.put(f"{api_urls['golang']}/admin/settings/feature_graph", 
                     json={"value": "true"}, 
                     headers=real_admin_headers)
