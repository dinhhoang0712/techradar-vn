"""
Gộp tất cả nguồn feature → matrix X (np.ndarray) đầu vào cho Scikit-learn.

Đây là điểm "bottleneck" duy nhất giữa stage features và stage train. Nếu cần
thêm/bớt nguồn feature thì chỉ sửa hàm `build_feature_matrix` ở đây.
"""

from __future__ import annotations

import dataclasses
import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

import numpy as np
import pandas as pd
import scipy.sparse as sp
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import (
    MinMaxScaler,
    RobustScaler,
    StandardScaler,
)

from conf.config import FeatureParams

logger = logging.getLogger(__name__)


@dataclass
class FeatureMeta:
    """
    Metadata mô tả feature matrix — ghi cùng X.npy để phục vụ debugging và
    feature importance.

    Fields:
        tech_ids:               thứ tự row của X.
        feature_groups:         {group_name: (col_start, col_end)} — slice cột
                                ứng với từng nguồn (fastrp, content, company_tfidf…).
        scaler_name:            "standard" | "minmax" | "robust".
        reduce_dim:             {"method": str, "n_components": int} | None.
        original_dim:           D trước khi reduce.
        final_dim:              D sau reduce.
        n_techs:                row count.
        n_techs_dropped:        row bị drop (degree quá thấp / no signal).
    """
    tech_ids: list[str]
    feature_groups: dict[str, tuple[int, int]]
    scaler_name: str
    reduce_dim: dict | None
    original_dim: int
    final_dim: int
    n_techs: int
    n_techs_dropped: int


def _reindex(df: pd.DataFrame, tech_ids: list[str], fill: float = 0.0) -> pd.DataFrame:
    """Reindex DataFrame theo tech_ids, fill giá trị thiếu bằng fill."""
    return df.set_index("tech_id").reindex(tech_ids, fill_value=fill).reset_index()


def _make_scaler(name: str):
    return {"standard": StandardScaler, "minmax": MinMaxScaler, "robust": RobustScaler}[name]()


