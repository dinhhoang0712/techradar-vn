"""Kiểm tra GET /pipeline/runs — đọc lịch sử các lần train (is_best=true) từ MLflow,
phục vụ biểu đồ chất lượng model theo thời gian ở admin (phát hiện model bị suy giảm
qua các lần retrain thay vì chỉ nhìn thấy trạng thái "thành công/thất bại")."""
import importlib.util
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

ML_ROOT = Path(__file__).resolve().parents[1]


def _force_import(module_name: str, file_path: Path):
    if module_name in sys.modules:
        del sys.modules[module_name]
    spec = importlib.util.spec_from_file_location(module_name, str(file_path))
    mod = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = mod
    spec.loader.exec_module(mod)
    return mod


# Khởi tạo Mock App — tránh AppStore() thật cố đọc MinIO/local parquet lúc lifespan startup.
_force_import("app", ML_ROOT / "app" / "__init__.py")
_store_mod = _force_import("app.store", ML_ROOT / "app" / "store.py")
_store_mod.get_store = lambda: MagicMock()
_main_mod = _force_import("app.main", ML_ROOT / "app" / "main.py")
_app = _main_mod.app


@pytest.fixture
def client():
    return TestClient(_app)


def _mock_run(run_id, algorithm, metrics, start_time, end_time, parent_run_id="run-parent-1"):
    info = MagicMock(run_id=run_id, start_time=start_time, end_time=end_time, status="FINISHED")
    data = MagicMock(
        params={"algorithm": algorithm},
        tags={"mlflow.parentRunId": parent_run_id} if parent_run_id else {},
        metrics=metrics,
    )
    return MagicMock(info=info, data=data)


def test_pipeline_runs_maps_mlflow_history_ordered_by_recency(client):
    best_run = _mock_run(
        "run-best-1", "hdbscan",
        {"silhouette": 0.42, "dbcv": 0.31},
        1_700_000_000_000, 1_700_000_060_000,
    )
    parent_run = MagicMock(data=MagicMock(tags={"snapshot": "2026-07-18-0900"}))

    fake_client = MagicMock()
    fake_client.get_experiment_by_name.return_value = MagicMock(experiment_id="exp-1")
    fake_client.search_runs.return_value = [best_run]
    fake_client.get_run.return_value = parent_run

    with patch("mlflow.tracking.MlflowClient", return_value=fake_client):
        resp = client.get("/pipeline/runs")

    assert resp.status_code == 200
    body = resp.json()
    assert len(body) == 1
    assert body[0]["run_id"] == "run-best-1"
    assert body[0]["snapshot_tag"] == "2026-07-18-0900"
    assert body[0]["algorithm"] == "hdbscan"
    assert body[0]["metrics"]["silhouette"] == pytest.approx(0.42)
    assert body[0]["duration_s"] == pytest.approx(60.0)

    fake_client.search_runs.assert_called_once()
    assert fake_client.search_runs.call_args.kwargs["filter_string"] == "tags.is_best = 'true'"


def test_pipeline_runs_returns_empty_list_when_experiment_missing(client):
    """Chưa từng train lần nào (experiment chưa tồn tại) → trả về [] thay vì lỗi."""
    fake_client = MagicMock()
    fake_client.get_experiment_by_name.return_value = None

    with patch("mlflow.tracking.MlflowClient", return_value=fake_client):
        resp = client.get("/pipeline/runs")

    assert resp.status_code == 200
    assert resp.json() == []


def test_pipeline_runs_tolerates_missing_parent_run(client):
    """Nếu không resolve được parent run (đã bị xoá/lỗi mạng) thì vẫn trả run, chỉ thiếu snapshot_tag."""
    best_run = _mock_run(
        "run-best-2", "hdbscan", {"silhouette": 0.5}, 1_700_000_000_000, None, parent_run_id=None,
    )
    fake_client = MagicMock()
    fake_client.get_experiment_by_name.return_value = MagicMock(experiment_id="exp-1")
    fake_client.search_runs.return_value = [best_run]

    with patch("mlflow.tracking.MlflowClient", return_value=fake_client):
        resp = client.get("/pipeline/runs")

    assert resp.status_code == 200
    body = resp.json()
    assert body[0]["snapshot_tag"] is None
    assert body[0]["finished_at"] is None
    assert body[0]["duration_s"] is None
