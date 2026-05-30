"""
Stage 03 — TRAIN: grid search clustering + log MLflow + chọn best + tính near clusters.

CLI:
    python -m pipelines.stage_03_train --params params.yaml

Output:
    data/models/<tag>/{best_model.pkl, best_labels.parquet}
    data/metrics/<tag>/best_metrics.json     (DVC metric)
    MLflow: parent run + N nested trial run + 1 best run + register model
            + artifact near_clusters.json (đọc bởi stage_05).
"""

import json
import pickle
import sys
from pathlib import Path

import mlflow
import numpy as np
import pandas as pd
import typer
from loguru import logger

app = typer.Typer(add_completion=False, help="Grid search + select best clusterer")


# ---------------------------------------------------------------------------
# Near-cluster computation
# ---------------------------------------------------------------------------

def _compute_near_clusters(
    X: np.ndarray,
    labels: np.ndarray,
    tech_ids: list[str],
    threshold: float,
) -> dict[str, list[dict]]:
    """
    Tính near_clusters cho mỗi tech_id.

    Với mỗi tech:
      - Tính khoảng cách Euclidean đến tất cả centroid ≠ cluster chính của tech.
      - Score = 1 - dist / max_dist (chuẩn hoá về [0,1]).
      - Giữ lại entry có score >= threshold, sắp xếp DESC.
    Noise points (label=-1) cũng được tính để biết chúng gần cluster nào.
    """
    # l.1 — Tính centroid mỗi cluster (bỏ noise)
    unique_labels = sorted(set(labels.tolist()))
    centroids: dict[int, np.ndarray] = {
        lbl: X[labels == lbl].mean(axis=0)
        for lbl in unique_labels
        if lbl != -1
    }

    if not centroids:
        return {tid: [] for tid in tech_ids}

    centroid_ids = list(centroids.keys())
    centroid_matrix = np.vstack([centroids[c] for c in centroid_ids])  # (K, D)

    # l.2 — Với mỗi tech
    result: dict[str, list[dict]] = {}
    for i, tech_id in enumerate(tech_ids):
        primary = int(labels[i])
        x = X[i]

        # Khoảng cách đến tất cả centroid trừ centroid chính
        dists: dict[int, float] = {}
        for j, cid in enumerate(centroid_ids):
            if cid != primary:
                dists[cid] = float(np.linalg.norm(x - centroid_matrix[j]))

        if not dists:
            result[tech_id] = []
            continue

        max_dist = max(dists.values())
        if max_dist == 0.0:
            result[tech_id] = []
            continue

        near = [
            {"cluster_id": cid, "score": round(1.0 - d / max_dist, 4)}
            for cid, d in dists.items()
            if (1.0 - d / max_dist) >= threshold
        ]
        near.sort(key=lambda e: e["score"], reverse=True)
        result[tech_id] = near

    return result


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

