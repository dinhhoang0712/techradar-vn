"""Kiểm tra RELATED_TO split-ratio (evaluator) và logic chọn best trial (tuner)."""

import numpy as np
import pandas as pd
import pytest

from src.clustering.evaluator import compute_related_split_ratio
from src.clustering.tuner import TrialResult, grid_search, select_best_trial

# ---------------------------------------------------------------------------
# compute_related_split_ratio (evaluator.py) — hàm thuần
# ---------------------------------------------------------------------------


def test_compute_related_split_ratio_basic():
    """t1-t2 cùng cụm (không split), t3-t4 khác cụm (split) → ratio 0.5."""
    df_related = pd.DataFrame({"tech_id_a": ["t1", "t3"], "tech_id_b": ["t2", "t4"]})
    tech_ids = ["t1", "t2", "t3", "t4"]
    labels = np.array([0, 0, 1, 2])

    result = compute_related_split_ratio(df_related, tech_ids, labels)

    assert result["related_pairs_total"] == 2
    assert result["related_pairs_evaluated"] == 2
    assert result["related_pairs_split"] == 1
    assert result["related_pairs_split_ratio"] == 0.5


def test_compute_related_split_ratio_excludes_noise():
    """Cặp có 1 tech là noise (label=-1) không được tính vào evaluated/split."""
    df_related = pd.DataFrame({"tech_id_a": ["t1"], "tech_id_b": ["t2"]})
    tech_ids = ["t1", "t2"]
    labels = np.array([-1, 0])

    result = compute_related_split_ratio(df_related, tech_ids, labels)

    assert result["related_pairs_evaluated"] == 0
    assert np.isnan(result["related_pairs_split_ratio"])


def test_compute_related_split_ratio_dedups_bidirectional_edges():
    """Cạnh xuất hiện cả 2 chiều (a->b và b->a) chỉ được tính 1 lần."""
    df_related = pd.DataFrame({"tech_id_a": ["t1", "t2"], "tech_id_b": ["t2", "t1"]})
    tech_ids = ["t1", "t2"]
    labels = np.array([0, 1])

    result = compute_related_split_ratio(df_related, tech_ids, labels)

    assert result["related_pairs_total"] == 1
    assert result["related_pairs_evaluated"] == 1
    assert result["related_pairs_split"] == 1


@pytest.mark.parametrize("df_related", [pd.DataFrame(), None])
def test_compute_related_split_ratio_missing_data_returns_nan(df_related):
    """Thiếu dữ liệu RELATED_TO (rỗng hoặc None) → NaN, không raise."""
    result = compute_related_split_ratio(df_related, ["t1", "t2"], np.array([0, 1]))

    assert result["related_pairs_total"] == 0
    assert np.isnan(result["related_pairs_split_ratio"])


# ---------------------------------------------------------------------------
# select_best_trial (tuner.py)
# ---------------------------------------------------------------------------


def _make_trial(**overrides) -> TrialResult:
    base = dict(
        algorithm="hdbscan",
        params={},
        labels=np.array([0, 1]),
        n_clusters=2,
        n_noise=0,
        noise_ratio=0.0,
        silhouette=0.5,
        davies_bouldin=1.0,
        calinski_harabasz=100.0,
        dbcv=0.5,
        passed_constraints=True,
        failure_reason=None,
        wall_seconds=1.0,
        related_split_ratio=float("nan"),
    )
    base.update(overrides)
    return TrialResult(**base)


def test_select_best_trial_davies_bouldin_picks_lowest():
    """Regression: trước đây bug ở `reverse` khiến davies_bouldin (thấp=tốt)
    lại chọn trial có davies_bouldin CAO NHẤT (tệ nhất)."""
    good = _make_trial(davies_bouldin=0.5)
    bad = _make_trial(davies_bouldin=3.0)

    best = select_best_trial([bad, good], primary_metric="davies_bouldin")

    assert best.davies_bouldin == 0.5


def test_select_best_trial_silhouette_picks_highest():
    """silhouette càng cao càng tốt — không bị ảnh hưởng bởi fix reverse."""
    low = _make_trial(silhouette=0.2)
    high = _make_trial(silhouette=0.8)

    best = select_best_trial([low, high], primary_metric="silhouette")

    assert best.silhouette == 0.8


