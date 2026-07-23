"""
Tạo snapshot raw data từ Neo4j → folder `data/raw/snapshot_<tag>/`.

Snapshot là input bất biến của các stage tiếp theo. DVC sẽ track folder này
qua `dvc.yaml`, nên bất cứ thay đổi nào (re-crawl, re-embed Article, …) đều
phải tạo snapshot mới (đổi `tag` trong `params.yaml`).
"""

from __future__ import annotations

import concurrent.futures
import dataclasses
import json
import logging
import subprocess
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from conf.config import get_settings, snapshot_dir

from src.data import neo4j_loader as loader

logger = logging.getLogger(__name__)

# Kept small on purpose: a remote AuraDB free-tier instance has a limited connection budget, so
# this overlaps round-trip latency across a handful of concurrent Cypher calls rather than trying
# to run every fetch_* at once.
_MAX_PARALLEL_QUERIES = 4


@dataclass
class SnapshotMeta:
    """
    Metadata mô tả 1 snapshot — ghi cùng vào `meta.json` để truy vết.

    Fields:
        tag:                  Tên snapshot (ví dụ "2026-05-06").
        created_at:           ISO timestamp lúc tạo.
        neo4j_uri:            URI Neo4j (đã chuẩn hoá ssc).
        node_counts:          {label: count}.
        edge_counts:          {rel_type: count}.
        article_embedding_dim: 768 (sanity check).
        article_with_embedding: số Article có embedding.
        rows_per_file:        {filename: nrows}.
        git_commit:           commit hash lúc snapshot (best-effort).
    """

    tag: str
    created_at: str
    neo4j_uri: str
    node_counts: dict[str, int]
    edge_counts: dict[str, int]
    article_embedding_dim: int
    article_with_embedding: int
    rows_per_file: dict[str, int]
    git_commit: str | None


