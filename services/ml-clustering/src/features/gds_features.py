"""
Feature engineering bằng Neo4j GDS (Graph Data Science).

Quy trình chuẩn:
    1. Stage `data` đã pull dữ liệu xong → giờ chỉ cần `gds.graph.project.cypher`
       trực tiếp trên AuraDB (GDS plugin có sẵn — đã verify 393 procs).
    2. Chạy lần lượt FastRP / Node2Vec / PageRank / Louvain / degreeCentrality.
    3. Đọc kết quả về thành DataFrame index theo `tech_id`.
    4. Drop named graph để giải phóng bộ nhớ.

LƯU Ý trên AuraDB:
  - Free tier giới hạn ~50k node trong projection — data hiện tại (~4,000 node
    sau khi gộp tất cả label) vẫn rất nhỏ, không cần lo.
  - Mỗi lần `gds.fastRP.stream` đều mutate trong session, nên chạy tuần tự.
  - Không gọi `gds.*.write` để tránh ghi rác lên DB chung; dùng `.stream`.
"""

from __future__ import annotations

import logging
import time

import numpy as np
import pandas as pd
from neo4j import Driver

logger = logging.getLogger(__name__)


def _run(driver: Driver, cypher: str, params: dict | None = None) -> list[dict]:
    """Helper chạy Cypher trong session ngắn hạn."""
    with driver.session() as s:
        return s.run(cypher, parameters=params or {}).data()


def _fill_missing_tech(
    df: pd.DataFrame,
    all_tech_ids: list[str],
    emb_cols: list[str],
) -> pd.DataFrame:
    """
    Đảm bảo df có đủ tất cả tech_id trong all_tech_ids.
    Tech nào thiếu → fill 0-vector cho các cột embedding.
    """
    df = df.set_index("tech_id")
    missing = set(all_tech_ids) - set(df.index)
    if missing:
        zeros = pd.DataFrame(
            0.0,
            index=list(missing),
            columns=emb_cols,
        )
        df = pd.concat([df, zeros])
    return df.loc[all_tech_ids].reset_index()


# ---------------------------------------------------------------------------
# Embedding methods
# ---------------------------------------------------------------------------

def compute_fastrp(
    driver: Driver,
    graph_name: str,
    embedding_dim: int = 64,
    iteration_weights: list[float] | None = None,
    normalization_strength: float = 0.0,
) -> pd.DataFrame:
    """
    Chạy gds.fastRP.stream → DataFrame index tech_id, columns fastrp_0…fastrp_{D-1}.
    Tech không có trong GDS result → fill 0-vector.
    """
    weights = iteration_weights or [0.0, 1.0, 1.0, 1.0]
    t0 = time.time()

    rows = _run(driver,
        """
        CALL gds.fastRP.stream($graph_name, {
            embeddingDimension:       $dim,
            iterationWeights:         $weights,
            normalizationStrength:    $norm
        })
        YIELD nodeId, embedding
        WITH gds.util.asNode(nodeId) AS n, embedding
        WHERE n:Technology
        RETURN elementId(n) AS tech_id, embedding
        """,
        {"graph_name": graph_name, "dim": embedding_dim,
         "weights": weights, "norm": normalization_strength},
    )

    cols = [f"fastrp_{i}" for i in range(embedding_dim)]
    df = pd.DataFrame(
        [{"tech_id": r["tech_id"], **dict(zip(cols, r["embedding"]))} for r in rows]
    )
    logger.info("compute_fastrp: %d rows, %.1fs", len(df), time.time() - t0)
    return df


def compute_node2vec(
    driver: Driver,
    graph_name: str,
    embedding_dim: int = 64,
    walk_length: int = 10,
    walks_per_node: int = 10,
) -> pd.DataFrame:
    """
    gds.node2vec.stream → DataFrame index tech_id, columns node2vec_0…node2vec_{D-1}.
    """
    t0 = time.time()
    rows = _run(driver,
        """
        CALL gds.node2vec.stream($graph_name, {
            embeddingDimension: $dim,
            walkLength:         $walk_length,
            walksPerNode:       $walks_per_node
        })
        YIELD nodeId, embedding
        WITH gds.util.asNode(nodeId) AS n, embedding
        WHERE n:Technology
        RETURN elementId(n) AS tech_id, embedding
        """,
        {"graph_name": graph_name, "dim": embedding_dim,
         "walk_length": walk_length, "walks_per_node": walks_per_node},
    )

    cols = [f"node2vec_{i}" for i in range(embedding_dim)]
    df = pd.DataFrame(
        [{"tech_id": r["tech_id"], **dict(zip(cols, r["embedding"]))} for r in rows]
    )
    logger.info("compute_node2vec: %d rows, %.1fs", len(df), time.time() - t0)
    return df


