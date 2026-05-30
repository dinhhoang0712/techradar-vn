# ml-clustering — Hệ thống phân cụm công nghệ

Module độc lập trong dự án **TechPulse** — phân cụm các node `:Technology` từ Neo4j AuraDB thành các nhóm có ý nghĩa, phục vụ ClusterDashboard và phân tích xu hướng.

## 1. Mục tiêu

- Tự động nhóm công nghệ theo đặc trưng ngữ nghĩa, tuyển dụng và đồ thị.
- Không cần gán nhãn thủ công.
- Phục vụ phân tích xu hướng công nghệ theo cụm.

## 2. Kiến trúc pipeline

```
Neo4j AuraDB
     │
     ▼
Stage 1: EXTRACT        pipelines/stage_01_extract.py
data/raw/snapshot_<tag>/
  ├ technologies.parquet
  ├ companies.parquet
  ├ articles.parquet
  ├ jobs.parquet
  └ edges_*.parquet
     │
     ▼
Stage 2: FEATURES       pipelines/stage_02_features.py
  - Chuẩn hóa alias (k8s → Kubernetes, ...)
  - Noise filter (min_job_count=3, blocklist, regex)
  - Name embedding 768d → PCA 64d  (intfloat/multilingual-e5-base)
  - Graph stats (degree, job/article/company count)
  - Job TF-IDF (500 features)
  - StandardScaler → UMAP 32d
data/features/<tag>/{X.npy, tech_ids.parquet, feature_meta.json}
     │
     ▼
Stage 3: TRAIN          pipelines/stage_03_train.py
  - HDBSCAN grid search (18 tổ hợp)
  - Constraints: 12–28 cụm, noise ≤ 60%
  - Chọn best theo Silhouette Score
  - MLflow log toàn bộ trials + register best model
data/models/<tag>/{best_model.pkl, best_labels.parquet}
     │
     ▼
Stage 4: LABEL          pipelines/stage_04_label.py
  - GPT-4o-mini tự động đặt tên + mô tả cụm
data/labels/<tag>/cluster_labels.json
     │
     ▼
FastAPI app             app/main.py  (port 8001)
  - Serve kết quả từ artifacts
  - Hỗ trợ publish lên S3 + auto-reload
```

Stage 5 (writeback Neo4j) không sử dụng.

## 3. Tech stack

| Thành phần | Công nghệ |
|---|---|
| Core ML | Scikit-learn 1.5+ (`sklearn.cluster.HDBSCAN`) |
| Name embedding | `intfloat/multilingual-e5-base` (SentenceTransformers) |
| Experiment tracking | MLflow (SQLite backend) |
| Data versioning | DVC 3.x |
| Auto label | GPT-4o-mini |
| API | FastAPI |
| Artifact storage | Local hoặc S3 (tuỳ cấu hình) |

## 4. Cấu trúc thư mục

```
src/ml-clustering/
├── params.yaml                     # toàn bộ hyperparameter — DVC tracked
├── dvc.yaml                        # định nghĩa 4 stage
├── conf/config.py                  # load .env + params.yaml
├── app/                            # FastAPI serving
│   ├── main.py
│   ├── store.py                    # load + cache artifacts (local/S3)
│   └── schemas.py
├── pipelines/
│   ├── stage_01_extract.py
│   ├── stage_02_features.py
│   ├── stage_03_train.py
│   └── stage_04_label.py
├── src/
│   ├── data/{neo4j_loader,snapshot}.py
│   ├── features/{content_features,graph_features,feature_pipeline,
│   │            noise_filter,tech_aliases,acronym_map}.py
│   ├── clustering/{trainer,tuner,evaluator}.py
│   ├── labeling/{llm_labeler.py,prompts/cluster_label.txt}
│   └── tracking/mlflow_logger.py
├── scripts/
│   └── publish_s3_artifacts.sh    # đẩy artifact lên S3 sau khi train xong
└── data/                          # gitignored, DVC-tracked
    ├── raw/snapshot_<tag>/
    ├── features/<tag>/
    ├── models/<tag>/
    └── labels/<tag>/
```

## 5. Cách chạy

### Cài đặt
```bash
cd src/ml-clustering
pip install -r requirements.txt
cp ../../.env .env   # cần NEO4J_URI, NEO4J_PASSWORD, OPENAI_API_KEY
```

### Chạy pipeline
```bash
# Chạy toàn bộ pipeline (DVC tự bỏ qua stage không thay đổi)
dvc repro

# Hoặc chạy từng stage
python -m pipelines.stage_01_extract --params params.yaml [--force]
python -m pipelines.stage_02_features --params params.yaml
python -m pipelines.stage_03_train --params params.yaml
python -m pipelines.stage_04_label --params params.yaml [--run-id <mlflow_run_id>]
```

### Xem kết quả MLflow
```bash
mlflow ui --backend-store-uri sqlite:///mlruns.db
```

### Chạy API
```bash
uvicorn app.main:app --reload --port 8001
```

### Publish lên S3 (sau khi train xong)
```bash
scripts/publish_s3_artifacts.sh --bucket <bucket> --region ap-southeast-1
```

## 6. Cấu hình

Toàn bộ hyperparameter trong `params.yaml`. Thay đổi giá trị → `dvc repro` để chạy lại đúng các stage bị ảnh hưởng.

Biến môi trường (trong `.env`):

| Biến | Mô tả |
|---|---|
| `NEO4J_URI` | URI AuraDB |
| `NEO4J_PASSWORD` | Mật khẩu Neo4j |
| `OPENAI_API_KEY` | Key GPT-4o-mini (dùng ở stage label) |
| `MLCLUSTER_S3_BUCKET` | S3 bucket (tuỳ chọn) |
| `MLCLUSTER_SNAPSHOT_TAG` | Tag snapshot API sẽ load (`latest` để tự động) |

## 7. Quy ước

- Mọi tham số ở `params.yaml`, không hardcode trong code.
- Mọi run đều log MLflow với tag snapshot tương ứng.
- Không gọi LLM trong vòng lặp train — chỉ gọi 1 lần ở Stage 4.
- `tech_id` = `elementId(t)` của Neo4j.