@app.command()
def main(
    params: str = typer.Option("params.yaml", help="Đường dẫn params.yaml"),
    skip_kdistance: bool = typer.Option(
        False,
        "--skip-kdistance/--no-skip-kdistance",
        help="Bỏ qua bước gợi ý eps qua k-distance (tăng tốc khi đã biết eps_grid).",
    ),
) -> None:
    """
    Yêu cầu logic (theo thứ tự):

      1. load_params + init_mlflow.
      2. Load X + tech_ids.
      3. Mở parent_run.
         a. Log params toàn cục.
         b. (DBSCAN + not skip) k-distance gợi ý eps.
         c. Grid search.
         d. Log mỗi trial.
         e. Select best.
         f. Re-fit best → lấy model object.
         g. Tính near_clusters.
         h. log_best_run (kèm near_clusters.json artifact).
         i. register_best_model.
         j. write_metrics_file.
         k. Lưu model + labels locally.
      4. In tóm tắt.
      5. Return 0 nếu OK; 1 nếu không trial nào pass.
    """
    from conf.config import features_dir, load_params, metrics_dir, models_dir
    from src.clustering.trainer import train_by_algorithm
    from src.clustering.tuner import (
        find_eps_via_kdistance,
        grid_search,
        select_best_trial,
    )
    from src.features.feature_pipeline import load_features
    from src.tracking.mlflow_logger import (
        init_mlflow,
        log_best_run,
        log_feature_meta,
        log_params_from_yaml,
        log_trial,
        parent_run,
        register_best_model,
        write_metrics_file,
    )

    # 1. Load params + init MLflow
    params_obj = load_params(params)
    tag = params_obj.snapshot.tag
    logger.info("Stage 03 — TRAIN | tag={} algorithm={}", tag, params_obj.clustering.algorithm)

    init_mlflow(params_obj.mlflow)

    # 2. Load X + tech_ids
    X, meta = load_features(features_dir(tag))
    tech_ids: list[str] = meta.tech_ids
    logger.info("Feature matrix: shape={}", X.shape)

    # 3. Parent MLflow run
    with parent_run(f"train_{tag}", tags={"snapshot": tag, "algorithm": params_obj.clustering.algorithm}) as run:

        # 3a. Log global params
        mlflow.log_params({
            "algorithm":    params_obj.clustering.algorithm,
            "scaler":       params_obj.features.scaler,
            "reduce_dim":   params_obj.features.reduce_dim.method,
            "n_components": params_obj.features.reduce_dim.n_components,
            "n_techs":      meta.n_techs,
            "final_dim":    meta.final_dim,
        })

        # Log full params.yaml (flattened) + file artifact
        log_params_from_yaml(params_obj, prefix="cfg")
        if Path(params).exists():
            mlflow.log_artifact(params, artifact_path="input")

        # 3b. Log feature meta
        feat_meta_path = features_dir(tag) / "feature_meta.json"
        if feat_meta_path.exists():
            log_feature_meta(feat_meta_path)

        # 3c. k-distance (chỉ cho DBSCAN)
        if not skip_kdistance and params_obj.clustering.algorithm == "dbscan":
            logger.info("Computing k-distance plot for eps suggestion...")
            try:
                kd = find_eps_via_kdistance(X)
                mlflow.log_params({"suggested_eps": kd["kneedle_eps"] or "N/A"})
                logger.info("Suggested eps (kneedle): {}", kd["kneedle_eps"])
            except Exception as exc:
                logger.warning("k-distance thất bại (bỏ qua): {}", exc)

        # 3d. Grid search
        logger.info("Bắt đầu grid search...")
        trials = grid_search(X, params_obj.clustering)

        # 3e. Log từng trial
        for trial in trials:
            log_trial(trial)

        # 3f. Select best
        try:
            best = select_best_trial(
                trials,
                params_obj.clustering.selection.primary_metric,
            )
        except RuntimeError as exc:
            logger.error("{}", exc)
            raise typer.Exit(code=1)

        # 3g. Re-fit best để lấy model object
        logger.info("Re-fitting best trial: {} {}", best.algorithm, best.params)
        model, best_labels = train_by_algorithm(best.algorithm, X, **best.params)

        # 3h. Tính near_clusters
        logger.info("Computing near-cluster scores (threshold={})...", params_obj.clustering.near_cluster_threshold)
        near_clusters_map = _compute_near_clusters(
            X, best_labels, tech_ids,
            threshold=params_obj.clustering.near_cluster_threshold,
        )
        n_near_edges = sum(len(v) for v in near_clusters_map.values())
        logger.info("near_cluster edges: {}", n_near_edges)

        # 3i. Log best run (model + labels + metadata)
        best_run_id = log_best_run(best, model, X, tech_ids)

        # Gắn near_clusters.json vào best run
        with mlflow.start_run(run_id=best_run_id, nested=True):
            mlflow.log_dict(near_clusters_map, "near_clusters.json")
        logger.info("near_clusters.json logged vào best run {}", best_run_id)

        # 3j. Register model
        register_best_model(best_run_id, params_obj.mlflow.registry_model_name)

        # 3k. Write metrics file (DVC metric)
        metrics_dict = {
            "n_clusters":         best.n_clusters,
            "n_noise":            best.n_noise,
            "noise_ratio":        best.noise_ratio,
            "silhouette":         best.silhouette,
            "davies_bouldin":     best.davies_bouldin,
            "calinski_harabasz":  best.calinski_harabasz,
            "wall_seconds":       best.wall_seconds,
            "n_near_edges":       n_near_edges,
        }
        write_metrics_file(metrics_dict, metrics_dir(tag) / "best_metrics.json")

        # 3l. Lưu model + labels locally
        m_dir = models_dir(tag)
        m_dir.mkdir(parents=True, exist_ok=True)

        model_path = m_dir / "best_model.pkl"
        with open(model_path, "wb") as f:
            pickle.dump(model, f)
        logger.info("Model saved → {}", model_path)

        labels_path = m_dir / "best_labels.parquet"
        df_labels = pd.DataFrame({
            "tech_id":    tech_ids,
            "cluster_id": best_labels.tolist(),
        })
        df_labels.to_parquet(labels_path, index=False)
        logger.info("Labels saved → {} ({} rows)", labels_path, len(df_labels))

    # 4. Summary
    print(f"\n{'='*55}")
    print(f"  Stage 03 TRAIN hoàn tất | tag={tag}")
    print(f"{'='*55}")
    print(f"  Algorithm        : {best.algorithm}")
    print(f"  Best params      : {best.params}")
    print(f"  n_clusters       : {best.n_clusters}")
    print(f"  Silhouette       : {best.silhouette:.4f}" if best.silhouette else "  Silhouette       : N/A")
    print(f"  Davies-Bouldin   : {best.davies_bouldin:.4f}" if best.davies_bouldin else "  Davies-Bouldin   : N/A")
    print(f"  Noise ratio      : {best.noise_ratio:.3f}  ({best.n_noise}/{meta.n_techs} noise)")
    print(f"  Near-cluster edges: {n_near_edges}")
    print(f"  MLflow best run  : {best_run_id}")
    print(f"{'='*55}\n")

    return 0


if __name__ == "__main__":
    app()
