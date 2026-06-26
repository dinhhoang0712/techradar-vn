"""Kiểm tra các dịch vụ API cung cấp thông tin phân cụm công nghệ."""
import pytest
import sys
import importlib.util
from pathlib import Path
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient

ML_ROOT = Path(__file__).resolve().parents[2] / "services" / "ml-clustering"

def _force_import(module_name: str, file_path: Path):
    if module_name in sys.modules: del sys.modules[module_name]
    spec = importlib.util.spec_from_file_location(module_name, str(file_path))
    mod = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = mod
    spec.loader.exec_module(mod)
    return mod

# Khởi tạo Mock App
_force_import("app", ML_ROOT / "app" / "__init__.py")
_store_mod = _force_import("app.store", ML_ROOT / "app" / "store.py")
_dummy_store = MagicMock()
_store_mod.get_store = lambda: _dummy_store
_main_mod = _force_import("app.main", ML_ROOT / "app" / "main.py")
_app = _main_mod.app

@pytest.fixture
def mock_store():
    store = MagicMock()
    store.tag = "2026-05-12"
    store.tech_to_cluster = {"t1": 0, "t2": -1}
    store.cluster_labels = {0: {"label": "Backend", "label_en": "BE", "domain": "IT", "confidence": 0.9, "is_coherent": True, "description": "D"}}
    store.cluster_to_techs = {0: ["Python"], -1: ["Noise"]}
    store.id_to_name = {"t1": "Python", "t2": "Noise"}
    store.name_lower_to_id = {"python": "t1", "noise": "t2"}
    store.lookup_tech = lambda n: (store.name_lower_to_id.get(n.lower()), store.tech_to_cluster.get(store.name_lower_to_id.get(n.lower())))
    store.get_cluster_label = lambda cid: store.cluster_labels.get(cid)
    return store

@pytest.fixture
def client(mock_store):
    orig = _main_mod.get_store
    _main_mod.get_store = lambda: mock_store
    try: yield TestClient(_app)
    finally: _main_mod.get_store = orig

def test_api_system_health_status(client):
    """Kiểm tra trạng thái hoạt động của hệ thống và phiên bản dữ liệu."""
    resp = client.get("/health").json()
    assert resp["status"] == "ok"
    assert resp["snapshot_tag"] == "2026-05-12"

def test_api_cluster_inventory_retrieval(client):
    """Kiểm tra việc truy xuất danh mục các nhóm công nghệ và thông tin chi tiết."""
    clusters = client.get("/clusters").json()
    assert clusters[0]["label"] == "Backend"
    detail = client.get("/clusters/0").json()
    assert "Python" in detail["members"]

def test_api_technology_prediction_service(client):
    """Kiểm tra dịch vụ dự đoán và tra cứu nhóm lĩnh vực cho các công nghệ."""
    resp = client.get("/tech/Python/cluster").json()
    assert resp["cluster_id"] == 0
    batch = client.post("/predict/batch", json={"tech_names": ["Python"]}).json()
    assert batch["n_found"] == 1
