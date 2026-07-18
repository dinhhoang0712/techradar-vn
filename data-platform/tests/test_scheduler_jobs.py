from __future__ import annotations

from unittest.mock import MagicMock, patch

import requests

from config import Settings
from scheduler.jobs import job_retrain_clustering


def _settings(**overrides) -> Settings:
    defaults = {"clustering_retrain_poll_interval_s": 0, "clustering_retrain_max_wait_s": 1}
    defaults.update(overrides)
    return Settings(**defaults)


def _fake_pg_conn():
    conn = MagicMock()
    return conn


@patch("scheduler.jobs.time.sleep")
@patch("common.db.log_pipeline_run")
@patch("common.db.get_pg_conn")
@patch("scheduler.jobs.requests.get")
@patch("scheduler.jobs.requests.post")
def test_retrain_clustering_polls_until_success_and_logs_success(
    mock_post, mock_get, mock_get_pg_conn, mock_log_run, mock_sleep,
):
    mock_get_pg_conn.return_value = _fake_pg_conn()
    mock_log_run.side_effect = [1, None]  # đầu ("running") trả run_id=1, các lần sau không cần giá trị
    mock_post.return_value = MagicMock(status_code=200, json=lambda: {"message": "started"})
    mock_post.return_value.raise_for_status = lambda: None
    mock_get.return_value = MagicMock(json=lambda: {"status": "success", "duration_s": 42})

    job_retrain_clustering(_settings())

    assert mock_log_run.call_count == 2
    final_call = mock_log_run.call_args_list[-1]
    assert final_call.args[1] == "retrain_clustering"
    assert final_call.args[2] == "success"
    assert final_call.kwargs["run_id"] == 1


@patch("scheduler.jobs.time.sleep")
@patch("common.db.log_pipeline_run")
@patch("common.db.get_pg_conn")
@patch("scheduler.jobs.requests.get")
@patch("scheduler.jobs.requests.post")
def test_retrain_clustering_polls_until_failed_and_logs_failed_with_error(
    mock_post, mock_get, mock_get_pg_conn, mock_log_run, mock_sleep,
):
    mock_get_pg_conn.return_value = _fake_pg_conn()
    mock_log_run.side_effect = [1, None]
    mock_post.return_value = MagicMock(status_code=200, json=lambda: {"message": "started"})
    mock_post.return_value.raise_for_status = lambda: None
    mock_get.return_value = MagicMock(json=lambda: {"status": "failed", "error": "Stage 4 LLM quota exceeded"})

    job_retrain_clustering(_settings())

    final_call = mock_log_run.call_args_list[-1]
    assert final_call.args[2] == "failed"
    assert "quota" in final_call.kwargs["error_msg"]


@patch("scheduler.jobs.time.sleep")
@patch("common.db.log_pipeline_run")
@patch("common.db.get_pg_conn")
@patch("scheduler.jobs.requests.get")
@patch("scheduler.jobs.requests.post")
def test_retrain_clustering_times_out_when_status_never_settles(
    mock_post, mock_get, mock_get_pg_conn, mock_log_run, mock_sleep,
):
    mock_get_pg_conn.return_value = _fake_pg_conn()
    mock_log_run.side_effect = [1, None]
    mock_post.return_value = MagicMock(status_code=200, json=lambda: {"message": "started"})
    mock_post.return_value.raise_for_status = lambda: None
    mock_get.return_value = MagicMock(json=lambda: {"status": "running"})  # không bao giờ settle

    job_retrain_clustering(_settings(clustering_retrain_poll_interval_s=0, clustering_retrain_max_wait_s=0))

    final_call = mock_log_run.call_args_list[-1]
    assert final_call.args[2] == "failed"
    assert "Timeout" in final_call.kwargs["error_msg"]


@patch("common.db.log_pipeline_run")
@patch("common.db.get_pg_conn")
@patch("scheduler.jobs.requests.post")
def test_retrain_clustering_connection_error_logs_failed_without_polling(
    mock_post, mock_get_pg_conn, mock_log_run,
):
    mock_get_pg_conn.return_value = _fake_pg_conn()
    mock_log_run.return_value = 1
    mock_post.side_effect = requests.exceptions.ConnectionError("refused")

    job_retrain_clustering(_settings())

    assert mock_log_run.call_count == 2
    final_call = mock_log_run.call_args_list[-1]
    assert final_call.args[2] == "failed"
    assert "không reach được" in final_call.kwargs["error_msg"]


@patch("common.db.log_pipeline_run")
@patch("common.db.get_pg_conn")
@patch("scheduler.jobs.requests.post")
def test_retrain_clustering_409_already_running_logs_failed(
    mock_post, mock_get_pg_conn, mock_log_run,
):
    mock_get_pg_conn.return_value = _fake_pg_conn()
    mock_log_run.return_value = 1
    response = MagicMock(status_code=409)
    error = requests.exceptions.HTTPError(response=response)
    mock_post.return_value.raise_for_status.side_effect = error

    job_retrain_clustering(_settings())

    final_call = mock_log_run.call_args_list[-1]
    assert final_call.args[2] == "failed"
    assert "đã đang chạy" in final_call.kwargs["error_msg"]
