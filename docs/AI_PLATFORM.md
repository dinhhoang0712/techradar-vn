# AI Platform — TechRadar VN

Tài liệu kỹ thuật đầy đủ cho các AI services trong hệ thống TechRadar VN.

---

## 📚 Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [ai-rag-core Service](#2-ai-rag-core-service)
3. [ml-clustering Service](#3-ml-clustering-service)
4. [Supporting Services](#4-supporting-services)
5. [Tích hợp với Spring Boot](#5-tích-hợp-với-spring-boot)
6. [Deployment](#6-deployment)
7. [Monitoring & Debugging](#7-monitoring--debugging)

---

## 1. Tổng quan

AI Platform bao gồm 2 Python services chính và các supporting services:

### Services chính

| Service | Port | Mô tải |
|---------|------|--------|
| **ai-rag-core** | 8000 | Graph RAG chatbot, recommendation, forecast, career assistant |
| **ml-clustering** | 8001 | HDBSCAN clustering pipeline + serving |

### Supporting services

| Service | Mô tả |
|---------|--------|
| **crawler** | Web crawlers cho 8 nguồn dữ liệu |
| **embedding-service** | Kafka consumer → sinh embedding → Neo4j |
| **qdrant-writer** | Kafka consumer → ghi embedding vào Qdrant (optional) |

### Phân tách trách nhiệm

| Chức năng | Spring Boot | Python |
|-----------|--------------|--------|
| Authentication & Authorization | ✅ JWT, SecurityConfig | ❌ |
| Schema PostgreSQL | ✅ Flyway migrations | ❌ (chỉ đọc) |
| Business logic CRUD | ✅ | ❌ |
| RAG + LLM | ❌ | ✅ ai-rag-core |
| ML clustering | ❌ | ✅ ml-clustering |
| Data ingestion | ❌ | ✅ crawler, embedding-service |

### Bảo mật nội bộ

Spring Boot gắn header `X-Internal-Auth: <INTERNAL_API_TOKEN>` vào tất cả request đến Python. Python kiểm tra header này — nếu token được cấu hình, request không có header hợp lệ bị từ chối 401.

---

## 2. ai-rag-core Service

**ai-rag-core** là FastAPI service (port 8000) cung cấp các AI capabilities: Graph RAG chatbot, recommendation, forecast, career assistant, summarization, report generation, và AI agent.

### 2.1 Architecture

```
ai-rag-core/
├── app/
│   ├── api/              # API routes & schemas
│   ├── core/             # RAG pipeline components
│   ├── services/         # Business logic services
│   ├── agent/            # LangChain agent
│   ├── memory/           # Conversation & user memory
│   ├── evaluation/       # RAGAS evaluation
│   ├── monitoring/       # Prometheus metrics
│   ├── db/               # Database clients (Neo4j, PostgreSQL)
│   └── prompts/          # Prompt templates
├── scripts/              # Utility scripts
└── tests/
```

### 2.2 API Endpoints

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/health` | Public | Health check |
| GET | `/metrics` | Public | Prometheus metrics |
| POST | `/chat` | X-Internal-Auth | Chat RAG (non-streaming) |
| POST | `/chat/stream` | X-Internal-Auth | Chat RAG (SSE streaming) |
| GET | `/chat/session/{id}/messages` | X-Internal-Auth | Lịch sử hội thoại |
| POST | `/embed/trigger` | X-Embed-Secret | Trigger embedding bài báo mới |
| GET | `/embed/status` | X-Internal-Auth | Trạng thái embedding job |
| POST | `/recommend` | X-Internal-Auth | Gợi ý công nghệ |
| POST | `/forecast` | X-Internal-Auth | Dự báo xu hướng |
| POST | `/career` | X-Internal-Auth | Tư vấn career path |
| POST | `/summarize` | X-Internal-Auth | Tóm tắt xu hướng công nghệ |
| POST | `/report` | X-Internal-Auth | Báo cáo xu hướng theo kỳ |
| POST | `/agent` | X-Internal-Auth | AI Agent (multi-tool) |

### 2.3 RAG Pipeline (Graph RAG)

Pipeline kết hợp **4 nguồn song song** trước khi sinh câu trả lời:

#### Luồng xử lý

```
query + user_id + session_id
        │
        ├── [0] Load conversation history (PostgreSQL, sliding window 10 turns)
        │
        ├── [1] Parallel asyncio.gather()
        │     ├── vector_search(query, top_k=5)
        │     │   embed query → Neo4j vector index → top-20 Article
        │     ├── graph_search(query)
        │     │   NER → Cypher → Job + Company + Technology
        │     └── get_user_context(user_id)
        │         PostgreSQL user_profile.preferences_json
        │
        ├── [1b] sql_analytics_search(tech_entities, months=6)
        │         PostgreSQL tech_analytics (Gold ETL)
        │
        ├── [2] Rerank articles
        │         BGE reranker (ONNX, CPU)
        │         threshold 0.40 → top-5
        │
        ├── [3] Fallback nếu không tìm thấy gì
        │         trả về "Không tìm thấy thông tin..."
        │
        ├── [4] Build prompt
        │         system_prompt + history + rag_template
        │
        ├── [5] LLM generate (OpenAI gpt-4o-mini hoặc Gemini)
        │         retry tối đa 3 lần khi 429/503
        │
        └── [6] RAGAS evaluation (fire-and-forget)
```

#### Chi tiết từng bước

**Vector Search**: Embed câu hỏi với prefix "query: " → tìm trong Neo4j vector index (768d cosine)

**Graph Traversal**: NER pipeline trích xuất entity (dictionary lookup → regex pattern → NLPHust NER). Alias normalization tự động: `k8s → Kubernetes`, `nodejs → Node.js`

**SQL Analytics**: Đọc từ `tech_analytics` table (Gold ETL rebuild mỗi đêm)

**Rerank**: BGE reranker chấm điểm cross-encoder, ngưỡng 0.40

**Build Prompt**: System prompt + conversation history + RAG context (articles + jobs + analytics + user context)

### 2.4 Services

#### Recommendation Service

Gợi ý công nghệ dựa trên graph traversal và analytics:

```json
POST /recommend
{
  "user_id": "uuid | null",
  "current_techs": ["React", "TypeScript"],
  "limit": 10
}
```

**Algorithm**:
1. Neo4j graph traversal tìm tech liên quan
2. SQL analytics lấy growth rate
3. Weighted score: 0.6 × co-occurrence + 0.4 × growth
4. LLM explain recommendation

#### Forecast Service

Dự báo xu hướng công nghệ với statistical signals:

```json
POST /forecast
{
  "technology": "React",
  "horizon_months": 6
}
```

**Signals**:
- Linear slope (numpy.polyfit)
- Momentum MoM (3 tháng gần nhất)
- Sentiment (Neo4j article sentiment)
- Job demand change
- Volatility

#### Career Assistant

Tư vấn career path với skill gap analysis:

```json
POST /career
{
  "user_id": "uuid | null",
  "target_role": "Senior Backend Developer",
  "current_skills": ["Python", "FastAPI"]
}
```

**Logic**:
1. Neo4j tìm skills yêu cầu cho target role
2. Tính skill gap: required - current
3. SQL analytics lấy job demand
4. LLM sinh roadmap markdown

#### Summarization Service

Tóm tắt tin tức công nghệ theo kỳ:

```json
POST /summarize
{
  "tech_name": "Kubernetes",
  "period": "2024-Q4",
  "format": "bullet"
}
```

**Period formats**: `"2024-Q4"`, `"2024-12"`, `"2024"`, `null` (3 tháng gần nhất)

#### Report Generator

Báo cáo xu hướng công nghệ theo kỳ:

```json
POST /report
{
  "period": "2024-Q4",
  "top_n": 10,
  "format": "markdown"
}
```

**Data sources**: PostgreSQL (top-growing tech) + Neo4j (top-mentioned tech)

#### AI Agent (LangChain)

Multi-tool agent với 4 tools:

| Tool | Mô tả |
|------|--------|
| `search_knowledge` | RAG search (article + job + analytics) |
| `recommend_technologies` | Gợi ý tech liên quan |
| `forecast_technology` | Dự báo xu hướng |
| `summarize_technology` | Tóm tắt tin tức |

### 2.5 Memory

#### Conversation Memory

Sliding window 10 turns từ PostgreSQL `chat_message` table.

#### User Long-term Memory

Lưu trong `user_profile.preferences_json`:

```json
{
  "interested_techs": ["React", "TypeScript", "Node.js"],
  "current_role": "Frontend Developer",
  "experience_years": 3,
  "tech_interactions": {
    "React": 12,
    "Next.js": 7
  }
}
```

Increment interaction counter fire-and-forget sau mỗi chat.

### 2.6 Monitoring

#### Prometheus Metrics

Endpoint: `GET /metrics`

| Metric | Type | Labels |
|--------|------|--------|
| `ai_rag_requests_total` | Counter | endpoint, status, llm_provider |
| `ai_rag_latency_seconds` | Histogram | endpoint, stage |
| `ai_rag_llm_tokens_total` | Counter | provider, model, token_type |
| `ai_rag_retrieval_results` | Histogram | source |

**Stages**: `retrieval`, `rerank`, `llm`, `total`

#### RAGAS Evaluation

Mặc định tắt (`EVAL_ENABLED=false`). Bật khi cần đánh giá chất lượng.

LLM judge faithfulness → MLflow logging → `mlflow ui`

### 2.7 Configuration

| Env var | Default | Mô tả |
|---------|---------|-------|
| `NEO4J_URI` | — | URI AuraDB hoặc local |
| `OPENAI_API_KEY` | — | API key OpenAI |
| `GEMINI_API_KEY` | — | API key Gemini |
| `LLM_PROVIDER` | `openai` | `"openai"` hoặc `"gemini"` |
| `LLM_MODEL` | `gpt-4o-mini` | Model LLM |
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `INTERNAL_API_TOKEN` | `""` | Token kiểm tra từ Spring |
| `EMBED_SECRET` | `changeme` | Secret cho `/embed/trigger` |
| `EVAL_ENABLED` | `false` | Bật RAGAS evaluation |
| `SQL_ANALYTICS_MONTHS` | `6` | Khoảng thời gian đọc tech_analytics |

### 2.8 Running the Service

**Local development:**
```bash
cd services/ai-rag-core
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
MODEL_WARMUP=background uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

**Docker:**
```bash
docker compose up ai-rag-core
```

**Health check:**
```bash
curl http://localhost:8000/health
curl http://localhost:8000/metrics
```

**Swagger UI:** http://localhost:8000/docs

**RAM requirement**: tối thiểu 4GB (embedder ~500MB + reranker ~1GB + app ~500MB)

---

## 3. ml-clustering Service

**ml-clustering** là FastAPI service (port 8001) cung cấp HDBSCAN clustering pipeline và serving cho technology clustering.

### 3.1 Architecture

```
ml-clustering/
├── app/                    # FastAPI serving
│   ├── main.py             # App + routes
│   ├── store.py            # Load + cache artifacts
│   ├── schemas.py          # Pydantic models
│   └── routes_pipeline.py # Pipeline trigger & status
├── conf/
│   └── config.py           # Settings
├── pipelines/             # 5-stage pipeline
│   ├── stage_01_extract.py
│   ├── stage_02_features.py
│   ├── stage_03_train.py
│   ├── stage_04_label.py
│   └── stage_05_writeback.py
├── src/                   # ML code
│   ├── data/              # Neo4j loader
│   ├── features/          # Feature extraction
│   ├── clustering/        # HDBSCAN training
│   ├── labeling/          # LLM labeling
│   └── tracking/          # MLflow logging
├── params.yaml            # Hyperparameters (DVC)
├── dvc.yaml               # Pipeline definition
└── data/                  # DVC-tracked artifacts
```

### 3.2 Pipeline 5 Stages

Pipeline HDBSCAN chạy tuần tự qua 5 stages:

```
Neo4j AuraDB
     │
     ▼
Stage 1 — EXTRACT
  Tải dữ liệu từ Neo4j xuống Parquet
     │
     ▼
Stage 2 — FEATURES
  Xây dựng feature matrix:
  - Alias normalization
  - Noise filter
  - Name embedding (E5 → PCA)
  - Graph features
  - Job TF-IDF
  - UMAP 32d
     │
     ▼
Stage 3 — TRAIN
  Grid search HDBSCAN hyperparameters
  Chọn best theo Silhouette Score
  MLflow log trials
     │
     ▼
Stage 4 — LABEL
  GPT-4o-mini sinh cluster labels
  - label (tiếng Việt)
  - domain
  - description
  - is_coherent
     │
     ▼
Stage 5 — WRITEBACK
  Ghi kết quả về Neo4j (optional)
```

### 3.3 API Endpoints

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/health` | Public | Health check + snapshot info |
| POST | `/pipeline/trigger` | X-Internal-Auth | Trigger pipeline retrain |
| GET | `/pipeline/status` | Public | Pipeline status |
| GET | `/clusters` | Public | Danh sách clusters |
| GET | `/clusters/{id}` | Public | Chi tiết cluster |
| GET | `/tech/{name}/cluster` | Public | Tra cứu cluster của tech |
| POST | `/predict/batch` | Public | Batch lookup |

### 3.4 Cluster Serving

**GET /clusters**
```json
[
  {
    "cluster_id": 0,
    "label": "Frontend Frameworks",
    "label_en": "Frontend Frameworks",
    "domain": "Frontend",
    "confidence": 0.92,
    "is_coherent": true,
    "n_members": 24
  }
]
```

Query params: `?is_coherent=true` để lọc chỉ cluster coherent.

**GET /tech/{tech_name}/cluster**
```json
{
  "tech_name": "React",
  "cluster_id": 0,
  "label": "Frontend Frameworks",
  "found": true
}
```

**POST /predict/batch**
```json
{
  "tech_names": ["React", "Kubernetes", "UnknownTech"]
}
```

### 3.5 Pipeline Trigger

**POST /pipeline/trigger**

Khởi động pipeline retrain trong background thread. Trả về ngay lập tức.

```bash
curl -X POST http://localhost:8001/pipeline/trigger \
  -H "X-Internal-Auth: techradar-internal-secret"
```

**GET /pipeline/status** - Theo dõi tiến độ:
```json
{
  "status": "running",
  "started_at": "2025-01-15T06:00:00+00:00",
  "current_stage": "pipelines.stage_03_train",
  "error": null
}
```

**Lịch tự động**: Chủ nhật 06:00 Asia/Ho_Chi_Minh (APScheduler trong data-platform)

### 3.6 Configuration

| Env var | Default | Mô tả |
|---------|---------|-------|
| `NEO4J_URI` | — | URI AuraDB |
| `NEO4J_PASSWORD` | — | Mật khẩu Neo4j |
| `OPENAI_API_KEY` | — | API key GPT-4o-mini (stage 4) |
| `INTERNAL_API_TOKEN` | `""` | Token kiểm tra `/pipeline/trigger` |
| `MLCLUSTER_S3_BUCKET` | `""` | S3 bucket artifacts (local nếu trống) |
| `MLCLUSTER_SNAPSHOT_TAG` | `latest` | Tag snapshot để load |

**Hyperparameters (params.yaml):**
```yaml
extract:
  snapshot_tag: ""
features:
  min_job_count: 3
  umap_n_components: 32
  pca_n_components: 64
train:
  min_clusters: 12
  max_clusters: 28
  max_noise_ratio: 0.60
  min_cluster_size: [10, 15, 20]
  min_samples: [3, 5, 8]
label:
  model: gpt-4o-mini
```

### 3.7 Running the Service

**Chạy pipeline thủ công:**
```bash
cd services/ml-clustering
pip install -r requirements.txt
dvc repro
```

**Chạy API serving:**
```bash
pip install -r requirements-api.txt
uvicorn app.main:app --port 8001 --reload
```

**Docker:**
```bash
docker compose up ml-clustering
```

**RAM requirement**:
- Serving: 512 MB
- Pipeline: 8 GB

---

## 4. Supporting Services

### 4.1 Crawler Service

Web crawlers cho 8 nguồn dữ liệu:

| Crawler | Nguồn | Loại dữ liệu |
|---------|--------|--------------|
| VNExpress.py | vnexpress.net | Bài viết công nghệ |
| GenK.py | genk.vn | Bài viết công nghệ |
| DanTri.py | dantri.com.vn | Bài viết công nghệ |
| ICTNews.py | ictnews.vn | Bài viết ICT |
| Viblo.py | viblo.asia | Bài viết kỹ thuật |
| GitHub.py | api.github.com | Repository trending |
| ITviec.py | itviec.com | Tin tuyển dụng IT |
| TopCV.py | topcv.vn | Tin tuyển dụng |

**Chạy theo lịch**: `run_all.py` chạy tuần tự tất cả crawlers, lặp mỗi `CRAWL_INTERVAL_HOURS` (default: 6 giờ).

**Docker (opt-in):**
```bash
docker compose --profile crawl up crawler
```

### 4.2 Embedding Service

Kafka consumer (`raw.articles` topic) → sinh embedding bằng `multilingual-e5-base` → ghi vector vào Neo4j node `Article.embedding`.

### 4.3 Qdrant Writer

Kafka consumer → nhận embedding → ghi vào Qdrant collection (vector store thay thế cho Neo4j vector index). Chỉ chạy khi dùng profile `vector`.

```bash
docker compose --profile vector up qdrant qdrant-writer
```

---

## 5. Tích hợp với Spring Boot

### 5.1 Spring Boot gọi Python

Tất cả module trong `apps/backend/features/` dùng `WebClient` với pattern:

```java
@Value("${app.python.ai.base-url:http://localhost:8000}")
private String aiBaseUrl;

@Value("${app.python.internal-token:}")
private String internalToken;

private WebClient webClient() {
    WebClient.Builder builder = webClientBuilder.baseUrl(aiBaseUrl);
    if (internalToken != null && !internalToken.isBlank()) {
        builder = builder.defaultHeader("X-Internal-Auth", internalToken);
    }
    return builder.build();
}
```

### 5.2 Endpoint Mapping

| Spring Boot | Python (ai-rag-core) | Python (ml-clustering) |
|-------------|----------------------|------------------------|
| POST `/api/v1/chat` | POST `/chat` | - |
| POST `/api/v1/chat/stream` | POST `/chat/stream` | - |
| POST `/api/v1/recommend` | POST `/recommend` | - |
| POST `/api/v1/forecast` | POST `/forecast` | - |
| POST `/api/v1/career` | POST `/career` | - |
| POST `/api/v1/chat/summarize` | POST `/summarize` | - |
| GET `/api/v1/report` | POST `/report` | - |
| POST `/api/v1/agent` | POST `/agent` | - |
| GET `/api/v1/clustering/clusters` | - | GET `/clusters` |

### 5.3 Security

- Spring Boot bảo vệ endpoint bằng JWT
- Python chỉ nhận request từ Spring (internal network) — kiểm tra `X-Internal-Auth`
- Public paths trong Spring vẫn cần X-Internal-Auth khi gọi Python — Spring tự thêm header

### 5.4 Timeout Configuration

| Module | Timeout |
|---------|---------|
| Chat | 120 giây |
| Agent | 120 giây |
| Recommend | 60 giây |
| Forecast | 60 giây |
| Career | 60 giây |
| Summarize | 60 giây |
| Report | 60 giây |

---

## 6. Deployment

### 6.1 Yêu cầu RAM

| Service | RAM tối thiểu | Ghi chú |
|---------|--------------|--------|
| ai-rag-core | 4 GB | embedder ~500MB + reranker ~1GB + overhead |
| ml-clustering (serving) | 512 MB | Chỉ load artifacts JSON/parquet |
| ml-clustering (pipeline) | 8 GB | SentenceTransformers + UMAP + HDBSCAN |

### 6.2 Docker Compose Commands

```bash
# Core stack
docker compose up --build

# Thêm crawler
docker compose --profile crawl up crawler

# Thêm Qdrant pipeline
docker compose --profile vector up qdrant qdrant-writer

# Tất cả profiles
docker compose --profile crawl --profile vector up
```

### 6.3 Lần đầu chạy

```bash
# 1. Copy và điền .env
cp .env.docker.example .env
# Điền: OPENAI_API_KEY, JWT_SECRET, INTERNAL_API_TOKEN

# 2. Build và start
docker compose up --build -d

# 3. Kiểm tra health
curl http://localhost:8000/health    # ai-rag-core
curl http://localhost:8001/health    # ml-clustering
curl http://localhost:8080/actuator/health  # Spring Boot

# 4. Chạy embedding bài báo (lần đầu)
docker compose exec ai-rag-core python -m scripts.embed_articles

# 5. Chạy ML clustering pipeline (lần đầu)
curl -X POST http://localhost:8001/pipeline/trigger \
  -H "X-Internal-Auth: $INTERNAL_API_TOKEN"
```

### 6.4 Service URLs

| Service | URL |
|---------|-----|
| Web UI | http://localhost:5173 |
| Spring Boot API | http://localhost:8080 |
| ai-rag-core Swagger | http://localhost:8000/docs |
| ai-rag-core Metrics | http://localhost:8000/metrics |
| ml-clustering Swagger | http://localhost:8001/docs |
| Neo4j Browser | http://localhost:7474 |
| MailHog | http://localhost:8025 |
| MinIO Console | http://localhost:9001 |

---

## 7. Monitoring & Debugging

### 7.1 Prometheus Metrics

**ai-rag-core**: `GET /metrics`

| Metric | Type | Labels |
|--------|------|--------|
| `ai_rag_requests_total` | Counter | endpoint, status, llm_provider |
| `ai_rag_latency_seconds` | Histogram | endpoint, stage |
| `ai_rag_llm_tokens_total` | Counter | provider, model, token_type |
| `ai_rag_retrieval_results` | Histogram | source |

**ml-clustering**: Không có metrics Prometheus (dùng MLflow)

### 7.2 MLflow Tracking

**ai-rag-core**:
```bash
cd services/ai-rag-core
mlflow ui   # http://localhost:5000
```

**ml-clustering**:
```bash
cd services/ml-clustering
mlflow ui --backend-store-uri sqlite:///mlruns.db
```

### 7.3 Logs

```bash
# ai-rag-core logs
docker logs techradar-ai-rag-core -f

# ml-clustering logs
docker logs techradar-ml-clustering -f

# Crawler logs
docker logs techradar-crawler -f
```

### 7.4 Common Issues

**Model download slow lần đầu**: Dockerfile đã pre-download models. Nếu chạy local, lần đầu sẽ mất 2-3 phút.

**Pipeline retrain failed**: Kiểm tra `/pipeline/status` endpoint để xem error message.

**RAG latency cao**: Kiểm tra nếu EVAL_ENABLED=true (tắt để giảm latency), hoặc giảm SQL_ANALYTICS_MONTHS.

**Memory issues**: Tăng RAM allocation cho Docker containers.

---

<div align="center">

**Last Updated**: 2026-07-01

</div>
