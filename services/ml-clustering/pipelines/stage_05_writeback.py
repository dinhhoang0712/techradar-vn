"""
Stage 05 — WRITEBACK (optional): tạo node :Cluster và ghi relationship :BELONGS_TO,
:NEAR_CLUSTER từ kết quả clustering về Neo4j.

Mặc định bị disable trong `params.yaml` để tránh ghi rác lên DB chung khi đang
thử nghiệm. Bật khi đã chốt model & labels.

CLI:
    python -m pipelines.stage_05_writeback --params params.yaml

Output:
    Neo4j — node :Cluster (mới hoặc MERGE)
           (:Technology)-[:BELONGS_TO  {score: 1.0}]->(:Cluster)
           (:Technology)-[:NEAR_CLUSTER {score: 0.xx}]->(:Cluster)
"""

import json
import math
import tempfile
from pathlib import Path
from typing import Any

import mlflow
import pandas as pd
import typer
from loguru import logger
from neo4j import Driver

app = typer.Typer(add_completion=False, help="Write cluster nodes & relationships to Neo4j")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _chunks(lst: list, size: int):
    """Chia list thành các chunk kích thước `size`."""
    for i in range(0, len(lst), size):
        yield lst[i : i + size]


def _run_batched(driver: Driver, cypher: str, rows: list[dict], batch_size: int) -> int:
    """
    Chạy `cypher` với tham số `rows` chia thành batch, trả tổng số record xử lý.
    Dùng UNWIND $rows bên trong cypher.
    """
    total = 0
    n_batches = math.ceil(len(rows) / batch_size)
    for i, chunk in enumerate(_chunks(rows, batch_size), 1):
        with driver.session() as session:
            session.run(cypher, {"rows": chunk})
        total += len(chunk)
        logger.debug("Batch {}/{} — {} rows written", i, n_batches, len(chunk))
    return total


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