def test_select_best_trial_tiebreaks_on_related_split_ratio():
    """Primary metric hoà nhau → trial có related_split_ratio thấp hơn (ít
    cặp RELATED_TO đã curate bị xếp khác cụm hơn) phải thắng."""
    tied_bad_related = _make_trial(silhouette=0.5, related_split_ratio=0.9)
    tied_good_related = _make_trial(silhouette=0.5, related_split_ratio=0.1)

    best = select_best_trial([tied_bad_related, tied_good_related], primary_metric="silhouette")

    assert best.related_split_ratio == 0.1


def test_select_best_trial_missing_related_ratio_is_neutral():
    """related_split_ratio=NaN (không truyền df_related vào grid_search) phải
    trung lập, không chặn tie-break kế tiếp (noise_ratio) — giữ nguyên hành vi
    cũ khi không dùng tính năng mới."""
    lower_noise = _make_trial(silhouette=0.5, noise_ratio=0.1, related_split_ratio=float("nan"))
    higher_noise = _make_trial(silhouette=0.5, noise_ratio=0.5, related_split_ratio=float("nan"))

    best = select_best_trial([higher_noise, lower_noise], primary_metric="silhouette")

    assert best.noise_ratio == 0.1


# ---------------------------------------------------------------------------
# grid_search — wiring tech_ids/df_related + require_max_related_split_ratio
# ---------------------------------------------------------------------------


@pytest.fixture
def _well_separated_data():
    # t1,t2 cụm A (gần nhau); t3,t4 cụm B (gần nhau, cách xa cụm A)
    X = np.array([[0.0, 0.0], [0.0, 0.1], [5.0, 5.0], [5.0, 5.1]])
    tech_ids = ["t1", "t2", "t3", "t4"]
    # t1 (cụm A) và t3 (cụm B) được curate là RELATED_TO — kmeans(k=2) chắc
    # chắn xếp chúng khác cụm với dữ liệu tách biệt rõ này.
    df_related = pd.DataFrame({"tech_id_a": ["t1"], "tech_id_b": ["t3"]})
    return X, tech_ids, df_related


def test_grid_search_computes_related_split_ratio_per_trial(mock_clustering_params, _well_separated_data):
    """related_split_ratio phải được tính cho MỌI trial khi truyền đủ
    tech_ids + df_related, không chỉ trial thắng cuối."""
    X, tech_ids, df_related = _well_separated_data
    mock_clustering_params.kmeans.n_clusters_grid = [2]

    trials = grid_search(X, mock_clustering_params, tech_ids=tech_ids, df_related=df_related)

    assert len(trials) == 1
    assert trials[0].related_split_ratio == 1.0


def test_grid_search_related_split_ratio_disabled_by_default(mock_clustering_params, _well_separated_data):
    """require_max_related_split_ratio mặc định None → không ảnh hưởng
    passed_constraints (không đổi hành vi cũ)."""
    X, tech_ids, df_related = _well_separated_data
    mock_clustering_params.kmeans.n_clusters_grid = [2]

    trials = grid_search(X, mock_clustering_params, tech_ids=tech_ids, df_related=df_related)

    assert trials[0].passed_constraints is True


def test_grid_search_applies_related_split_ratio_constraint(mock_clustering_params, _well_separated_data):
    """require_max_related_split_ratio được set chặt → trial vượt ngưỡng bị
    đánh passed_constraints=False dù đạt mọi ràng buộc cũ (n_clusters/noise)."""
    X, tech_ids, df_related = _well_separated_data
    mock_clustering_params.kmeans.n_clusters_grid = [2]
    mock_clustering_params.selection.require_max_related_split_ratio = 0.0

    trials = grid_search(X, mock_clustering_params, tech_ids=tech_ids, df_related=df_related)

    assert trials[0].related_split_ratio == 1.0
    assert trials[0].passed_constraints is False


def test_grid_search_without_tech_ids_or_df_related_is_unaffected(mock_clustering_params):
    """Không truyền tech_ids/df_related (caller cũ) → related_split_ratio NaN,
    hành vi y hệt trước khi có tính năng này."""
    X = np.array([[0.0, 0.0], [0.0, 0.1], [5.0, 5.0], [5.0, 5.1]])
    mock_clustering_params.kmeans.n_clusters_grid = [2]

    trials = grid_search(X, mock_clustering_params)

    assert np.isnan(trials[0].related_split_ratio)
    assert trials[0].passed_constraints is True
