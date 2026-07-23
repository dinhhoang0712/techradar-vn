"""Kiểm tra champion/challenger gate của register_best_model (mlflow_logger)."""

from unittest.mock import MagicMock, patch

from mlflow.exceptions import MlflowException

from src.tracking.mlflow_logger import register_best_model


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
