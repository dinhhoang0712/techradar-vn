# ml-clustering — Technology Clustering Service

FastAPI service phân cụm công nghệ IT (HDBSCAN) và phục vụ kết quả qua REST API. Hỗ trợ retrain tự động qua pipeline trigger endpoint.

> **Tài liệu đầy đủ:** [docs/AI_PLATFORM.md](../../docs/AI_PLATFORM.md#3-servicesml-clustering)

---

## Nhanh

```bash
# Serving only (nhẹ hơn)
pip install -r requirements-api.txt
uvicorn app.main:app --port 8001

# Docker (từ project root)
docker compose up ml-clustering
```

Swagger: http://localhost:8001/docs

---

## Endpoints serving

| Method | Path | Mô tả |
|---|---|---|
| GET | `/health` | Thông tin snapshot hiện tại |
| GET | `/clusters` | Danh sách tất cả cluster |
| GET | `/clusters/{id}` | Chi tiết cluster + members |
| GET | `/tech/{name}/cluster` | Tra cứu cluster của 1 tech |
| POST | `/predict/batch` | Batch lookup nhiều tech |
| PUT | `/clusters/{cluster_id}/label` | Admin override nhãn cluster |
| POST | `/pipeline/trigger` | Khởi động retrain pipeline |
| GET | `/pipeline/status` | Trạng thái pipeline |
| GET | `/pipeline/runs` | Lịch sử các lần chạy pipeline |

---

## Pipeline 6 stages

```
Stage 1: EXTRACT    Neo4j → Parquet (technologies, jobs, articles, edges)
Stage 2: FEATURES   Alias normalization + noise filter + embedding + UMAP
Stage 3: TRAIN      HDBSCAN grid search (18 combos) → best DBCV score (không phải Silhouette —
                    DBCV phù hợp hơn cho density-based clustering như HDBSCAN)
                    Register model — chỉ promote "champion" nếu thắng champion cũ. Model KHÔNG
                    thắng → Stage 4/6/5 bên dưới bị bỏ qua (xem app/routes_pipeline.py), trừ khi
                    trigger qua API với ?force=true.
Stage 4: LABEL      GPT-4o-mini đặt tên + mô tả cho từng cluster
Stage 6: PUBLISH    Upload artifact lên MinIO + verify (head_object) rồi mới flip latest.json —
                    no-op nếu MLCLUSTER_MINIO_BUCKET không cấu hình (xem pipelines/stage_06_publish.py)
Stage 5: WRITEBACK  Ghi cluster_id về Neo4j (không sử dụng) — chạy SAU publish, không phải trước,
                    để graph không bị đụng nếu publish lỗi
```

**Chạy pipeline:**
```bash
# Qua DVC
dvc repro

# Qua API (khi service đang chạy)
curl -X POST http://localhost:8001/pipeline/trigger \
  -H "X-Internal-Auth: techradar-internal-secret"

# Theo dõi
curl http://localhost:8001/pipeline/status
```

Pipeline chạy **ngầm** (background thread) — API vẫn serving trong khi retrain.

**Lịch tự động:** Chủ nhật 06:00 Asia/Ho_Chi_Minh (APScheduler trong data-platform).

---

## Biến môi trường

```env
NEO4J_URI=neo4j+s://...
NEO4J_PASSWORD=...
OPENAI_API_KEY=...        # Stage 4 — LLM labeling
INTERNAL_API_TOKEN=...    # Bảo vệ /pipeline/trigger
```

Hyperparameters: chỉnh trong `params.yaml` → `dvc repro`.

---

## MLflow

```bash
mlflow ui --backend-store-uri sqlite:///mlruns.db
# http://localhost:5000
```

Xem đầy đủ tại [docs/AI_PLATFORM.md § 3](../../docs/AI_PLATFORM.md#3-servicesml-clustering).