@app.command()
def main(
    params: str = typer.Option("params.yaml", help="Đường dẫn params.yaml"),
    run_id: str = typer.Option(..., help="MLflow run_id của best run (để đọc near_clusters.json)"),
    dry_run: bool = typer.Option(True, "--dry-run/--no-dry-run", help="Chỉ in plan, không ghi thật"),
) -> None:
    """
    Yêu cầu logic (theo thứ tự):

      1. `params_obj = load_params(params)`. Nếu `params_obj.writeback.enabled is False`:
            in cảnh báo + exit 0 (idempotent, không lỗi).

      2. Load dữ liệu đầu vào:
           a. `data/models/<tag>/best_labels.parquet`  → DataFrame {tech_id, cluster_id}.
           b. `data/labels/<tag>/cluster_labels.json`  → {cluster_id: ClusterLabel}
              (ClusterLabel có ít nhất field: name, size).
           c. `near_clusters_map` từ MLflow artifact:
                client = mlflow.tracking.MlflowClient()
                artifact_path = client.download_artifacts(run_id, "near_clusters.json")
                near_clusters_map = json.load(open(artifact_path))
              → {tech_id (str): [{cluster_id, score}, ...]}

      3. Nếu `dry_run=True`: in 5 row của mỗi dataset, exit 0.

      4. Mở Neo4j driver (`get_settings()`).

      5. (Optional) Clean writeback — xoá relationship cũ trước khi ghi mới
         để đảm bảo kết quả idempotent khi chạy lại.
         Chỉ chạy nếu `params_obj.writeback.clean_before_write` là True.

      6. Tạo node :Cluster (chạy trước relationship).

      7. Ghi relationship :BELONGS_TO (primary cluster).

      8. Ghi relationship :NEAR_CLUSTER (soft link).

      9. Verify: đếm relationship đã ghi, cảnh báo nếu lệch.

      10. Đóng driver, return 0.
    """
    from conf.config import labels_dir, load_params, models_dir
    from src.data.neo4j_loader import close_driver, get_driver, run_query

    # 1. Load params
    params_obj = load_params(params)
    tag = params_obj.snapshot.tag
    wb = params_obj.writeback

    if not wb.enabled:
        logger.warning(
            "writeback.enabled = false trong params.yaml. "
            "Bật lên khi đã chốt model & labels rồi chạy lại."
        )
        raise typer.Exit(code=0)

    logger.info("Stage 05 — WRITEBACK | tag={} dry_run={}", tag, dry_run)

    # 2a. best_labels.parquet
    labels_path = models_dir(tag) / "best_labels.parquet"
    if not labels_path.exists():
        logger.error("Không tìm thấy: {}", labels_path)
        raise typer.Exit(code=1)
    df_labels: pd.DataFrame = pd.read_parquet(labels_path)
    logger.info("best_labels: {} rows, {} clusters", len(df_labels), df_labels["cluster_id"].nunique())

    # 2b. cluster_labels.json
    cl_path = labels_dir(tag) / "cluster_labels.json"
    if not cl_path.exists():
        logger.error("Không tìm thấy: {}", cl_path)
        raise typer.Exit(code=1)
    cluster_labels_raw: dict[str, Any] = json.loads(cl_path.read_text(encoding="utf-8"))
    # key là str(cluster_id) theo save_cluster_labels convention
    cluster_meta: list[dict] = [
        {
            "cluster_id": int(k),
            "cluster_label": v.get("label", f"Cluster_{k}"),
            "size": v.get("member_count", 0),
        }
        for k, v in cluster_labels_raw.items()
    ]
    logger.info("cluster_labels.json: {} cụm", len(cluster_meta))

    # 2c. near_clusters.json từ MLflow
    mlflow_params = params_obj.mlflow
    mlflow.set_tracking_uri(mlflow_params.tracking_uri)
    client = mlflow.tracking.MlflowClient()
    with tempfile.TemporaryDirectory() as tmp_dir:
        local_artifact = client.download_artifacts(run_id, "near_clusters.json", tmp_dir)
        with open(local_artifact, encoding="utf-8") as f:
            near_clusters_map: dict[str, list[dict]] = json.load(f)
    logger.info("near_clusters.json: {} techs có near-cluster entries", len(near_clusters_map))

    # 3. Dry-run: in preview rồi exit
    if dry_run:
        logger.info("=== DRY-RUN MODE — không ghi vào Neo4j ===")
        print("\n[best_labels] 5 rows đầu:")
        print(df_labels.head())
        print("\n[cluster_meta] 5 cụm đầu:")
        for row in cluster_meta[:5]:
            print(" ", row)
        print("\n[near_clusters] 5 techs đầu:")
        for tid, entries in list(near_clusters_map.items())[:5]:
            print(f"  {tid}: {entries[:3]}")
        raise typer.Exit(code=0)

    # 4. Kết nối Neo4j
    driver = get_driver()
    logger.info("Neo4j driver ready")

    try:
        # 5. Clean cũ nếu cần
        if wb.clean_before_write:
            all_tech_ids = df_labels["tech_id"].tolist()
            logger.info("Clean :BELONGS_TO/:NEAR_CLUSTER cũ cho {} techs ...", len(all_tech_ids))
            # Chạy batch vì IN $list có giới hạn
            for chunk in _chunks(all_tech_ids, wb.apoc_batch_size):
                with driver.session() as session:
                    session.run(
                        """
                        UNWIND $tech_ids AS tid
                        MATCH (t:Technology) WHERE elementId(t) = tid
                        MATCH (t)-[r:BELONGS_TO|NEAR_CLUSTER]->(:Cluster)
                        DELETE r
                        """,
                        {"tech_ids": chunk},
                    )
            logger.info("Clean xong.")

        # 6. Tạo node :Cluster
        logger.info("Tạo {} node :Cluster ...", len(cluster_meta))
        with driver.session() as session:
            session.run(
                """
                UNWIND $clusters AS c
                MERGE (cl:Cluster {cluster_id: c.cluster_id})
                SET cl.name       = c.cluster_label,
                    cl.size       = c.size,
                    cl.updated_at = datetime()
                """,
                {"clusters": cluster_meta},
            )
        logger.info(":Cluster nodes OK")

        # 7. Ghi :BELONGS_TO
        belongs_to_rows = [
            {"tech_id": str(row["tech_id"]), "cluster_id": int(row["cluster_id"])}
            for _, row in df_labels.iterrows()
            if int(row["cluster_id"]) != -1
        ]
        logger.info("Ghi {} :BELONGS_TO relationships ...", len(belongs_to_rows))
        _run_batched(
            driver,
            """
            UNWIND $rows AS row
            MATCH (t:Technology) WHERE elementId(t) = row.tech_id
            MATCH (c:Cluster {cluster_id: row.cluster_id})
            MERGE (t)-[r:BELONGS_TO]->(c)
            SET r.score      = 1.0,
                r.updated_at = datetime()
            """,
            belongs_to_rows,
            wb.apoc_batch_size,
        )
        logger.info(":BELONGS_TO written")

        # 8. Ghi :NEAR_CLUSTER
        threshold = params_obj.clustering.near_cluster_threshold
        near_rows: list[dict] = []
        for tech_id, entries in near_clusters_map.items():
            for entry in entries:
                score = float(entry.get("score", 0.0))
                if score >= threshold:
                    near_rows.append({
                        "tech_id": str(tech_id),
                        "cluster_id": int(entry["cluster_id"]),
                        "score": score,
                    })
        logger.info(
            "Ghi {} :NEAR_CLUSTER relationships (threshold={}) ...",
            len(near_rows), threshold,
        )
        _run_batched(
            driver,
            """
            UNWIND $rows AS row
            MATCH (t:Technology) WHERE elementId(t) = row.tech_id
            MATCH (c:Cluster {cluster_id: row.cluster_id})
            MERGE (t)-[r:NEAR_CLUSTER]->(c)
            SET r.score      = row.score,
                r.updated_at = datetime()
            """,
            near_rows,
            wb.apoc_batch_size,
        )
        logger.info(":NEAR_CLUSTER written")

        # 9. Verify
        res_belongs = run_query(
            "MATCH (:Technology)-[:BELONGS_TO]->(:Cluster) RETURN count(*) AS n"
        )
        res_near = run_query(
            "MATCH (:Technology)-[:NEAR_CLUSTER]->(:Cluster) RETURN count(*) AS n"
        )
        n_belongs_db = res_belongs[0]["n"]
        n_near_db = res_near[0]["n"]

        logger.info(
            "Verify: :BELONGS_TO={} (kỳ vọng {}), :NEAR_CLUSTER={} (kỳ vọng {})",
            n_belongs_db, len(belongs_to_rows),
            n_near_db, len(near_rows),
        )
        if n_belongs_db < len(belongs_to_rows):
            logger.warning(
                "BELONGS_TO thiếu {} — có thể một số tech_id không còn trong DB.",
                len(belongs_to_rows) - n_belongs_db,
            )
        if n_near_db < len(near_rows):
            logger.warning(
                "NEAR_CLUSTER thiếu {} — kiểm tra lại near_clusters.json.",
                len(near_rows) - n_near_db,
            )

    finally:
        # 10. Đóng driver
        close_driver()

    logger.info("Stage 05 hoàn tất.")
    return 0


if __name__ == "__main__":
    app()
