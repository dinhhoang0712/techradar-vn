"""Kiểm tra champion gate + thứ tự stage mới trong _run_pipeline (app/routes_pipeline.py):
sau TRAIN, model không tốt hơn champion hiện tại thì bỏ qua label/publish/writeback (và trả
params.yaml về tag cũ) thay vì luôn chạy hết như trước; khi có deploy, publish (stage_06)
phải chạy TRƯỚC writeback (stage_05) — không phải sau — để Neo4j không bị đụng nếu publish lỗi."""

import importlib
from unittest.mock import MagicMock

import pytest

from app import routes_pipeline


class _FakeRedis:
    def publish(self, channel, message):
        pass


def _ok_result():
    return MagicMock(returncode=0, stderr="")


@pytest.fixture(autouse=True)
def _isolate(monkeypatch):
    """Không đụng Redis/params.yaml thật, không phụ thuộc giờ hệ thống giữa các test."""
    monkeypatch.setattr(routes_pipeline, "_redis_client", _FakeRedis())
    monkeypatch.setattr(routes_pipeline, "_generate_snapshot_tag", lambda: "2026-07-27-1200")
    monkeypatch.setattr(
        routes_pipeline, "load_params", lambda *a, **k: MagicMock(mlflow=MagicMock(tracking_uri="http://mlflow"))
    )
    monkeypatch.setattr(routes_pipeline, "_resolve_best_run_id", lambda params: "run-best-1")
    yield


def test_skips_deploy_stages_and_reverts_tag_when_not_promoted(monkeypatch):
    bump_mock = MagicMock(return_value="2026-07-20-0900")
    monkeypatch.setattr(routes_pipeline, "_bump_snapshot_tag", bump_mock)
    monkeypatch.setattr("src.tracking.mlflow_logger.is_run_promoted_to_champion", lambda run_id, uri: False)

    run_calls: list[list[str]] = []
    monkeypatch.setattr("subprocess.run", lambda cmd, **kwargs: (run_calls.append(cmd), _ok_result())[1])

    routes_pipeline._run_pipeline(force=False)

    ran_stages = [c[2] for c in run_calls]
    assert ran_stages == [
        "pipelines.stage_01_extract",
        "pipelines.stage_02_features",
        "pipelines.stage_03_train",
    ]

    # Bump lần 1 sang tag mới, lần 2 TRẢ VỀ tag cũ — nếu không, params.yaml sẽ mãi trỏ tới 1
    # tag thiếu label/writeback, service tự làm hỏng chính nó ở lần restart/reset_store() sau.
    assert bump_mock.call_args_list[0].args[0] == "2026-07-27-1200"
    assert bump_mock.call_args_list[1].args[0] == "2026-07-20-0900"

    with routes_pipeline._LOCK:
        assert routes_pipeline._state["status"] == "success"
        assert routes_pipeline._state["deployed"] is False
        assert "không tốt hơn champion" in routes_pipeline._state["note"]


def test_runs_publish_before_writeback_when_promoted(monkeypatch):
    monkeypatch.setattr(routes_pipeline, "_bump_snapshot_tag", MagicMock(return_value="2026-07-20-0900"))
    monkeypatch.setattr("src.tracking.mlflow_logger.is_run_promoted_to_champion", lambda run_id, uri: True)
    # Resolve app.store tại execution time (không phải collection time) — test_pipeline_runs.py
    # force-reimport app.store lúc collection, có thể khiến 1 tên module bind sớm bị stale.
    store_mod = importlib.import_module("app.store")
    monkeypatch.setattr(store_mod, "reset_store", lambda: None)
    monkeypatch.setattr(store_mod, "get_store", lambda: MagicMock())

    run_calls: list[list[str]] = []
    monkeypatch.setattr("subprocess.run", lambda cmd, **kwargs: (run_calls.append(cmd), _ok_result())[1])

    routes_pipeline._run_pipeline(force=False)

    ran_stages = [c[2] for c in run_calls]
    assert ran_stages == [
        "pipelines.stage_01_extract",
        "pipelines.stage_02_features",
        "pipelines.stage_03_train",
        "pipelines.stage_04_label",
        "pipelines.stage_06_publish",
        "pipelines.stage_05_writeback",
    ]

    publish_cmd = run_calls[4]
    assert "--run-id" in publish_cmd
    assert "run-best-1" in publish_cmd

    writeback_cmd = run_calls[5]
    assert "--run-id" in writeback_cmd
    assert "run-best-1" in writeback_cmd
    assert "--no-dry-run" in writeback_cmd

    with routes_pipeline._LOCK:
        assert routes_pipeline._state["status"] == "success"
        assert routes_pipeline._state["deployed"] is True


def test_force_bypasses_promotion_check(monkeypatch):
    monkeypatch.setattr(routes_pipeline, "_bump_snapshot_tag", MagicMock(return_value="2026-07-20-0900"))
    promoted_mock = MagicMock()
    monkeypatch.setattr("src.tracking.mlflow_logger.is_run_promoted_to_champion", promoted_mock)
    store_mod = importlib.import_module("app.store")
    monkeypatch.setattr(store_mod, "reset_store", lambda: None)
    monkeypatch.setattr(store_mod, "get_store", lambda: MagicMock())
    monkeypatch.setattr("subprocess.run", lambda cmd, **kwargs: _ok_result())

    routes_pipeline._run_pipeline(force=True)

    promoted_mock.assert_not_called()
    with routes_pipeline._LOCK:
        assert routes_pipeline._state["deployed"] is True


def test_deploy_stage_failure_marks_pipeline_failed(monkeypatch):
    """publish thất bại (exit 1) — pipeline phải báo failed, không được coi writeback tiếp theo
    là 1 stage độc lập bỏ qua được lỗi trước đó."""
    monkeypatch.setattr(routes_pipeline, "_bump_snapshot_tag", MagicMock(return_value="2026-07-20-0900"))
    monkeypatch.setattr("src.tracking.mlflow_logger.is_run_promoted_to_champion", lambda run_id, uri: True)

    def fake_run(cmd, **kwargs):
        if "pipelines.stage_06_publish" in cmd:
            return MagicMock(returncode=1, stderr="upload failed")
        return _ok_result()

    monkeypatch.setattr("subprocess.run", fake_run)

    routes_pipeline._run_pipeline(force=False)

    with routes_pipeline._LOCK:
        assert routes_pipeline._state["status"] == "failed"
        assert routes_pipeline._state["deployed"] is False
        assert "stage_06_publish" in routes_pipeline._state["error"]
