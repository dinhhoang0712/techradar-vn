"""
Stage 04 — LABEL: Gemini 2.5 Flash gán tên + mô tả cho từng cụm.

CLI:
    python -m pipelines.stage_04_label --params params.yaml [--run-id <mlflow_run>]

Output: data/labels/<tag>/cluster_labels.json
"""

import sys

import mlflow
import numpy as np
import pandas as pd
import typer
from loguru import logger

app = typer.Typer(add_completion=False, help="Auto-label clusters with Gemini")


@app.command()
def main(
    params: str = typer.Option("params.yaml", help="Đường dẫn params.yaml"),
    run_id: str | None = typer.Option(
        None,
        help="MLflow run_id của best run (nếu None → đọc từ data/models/<tag>/).",
    ),
) -> None:
    """
    Yêu cầu logic:

      1. `params_obj = load_params(params)`. `tag = params_obj.snapshot.tag`.
      2. Load `best_labels.parquet` → DataFrame `{tech_id, cluster_id}`.
      3. Load X + tech_ids: `X, meta = load_features(features_dir(tag))`.
         Đảm bảo thứ tự tech_ids khớp với labels.parquet.
      4. Load các DataFrame phụ trợ từ snapshot:
           df_technologies, df_companies, df_jobs,
           df_edges_company_uses_tech, df_edges_job_requires_tech.
      5. Build `cluster_to_members = map_cluster_to_members(labels, tech_ids)`.
      6. `cluster_labels = label_all_clusters(
                cluster_to_members, X, tech_ids, df_technologies,
                df_edges_company_uses_tech, df_companies,
                df_edges_job_requires_tech, df_jobs,
                params_obj.labeling,
            )`.
      7. `save_cluster_labels(cluster_labels, labels_dir(tag) / "cluster_labels.json")`.
      8. Nếu có `run_id`: append vào MLflow run đó như 1 artifact để đính kèm.
      9. In bảng tóm tắt: cluster_id | label | size | confidence | top-3 members.
     10. Return 0.
    """
    from conf.config import (
        features_dir,
        labels_dir,
        load_params,
        models_dir,
        snapshot_dir,
    )
    from src.features.feature_pipeline import load_features
    from src.labeling.llm_labeler import (
        label_all_clusters,
        map_cluster_to_members,
        save_cluster_labels,
    )
    from src.tracking.mlflow_logger import init_mlflow

    # 1. Load params
    params_obj = load_params(params)
    tag = params_obj.snapshot.tag
    logger.info("Stage 04 — LABEL | snapshot tag={}", tag)

    # 2. Load best_labels.parquet
    best_labels_path = models_dir(tag) / "best_labels.parquet"
    if not best_labels_path.exists():
        logger.error("Không tìm thấy best_labels.parquet: {}", best_labels_path)
        raise typer.Exit(code=1)

    df_labels: pd.DataFrame = pd.read_parquet(best_labels_path)
    logger.info("best_labels.parquet: {} rows, clusters={}", len(df_labels), df_labels["cluster_id"].nunique())

    # 3. Load X + meta; căn thứ tự tech_ids theo meta
    X, meta = load_features(features_dir(tag))
    tech_ids: list[str] = meta.tech_ids

    # Reindex df_labels theo thứ tự của tech_ids (để labels array căn với X)
    label_map: dict[str, int] = df_labels.set_index("tech_id")["cluster_id"].to_dict()
    # tech_id nào không có trong best_labels → gán noise (-1)
    labels_aligned = np.array(
        [label_map.get(tid, -1) for tid in tech_ids], dtype=np.int32
    )

    # 4. Load snapshot DataFrames
    snap_dir = snapshot_dir(tag)
    df_technologies: pd.DataFrame = pd.read_parquet(snap_dir / "technologies.parquet")
    df_companies: pd.DataFrame = pd.read_parquet(snap_dir / "companies.parquet")
    df_jobs: pd.DataFrame = pd.read_parquet(snap_dir / "jobs.parquet")
    df_edges_company: pd.DataFrame = pd.read_parquet(snap_dir / "edges_company_uses_tech.parquet")
    df_edges_job: pd.DataFrame = pd.read_parquet(snap_dir / "edges_job_requires_tech.parquet")
    logger.info(
        "Snapshot loaded: {} techs, {} companies, {} jobs",
        len(df_technologies), len(df_companies), len(df_jobs),
    )

    # 5. Build cluster_to_members
    cluster_to_members = map_cluster_to_members(labels_aligned, tech_ids)
    n_real = sum(1 for cid in cluster_to_members if cid != -1)
    logger.info("cluster_to_members: {} cụm thực (+ noise=-1)", n_real)

    # 6. Gán nhãn
    cluster_labels = label_all_clusters(
        cluster_to_members=cluster_to_members,
        X=X,
        tech_ids=tech_ids,
        df_technologies=df_technologies,
        df_edges_company_uses_tech=df_edges_company,
        df_companies=df_companies,
        df_edges_job_requires_tech=df_edges_job,
        df_jobs=df_jobs,
        params=params_obj.labeling,
    )

    # 7. Lưu
    out_dir = labels_dir(tag)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "cluster_labels.json"
    save_cluster_labels(cluster_labels, out_path)

    # 8. Log artifact vào MLflow nếu có run_id
    if run_id:
        init_mlflow(params_obj.mlflow)
        with mlflow.start_run(run_id=run_id):
            mlflow.log_artifact(str(out_path), artifact_path="labels")
        logger.info("Đã log artifact cluster_labels.json vào MLflow run {}", run_id)

    # 9. In bảng tóm tắt
    _print_summary(cluster_labels)

    return 0


def _print_summary(cluster_labels: dict) -> None:
    from src.labeling.llm_labeler import ClusterLabel

    header = f"{'ID':>4}  {'Label':<28}  {'Domain':<18}  {'Size':>5}  {'Conf':>5}  Top-3 Members"
    sep = "-" * len(header)
    print("\n" + sep)
    print(header)
    print(sep)

    for cid, lbl in sorted(cluster_labels.items()):
        top3 = ", ".join(lbl.sample_techs[:3]) if lbl.sample_techs else "-"
        status = "" if lbl.label != "UNLABELED" else " [FAIL]"
        print(
            f"{cid:>4}  {lbl.label + status:<28}  {lbl.domain:<18}"
            f"  {lbl.member_count:>5}  {lbl.confidence:>5.2f}  {top3}"
        )

    print(sep)
    n_ok = sum(1 for lb in cluster_labels.values() if lb.label != "UNLABELED")
    print(f"Tổng: {len(cluster_labels)} cụm | OK: {n_ok} | FAIL: {len(cluster_labels) - n_ok}\n")


if __name__ == "__main__":
    app()
