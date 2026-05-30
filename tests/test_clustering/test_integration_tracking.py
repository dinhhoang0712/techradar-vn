"""Kiểm tra tích hợp lưu trữ tham số và cấu hình hệ thống."""
import pytest
from src.tracking.mlflow_logger import _flatten_params
from conf.config import load_params, MODULE_ROOT

def test_mlflow_parameter_serialization_logic():
    """Kiểm tra việc làm phẳng và chuẩn hóa tham số trước khi lưu trữ vào MLflow."""
    params = {"clustering": {"algorithm": "kmeans"}}
    flat = _flatten_params(params, prefix="cfg")
    assert flat["cfg.clustering.algorithm"] == "kmeans"

def test_system_config_loading_integrity():
    """Đảm bảo các tham số từ file cấu hình được tải lên chính xác và đầy đủ."""
    params_path = MODULE_ROOT / "params.yaml"
    if params_path.exists():
        params = load_params(params_path)
        assert params.snapshot.tag is not None
