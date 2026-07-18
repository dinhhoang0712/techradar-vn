"""
Grid search hyperparameters cho clustering + chọn trial tốt nhất.

Khác với GridSearchCV (cần label thật), ở đây ta tự chấm bằng các metric
internal: Silhouette, Davies-Bouldin, Calinski-Harabasz.

Mỗi trial → ghi 1 MLflow nested run; trial tốt nhất được register vào MLflow
Model Registry (xem `mlflow_logger.register_best_model`).
"""

from __future__ import annotations

import itertools
import logging
import time
from dataclasses import dataclass, field
from typing import Any, Literal

import numpy as np
import pandas as pd

from conf.config import ClusteringParams
from src.clustering.evaluator import compute_related_split_ratio, evaluate_clustering
from src.clustering.trainer import train_by_algorithm

logger = logging.getLogger(__name__)
_NAN = float("nan")

PrimaryMetric = Literal["silhouette", "davies_bouldin", "calinski_harabasz", "dbcv"]


def normalize_metric_for_comparison(value: float | None, primary_metric: PrimaryMetric) -> float | None:
    """
    Chuẩn hoá 1 giá trị metric về "cao hơn = tốt hơn" — davies_bouldin (thấp hơn =
    tốt hơn) bị negate. Dùng chung bởi `select_best_trial` (so trial trong 1 lần
    grid search) VÀ `mlflow_logger.register_best_model` (so model mới vs champion
    đang serve, đọc từ MLflow) để 2 nơi không lệch quy ước "chiều tốt hơn".
    """
    if value is None:
        return None
    return -value if primary_metric == "davies_bouldin" else value


@dataclass
class TrialResult:
    """
    Kết quả một lần thử (một bộ hyperparam).

    Fields:
        algorithm:         "dbscan" | "hdbscan" | "kmeans".
        params:            dict tham số đã thử (eps, min_samples, n_clusters…).
        labels:            np.ndarray nhãn cụm (-1 = noise).
        n_clusters:        số cụm thực tế (không tính noise).
        n_noise:           số điểm noise.
        noise_ratio:       n_noise / n_total.
        silhouette:        điểm silhouette (chỉ tính trên non-noise).
        davies_bouldin:    DB index (thấp = tốt).
        calinski_harabasz: CH index (cao = tốt).
        dbcv:              xấp xỉ DBCV (chỉ có với hdbscan, NaN với thuật toán khác).
        passed_constraints: bool — đạt require_min_clusters & max_noise_ratio chưa.
        failure_reason:    nếu fit/scoring fail → string giải thích, ngược lại None.
        wall_seconds:      thời gian fit + score.
        related_split_ratio: tỉ lệ cặp RELATED_TO (ground-truth thủ công) bị xếp
                           khác cụm — NaN nếu không truyền df_related/tech_ids
                           vào grid_search. Xem evaluator.compute_related_split_ratio.
    """
    algorithm: str
    params: dict[str, Any]
    labels: np.ndarray = field(repr=False)
    n_clusters: int
    n_noise: int
    noise_ratio: float
    silhouette: float | None
    davies_bouldin: float | None
    calinski_harabasz: float | None
    dbcv: float | None
    passed_constraints: bool
    failure_reason: str | None
    wall_seconds: float
    related_split_ratio: float | None = None


def _build_param_grid(params: ClusteringParams) -> list[dict]:
    """Tạo cartesian product tham số theo algorithm."""
    alg = params.algorithm
    if alg == "dbscan":
        g = params.dbscan
        return [
            {"eps": eps, "min_samples": ms, "metric": g.metric}
            for eps, ms in itertools.product(g.eps_grid, g.min_samples_grid)
        ]
    elif alg == "hdbscan":
        g = params.hdbscan
        return [
            {
                "min_cluster_size": mcs,
                "min_samples": ms,
                "cluster_selection_method": g.cluster_selection_method,
            }
            for mcs, ms in itertools.product(
                g.min_cluster_size_grid, g.min_samples_grid
            )
        ]
    else:  # kmeans
        g = params.kmeans
        return [
            {"n_clusters": nc, "n_init": g.n_init, "random_state": g.random_state}
            for nc in g.n_clusters_grid
        ]


