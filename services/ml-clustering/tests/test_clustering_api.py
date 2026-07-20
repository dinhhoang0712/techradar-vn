"""Kiểm tra các dịch vụ API cung cấp thông tin phân cụm công nghệ."""
import pytest
import sys
import importlib.util
from pathlib import Path
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient

ML_ROOT = Path(__file__).resolve().parents[1]

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
    store.tech_membership_probability = {"t1": 0.92}
    store.tech_outlier_score = {"t2": 0.15}
    store.get_near_clusters = lambda tid: (
        [{"cluster_id": 1, "score": 0.71, "label": "Data", "label_en": "Data"}] if tid == "t1" else []
    )
    # Mặc định: không có ứng viên provisional nào (giống store thật khi không match đủ giống).
    # Test riêng cho nhánh provisional override lambda này.
    store.find_nearest_known_tech = lambda n: None

    def _save_cluster_override(cid, *, label=None, label_en=None, description=None, domain=None, actor=None):
        if cid not in store.cluster_labels:
            raise KeyError(f"Cluster {cid} không tồn tại")
        fields = {
            k: v for k, v in {
                "label": label, "label_en": label_en,
                "description": description, "domain": domain,
            }.items() if v is not None
        }
        if not fields:
            raise ValueError("Cần ít nhất 1 trường để cập nhật")
        updated = {
            **store.cluster_labels[cid], **fields,
            "overridden": True, "overridden_by": actor, "overridden_at": "2026-07-18T00:00:00+00:00",
        }
        store.cluster_labels[cid] = updated
        return updated

    store.save_cluster_override = _save_cluster_override
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


def test_api_system_health_returns_503_when_data_not_available(client, mock_store):
    """Store rỗng (artifact chưa load được) phải trả 503, không phải 200 giả."""
    # Giống _init_empty() thật (app/store.py) khi artifact chưa load được.
    mock_store.data_available = False
    mock_store.tag = "none"
    mock_store.requested_tag = "latest"
    mock_store.source = "local"
    mock_store.tech_to_cluster = {}
    mock_store.cluster_labels = {}
    resp = client.get("/health")
    assert resp.status_code == 503
    body = resp.json()
    assert body["status"] == "degraded"
    assert body["data_available"] is False

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


def test_api_technology_unknown_with_no_provisional_match_returns_404(client):
    """Không khớp gì cả (kể cả provisional) → 404, không đoán liều."""
    resp = client.get("/tech/CompletelyUnknownTech/cluster")
    assert resp.status_code == 404


def test_api_technology_provisional_match_returns_200_with_flag(client, mock_store):
    """Tech chưa có trong snapshot nhưng khớp đủ giống 1 tech đã biết (difflib) →
    vẫn trả 200 kèm cluster tạm, found=False nhưng provisional=True."""
    mock_store.find_nearest_known_tech = lambda n: ("Python", 0, 0.86)

    resp = client.get("/tech/Pythonn/cluster")

    assert resp.status_code == 200
    body = resp.json()
    assert body["found"] is False
    assert body["provisional"] is True
    assert body["cluster_id"] == 0
    assert body["label"] == "Backend"
    assert body["matched_via"] == "Python"
    assert body["match_score"] == 0.86


def test_api_batch_predict_counts_provisional_separately_from_not_found(client, mock_store):
    """n_provisional phải tách biệt khỏi n_found/n_not_found trong batch."""
    def _fake_provisional(name):
        return ("Python", 0, 0.8) if name == "Pythonn" else None
    mock_store.find_nearest_known_tech = _fake_provisional

    batch = client.post(
        "/predict/batch", json={"tech_names": ["Python", "Pythonn", "TotallyUnknown"]}
    ).json()

    assert batch["n_found"] == 1
    assert batch["n_provisional"] == 1
    assert batch["n_not_found"] == 1


def test_api_technology_soft_clustering_and_near_clusters(client):
    """Kiểm tra membership_probability/outlier_score/near_clusters được expose
    đúng qua /tech/{name}/cluster (trước đây near_clusters chỉ nằm trong MLflow,
    không route nào đọc lại)."""
    resp = client.get("/tech/Python/cluster").json()
    assert resp["membership_probability"] == pytest.approx(0.92)
    assert resp["near_clusters"][0]["cluster_id"] == 1

    resp_noise = client.get("/tech/Noise/cluster").json()
    assert resp_noise["outlier_score"] == pytest.approx(0.15)
    assert resp_noise["near_clusters"] == []


def test_api_cluster_label_override_updates_in_place_and_flags_overridden(client):
    """Admin ghi đè nhãn AI-generated: cập nhật ngay lập tức, không cần chờ reload."""
    resp = client.put("/clusters/0/label", json={"label": "Django Ecosystem"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["label"] == "Django Ecosystem"
    assert body["label_en"] == "BE"  # trường không truyền vào giữ nguyên giá trị cũ
    assert body["overridden"] is True

    # GET phải phản ánh override ngay, không cần restart/reload.
    detail = client.get("/clusters/0").json()
    assert detail["label"] == "Django Ecosystem"
    assert detail["overridden"] is True


def test_api_cluster_label_override_records_actor_from_header(client):
    resp = client.put("/clusters/0/label", json={"domain": "Backend"}, headers={"X-Actor": "admin-42"})
    assert resp.status_code == 200
    assert resp.json()["overridden_by"] == "admin-42"


def test_api_cluster_label_override_rejects_unknown_cluster(client):
    resp = client.put("/clusters/999/label", json={"label": "X"})
    assert resp.status_code == 404


def test_api_cluster_label_override_rejects_empty_body(client):
    resp = client.put("/clusters/0/label", json={})
    assert resp.status_code == 400
