"""
Content feature cho Technology dựa trên embedding của Article có MENTIONS.

Lý do: name + description hiện tại của Technology trong DB rất nghèo
(`description = "Mentioned in Unknown"` cho hầu hết). Article embedding (768d
multilingual-e5-base) đã có sẵn cho 526/834 bài → ta lấy mean/weighted mean
các embedding của bài có MENTIONS từng tech.

Đây là "content" feature mạnh nhất hiện có vì nó nắm bắt bối cảnh ngữ nghĩa
mà tech được nhắc tới.
"""

from __future__ import annotations

import functools
import logging
from typing import Literal

import numpy as np
import pandas as pd
from sklearn.preprocessing import normalize

logger = logging.getLogger(__name__)

EMB_DIM = 768
EMB_COLS = [f"article_emb_{i}" for i in range(EMB_DIM)]
_LAMBDA = 1 / 365  # decay rate cho weighted_by_recency

def aggregate_article_embeddings(
    df_articles: pd.DataFrame,
    df_edges_mentions: pd.DataFrame,
    df_technologies: pd.DataFrame,
    method: Literal["mean", "weighted_by_recency"] = "mean",
    min_articles_per_tech: int = 1,
) -> pd.DataFrame:
    """
    Tính vector content (768d) cho mỗi tech từ embedding trung bình của
    các Article -[:MENTIONS]-> tech.

    Trả về DataFrame: tech_id | article_emb_0…767 | content_n_articles
    Tech không đủ bài → zero-vector + content_n_articles=0.
    """
    # Chỉ giữ Article có embedding hợp lệ
    df_art = df_articles[df_articles["embedding"].notna()].copy()
    df_art["embedding"] = df_art["embedding"].apply(
        lambda e: np.array(e, dtype=np.float32)
    )

    # Join: article → tech qua edges
    merged = df_edges_mentions.merge(
        df_art[["article_id", "embedding", "published_date"]],
        on="article_id",
        how="inner",
    )

    all_tech_ids = df_technologies["tech_id"].tolist()
    results = []

    for tech_id, group in merged.groupby("tech_id"):
        n = len(group)
        if n < min_articles_per_tech:
            results.append({
                "tech_id": tech_id,
                **dict(zip(EMB_COLS, np.zeros(EMB_DIM, dtype=np.float32))),
                "content_n_articles": 0,
            })
            continue

        # Stack embeddings → normalize L2 từng vector trước khi aggregate
        embs = np.vstack(group["embedding"].values)           # (n, 768)
        embs = normalize(embs, norm="l2")                      # cosine-safe

        if method == "weighted_by_recency":
            dates = pd.to_datetime(group["published_date"], errors="coerce")
            latest = dates.max()
            delta_days = (latest - dates).dt.days.fillna(0).values.astype(float)
            weights = np.exp(-_LAMBDA * delta_days)
            weights /= weights.sum()
            vec = (embs * weights[:, None]).sum(axis=0)
        else:
            vec = embs.mean(axis=0)

        results.append({
            "tech_id": tech_id,
            **dict(zip(EMB_COLS, vec.astype(np.float32))),
            "content_n_articles": n,
        })

    df_result = pd.DataFrame(results)

    # Đảm bảo đủ tất cả tech — tech thiếu → zero-vector
    present = set(df_result["tech_id"])
    missing = [t for t in all_tech_ids if t not in present]
    if missing:
        zeros = pd.DataFrame([{
            "tech_id": t,
            **dict(zip(EMB_COLS, np.zeros(EMB_DIM, dtype=np.float32))),
            "content_n_articles": 0,
        } for t in missing])
        df_result = pd.concat([df_result, zeros], ignore_index=True)

    df_result = df_result.set_index("tech_id").loc[all_tech_ids].reset_index()
    logger.info(
        "aggregate_article_embeddings: %d tech, %d có bài, %d zero-vector",
        len(df_result),
        (df_result["content_n_articles"] > 0).sum(),
        (df_result["content_n_articles"] == 0).sum(),
    )
    return df_result


@functools.lru_cache(maxsize=2)
def _get_model(model_name: str):
    """Load SentenceTransformer — cache trong RAM, không reload mỗi lần gọi."""
    from sentence_transformers import SentenceTransformer
    logger.info("Loading embedder model: %s", model_name)
    return SentenceTransformer(model_name)


def embed_tech_names_fallback(
    tech_names: list[str],
    model_name: str = "intfloat/multilingual-e5-base",
) -> np.ndarray:
    """
    Fallback: encode tên tech bằng sentence-transformers khi không có bài viết nào.
    Prefix "passage:" theo quy định của e5 model.
    Trả về np.ndarray shape (N, 768).
    """
    model = _get_model(model_name)
    prefixed = [f"passage: {name}" for name in tech_names]
    embeddings = model.encode(prefixed, normalize_embeddings=True, show_progress_bar=False)
    return embeddings.astype(np.float32)


def fill_missing_content_with_name_embedding(
    df_content: pd.DataFrame,
    df_technologies: pd.DataFrame,
) -> pd.DataFrame:
    """
    Tech nào content_n_articles == 0 → thay embedding bằng encode(name).
    Giữ nguyên cột content_n_articles. L2-normalize lại toàn bộ sau merge.
    """
    df = df_content.copy()
    mask = df["content_n_articles"] == 0

    if mask.sum() == 0:
        logger.info("fill_missing_content: không có tech nào cần fallback.")
        return df

    # Lấy tên của các tech thiếu embedding
    missing_ids = df.loc[mask, "tech_id"].tolist()
    name_map = df_technologies.set_index("tech_id")["name"].to_dict()
    names = [name_map.get(t, t) for t in missing_ids]

    fallback_embs = embed_tech_names_fallback(names)  # (N, 768)

    for i, tech_id in enumerate(missing_ids):
        idx = df.index[df["tech_id"] == tech_id][0]
        df.loc[idx, EMB_COLS] = fallback_embs[i]

    # L2-normalize toàn bộ embedding sau khi merge
    emb_matrix = df[EMB_COLS].values.astype(np.float32)
    emb_matrix = normalize(emb_matrix, norm="l2")
    df[EMB_COLS] = emb_matrix

    logger.info("fill_missing_content: đã fallback %d tech bằng name embedding.", mask.sum())
    return df