def grid_search(
    X: np.ndarray,
    params: ClusteringParams,
    tech_ids: list[str] | None = None,
    df_related: pd.DataFrame | None = None,
) -> list[TrialResult]:
    """
    Chạy grid search → list TrialResult (kể cả trial thất bại).
    Không raise khi 1 trial fail — ghi failure_reason và tiếp tục.

    Nếu truyền cả `tech_ids` và `df_related`: tính thêm `related_split_ratio`
    cho MỖI trial (không chỉ trial thắng cuối) bằng
    `evaluator.compute_related_split_ratio` — cho phép
    `selection.require_max_related_split_ratio` áp dụng như một ràng buộc
    ngay tại vòng grid search, và cho `select_best_trial` dùng làm tie-break.
    Thiếu 1 trong 2 tham số → related_split_ratio = NaN, hành vi y hệt trước đây.
    """
    param_grid = _build_param_grid(params)
    logger.info(
        "Grid search algorithm=%s, tổng %d trial(s).",
        params.algorithm, len(param_grid),
    )
    print(f"[grid_search] {params.algorithm} — {len(param_grid)} trials")

    sel = params.selection
    can_score_related = tech_ids is not None and df_related is not None
    results: list[TrialResult] = []

    for i, trial_params in enumerate(param_grid, 1):
        t0 = time.time()
        try:
            model, labels = train_by_algorithm(params.algorithm, X, **trial_params)
            metrics   = evaluate_clustering(X, labels, model=model)
            wall      = time.time() - t0

            related_ratio = _NAN
            if can_score_related:
                related_ratio = compute_related_split_ratio(
                    df_related, tech_ids, labels
                )["related_pairs_split_ratio"]

            passed = (
                metrics["n_clusters"] >= sel.require_min_clusters
                and metrics["n_clusters"] <= sel.require_max_clusters
                and metrics["noise_ratio"] <= sel.require_max_noise_ratio
            )
            if sel.require_max_related_split_ratio is not None and not np.isnan(related_ratio):
                passed = passed and related_ratio <= sel.require_max_related_split_ratio

            result = TrialResult(
                algorithm          = params.algorithm,
                params             = trial_params,
                labels             = labels,
                n_clusters         = metrics["n_clusters"],
                n_noise            = metrics["n_noise"],
                noise_ratio        = metrics["noise_ratio"],
                silhouette         = metrics["silhouette"],
                davies_bouldin     = metrics["davies_bouldin"],
                calinski_harabasz  = metrics["calinski_harabasz"],
                dbcv               = metrics["dbcv"],
                passed_constraints = passed,
                failure_reason     = None,
                wall_seconds       = wall,
                related_split_ratio = related_ratio,
            )
        except Exception as exc:
            wall = time.time() - t0
            logger.warning("Trial %d/%d failed: %s", i, len(param_grid), exc)
            result = TrialResult(
                algorithm          = params.algorithm,
                params             = trial_params,
                labels             = np.array([], dtype=int),
                n_clusters         = 0,
                n_noise            = 0,
                noise_ratio        = 1.0,
                silhouette         = None,
                davies_bouldin     = None,
                calinski_harabasz  = None,
                dbcv               = None,
                passed_constraints = False,
                failure_reason     = str(exc),
                wall_seconds       = wall,
                related_split_ratio = _NAN,
            )

        results.append(result)
        status = "✓" if result.passed_constraints else "✗"
        sil_str = f"{result.silhouette:.3f}" if result.silhouette is not None else "N/A"
        print(
            f"  [{status}] Trial {i}/{len(param_grid)} "
            f"params={trial_params} "
            f"n_clusters={result.n_clusters} "
            f"silhouette={sil_str} "
            f"({wall:.1f}s)"
        )

    n_passed = sum(r.passed_constraints for r in results)
    logger.info("Grid search xong: %d/%d trial pass constraints.", n_passed, len(results))
    return results


