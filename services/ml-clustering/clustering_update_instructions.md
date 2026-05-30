# Hướng dẫn cập nhật skeleton ml-clustering

## Mục tiêu

Cập nhật pipeline phân cụm theo **Hướng 1: Hard cluster + Near Cluster score dựa trên ngưỡng (threshold-based)**, để hỗ trợ hiển thị web visualization — trong đó một Technology node (ví dụ Python) có thể hiển thị liên kết với nhiều Cluster khác nhau dựa trên độ gần.

---

## Tổng quan thay đổi

Chỉ 2 stage cần sửa:

| Stage | File | Thay đổi |
|---|---|---|
| Stage 3 | `pipelines/stage_03_train.py` | Tính near_clusters theo threshold, log artifact |
| Stage 5 | `pipelines/stage_05_writeback.py` | Tạo node `:Cluster`, relationship `:BELONGS_TO` và `:NEAR_CLUSTER` |

Các file khác (stage 1, 2, 4) **không thay đổi**.

---

## Chi tiết từng thay đổi

### 1. `params.yaml` — thêm tham số mới

Thêm key sau vào `params.yaml` (KHÔNG hardcode trong code):

```yaml
clustering:
  near_cluster_threshold: 0.70   # score tối thiểu để ghi nhận near cluster
```

---

### 2. `pipelines/stage_03_train.py`

**Vị trí thêm code:** Sau khi HDBSCAN (hoặc DBSCAN/KMeans) fit xong và labels đã được gán.

**Logic cần implement:**

```python
# --- THÊM BLOCK NÀY VÀO CUỐI STAGE 3 ---

from sklearn.metrics.pairwise import euclidean_distances
import json

NEAR_CLUSTER_THRESHOLD = params["clustering"]["near_cluster_threshold"]

# 1. Tính centroid của từng cluster (bỏ noise label = -1)
centroids = {
    label: X[labels == label].mean(axis=0)
    for label in set(labels) if label != -1
}

# 2. Với mỗi tech, tính score đến tất cả cluster khác (trừ primary)
near_clusters_map = {}

for i, tech_id in enumerate(tech_ids):
    primary_label = labels[i]

    # Tính khoảng cách đến tất cả centroid (trừ primary)
    dists = {
        label: float(euclidean_distances([X[i]], [centroid])[0][0])
        for label, centroid in centroids.items()
        if label != primary_label
    }

    if not dists:
        near_clusters_map[tech_id] = []
        continue

    # Convert distance → similarity score (0–1), distance nhỏ → score cao
    max_dist = max(dists.values())
    scores = {
        label: round(1 - (d / max_dist), 4)
        for label, d in dists.items()
    }

    # Chỉ giữ những cluster vượt ngưỡng
    near = [
        {"cluster_id": label, "score": score}
        for label, score in scores.items()
        if score >= NEAR_CLUSTER_THRESHOLD
    ]

    # Sắp xếp giảm dần theo score
    near_clusters_map[tech_id] = sorted(near, key=lambda x: -x["score"])

# 3. Log artifact vào MLflow để stage 5 đọc lại
mlflow.log_dict(near_clusters_map, "near_clusters.json")

# --- KẾT THÚC BLOCK ---
```

**Lưu ý quan trọng:**
- `tech_ids` là list `elementId(t)` của Neo4j, khớp với `tech_id` trong parquet.
- `X` là feature matrix sau dimensionality reduction (nếu có), hoặc raw features.
- Noise points (`labels[i] == -1`) vẫn được tính near_clusters bình thường — chúng không có primary cluster nhưng vẫn nên biết gần cluster nào.

---

### 3. `pipelines/stage_05_writeback.py`

**Thay đổi toàn bộ logic writeback.** Cần thực hiện 3 bước theo thứ tự:

#### Bước 1 — Load near_clusters từ MLflow artifact

```python
import mlflow
import json

client = mlflow.tracking.MlflowClient()
artifact_path = client.download_artifacts(run_id, "near_clusters.json")
with open(artifact_path) as f:
    near_clusters_map = json.load(f)
```

#### Bước 2 — Tạo node `:Cluster` trong Neo4j

Chạy trước, 1 lần, trước khi ghi relationship:

```cypher
UNWIND $clusters AS c
MERGE (cl:Cluster {cluster_id: c.cluster_id})
SET cl.name        = c.cluster_label,
    cl.size        = c.size,
    cl.updated_at  = datetime()
```