def build_feature_matrix(
    df_technologies: pd.DataFrame,
    gds_features: dict[str, pd.DataFrame],
    content_emb: pd.DataFrame,
    graph_stats: pd.DataFrame,
    company_tfidf: tuple[sp.csr_matrix, list[str]] | None,
    job_tfidf: tuple[sp.csr_matrix, list[str]] | None,
    params: FeatureParams,
) -> tuple[np.ndarray, FeatureMeta]:
    """
    Gộp tất cả feature sources → X dense float32 + FeatureMeta.
    """
    tech_ids = df_technologies["tech_id"].tolist()
    blocks: list[np.ndarray] = []
    feature_groups: dict[str, tuple[int, int]] = {}
    col = 0

    def add_block(name: str, arr: np.ndarray) -> None:
        nonlocal col
        if sp.issparse(arr):
            arr = arr.toarray()
        arr = np.asarray(arr, dtype=np.float32)
        blocks.append(arr)
        feature_groups[name] = (col, col + arr.shape[1])
        col += arr.shape[1]

    # 1. Graph stats (neighborhood counts + log + ratio)
    stat_cols = [c for c in graph_stats.columns if c != "tech_id"]
    add_block("graph_stats", _reindex(graph_stats, tech_ids)[stat_cols].values)

    # 2. PageRank
    if "pagerank" in gds_features:
        pr = _reindex(gds_features["pagerank"], tech_ids)[["pagerank"]].values
        add_block("pagerank", pr)

    # 3. Degree centrality
    if "degree" in gds_features:
        deg_cols = ["degree_in", "degree_out", "degree_total"]
        deg = _reindex(gds_features["degree"], tech_ids)[deg_cols].values
        add_block("degree", deg)

    # 4. Louvain one-hot top-K
    if "louvain" in gds_features:
        top_k = params.louvain.top_k_communities
        louvain_ids = _reindex(gds_features["louvain"], tech_ids)["louvain_community"].values
        top_communities = (
            pd.Series(louvain_ids).value_counts().head(top_k).index.tolist()
        )
        onehot = np.zeros((len(tech_ids), top_k), dtype=np.float32)
        for k, cid in enumerate(top_communities):
            onehot[:, k] = (louvain_ids == cid).astype(np.float32)
        add_block("louvain_onehot", onehot)

    # 5. WCC
    if "wcc" in gds_features:
        wcc = _reindex(gds_features["wcc"], tech_ids)[["wcc_id"]].values
        add_block("wcc", wcc)

    # 6. FastRP hoặc Node2Vec
    if "fastrp" in gds_features:
        fp_cols = [c for c in gds_features["fastrp"].columns if c.startswith("fastrp_")]
        fp = _reindex(gds_features["fastrp"], tech_ids)[fp_cols].values
        add_block("fastrp", fp)
    elif "node2vec" in gds_features:
        nv_cols = [c for c in gds_features["node2vec"].columns if c.startswith("node2vec_")]
        nv = _reindex(gds_features["node2vec"], tech_ids)[nv_cols].values
        add_block("node2vec", nv)

    # 7. Content embedding (768d)
    emb_cols = [c for c in content_emb.columns if c.startswith("article_emb_")]
    ce = _reindex(content_emb, tech_ids)[emb_cols].values
    add_block("content_emb", ce)

    # 8. Company TF-IDF
    if company_tfidf is not None and params.use_company_tfidf:
        add_block("company_tfidf", company_tfidf[0])

    # 9. Job TF-IDF
    if job_tfidf is not None and params.use_job_tfidf:
        add_block("job_tfidf", job_tfidf[0])

    # Ghép tất cả thành ma trận dense
    X = np.hstack(blocks).astype(np.float32)
    original_dim = X.shape[1]
    logger.info("Feature matrix trước scale: shape=%s", X.shape)

    # Scale từng group riêng
    transformers = [
        (name, _make_scaler(params.scaler), list(range(start, end)))
        for name, (start, end) in feature_groups.items()
    ]
    ct = ColumnTransformer(transformers, remainder="drop")
    X = ct.fit_transform(X).astype(np.float32)

    # Áp dụng feature_weights — nhân từng block sau scale
    if params.feature_weights:
        for name, w in params.feature_weights.items():
            if name in feature_groups and w != 1.0:
                s, e = feature_groups[name]
                X[:, s:e] *= w
                logger.info("feature_weights: block '%s' × %.2f (cols %d..%d)", name, w, s, e - 1)

    # Thay NaN/Inf (do zero-variance columns → std=0) bằng 0 trước khi giảm chiều
    n_bad = np.isnan(X).sum() + np.isinf(X).sum()
    if n_bad > 0:
        logger.warning("Feature matrix sau scale có %d NaN/Inf — thay bằng 0.", n_bad)
        X = np.nan_to_num(X, nan=0.0, posinf=0.0, neginf=0.0)

    # Giảm chiều UMAP / PCA
    final_dim = original_dim
    reduce_meta = None
    if params.reduce_dim.enabled and params.reduce_dim.method != "none":
        n_components = params.reduce_dim.n_components
        if params.reduce_dim.method == "umap":
            import umap
            reducer = umap.UMAP(n_components=n_components, random_state=42, verbose=False)
        else:
            from sklearn.decomposition import PCA
            reducer = PCA(n_components=n_components, random_state=42)

        X = reducer.fit_transform(X).astype(np.float32)
        final_dim = X.shape[1]
        reduce_meta = {"method": params.reduce_dim.method, "n_components": n_components}
        logger.info("Sau %s: shape=%s", params.reduce_dim.method, X.shape)

    # Validate — không cho NaN/Inf vào HDBSCAN
    if np.isnan(X).any() or np.isinf(X).any():
        raise ValueError("Feature matrix chứa NaN hoặc Inf sau khi xử lý.")

    meta = FeatureMeta(
        tech_ids=tech_ids,
        feature_groups=feature_groups,
        scaler_name=params.scaler,
        reduce_dim=reduce_meta,
        original_dim=original_dim,
        final_dim=final_dim,
        n_techs=len(tech_ids),
        n_techs_dropped=0,
    )
    return X, meta


def save_features(X: np.ndarray, meta: FeatureMeta, out_dir: str | Path) -> None:
    """
    Ghi X.npy, tech_ids.parquet, feature_meta.json vào out_dir.
    """
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)

    assert len(X) == len(meta.tech_ids), "X rows != tech_ids length"

    np.save(out / "X.npy", X)
    pd.DataFrame({"tech_id": meta.tech_ids}).to_parquet(
        out / "tech_ids.parquet", index=False
    )
    (out / "feature_meta.json").write_text(
        json.dumps(dataclasses.asdict(meta), indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    logger.info("save_features: X%s → %s", X.shape, out)


def load_features(in_dir: str | Path) -> tuple[np.ndarray, FeatureMeta]:
    """Đọc X.npy + feature_meta.json. Raise FileNotFoundError nếu thiếu."""
    d = Path(in_dir)
    for f in ("X.npy", "tech_ids.parquet", "feature_meta.json"):
        if not (d / f).exists():
            raise FileNotFoundError(f"Thiếu file: {d / f}")

    X = np.load(d / "X.npy")
    raw = json.loads((d / "feature_meta.json").read_text(encoding="utf-8"))
    meta = FeatureMeta(**raw)
    return X, meta