# ---------------------------------------------------------------------------
# Centrality
# ---------------------------------------------------------------------------

def compute_pagerank(
    driver: Driver,
    graph_name: str,
    damping: float = 0.85,
    max_iter: int = 20,
) -> pd.DataFrame:
    """
    gds.pageRank.stream → DataFrame columns: tech_id, pagerank.
    Không clip outlier — feature_pipeline xử lý log-scale.
    """
    t0 = time.time()
    rows = _run(driver,
        """
        CALL gds.pageRank.stream($graph_name, {
            dampingFactor:  $damping,
            maxIterations:  $max_iter
        })
        YIELD nodeId, score
        WITH gds.util.asNode(nodeId) AS n, score
        WHERE n:Technology
        RETURN elementId(n) AS tech_id, score AS pagerank
        """,
        {"graph_name": graph_name, "damping": damping, "max_iter": max_iter},
    )
    df = pd.DataFrame(rows)
    logger.info("compute_pagerank: %d rows, %.1fs", len(df), time.time() - t0)
    return df


def compute_degree_centrality(
    driver: Driver,
    graph_name: str,
) -> pd.DataFrame:
    """
    gds.degree.stream → columns: tech_id, degree_in, degree_out, degree_total.
    Projection UNDIRECTED nên degree_in == degree_out — vẫn export cả 3.
    """
    t0 = time.time()
    rows = _run(driver,
        """
        CALL gds.degree.stream($graph_name)
        YIELD nodeId, score
        WITH gds.util.asNode(nodeId) AS n, score
        WHERE n:Technology
        RETURN elementId(n) AS tech_id,
               toInteger(score) AS degree_in,
               toInteger(score) AS degree_out,
               toInteger(score) AS degree_total
        """,
        {"graph_name": graph_name},
    )
    df = pd.DataFrame(rows)
    logger.info("compute_degree_centrality: %d rows, %.1fs", len(df), time.time() - t0)
    return df


# ---------------------------------------------------------------------------
# Community
# ---------------------------------------------------------------------------

def compute_louvain(
    driver: Driver,
    graph_name: str,
    max_levels: int = 10,
) -> pd.DataFrame:
    """
    gds.louvain.stream → columns: tech_id, louvain_community.
    Community id là số nguyên raw — feature_pipeline sẽ one-hot top-K.
    """
    t0 = time.time()
    rows = _run(driver,
        """
        CALL gds.louvain.stream($graph_name, {maxLevels: $max_levels})
        YIELD nodeId, communityId
        WITH gds.util.asNode(nodeId) AS n, communityId
        WHERE n:Technology
        RETURN elementId(n) AS tech_id, communityId AS louvain_community
        """,
        {"graph_name": graph_name, "max_levels": max_levels},
    )
    df = pd.DataFrame(rows)
    logger.info("compute_louvain: %d rows, %.1fs", len(df), time.time() - t0)
    return df


def compute_wcc(
    driver: Driver,
    graph_name: str,
) -> pd.DataFrame:
    """
    gds.wcc.stream → columns: tech_id, wcc_id.
    Phát hiện component rời rạc trong đồ thị.
    """
    t0 = time.time()
    rows = _run(driver,
        """
        CALL gds.wcc.stream($graph_name)
        YIELD nodeId, componentId
        WITH gds.util.asNode(nodeId) AS n, componentId
        WHERE n:Technology
        RETURN elementId(n) AS tech_id, componentId AS wcc_id
        """,
        {"graph_name": graph_name},
    )
    df = pd.DataFrame(rows)
    logger.info("compute_wcc: %d rows, %.1fs", len(df), time.time() - t0)
    return df
