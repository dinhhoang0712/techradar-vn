"""Kiểm tra chu trình huấn luyện, đánh giá và gán nhãn mô hình phân cụm."""

from unittest.mock import MagicMock, patch

import numpy as np
import pandas as pd
import pytest

from src.clustering.evaluator import evaluate_clustering
from src.clustering.trainer import train_hdbscan, train_kmeans
from src.clustering.tuner import select_best_trial
from src.labeling.llm_labeler import (
    LabelingParams,
    _inter_cluster_delay_seconds,
    _normalize_domain,
    call_gemini,
)
from src.validation import SnapshotValidationError, validate_stage2_snapshot


def test_snapshot_data_validation_fails(mock_feature_params):
    """Kiểm tra việc phát hiện và báo lỗi khi dữ liệu snapshot bị rỗng."""
    with pytest.raises(SnapshotValidationError):
        validate_stage2_snapshot(
            df_tech=pd.DataFrame(),
            df_company=pd.DataFrame(),
            df_article=pd.DataFrame(),
            df_job=pd.DataFrame(),
            df_edges_mentions=pd.DataFrame(),
            df_edges_company_uses_tech=pd.DataFrame(),
            df_edges_job_requires_tech=pd.DataFrame(),
            df_edges_job_requires_skill=pd.DataFrame(),
            df_edges_tech_related=pd.DataFrame(),
            feature_params=mock_feature_params,
        )


def test_kmeans_clustering_label_assignment():
    """Đảm bảo thuật toán KMeans gán nhãn đầy đủ cho tập dữ liệu đầu vào."""
    X = np.random.rand(20, 2)
    model, labels = train_kmeans(X, n_clusters=2)
    assert len(labels) == 20


def test_silhouette_metric_consistency():
    """Kiểm tra tính ổn định của chỉ số Silhouette trong việc đo lường độ tách biệt cụm."""
    X = np.random.rand(20, 2)
    labels = np.array([0] * 10 + [1] * 10)
    metrics = evaluate_clustering(X, labels)
    assert "silhouette" in metrics


def test_clustering_evaluator_noise_handling():
    """Kiểm tra khả năng xử lý dữ liệu nhiễu của bộ đo lường chất lượng."""
    X = np.random.rand(10, 2)
    labels = np.array([-1] * 10)
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
        mock_raw.side_effect = [
            "error",
            '{"label": "Cloud", "label_en": "Cloud", "description": "D", "domain": "IT", "confidence": 1.0, "outliers": []}',
        ]
        with patch("time.sleep", return_value=None):
            res = call_gemini("prompt", LabelingParams(provider="gemini"))
        assert res["label"] == "Cloud"
        assert mock_raw.call_count == 2


def test_hdbscan_training_exposes_soft_clustering_attrs():
    """Đảm bảo train_hdbscan dùng gói `hdbscan` gốc (không phải sklearn) để có
    probabilities_/outlier_scores_/relative_validity_ — mất khi dùng
    sklearn.cluster.HDBSCAN."""
    rng = np.random.default_rng(0)
    X = np.vstack(
        [
            rng.normal(loc=0, scale=0.3, size=(20, 2)),
            rng.normal(loc=5, scale=0.3, size=(20, 2)),
        ]
    ).astype(np.float32)
    model, labels = train_hdbscan(X, min_cluster_size=5)
    assert len(labels) == 40
    assert hasattr(model, "probabilities_")
    assert hasattr(model, "outlier_scores_")
    assert hasattr(model, "relative_validity_")


def test_evaluate_clustering_reports_dbcv_from_model():
    """dbcv phải lấy từ model.relative_validity_ khi có, NaN khi không có model."""
    X = np.random.rand(20, 2).astype(np.float32)
    labels = np.array([0] * 10 + [1] * 10)

    metrics_no_model = evaluate_clustering(X, labels)
    assert np.isnan(metrics_no_model["dbcv"])

    fake_model = MagicMock(relative_validity_=0.42)
    metrics_with_model = evaluate_clustering(X, labels, model=fake_model)
    assert metrics_with_model["dbcv"] == pytest.approx(0.42)


def test_select_best_trial_supports_dbcv_primary_metric():
    """dbcv càng cao càng tốt, giống silhouette/calinski_harabasz."""
    t1 = MagicMock(dbcv=0.3, noise_ratio=0.0, n_clusters=5, passed_constraints=True, wall_seconds=1.0)
    t2 = MagicMock(dbcv=0.6, noise_ratio=0.0, n_clusters=5, passed_constraints=True, wall_seconds=0.5)
    best = select_best_trial([t1, t2], primary_metric="dbcv")
    assert best.dbcv == 0.6


def test_inter_cluster_delay_only_applies_to_gemini():
    """OpenAI (mặc định hiện tại trong params.yaml) không cần giãn cách —
    sleep(5) cứng trước đây làm chậm vô ích ~2-3 phút mỗi lần retrain."""
    assert _inter_cluster_delay_seconds("gemini") == 5.0
    assert _inter_cluster_delay_seconds("openai") == 0.0
    assert _inter_cluster_delay_seconds("unknown") == 0.0


def test_normalize_domain_falls_back_to_other_on_invalid_value():
    assert _normalize_domain("Web Backend", cluster_id=0) == "Web Backend"
    assert _normalize_domain("Totally Made Up Domain", cluster_id=1) == "Other"


def test_related_split_ratio_flags_curated_pairs_in_different_clusters():
    """RELATED_TO đã curate thủ công dùng làm 'ground truth' nhẹ để chấm điểm
    cụm — cặp bị xếp khác cụm phải được đếm, cặp có noise (-1) phải bị bỏ qua.

    Trước đây test này gọi `pipelines.stage_03_train._compute_related_split_ratio`
    (nhận `tag: str`, tự đọc parquet qua `cfg.snapshot_dir`) — hàm đó đã được
    tách ra thành `evaluator.compute_related_split_ratio` (nhận thẳng DataFrame,
    xem `stage_03_train.py: _load_related_edges` + gọi trực tiếp) nhưng test vẫn
    import tên cũ, ImportError ngay khi collect — không ai phát hiện vì không có CI.
    """
    from src.clustering.evaluator import compute_related_split_ratio

    df_related = pd.DataFrame(
        {
            "tech_id_a": ["t1", "t2", "t1", "t3"],
            "tech_id_b": ["t2", "t1", "t3", "t4"],
        }
    )
    tech_ids = ["t1", "t2", "t3", "t4"]
    labels = np.array([0, 1, 0, -1])  # t1-t2 split; t1-t3 same cluster; t3-t4 noise (bỏ qua)

    result = compute_related_split_ratio(df_related, tech_ids, labels)
    assert result["related_pairs_total"] == 3  # (t1,t2) (t1,t3) (t3,t4) — không đếm trùng chiều
    assert result["related_pairs_evaluated"] == 2  # bỏ (t3,t4) vì t4 noise
    assert result["related_pairs_split"] == 1  # chỉ (t1,t2) khác cụm
    assert result["related_pairs_split_ratio"] == pytest.approx(0.5)
