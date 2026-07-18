"""
Wrap Scikit-learn / hdbscan thành interface đồng nhất để Stage train + Tuner gọi.

Tất cả hàm `train_*` đều trả về `(model, labels)`:
  - model: object đã `.fit()` (DBSCAN / KMeans / HDBSCAN), KHÔNG fit lại.
  - labels: np.ndarray int — nhãn cụm; -1 nghĩa là noise (DBSCAN/HDBSCAN).
"""

from __future__ import annotations

from typing import Any, Literal

import numpy as np
from sklearn.cluster import DBSCAN, KMeans
from sklearn.preprocessing import normalize


def train_dbscan(
    X: np.ndarray,
    eps: float,
    min_samples: int,
    metric: Literal["cosine", "euclidean"] = "cosine",
) -> tuple[DBSCAN, np.ndarray]:
    """
    Fit DBSCAN với (eps, min_samples, metric).

    Yêu cầu logic:
      - Với metric="cosine": sklearn yêu cầu input đã L2-normalize, hoặc dùng
        `metric="cosine"` thẳng trong `DBSCAN(metric=...)`. Mặc định ưu tiên
        cách 1 (L2-normalize ngoài) vì cosine builtin của sklearn không hỗ trợ
        ball_tree → chậm với N lớn.
      - Trả nguyên model (chưa serialize). Caller (mlflow_logger) sẽ pickle.
      - Không in toàn bộ labels.
    """
    if metric == "cosine":
        X = normalize(X)
        metric = "euclidean"

    model = DBSCAN(eps=eps, min_samples=min_samples, metric=metric)
    model.fit(X)
    return model, model.labels_


def train_kmeans(
    X: np.ndarray,
    n_clusters: int,
    n_init: int = 10,
    random_state: int = 42,
) -> tuple[KMeans, np.ndarray]:
    """
    Fit KMeans++ với `n_clusters`.

    Yêu cầu logic:
      - `algorithm="lloyd"` (mặc định 1.5+).
      - Với data ít chiều sau UMAP (32d), KMeans là baseline ổn để so sánh.
      - Không có khái niệm noise; mọi điểm đều có label ≥ 0.
    """
    model = KMeans(
        n_clusters=n_clusters,
        n_init=n_init,
        random_state=random_state,
        algorithm="lloyd",
    )
    model.fit(X)
    return model, model.labels_


def train_hdbscan(
    X: np.ndarray,
    min_cluster_size: int,
    min_samples: int | None = None,
    cluster_selection_method: Literal["eom", "leaf"] = "eom",
    metric: str = "euclidean",
) -> tuple[Any, np.ndarray]:
    """
    Fit HDBSCAN bằng gói `hdbscan` gốc (KHÔNG dùng `sklearn.cluster.HDBSCAN`)
    — model lý tưởng cho data có ~53% node noise.

    Lý do dùng gói `hdbscan` thay vì sklearn: gói gốc cho `model.probabilities_`
    (độ tin cậy gán cụm — soft clustering), `model.outlier_scores_` (GLOSH
    outlier score, phân biệt "chắc chắn noise" với "cận biên") và
    `model.relative_validity_` (xấp xỉ DBCV — metric nội tại thiết kế riêng
    cho density-based clustering, phù hợp hơn Silhouette/Davies-Bouldin vốn
    giả định cụm dạng lồi). sklearn's HDBSCAN không expose các thuộc tính này.
    `hdbscan==0.8.x` tương thích với `scikit-learn==1.5.2` đang pin trong
    requirements.txt (chỉ xung đột với scikit-learn >= 1.6).

    Yêu cầu logic:
      - Cosine không support trực tiếp ở `hdbscan` — caller phải L2-normalize
        trước, dùng metric="euclidean" (tương đương cosine sau L2).
      - `min_samples=None` → để mặc định theo `min_cluster_size`.
      - `gen_min_span_tree=True` để có `relative_validity_` (DBCV).
      - `prediction_data=True` để có membership vector / outlier score.
    """
    import hdbscan as hdbscan_pkg

    model = hdbscan_pkg.HDBSCAN(
        min_cluster_size=min_cluster_size,
        min_samples=min_samples,
        cluster_selection_method=cluster_selection_method,
        metric=metric,
        gen_min_span_tree=True,
        prediction_data=True,
    )
    model.fit(X)
    return model, model.labels_


def train_by_algorithm(
    algorithm: Literal["dbscan", "hdbscan", "kmeans"],
    X: np.ndarray,
    **kwargs,
) -> tuple[Any, np.ndarray]:
    """
    Dispatcher. Dùng trong tuner để gọi theo tên thuật toán mà không phải if/else.

    Yêu cầu logic:
      - Validate `kwargs` đầy đủ cho từng algorithm; thiếu → KeyError rõ ràng.
      - Không nuốt exception trong khi fit (tuner sẽ catch và đánh trial là failed).
    """
    dispatch = {
        "dbscan":   train_dbscan,
        "hdbscan":  train_hdbscan,
        "kmeans":   train_kmeans,
    }
    if algorithm not in dispatch:
        raise KeyError(
            f"Unknown algorithm '{algorithm}'. "
            f"Supported: {list(dispatch.keys())}"
        )
    return dispatch[algorithm](X, **kwargs)