def find_eps_via_kdistance(X: np.ndarray, k: int = 5) -> dict:
    """
    Gợi ý eps cho DBSCAN qua k-distance plot + Kneedle algorithm.
    Không raise nếu không tìm được knee — trả kneedle_eps=None.
    """
    from sklearn.neighbors import NearestNeighbors

    nn = NearestNeighbors(n_neighbors=k + 1, metric="euclidean")
    nn.fit(X)
    distances, _ = nn.kneighbors(X)
    k_distances = sorted(distances[:, k], reverse=True)

    kneedle_eps = None
    try:
        from kneed import KneeLocator
        kl = KneeLocator(
            range(len(k_distances)),
            k_distances,
            curve="convex",
            direction="decreasing",
        )
        if kl.knee is not None:
            kneedle_eps = float(k_distances[kl.knee])
    except Exception as e:
        # Fallback: lấy điểm có đạo hàm rời rạc lớn nhất
        logger.warning("kneed không khả dụng (%s), dùng fallback.", e)
        diffs = np.diff(k_distances)
        knee_idx = int(np.argmax(np.abs(diffs)))
        kneedle_eps = float(k_distances[knee_idx])

    logger.info("find_eps_via_kdistance: k=%d, kneedle_eps=%.4f", k, kneedle_eps or -1)
    return {
        "k":            k,
        "kneedle_eps":  kneedle_eps,
        "k_distances":  k_distances,
    }


def select_best_trial(
    trials: list[TrialResult],
    primary_metric: PrimaryMetric,
) -> TrialResult:
    """
    Chọn trial tốt nhất trong số passed_constraints == True.
    Raise RuntimeError nếu không có trial nào pass.

    `primary_metric="dbcv"` dùng Density-Based Clustering Validation (xấp xỉ
    qua `relative_validity_` của hdbscan) — chỉ có ý nghĩa khi
    `clustering.algorithm="hdbscan"`; với dbscan/kmeans giá trị này luôn NaN
    nên sẽ rơi về tie-break (không raise, nhưng nên tránh chọn "dbcv" làm
    primary_metric nếu không dùng hdbscan).
    """
    passed = [t for t in trials if t.passed_constraints]
    if not passed:
        raise RuntimeError(
            "Không có trial nào pass constraints. "
            "Gợi ý: giảm require_min_clusters, tăng require_max_noise_ratio, "
            "hoặc đổi algorithm trong params.yaml."
        )

    def _primary_value(t: TrialResult) -> float | None:
        # Chuẩn hoá về "càng cao càng tốt" cho mọi metric — xem normalize_metric_for_comparison.
        raw = getattr(t, primary_metric)
        return normalize_metric_for_comparison(raw, primary_metric)

    def _related_tiebreak_value(t: TrialResult) -> float:
        # related_split_ratio: thấp hơn = tốt hơn (ít cặp biết-liên-quan bị
        # xếp khác cụm) → negate để cùng chiều "cao hơn = tốt hơn" như trên.
        # Thiếu dữ liệu (None/NaN) → 0.0 (trung lập, không đẩy thứ tự).
        r = t.related_split_ratio
        if r is None or (isinstance(r, float) and np.isnan(r)):
            return 0.0
        return -r

    def sort_key(t: TrialResult):
        primary = _primary_value(t)
        # Tie-break theo thứ tự ưu tiên: khớp RELATED_TO đã curate → ít noise
        # → nhiều cụm → nhanh hơn. Mọi giá trị đã chuẩn hoá "cao hơn = tốt
        # hơn" nên LUÔN sort reverse=True (xem _primary_value/_related_tiebreak_value).
        return (
            primary if primary is not None else float("-inf"),
            _related_tiebreak_value(t),
            -t.noise_ratio,
            t.n_clusters,
            -t.wall_seconds,
        )

    # reverse=True luôn đúng vì mọi thành phần của sort_key đã được chuẩn hoá
    # "giá trị cao hơn = tốt hơn" (xem _primary_value/_related_tiebreak_value).
    # Trước đây `reverse` được tính theo primary_metric (loại trừ
    # "davies_bouldin") — khiến trial CÓ Davies-Bouldin CAO NHẤT (tệ nhất) bị
    # chọn khi primary_metric="davies_bouldin", ngược hẳn ý nghĩa "thấp hơn =
    # tốt hơn" của chính metric này.
    best = sorted(passed, key=sort_key, reverse=True)[0]
    logger.info(
        "Best trial: params=%s n_clusters=%d silhouette=%.4f noise_ratio=%.3f",
        best.params, best.n_clusters, best.silhouette or 0, best.noise_ratio,
    )
    return best
