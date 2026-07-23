"""
Tính các chỉ số đánh giá clustering. Dùng cho tuner.score và stage 03 cuối cùng.
"""

from __future__ import annotations

from collections import Counter
from typing import Any

import numpy as np
import pandas as pd
from sklearn.metrics import (
    calinski_harabasz_score,
    davies_bouldin_score,
    silhouette_score,
)

_NAN = float("nan")
_SILHOUETTE_SAMPLE_LIMIT = 5_000


def evaluate_clustering(
    X: np.ndarray,
    labels: np.ndarray,
    model: Any | None = None,
) -> dict[str, float | int]:
    """
    Tính bộ chỉ số tiêu chuẩn.

    Tham số:
        X:      ma trận đặc trưng (đã scale, cùng không gian khi fit).
        labels: nhãn cụm; -1 = noise.
        model:  model đã fit (tuỳ chọn) — nếu là `hdbscan.HDBSCAN` với
                `gen_min_span_tree=True`, đọc thêm `relative_validity_` (DBCV).

    Trả về dict:
        {
          "n_total":           int,
          "n_clusters":        int,    # không tính noise
          "n_noise":           int,
          "noise_ratio":       float,
          "silhouette":        float,  # NaN nếu không tính được
          "davies_bouldin":    float,
          "calinski_harabasz": float,
          "dbcv":              float,  # NaN nếu model không phải hdbscan hoặc thiếu attr
          "min_cluster_size":  int,
          "max_cluster_size":  int,
          "median_cluster_size": float,
        }

    Yêu cầu logic:
      - Tính silhouette/DB/CH CHỈ trên các điểm có label != -1; nếu sau khi loại
        noise mà số cụm < 2 → các metric này = NaN (không raise).
      - Với data > 5,000 điểm: sample 5,000 cho silhouette để giảm O(N^2).
        (Hiện chỉ ~1,137 nên không cần, nhưng để code tổng quát.)
      - DBCV (Density-Based Clustering Validation) thiết kế riêng cho
        density-based clustering (HDBSCAN) — phù hợp hơn Silhouette/DB vốn giả
        định cụm dạng lồi. `relative_validity_` là xấp xỉ nhanh của DBCV, có
        sẵn miễn phí từ minimum spanning tree đã tính trong lúc fit.
      - Trả về dict đầy đủ cả khi fail (giá trị NaN), vì MLflow cần shape ổn định.
    """
    n_total = len(labels)
    n_noise = int((labels == -1).sum())

    # Lọc noise ra trước khi tính metric
    mask = labels != -1
    X_clean = X[mask]
    lab_clean = labels[mask]
    n_clusters = len(set(lab_clean))

    # Tính 3 metric clustering — chỉ khi có ít nhất 2 cụm
    if n_clusters >= 2:
        # Silhouette: sample nếu data quá lớn (tránh O(N^2))
        if len(X_clean) > _SILHOUETTE_SAMPLE_LIMIT:
            rng = np.random.default_rng(42)
            idx = rng.choice(len(X_clean), _SILHOUETTE_SAMPLE_LIMIT, replace=False)
            sil = float(silhouette_score(X_clean[idx], lab_clean[idx]))
        else:
            sil = float(silhouette_score(X_clean, lab_clean))

        db = float(davies_bouldin_score(X_clean, lab_clean))
        ch = float(calinski_harabasz_score(X_clean, lab_clean))
    else:
        sil = db = ch = _NAN

    dbcv = _NAN
    relative_validity = getattr(model, "relative_validity_", None)
    if relative_validity is not None and not (isinstance(relative_validity, float) and np.isnan(relative_validity)):
        dbcv = float(relative_validity)

    # Thống kê kích thước cụm
    sizes = sorted(Counter(lab_clean).values()) if n_clusters > 0 else [0]

    return {
        "n_total": n_total,
        "n_clusters": n_clusters,
        "n_noise": n_noise,
        "noise_ratio": round(n_noise / n_total, 4) if n_total else _NAN,
        "silhouette": sil,
        "davies_bouldin": db,
        "calinski_harabasz": ch,
        "dbcv": dbcv,
        "min_cluster_size": int(min(sizes)),
        "max_cluster_size": int(max(sizes)),
        "median_cluster_size": float(np.median(sizes)),
    }


