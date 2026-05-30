"""
Stage 01 — EXTRACT: Pull data từ Neo4j AuraDB → snapshot parquet.

Đây là điểm vào duy nhất tương tác với Neo4j ở bước offline. Chạy 1 lần khi
cần snapshot dữ liệu mới.

CLI:
    python -m pipelines.stage_01_extract --params params.yaml [--force]

Output: data/raw/snapshot_<tag>/{technologies,companies,...,edges_*.parquet, meta.json}
"""

import typer
from loguru import logger

app = typer.Typer(add_completion=False, help="Pull Neo4j → snapshot parquet")


@app.command()
def main(
    params: str = typer.Option("params.yaml", help="Đường dẫn params.yaml"),
    force: bool = typer.Option(False, "--force/--no-force", help="Ghi đè snapshot cũ nếu trùng tag"),
) -> None:
    """
    Yêu cầu logic:
      1. `params_obj = load_params(params)`.
      2. Validate kết nối Neo4j (gọi `neo4j_loader.run_query("RETURN 1")`).
      3. Gọi `snapshot.take_snapshot(tag=params_obj.snapshot.tag,
                                     min_tech_degree=params_obj.snapshot.min_tech_degree)`.
         Nếu `force=False` và snapshot dir đã tồn tại + non-empty → exit 1.
      4. In tóm tắt từ `SnapshotMeta` (counts, embedding coverage, file paths).
      5. Đóng driver (`close_driver`).
      6. Return 0 nếu OK, exit code != 0 nếu lỗi.
    """
    from conf.config import load_params, snapshot_dir
    from src.data import neo4j_loader as loader
    from src.data.snapshot import take_snapshot

    # 1. Load params
    params_obj = load_params(params)
    tag = params_obj.snapshot.tag
    logger.info("Stage 01 — EXTRACT | tag={}", tag)

    # 2. Validate Neo4j connection
    try:
        loader.run_query("RETURN 1")
        logger.info("Neo4j connection OK")
    except Exception as exc:
        logger.error("Kết nối Neo4j thất bại: {}", exc)
        raise typer.Exit(code=1)

    # 3. Handle force — xoá snapshot cũ nếu cần
    snap_dir = snapshot_dir(tag)
    if force and snap_dir.exists() and any(snap_dir.iterdir()):
        logger.warning("--force: xoá snapshot cũ tại {}", snap_dir)
        for f in snap_dir.iterdir():
            if f.is_file():
                f.unlink()

    try:
        meta = take_snapshot(
            tag=tag,
            min_tech_degree=params_obj.snapshot.min_tech_degree,
        )
    except FileExistsError as exc:
        logger.error("{}", exc)
        logger.info("Dùng --force để ghi đè, hoặc đổi snapshot.tag trong params.yaml.")
        raise typer.Exit(code=1)
    except Exception as exc:
        logger.error("take_snapshot thất bại: {}", exc)
        raise typer.Exit(code=1)

    # 4. In tóm tắt
    print(f"\n{'='*55}")
    print(f"  Snapshot '{tag}' hoàn tất")
    print(f"{'='*55}")
    print(f"  Neo4j URI        : {meta.neo4j_uri}")
    print(f"  Created at       : {meta.created_at}")
    print(f"  Git commit       : {meta.git_commit or 'N/A'}")
    print()
    print("  Node counts:")
    for label, count in meta.node_counts.items():
        print(f"    {label:<15} {count:>6}")
    print()
    print("  Edge counts:")
    for rel, count in meta.edge_counts.items():
        print(f"    {rel:<20} {count:>6}")
    print()
    print(f"  Article embedding: {meta.article_with_embedding}/{meta.node_counts.get('Article', '?')} bài có embedding ({meta.article_embedding_dim}d)")
    print()
    print("  Files:")
    for fname, nrows in meta.rows_per_file.items():
        print(f"    {fname:<40} {nrows:>6} rows")
    print(f"{'='*55}\n")

    # 5. Đóng driver
    loader.close_driver()

    return 0


if __name__ == "__main__":
    app()
