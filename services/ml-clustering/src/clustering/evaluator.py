"""
Tính các chỉ số đánh giá clustering. Dùng cho tuner.score và stage 03 cuối cùng.
"""

from __future__ import annotations

from collections import Counter

import numpy as np
from sklearn.metrics import (
    calinski_harabasz_score,
    davies_bouldin_score,
    silhouette_score,
)

_NAN = float("nan")
_SILHOUETTE_SAMPLE_LIMIT = 5_000


def evaluate_clustering(X: np.ndarray, labels: np.ndarray) -> dict[str, float | int]:
    """
    Tính bộ chỉ số tiêu chuẩn.

    Tham số:
        X:      ma trận đặc trưng (đã scale, cùng không gian khi fit).
        labels: nhãn cụm; -1 = noise.

    Trả về dict:
        {
          "n_total":           int,
          "n_clusters":        int,    # không tính noise
          "n_noise":           int,
          "noise_ratio":       float,
          "silhouette":        float,  # NaN nếu không tính được
          "davies_bouldin":    float,
          "calinski_harabasz": float,
          "min_cluster_size":  int,
          "max_cluster_size":  int,
          "median_cluster_size": float,
        }

    Yêu cầu logic:
      - Tính silhouette/DB/CH CHỈ trên các điểm có label != -1; nếu sau khi loại
        noise mà số cụm < 2 → các metric này = NaN (không raise).
      - Với data > 5,000 điểm: sample 5,000 cho silhouette để giảm O(N^2).
        (Hiện chỉ ~1,137 nên không cần, nhưng để code tổng quát.)
      - Trả về dict đầy đủ cả khi fail (giá trị NaN), vì MLflow cần shape ổn định.
    """
    n_total = len(labels)
    n_noise = int((labels == -1).sum())

    # Lọc noise ra trước khi tính metric
    mask      = labels != -1
    X_clean   = X[mask]
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

    # Thống kê kích thước cụm
    sizes = sorted(Counter(lab_clean).values()) if n_clusters > 0 else [0]

    return {
        "n_total":             n_total,
        "n_clusters":          n_clusters,
        "n_noise":             n_noise,
        "noise_ratio":         round(n_noise / n_total, 4) if n_total else _NAN,
        "silhouette":          sil,
        "davies_bouldin":      db,
        "calinski_harabasz":   ch,
        "min_cluster_size":    int(min(sizes)),
        "max_cluster_size":    int(max(sizes)),
        "median_cluster_size": float(np.median(sizes)),
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
