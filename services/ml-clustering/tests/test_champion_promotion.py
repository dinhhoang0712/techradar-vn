"""Kiểm tra champion/challenger gate của register_best_model (mlflow_logger)."""

from unittest.mock import MagicMock, patch

from mlflow.exceptions import MlflowException

from src.tracking.mlflow_logger import is_run_promoted_to_champion, register_best_model


def _mock_register_model(version="2"):
    return MagicMock(version=version)


@patch("src.tracking.mlflow_logger.mlflow")
def test_promotes_when_no_champion_exists_yet(mock_mlflow):
    mock_mlflow.register_model.return_value = _mock_register_model("1")
    client = MagicMock()
    client.get_model_version_by_alias.side_effect = MlflowException("not found")
    mock_mlflow.tracking.MlflowClient.return_value = client

    result = register_best_model("run123", "tech-clusters", primary_metric="silhouette", primary_metric_value=0.5)

    assert result == {"version": 1, "promoted": True, "reason": "chưa có champion — promote lần đầu"}
    client.set_registered_model_alias.assert_called_once_with("tech-clusters", "champion", 1)


@patch("src.tracking.mlflow_logger.mlflow")
def test_promotes_when_new_silhouette_beats_champion(mock_mlflow):
    mock_mlflow.register_model.return_value = _mock_register_model("3")
    client = MagicMock()
    champion_version = MagicMock(run_id="champ_run", version=2)
    client.get_model_version_by_alias.return_value = champion_version
    client.get_run.return_value = MagicMock(data=MagicMock(metrics={"silhouette": 0.4}))
    mock_mlflow.tracking.MlflowClient.return_value = client

    result = register_best_model("run456", "tech-clusters", primary_metric="silhouette", primary_metric_value=0.6)

    assert result["promoted"] is True
    assert result["version"] == 3
    client.set_registered_model_alias.assert_called_once_with("tech-clusters", "champion", 3)


@patch("src.tracking.mlflow_logger.mlflow")
def test_keeps_old_champion_when_new_model_is_worse(mock_mlflow):
    mock_mlflow.register_model.return_value = _mock_register_model("3")
    client = MagicMock()
    champion_version = MagicMock(run_id="champ_run", version=2)
    client.get_model_version_by_alias.return_value = champion_version
    client.get_run.return_value = MagicMock(data=MagicMock(metrics={"silhouette": 0.7}))
    mock_mlflow.tracking.MlflowClient.return_value = client

    result = register_best_model("run789", "tech-clusters", primary_metric="silhouette", primary_metric_value=0.6)

    assert result["promoted"] is False
    assert result["version"] == 3  # vẫn đăng ký version mới, chỉ không gán alias
    client.set_registered_model_alias.assert_not_called()


@patch("src.tracking.mlflow_logger.mlflow")
def test_davies_bouldin_lower_is_better_new_wins_with_lower_value(mock_mlflow):
    """davies_bouldin: thấp hơn = tốt hơn — model mới 0.3 phải THẮNG champion 0.5."""
    mock_mlflow.register_model.return_value = _mock_register_model("2")
    client = MagicMock()
    champion_version = MagicMock(run_id="champ_run", version=1)
    client.get_model_version_by_alias.return_value = champion_version
    client.get_run.return_value = MagicMock(data=MagicMock(metrics={"davies_bouldin": 0.5}))
    mock_mlflow.tracking.MlflowClient.return_value = client

    result = register_best_model("runabc", "tech-clusters", primary_metric="davies_bouldin", primary_metric_value=0.3)

    assert result["promoted"] is True
    client.set_registered_model_alias.assert_called_once_with("tech-clusters", "champion", 2)


@patch("src.tracking.mlflow_logger.mlflow")
def test_promotes_unconditionally_when_primary_metric_not_given(mock_mlflow):
    """Tương thích ngược: caller không truyền primary_metric → luôn promote như trước."""
    mock_mlflow.register_model.return_value = _mock_register_model("5")
    client = MagicMock()
    mock_mlflow.tracking.MlflowClient.return_value = client

    result = register_best_model("run999", "tech-clusters")

    assert result["promoted"] is True
    client.get_model_version_by_alias.assert_not_called()
    client.set_registered_model_alias.assert_called_once_with("tech-clusters", "champion", 5)


