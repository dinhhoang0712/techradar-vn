"""Kiểm tra chu trình huấn luyện, đánh giá và gán nhãn mô hình phân cụm."""
import pytest
import numpy as np
from unittest.mock import patch, MagicMock
from src.clustering.trainer import train_kmeans
from src.clustering.evaluator import evaluate_clustering
from src.clustering.tuner import select_best_trial, TrialResult
from src.labeling.llm_labeler import call_gemini, LabelingParams
from src.validation import validate_stage2_snapshot, SnapshotValidationError

def test_snapshot_data_validation_fails(mock_feature_params):
    """Kiểm tra việc phát hiện và báo lỗi khi dữ liệu snapshot bị rỗng."""
    with pytest.raises(SnapshotValidationError):
        validate_stage2_snapshot(df_tech=pd.DataFrame(), df_company=pd.DataFrame(), df_article=pd.DataFrame(), 
            df_job=pd.DataFrame(), df_edges_mentions=pd.DataFrame(), df_edges_company_uses_tech=pd.DataFrame(),
            df_edges_job_requires_tech=pd.DataFrame(), df_edges_job_requires_skill=pd.DataFrame(),
            df_edges_tech_related=pd.DataFrame(), feature_params=mock_feature_params)

def test_kmeans_clustering_label_assignment():
    """Đảm bảo thuật toán KMeans gán nhãn đầy đủ cho tập dữ liệu đầu vào."""
    X = np.random.rand(20, 2)
    model, labels = train_kmeans(X, n_clusters=2)
    assert len(labels) == 20

def test_silhouette_metric_consistency():
    """Kiểm tra tính ổn định của chỉ số Silhouette trong việc đo lường độ tách biệt cụm."""
    X = np.random.rand(20, 2)
    labels = np.array([0]*10 + [1]*10)
    metrics = evaluate_clustering(X, labels)
    assert "silhouette" in metrics

def test_clustering_evaluator_noise_handling():
    """Kiểm tra khả năng xử lý dữ liệu nhiễu của bộ đo lường chất lượng."""
    X = np.random.rand(10, 2)
    labels = np.array([-1]*10)
    metrics = evaluate_clustering(X, labels)
    assert np.isnan(metrics["silhouette"])

def test_optimal_trial_selection_accuracy():
    """Đảm bảo logic chọn lựa kịch bản tối ưu dựa trên điểm số cao nhất."""
    t1 = MagicMock(silhouette=0.5, noise_ratio=0.0, n_clusters=5, passed_constraints=True, wall_seconds=1.0)
    t2 = MagicMock(silhouette=0.8, noise_ratio=0.0, n_clusters=5, passed_constraints=True, wall_seconds=0.5)
    best = select_best_trial([t1, t2], primary_metric="silhouette")
    assert best.silhouette == 0.8

def test_llm_labeling_automatic_retry_logic():
    """Kiểm tra cơ chế tự động thử lại khi gặp lỗi định dạng từ phía AI gán nhãn."""
    with patch("src.labeling.llm_labeler._call_llm_raw") as mock_raw:
        mock_raw.side_effect = ["error", '{"label": "Cloud", "label_en": "Cloud", "description": "D", "domain": "IT", "confidence": 1.0, "outliers": []}']
        with patch("time.sleep", return_value=None):
            res = call_gemini("prompt", LabelingParams(provider="gemini"))
        assert res["label"] == "Cloud"
        assert mock_raw.call_count == 2
import pandas as pd # Import bổ sung cho validate_stage2_snapshot
