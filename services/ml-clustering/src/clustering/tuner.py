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

from conf.config import ClusteringParams
from src.clustering.evaluator import evaluate_clustering
from src.clustering.trainer import train_by_algorithm

logger = logging.getLogger(__name__)


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
        passed_constraints: bool — đạt require_min_clusters & max_noise_ratio chưa.
        failure_reason:    nếu fit/scoring fail → string giải thích, ngược lại None.
        wall_seconds:      thời gian fit + score.
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
    passed_constraints: bool
    failure_reason: str | None
    wall_seconds: float


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
) -> list[TrialResult]:
    """
    Chạy grid search → list TrialResult (kể cả trial thất bại).
    Không raise khi 1 trial fail — ghi failure_reason và tiếp tục.
    """
    param_grid = _build_param_grid(params)
    logger.info(
        "Grid search algorithm=%s, tổng %d trial(s).",
        params.algorithm, len(param_grid),
    )
    print(f"[grid_search] {params.algorithm} — {len(param_grid)} trials")

    sel = params.selection
    results: list[TrialResult] = []

    for i, trial_params in enumerate(param_grid, 1):
        t0 = time.time()
        try:
            _, labels = train_by_algorithm(params.algorithm, X, **trial_params)
            metrics   = evaluate_clustering(X, labels)
            wall      = time.time() - t0

            passed = (
                metrics["n_clusters"] >= sel.require_min_clusters
                and metrics["n_clusters"] <= sel.require_max_clusters
                and metrics["noise_ratio"] <= sel.require_max_noise_ratio
            )

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
                passed_constraints = passed,
                failure_reason     = None,
                wall_seconds       = wall,
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
                passed_constraints = False,
                failure_reason     = str(exc),
                wall_seconds       = wall,
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
    primary_metric: Literal["silhouette", "davies_bouldin", "calinski_harabasz"],
) -> TrialResult:
    """
    Chọn trial tốt nhất trong số passed_constraints == True.
    Raise RuntimeError nếu không có trial nào pass.
    """
    passed = [t for t in trials if t.passed_constraints]
    if not passed:
        raise RuntimeError(
            "Không có trial nào pass constraints. "
            "Gợi ý: giảm require_min_clusters, tăng require_max_noise_ratio, "
            "hoặc đổi algorithm trong params.yaml."
        )

    # Hướng tốt của từng metric
    reverse = primary_metric in ("silhouette", "calinski_harabasz")

    def sort_key(t: TrialResult):
        primary = t.silhouette if primary_metric == "silhouette" else (
            t.calinski_harabasz if primary_metric == "calinski_harabasz"
            else -(t.davies_bouldin or float("inf"))
        )
        # Tie-break: ít noise → nhiều cụm → nhanh hơn
        return (
            primary if primary is not None else float("-inf"),
            -t.noise_ratio,
            t.n_clusters,
            -t.wall_seconds,
        )

    best = sorted(passed, key=sort_key, reverse=reverse)[0]
    logger.info(
        "Best trial: params=%s n_clusters=%d silhouette=%.4f noise_ratio=%.3f",
        best.params, best.n_clusters, best.silhouette or 0, best.noise_ratio,
    )
    return best
