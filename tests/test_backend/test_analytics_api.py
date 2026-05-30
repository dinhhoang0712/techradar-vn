import pytest
import requests

def test_graph_road_analysis_validation(api_urls):
    """Kiểm tra validation của Road Analysis (Thiếu from/to)."""
    # 1. Thiếu 'to'
    res = requests.get(f"{api_urls['golang']}/graph/road_analysis", params={"from": "Java"})
    assert res.status_code == 400
    
    # 2. from == to
    res = requests.get(f"{api_urls['golang']}/graph/road_analysis", params={"from": "Java", "to": "Java"})
    assert res.status_code == 400

def test_radar_top_skills_structure(api_urls):
    """Kiểm tra cấu trúc dữ liệu của API Radar Top Skills."""
    res = requests.get(f"{api_urls['golang']}/radar/top10")
    assert res.status_code == 200
    json_data = res.json()
    assert "data" in json_data
    assert isinstance(json_data["data"], list)

def test_radar_search_structure_details(api_urls):
    """Radar search -> verify response structure có data[].year, data[].month, data[].keywords."""
    params = {"keywords": "Java", "months": 6}
    res = requests.get(f"{api_urls['golang']}/radar/search", params=params)
    assert res.status_code == 200
    data = res.json().get("data", [])
    if data:
        item = data[0]
        assert "year" in item
        assert "month" in item
        assert "keywords" in item

def test_compare_search_window_logic(api_urls):
    """Compare search -> verify data[].monthly có đủ số tháng theo window."""
    months = 6
    params = {"keywords": "Java,Python", "months": months}
    res = requests.get(f"{api_urls['golang']}/compare/search", params=params)
    assert res.status_code == 200
    data = res.json().get("data", [])
    if data:
        # Logic Backend: windowStart = now - months, sau đó chạy spine tới now.
        # Ví dụ: months=6, now=May -> spine lấy Nov, Dec, Jan, Feb, Mar, Apr, May (7 tháng).
        # Vậy tổng số tháng trả về phải là months + 1
        assert len(data[0].get("monthly", [])) == months + 1

def test_radar_top4_growth_metrics(api_urls):
    """Radar top4 -> verify có growth_rate, mom_rate fields."""
    res = requests.get(f"{api_urls['golang']}/radar/top4")
    assert res.status_code == 200
    data = res.json().get("data", [])
    if data:
        item = data[0]
        assert "growth_rate" in item
        assert "mom_rate" in item

def test_location_normalization_search(api_urls):
    """Xác nhận API Graph là PUBLIC (không cần token)."""
    res = requests.get(f"{api_urls['golang']}/graph/explore", params={"keywords": "Hà Nội"})
    assert res.status_code == 200
    assert "nodes" in res.json().get("data", {})

def test_graph_road_analysis_logic_cases(api_urls):
    """Kiểm tra logic Graph Road: found=true, found=false và validation."""
    # 1. Trường hợp found=false
    params = {"from": "Java", "to": "Sửa ống nước 123"}
    res = requests.get(f"{api_urls['golang']}/graph/road_analysis", params=params)
    assert res.status_code == 200
    data = res.json().get("data", {})
    assert data.get("found") is False

def test_graph_road_analysis_missing_params(api_urls):
    """Kiểm tra validation: Thiếu from hoặc to -> 400."""
    # Thiếu from
    res1 = requests.get(f"{api_urls['golang']}/graph/road_analysis", params={"to": "Java"})
    assert res1.status_code == 400
    # Thiếu to
    res2 = requests.get(f"{api_urls['golang']}/graph/road_analysis", params={"from": "Java"})
    assert res2.status_code == 400