def take_snapshot(tag: str, min_tech_degree: int = 1) -> SnapshotMeta:
    """
    Pull toàn bộ data cần cho clustering từ Neo4j → data/raw/snapshot_<tag>/.
    Raise nếu folder đã tồn tại và không rỗng (tránh ghi đè vô tình).
    """
    out_dir = snapshot_dir(tag)

    # Bảo vệ: không ghi đè snapshot cũ
    if any(out_dir.iterdir()):
        raise FileExistsError(f"Snapshot '{tag}' đã tồn tại tại {out_dir}. Dùng --force để ghi đè.")

    logger.info("Bắt đầu snapshot tag='%s' → %s", tag, out_dir)

    # ------------------------------------------------------------------ fetch
    fetch_map = {
        "technologies": (loader.fetch_technologies, {"min_degree": min_tech_degree}),
        "companies": (loader.fetch_companies, {}),
        "articles": (loader.fetch_articles, {"only_with_embedding": False}),
        "jobs": (loader.fetch_jobs, {}),
        "skills": (loader.fetch_skills, {}),
        "edges_article_mentions_tech": (loader.fetch_edges_article_mentions_tech, {}),
        "edges_company_uses_tech": (loader.fetch_edges_company_uses_tech, {}),
        "edges_job_requires_tech": (loader.fetch_edges_job_requires_tech, {}),
        "edges_job_requires_skill": (loader.fetch_edges_job_requires_skill, {}),
        "edges_tech_related_tech": (loader.fetch_edges_tech_related_tech, {}),
        "edges_skill_is_technology": (loader.fetch_edges_skill_is_technology, {}),
    }

    # Các fetch_* đọc data rời nhau (node/edge riêng biệt) nên chạy song song
    # qua 1 thread pool nhỏ, dùng chung driver singleton — mỗi Cypher call vẫn
    # là 1 session Neo4j riêng nhưng round-trip latency được overlap thay vì
    # cộng dồn tuần tự (đặc biệt quan trọng với AuraDB ở xa).
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(_MAX_PARALLEL_QUERIES, len(fetch_map))) as pool:
        fetch_futures = {name: pool.submit(fn, **kwargs) for name, (fn, kwargs) in fetch_map.items()}
        dfs = {name: fut.result() for name, fut in fetch_futures.items()}

    rows_per_file: dict[str, int] = {}
    for name, df in dfs.items():
        loader.save_parquet(df, out_dir / f"{name}.parquet")
        rows_per_file[name] = len(df)

    # ------------------------------------------------------------------ verify embedding dim
    df_articles = dfs["articles"]
    emb_dim = 0
    article_with_embedding = 0
    if len(df_articles) > 0 and "embedding" in df_articles.columns:
        df_with_emb = df_articles[df_articles["embedding"].notna()]
        article_with_embedding = len(df_with_emb)
        if article_with_embedding > 0:
            sample_emb = df_with_emb.iloc[0]["embedding"]
            emb_dim = len(sample_emb)
            if emb_dim != 768:
                raise ValueError(f"Article embedding dim={emb_dim}, kỳ vọng 768.")
    else:
        logger.info("Không có Article trong DB — bỏ qua kiểm tra embedding.")

    # ------------------------------------------------------------------ node / edge counts từ DB
    # Mỗi label/rel type là 1 round-trip độc lập → chạy song song qua cùng
    # thread pool nhỏ như trên thay vì tuần tự từng cái.
    node_labels = ("Technology", "Article", "Company", "Job", "Skill")
    rel_candidates = ("MENTIONS", "USES", "REQUIRES", "RELATED_TO", "IS_TECHNOLOGY", "HIRES_FOR")

    with concurrent.futures.ThreadPoolExecutor(max_workers=min(_MAX_PARALLEL_QUERIES, len(node_labels) + 1)) as pool:
        node_futures = {
            label: pool.submit(loader.run_query, f"MATCH (n:{label}) RETURN count(n) AS c") for label in node_labels
        }
        # Lấy danh sách rel types thực có trong DB trước để tránh query thừa
        rel_types_future = pool.submit(
            loader.run_query,
            "CALL db.relationshipTypes() YIELD relationshipType RETURN relationshipType",
        )

        node_counts = {label: fut.result()[0]["c"] for label, fut in node_futures.items()}
        existing_rels = {r["relationshipType"] for r in rel_types_future.result()}

    edge_counts = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(_MAX_PARALLEL_QUERIES, len(rel_candidates))) as pool:
        edge_futures = {
            rel: pool.submit(loader.run_query, f"MATCH ()-[r:{rel}]->() RETURN count(r) AS c")
            for rel in rel_candidates
            if rel in existing_rels
        }
        for rel in rel_candidates:
            edge_counts[rel] = edge_futures[rel].result()[0]["c"] if rel in edge_futures else 0

    # ------------------------------------------------------------------ git commit
    git_commit: str | None = None
    try:
        git_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        dirty = subprocess.check_output(["git", "status", "--porcelain"], text=True).strip()
        if dirty:
            logger.warning("Repo có uncommitted changes khi snapshot.")
    except Exception:
        logger.warning("Không lấy được git commit.")

    # ------------------------------------------------------------------ ghi meta
    meta = SnapshotMeta(
        tag=tag,
        created_at=datetime.now(UTC).isoformat(),
        neo4j_uri=get_settings().active_neo4j_uri,
        node_counts=node_counts,
        edge_counts=edge_counts,
        article_embedding_dim=emb_dim,
        article_with_embedding=article_with_embedding,
        rows_per_file=rows_per_file,
        git_commit=git_commit,
    )

    meta_path = out_dir / "meta.json"
    meta_path.write_text(
        json.dumps(dataclasses.asdict(meta), indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    logger.info("Snapshot hoàn tất. meta → %s", meta_path)

    loader.close_driver()
    return meta


def load_snapshot_meta(tag: str) -> SnapshotMeta:
    """Đọc data/raw/snapshot_<tag>/meta.json → SnapshotMeta."""
    meta_path = snapshot_dir(tag) / "meta.json"
    if not meta_path.exists():
        raise FileNotFoundError(f"meta.json không tồn tại: {meta_path}")
    data = json.loads(meta_path.read_text(encoding="utf-8"))
    return SnapshotMeta(**data)


def list_snapshot_files(tag: str) -> dict[str, Path]:
    """
    Trả về dict tên-logic → path tuyệt đối các file parquet.
    Raise nếu thiếu bất kỳ file nào.
    """
    out_dir = snapshot_dir(tag)
    keys = [
        "technologies",
        "companies",
        "articles",
        "jobs",
        "skills",
        "edges_article_mentions_tech",
        "edges_company_uses_tech",
        "edges_job_requires_tech",
        "edges_job_requires_skill",
        "edges_tech_related_tech",
        "edges_skill_is_technology",
    ]
    result: dict[str, Path] = {}
    missing = []
    for key in keys:
        p = out_dir / f"{key}.parquet"
        if not p.exists():
            missing.append(str(p))
        else:
            result[key] = p.resolve()

    if missing:
        raise FileNotFoundError(f"Snapshot '{tag}' thiếu {len(missing)} file:\n" + "\n".join(missing))
    return result