- `cluster_label` lấy từ output Stage 4 (Gemini đặt tên).
- `size` là số Technology node thuộc primary cluster đó.

#### Bước 3 — Ghi relationship `:BELONGS_TO` (primary)

```cypher
UNWIND $rows AS row
MATCH (t:Technology {tech_id: row.tech_id})
MATCH (c:Cluster    {cluster_id: row.cluster_id})
MERGE (t)-[r:BELONGS_TO]->(c)
SET r.score      = 1.0,
    r.updated_at = datetime()
```

Dùng APOC batch, chunk size 500:

```python
neo4j_driver.execute_query(
    "CALL apoc.periodic.iterate(...)",
    parameters_={"rows": belongs_to_rows},
    database_="neo4j"
)
```

#### Bước 4 — Ghi relationship `:NEAR_CLUSTER` (soft link)

```cypher
UNWIND $rows AS row
MATCH (t:Technology {tech_id: row.tech_id})
MATCH (c:Cluster    {cluster_id: row.cluster_id})
MERGE (t)-[r:NEAR_CLUSTER]->(c)
SET r.score      = row.score,
    r.updated_at = datetime()
```

Build `near_cluster_rows` từ `near_clusters_map`:

```python
near_cluster_rows = [
    {
        "tech_id":    tech_id,
        "cluster_id": entry["cluster_id"],
        "score":      entry["score"]
    }
    for tech_id, entries in near_clusters_map.items()
    for entry in entries
    if entry["score"] >= params["clustering"]["near_cluster_threshold"]
]
```

**Lưu ý quan trọng:**
- Dùng `MERGE` thay vì `CREATE` để idempotent — chạy lại nhiều lần không sinh relationship trùng.
- Tham số hoá Cypher, không nối f-string.
- Xoá relationship cũ trước khi ghi mới nếu muốn "clean writeback":

```cypher
MATCH (t:Technology)-[r:BELONGS_TO|NEAR_CLUSTER]->(:Cluster)
WHERE t.tech_id IN $tech_ids
DELETE r
```

---

## Schema Neo4j sau khi writeback

```
(:Technology)-[:BELONGS_TO  {score: 1.0}]->(:Cluster)
(:Technology)-[:NEAR_CLUSTER {score: 0.xx}]->(:Cluster)
```

Node `:Cluster` properties:

```
cluster_id:  int        # label từ HDBSCAN
name:        string     # đặt bởi Gemini Stage 4
size:        int        # số tech thuộc primary
updated_at:  datetime
```

Relationship properties:

| Relationship | score | Ý nghĩa |
|---|---|---|
| `BELONGS_TO` | 1.0 (cố định) | Primary cluster |
| `NEAR_CLUSTER` | 0.70–1.0 | Mức độ liên quan (threshold từ params.yaml) |

---

## Query mẫu cho web visualization

**Lấy tất cả cluster và member của 1 cluster:**
```cypher
MATCH (t:Technology)-[:BELONGS_TO]->(c:Cluster {name: $cluster_name})
RETURN t.name, t.tech_id, c.name
```

**Lấy Python và tất cả cluster liên quan (primary + near):**
```cypher
MATCH (t:Technology {name: "Python"})-[r:BELONGS_TO|NEAR_CLUSTER]->(c:Cluster)
RETURN c.name, type(r) AS rel_type, r.score
ORDER BY r.score DESC
```

**Lấy toàn bộ graph cho visualization:**
```cypher
MATCH (t:Technology)-[r:BELONGS_TO|NEAR_CLUSTER]->(c:Cluster)
RETURN t.name, c.name, type(r), r.score
```

---

## Checklist kiểm tra sau khi sửa

- [ ] `params.yaml` có key `near_cluster_threshold`
- [ ] Stage 3 log artifact `near_clusters.json` vào MLflow run
- [ ] Stage 5 tạo node `:Cluster` trước khi ghi relationship
- [ ] Stage 5 ghi cả `:BELONGS_TO` và `:NEAR_CLUSTER`
- [ ] Cypher dùng `MERGE`, không dùng `CREATE`
- [ ] Cypher tham số hoá, không nối f-string
- [ ] Noise points (`primary_cluster = -1`) không ghi `:BELONGS_TO` (skip)
- [ ] `dvc.yaml` cập nhật output của stage 3 nếu có lưu `near_clusters.json` ra disk