def compute_related_split_ratio(
    df_related: pd.DataFrame | None,
    tech_ids: list[str],
    labels: np.ndarray,
) -> dict[str, float | int]:
    """
    Tính tỉ lệ cặp RELATED_TO (quan hệ tech-tech đã curate thủ công, chất
    lượng "ground truth") bị xếp vào 2 cụm khác nhau — tín hiệu bán giám sát
    (semi-supervised) duy nhất hệ thống có, bổ sung cho Silhouette/DBCV/CH vốn
    hoàn toàn nội tại (không đối chiếu gì bên ngoài).

    Bỏ qua cặp mà 1 trong 2 tech không có trong `tech_ids`/`labels` (không nằm
    trong feature matrix) hoặc là noise (label=-1) — noise không phải "cụm sai".

    Hàm thuần (không đọc file) để gọi được cho MỌI trial trong grid search,
    không chỉ trial thắng cuối cùng — caller (stage_03_train) chịu trách
    nhiệm load `df_related` một lần từ snapshot.

    Trả về NaN cho `related_pairs_split_ratio` nếu không có cặp nào đánh giá
    được (thiếu dữ liệu RELATED_TO, hoặc mọi cặp đều dính noise).
    """
    empty_result: dict[str, float | int] = {
        "related_pairs_total": 0,
        "related_pairs_evaluated": 0,
        "related_pairs_split": 0,
        "related_pairs_split_ratio": _NAN,
    }
    if df_related is None or df_related.empty or not {"tech_id_a", "tech_id_b"}.issubset(df_related.columns):
        return empty_result

    label_by_tech = dict(zip(tech_ids, labels.tolist()))

    seen_pairs: set[tuple[str, str]] = set()
    n_evaluated = 0
    n_split = 0
    for a, b in zip(df_related["tech_id_a"], df_related["tech_id_b"]):
        pair = tuple(sorted((str(a), str(b))))
        if pair[0] == pair[1] or pair in seen_pairs:
            continue
        seen_pairs.add(pair)

        cid_a = label_by_tech.get(pair[0])
        cid_b = label_by_tech.get(pair[1])
        if cid_a is None or cid_b is None or cid_a == -1 or cid_b == -1:
            continue  # tech không có trong feature matrix hoặc là noise

        n_evaluated += 1
        if cid_a != cid_b:
            n_split += 1

    ratio = round(n_split / n_evaluated, 4) if n_evaluated else _NAN
    return {
        "related_pairs_total": len(seen_pairs),
        "related_pairs_evaluated": n_evaluated,
        "related_pairs_split": n_split,
        "related_pairs_split_ratio": ratio,
    }


def cluster_size_distribution(labels: np.ndarray) -> dict[int, int]:
    """
    Trả về `{cluster_id: count}`, bỏ noise.

    Yêu cầu: sắp xếp DESC theo count, có thể giúp đặt tên cụm sau này.
    """
    counts = Counter(int(lbl) for lbl in labels if lbl != -1)
    return dict(sorted(counts.items(), key=lambda x: -x[1]))


def map_cluster_to_members(
    labels: np.ndarray,
    tech_ids: list[str],
) -> dict[int, list[str]]:
    """
    Đảo ngược `labels` → `{cluster_id: [tech_id, ...]}`.

    Yêu cầu:
      - Cluster -1 cũng include (key = -1) — caller sẽ tự bỏ qua khi label.
      - Không sort phần member ở đây; downstream sẽ sort theo độ "tâm" của cụm.
    """
    result: dict[int, list[str]] = {}
    for tech_id, label in zip(tech_ids, labels):
        result.setdefault(int(label), []).append(tech_id)
    return result