@patch("src.tracking.mlflow_logger.mlflow")
def test_promotes_when_champion_run_missing_the_metric(mock_mlflow):
    """Champion cũ thiếu chính metric đang so sánh (vd đổi primary_metric giữa các lần
    train) — không nên chặn promote vì không có cơ sở so sánh hợp lệ."""
    mock_mlflow.register_model.return_value = _mock_register_model("4")
    client = MagicMock()
    champion_version = MagicMock(run_id="champ_run", version=3)
    client.get_model_version_by_alias.return_value = champion_version
    client.get_run.return_value = MagicMock(data=MagicMock(metrics={}))
    mock_mlflow.tracking.MlflowClient.return_value = client

    result = register_best_model("runxyz", "tech-clusters", primary_metric="silhouette", primary_metric_value=0.6)

    assert result["promoted"] is True
    client.set_registered_model_alias.assert_called_once_with("tech-clusters", "champion", 4)


# ---------------------------------------------------------------------------
# is_run_promoted_to_champion — dùng bởi stage_06_publish + routes_pipeline để quyết định
# publish/writeback hay bỏ qua. Tag `promoted_to_champion` nằm trên PARENT run của run_id
# truyền vào (xem docstring hàm), nên test phải mock get_run trả về tags.mlflow.parentRunId
# rồi 1 lần get_run thứ 2 cho chính parent đó.
# ---------------------------------------------------------------------------


def test_is_promoted_reads_tag_from_parent_run():
    best_run = MagicMock(data=MagicMock(tags={"mlflow.parentRunId": "parent-1"}))
    parent_run = MagicMock(data=MagicMock(tags={"promoted_to_champion": "True"}))
    client = MagicMock()
    client.get_run.side_effect = lambda rid: {"best-1": best_run, "parent-1": parent_run}[rid]

    with patch("mlflow.tracking.MlflowClient", return_value=client):
        assert is_run_promoted_to_champion("best-1", "http://mlflow") is True


def test_is_promoted_false_when_parent_tag_says_not_promoted():
    best_run = MagicMock(data=MagicMock(tags={"mlflow.parentRunId": "parent-1"}))
    parent_run = MagicMock(data=MagicMock(tags={"promoted_to_champion": "False"}))
    client = MagicMock()
    client.get_run.side_effect = lambda rid: {"best-1": best_run, "parent-1": parent_run}[rid]

    with patch("mlflow.tracking.MlflowClient", return_value=client):
        assert is_run_promoted_to_champion("best-1", "http://mlflow") is False


def test_is_promoted_fails_open_when_no_parent_run_id():
    best_run = MagicMock(data=MagicMock(tags={}))
    client = MagicMock()
    client.get_run.return_value = best_run

    with patch("mlflow.tracking.MlflowClient", return_value=client):
        assert is_run_promoted_to_champion("best-1", "http://mlflow") is True


def test_is_promoted_fails_open_when_parent_missing_promotion_tag():
    best_run = MagicMock(data=MagicMock(tags={"mlflow.parentRunId": "parent-1"}))
    parent_run = MagicMock(data=MagicMock(tags={}))
    client = MagicMock()
    client.get_run.side_effect = lambda rid: {"best-1": best_run, "parent-1": parent_run}[rid]

    with patch("mlflow.tracking.MlflowClient", return_value=client):
        assert is_run_promoted_to_champion("best-1", "http://mlflow") is True


def test_is_promoted_fails_open_when_mlflow_query_raises():
    client = MagicMock()
    client.get_run.side_effect = RuntimeError("tracking server unreachable")

    with patch("mlflow.tracking.MlflowClient", return_value=client):
        assert is_run_promoted_to_champion("best-1", "http://mlflow") is True
