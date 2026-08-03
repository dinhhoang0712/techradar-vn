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
| **ai-rag-core** | 8000 | Graph RAG chatbot, recommendation, forecast, career assistant, mock interview |
| **ml-clustering** | 8001 | HDBSCAN clustering pipeline + serving |

### Supporting services

| Service | Mô tả |
|---------|--------|
| **crawler** | Web crawlers cho 9 nguồn dữ liệu |
| **embedding-service** | Kafka consumer (`extracted_articles`/`extracted_jobs`) → sinh embedding → publish `article_vectors`/`job_vectors` (Kafka), profile `vector` |
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

**ai-rag-core** là FastAPI service (port 8000) cung cấp các AI capabilities: Graph RAG chatbot, recommendation, forecast, career assistant, summarization, report generation, AI agent, và AI mock interview (mới).

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
| POST | `/interview` | X-Internal-Auth | AI mock interview, stateless (xem §2.4) |
| POST | `/company-insight` | X-Internal-Auth | Tóm tắt AI về hồ sơ tuyển dụng/tech stack công ty (`routes_company_insight.py`) |
| POST | `/internal/ai/llm-summary` | X-Internal-Auth | Tóm tắt bằng LLM cho tính năng Compare (`routes_internal.py`) |
| POST | `/internal/ai/moderation-suggestion` | X-Internal-Auth | Gợi ý kiểm duyệt nội dung report cho admin (`routes_internal.py`) |

### 2.3 RAG Pipeline (Adaptive Hybrid Graph RAG)

Pipeline **thích ứng (adaptive)**: một Strategy Selector dựa trên luật (rule-based, xem dưới)
quyết định nhánh nào thực sự cần chạy cho từng câu hỏi, thay vì luôn chạy cố định tất cả các
nguồn. `vector_search` luôn chạy (baseline, không bao giờ bị tắt); `graph_search`, mở rộng đồ thị
đa bước (graph expansion), và `sql_analytics_search` chỉ chạy khi Strategy Selector bật cờ tương
ứng. Có thể tắt toàn bộ phần adaptive để quay về hành vi cũ (luôn chạy tất cả) qua
`strategy_selector_enabled=False` — dùng cho A/B ablation, xem §2.6.

#### Luồng xử lý

```
query + user_id + session_id
        │
        ├── [0] Load conversation history (PostgreSQL, sliding window 10 turns)
        │
        ├── [1] extract_query_entities(query)  — hoist 1 lần, dùng chung cho mọi nhánh dưới
        │         NER: dictionary lookup → regex pattern → NLPHust NER
        │         Alias normalization: k8s → Kubernetes, nodejs → Node.js
        │
        ├── [2] select_strategy(query, extracted)  — Strategy Selector (rule-based, capability-based)
        │         3 quyết định ĐỘC LẬP, không loại trừ nhau:
        │           use_graph              — có thực thể (tech/company/job title/location) không
        │           graph_expansion_depth  — 0/1/2, tuỳ tín hiệu so sánh/đa bước (MULTIHOP_INTENT_PATTERNS)
        │           use_sql_analytics      — có tech entity VÀ tín hiệu phân tích/xu hướng (ANALYTICS_INTENT_PATTERNS)
        │         Một câu hỏi có thể bật ĐỒNG THỜI nhiều nhánh (vd "Xu hướng lương Java" bật cả
        │         use_graph lẫn use_sql_analytics) — không phân loại vào 1 type cố định.
        │
        ├── [3] Parallel asyncio.gather() theo tasks dict (chỉ chứa nhánh thực sự cần chạy)
        │     ├── vector_search(query, top_k=5)          — luôn chạy
        │     │     embed query → Neo4j vector index → top-20 Article
        │     ├── graph_search(query, extracted)          — nếu use_graph
        │     │     Cypher (graph_queries.py) → Job + Company + Technology, 1-hop
        │     ├── expand_subgraph(tech_names, company_names, depth)  — nếu graph_expansion_depth > 0
        │     │     subgraph_expand_query() — 1-2 hop, ORDER BY pagerank_score (đọc GDS score có
        │     │     sẵn từ apps/backend, xem docs/DATABASE.md §4.1) → triple phẳng kèm hop-distance
        │     ├── sql_analytics_search(tech_names, months=6)  — nếu use_sql_analytics
        │     │     PostgreSQL tech_analytics (Gold ETL)
        │     └── get_user_context(user_id)               — nếu có user_id
        │           PostgreSQL user_profile.preferences_json
        │
        ├── [4] rerank_context(query, articles, jobs, companies, sql_rows)  — unified rerank
        │         Rerank RIÊNG từng loại nguồn (không gộp 1 batch — điểm cross-encoder không hiệu
        │         chỉnh được giữa các miền văn bản khác nhau), cùng BGE reranker (ONNX, CPU),
        │         ngưỡng 0.40. Triple từ graph expansion KHÔNG qua rerank (đã anchor theo entity,
        │         đã cap/dedup bằng Cypher).
        │
        ├── [5] Fallback nếu không tìm thấy gì
        │         trả về "Không tìm thấy thông tin..."
        │
        ├── [6] Build prompt
        │         system_prompt + history + rag_template, gồm 4 khối context (article/job/company+
        │         analytics/subgraph — khối subgraph nhóm triple theo hop, không phải danh sách phẳng)
        │
        ├── [7] LLM generate (OpenAI gpt-4o-mini, Gemini, Groq, hoặc Claude — qua services/llm-gateway)
        │         LLMGateway: provider chính LLM_PROVIDER, retry cùng provider tối đa 3 lần khi
        │         429/503 (_MAX_RETRIES), hết retry thì tự fallback sang provider kế tiếp còn API
        │         key (thứ tự OpenAI→Groq→Gemini→Claude, trừ provider chính đứng đầu)
        │
        └── [8] Evaluation (fire-and-forget) — xem §2.6
```

Response trả thêm 2 field so với trước: `subgraph` (JSON-LD tối giản của các triple mở rộng,
`null` nếu không expansion) và `strategy` (`{use_graph, graph_expansion_depth,
use_sql_analytics, matched_signals}` — giải thích được Strategy Selector đã quyết định gì cho
câu hỏi này, phục vụ explainability/debug).

`pipeline.py` (non-streaming) và `pipeline_stream.py` (SSE streaming) triển khai **song song, cố
ý trùng lặp logic** (không share qua module chung) để tương thích cách test hiện tại monkeypatch
từng module riêng — mọi thay đổi ở 1 file phải tự tay áp dụng sang file kia, không có cơ chế
chống trôi (drift).

#### Chi tiết từng bước

**Vector Search**: Embed câu hỏi với prefix "query: " → tìm trong Neo4j vector index (768d cosine)

**Graph Traversal**: NER pipeline trích xuất entity (dictionary lookup → regex pattern → NLPHust NER). Alias normalization tự động: `k8s → Kubernetes`, `nodejs → Node.js`

**Graph Expansion**: mở rộng 1-2 hop từ thực thể đã trích xuất (không phải node cụ thể matched bởi
graph search — seed độc lập để giữ song song hoá), depth do Strategy Selector quyết định, clamp
theo `graph_max_hops`. Tận dụng `pagerank_score` đã tính sẵn bởi backend Java (GDS) để ưu tiên
công nghệ trung tâm khi kết quả bị cắt bởi `graph_expansion_limit`.

**SQL Analytics**: Đọc từ `tech_analytics` table (Gold ETL rebuild mỗi đêm)

**Rerank**: BGE reranker chấm điểm cross-encoder, ngưỡng 0.40, chạy riêng theo từng loại nguồn (unified rerank)

**Build Prompt**: System prompt + conversation history + RAG context (article + job/company + analytics + subgraph, nhóm theo hop + user context)

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

#### AI Interview (NEW)

Phỏng vấn thử với AI — **stateless**: không lưu session phía server (khác `/chat`, giống
`/career`/`/recommend`). Trạng thái được suy ra hoàn toàn từ độ dài `history` do client gửi mỗi
lượt (`app/services/interview_service.py`):

```
POST /interview
{
  "target_role": "Senior Backend Developer",
  "target_company": "Tiki",          // optional
  "history": []                       // [] = bắt đầu buổi mới
}
```

- **`len(history) == 0`** (mở đầu): tra Neo4j lấy 1 job posting thật khớp `target_role`
  (+ `target_company` nếu có) qua `Job-[:POSTED_BY|HIRES_FOR]->Company` (`graph_queries.py`,
  `JOBS_BY_TITLE_AND_COMPANY`, fallback về `JOBS_BY_TITLE` dùng chung với RAG chat nếu không có
  company hoặc không tìm thấy, fallback tiếp về câu nhắc chung chung nếu Neo4j lỗi/không có kết
  quả) → LLM sinh 1 câu hỏi mở đầu (`interview_opening_template.txt`) → trả `next_question`,
  `turn=1`.
- **`0 < len(history) < 5`** (giữa buổi): 1 LLM call vừa chấm điểm câu trả lời gần nhất vừa sinh
  câu hỏi tiếp theo, parse theo delimiter cố định `---FEEDBACK---`/`---QUESTION---`
  (`interview_turn_template.txt`) → trả `feedback_on_last_answer`, `next_question`, `turn`.
- **`len(history) >= 5`** (`MAX_TURNS`): LLM sinh nhận xét tổng kết (`interview_final_template.txt`),
  điểm số lấy từ dòng `SCORE: N/10` cuối cùng (regex, clamp 0-10, mặc định 5 nếu model không theo
  đúng format) → trả `is_final=true`, `final_summary: {score, summary}`.

**Response fields:** `next_question`, `feedback_on_last_answer`, `is_final`, `turn`, `final_summary`
(`{score, summary}` hoặc `null`).

**Lưu ý khác với các route AI khác:** `routes_interview.py` KHÔNG có `try/except` quanh lời gọi
LLM (khác `/chat` và `/internal/ai/llm-summary`, cả 2 đều catch → trả `HTTPException` có status
code rõ ràng) — nếu `generate()` hết retry, lỗi sẽ trồi lên thành `500` mặc định của FastAPI,
không có `detail` mô tả. Neo4j lookup thì có catch (log warning, fallback sang text chung chung).

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

#### Cost tracking (`llm_usage_log`)

Bảng Postgres `llm_usage_log` là nơi lưu billing/cost thật (provider, model, input/output
tokens, `cost_usd` tính từ bảng giá tĩnh trong `services/llm-gateway/src/llm_gateway/pricing.py`,
và `fallback_from` nếu request phải rơi từ provider chính sang provider dự phòng). Ghi vào bảng
này là việc của **caller**, không phải của gateway — `services/llm-gateway` chỉ gọi callback
`on_usage` sau mỗi lần gọi LLM thành công, còn ghi Postgres ở đâu là do từng service tự quyết.
`ai-rag-core` wire callback này qua `app/core/llm_usage_sink.py::log_usage_to_postgres` (dùng ở
`get_gateway()`, `app/core/generator.py`) nên mọi lần chat/agent/interview qua LLM đều có dòng
trong `llm_usage_log`. **`ml-clustering` hiện KHÔNG wire `on_usage`** (`build_gateway()` ở
`src/labeling/llm_labeler.py` không truyền tham số này) — nghĩa là các lần gọi LLM để sinh cluster
label (Stage 4) chưa được track cost/usage ở bảng này, dù vẫn đi qua cùng `services/llm-gateway`.

#### Evaluation (`scripts/evaluate_rag.py`)

2 chế độ, chọn qua `EVAL_METRICS_MODE`:
- **`local`** (mặc định) — không dùng LLM: faithfulness ước lượng bằng word-overlap, relevancy
  bằng cosine similarity embedding. Dựng thêm chế độ này vì quota miễn phí Groq/Gemini dễ cạn khi
  chạy RAGAS thật nhiều lần trong lúc ablation — số liệu chỉ mang tính proxy, không thay thế RAGAS
  chuẩn.
- **`ragas`** — RAGAS thật (`faithfulness`, `answer_relevancy`, và `context_precision`/
  `context_recall` nếu câu hỏi có `ground_truth` khác rỗng trong `TEST_QUERIES`), judge model qua
  Groq (`ChatGroq`, mặc định `llama-3.3-70b-versatile`) + `HuggingFaceEmbeddings`.

Cả 2 chế độ log vào MLflow (experiment `rag_evaluation`, kèm tham số `variant`/
`strategy_selector_enabled`/`graph_expansion_enabled`/`unified_rerank_enabled` để so sánh ablation)
→ `mlflow ui`. `TEST_QUERIES`'s `ground_truth` là dữ liệu tra trực tiếp Postgres/Neo4j (độc lập với
pipeline đang được đánh giá) — xem `docs/GRAPH_RAG_KG_REPORT.md` §3.1 để biết cách soạn và trạng
thái duyệt hiện tại.

### 2.7 Configuration

| Env var | Default | Mô tả |
|---------|---------|-------|
| `NEO4J_URI` | — | URI Neo4j (self-hosted qua docker-compose — dự án từng chạy AuraDB, đã migrate) |
| `OPENAI_API_KEY` | — | API key OpenAI |
| `GEMINI_API_KEY` | — | API key Gemini |
| `GROQ_API_KEY` | — | API key Groq |
| `ANTHROPIC_API_KEY` | — | API key Claude (services/llm-gateway) |
| `LLM_PROVIDER` | `openai` | `"openai"` \| `"gemini"` \| `"groq"` \| `"claude"` |
| `LLM_MODEL` | `gpt-4o-mini` | Model LLM |
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `INTERNAL_API_TOKEN` | `""` | Token kiểm tra từ Spring |
| `EMBED_SECRET` | `changeme` | Secret cho `/embed/trigger` |
| `EVAL_ENABLED` | `false` | Bật RAGAS evaluation |
| `EVAL_METRICS_MODE` | `local` | `"local"` (proxy, không LLM) \| `"ragas"` (RAGAS thật, cần Groq quota) — xem §2.6 |
| `SQL_ANALYTICS_MONTHS` | `6` | Khoảng thời gian đọc tech_analytics |
| `STRATEGY_SELECTOR_ENABLED` | `true` | Bật Strategy Selector (adaptive); `false` = quay về hành vi cũ, luôn chạy mọi nhánh — dùng cho ablation §2.3 |
| `GRAPH_EXPANSION_ENABLED` | `true` | Bật mở rộng đồ thị đa bước |
| `GRAPH_MAX_HOPS` | `2` | Trần an toàn cho `graph_expansion_depth` (Strategy Selector không được vượt qua) |
| `GRAPH_EXPANSION_LIMIT` | `100` | Số triple tối đa trả về mỗi lần mở rộng đồ thị |
| `UNIFIED_RERANK_ENABLED` | `true` | Bật rerank riêng từng loại nguồn (article/job/company/analytics) |
| `RERANK_TOP_K` | `5` | Số kết quả giữ lại sau rerank mỗi loại nguồn |

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
│   ├── features/          # Feature extraction (tech_aliases.py, noise_filter.py, ...)
│   ├── clustering/        # HDBSCAN training
│   ├── labeling/          # LLM labeling
│   └── tracking/          # MLflow logging
├── scripts/               # Portability tooling (KHÔNG phải pipeline chính, chạy tay/1 lần)
│   ├── seed_related_to.py            # Seed RELATED_TO ground truth thủ công vào Neo4j
│   ├── export_tech_alias_seed.py     # Export dp_tech_alias_map (Postgres) → conf/seed_data/tech_alias_seed.json
│   ├── publish_git_artifacts.sh       # Publish model artifact qua git (thay MinIO, miễn phí/portable)
│   └── adopt_git_artifacts.sh         # Adopt lại artifact từ git trên máy khác
├── conf/seed_data/         # Seed file check-in vào git — nguồn "ground truth"/canonical portable
│   ├── related_to_seed.json          # Cặp Technology RELATED_TO curate thủ công
│   └── tech_alias_seed.json          # Export tĩnh của dp_tech_alias_map — merge vào TECH_ALIAS_MAP
├── params.yaml            # Hyperparameters (DVC)
├── dvc.yaml               # Pipeline definition
└── data/                  # DVC-tracked artifacts
```

> **`tech_aliases.py` dùng 2 nguồn alias:** `TECH_ALIAS_MAP` hardcode trong source (bao gồm cả
> tên design tool và tiếng Việt như "học máy"/"trí tuệ nhân tạo" mà `dp_tech_alias_map` không
> có) VÀ `conf/seed_data/tech_alias_seed.json` (export tĩnh từ `dp_tech_alias_map` — xem
> [`docs/DATABASE.md`](./DATABASE.md) §4.3). 2 map được merge lúc import module, **seed đè lên
> hardcoded khi trùng key** vì seed phản ánh đúng canonical name đang thực sự được ghi vào Neo4j
> sống (write-time canonicalization + `tech_dedup`). Seed file là snapshot tĩnh — chạy lại
> `python -m scripts.export_tech_alias_seed` để cập nhật khi `dp_tech_alias_map` có alias mới
> đáng kể (không tự động, không có Postgres runtime dependency trong service này).

### 3.2 Pipeline 6 Stages

Pipeline HDBSCAN chạy tuần tự qua 6 stages. Sau TRAIN, pipeline kiểm tra model mới có được
promote lên champion không (§ Champion/Challenger gate bên dưới) — nếu không, LABEL/PUBLISH/
WRITEBACK bị bỏ qua toàn bộ (không tốn LLM cost, không ghi Neo4j, không đổi artifact đang serve):

```
Neo4j (self-hosted, docker-compose)
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
  Chọn best theo primary_metric (Silhouette/DBCV/...)
  MLflow log trials
  Register model — chỉ promote "champion" nếu thắng champion cũ
     │
     ▼  (bỏ qua nếu KHÔNG promote — xem ghi chú champion gate)
Stage 4 — LABEL
  LLM sinh cluster labels qua services/llm-gateway (llm_labeler.py::build_gateway) — provider
  chính = params.yaml labeling.provider (hiện tại: "groq"), fallback tự động sang provider kế
  tiếp còn API key theo thứ tự gemini → openai → groq (Claude KHÔNG được hỗ trợ cho path này —
  llm_labeler.py không có nhánh build provider Claude)
  - label (tiếng Việt)
  - domain
  - description
  - is_coherent
     │
     ▼
Stage 6 — PUBLISH
  Upload artifact (best_labels.parquet, cluster_labels.json, technologies.parquet)
  lên MinIO, verify (head_object) rồi mới cập nhật manifest latest.json —
  chỉ khi mọi artifact bắt buộc đã verify xong mới flip manifest (không bao giờ
  để serving thấy 1 tag publish nửa vời). No-op nếu MinIO không cấu hình.
     │
     ▼
Stage 5 — WRITEBACK
  Ghi kết quả về Neo4j (optional) — đứng SAU publish (không phải trước như cũ):
  nếu publish lỗi, Neo4j không bị đụng, tránh graph và serving API lệch tag nhau.
```

> **Publish / Artifact Deployment (Stage 6 — `pipelines/stage_06_publish.py`)**: trước đây bước
> đẩy artifact lên MinIO chỉ là 1 hàm phụ chạy SAU writeback và không bao giờ ghi `latest.json`
> — nghĩa là chế độ `MLCLUSTER_SNAPSHOT_TAG=latest` (đọc bởi `app/store.py` để hỗ trợ reload
> theo TTL / nhiều replica) là cấu hình chết, chưa từng hoạt động. Stage 6 khắc phục bằng cách:
> upload artifact → `head_object` verify từng file đúng kích thước → CHỈ SAU ĐÓ mới ghi đè
> `latest.json` (kèm `previous_tag` để audit/rollback thủ công). Nếu 1 upload thiếu/lỗi,
> `latest.json` giữ nguyên tag cũ — serving không bao giờ thấy 1 lần publish dở dang, tránh
> đúng lỗi 503 mô tả ở đầu tài liệu này (model mới train xong nhưng chưa lên MinIO đầy đủ).

> **Alias normalization (Stage 2)** dùng `src/features/tech_aliases.py` §3.1 ở trên — merge trùng
> lặp ngay trên snapshot Parquet của lần train đó, không đụng Neo4j sống. Tự nó không còn hoàn
> toàn độc lập với `dp_tech_alias_map` nữa: kể từ khi có `conf/seed_data/tech_alias_seed.json`
> (export tĩnh từ `dp_tech_alias_map`, seed đè lên hardcoded khi trùng key), 2 nguồn alias đã
> thống nhất tên canonical (vd không còn lệch `"Vue"` (`dp_tech_alias_map`) vs `"Vue.js"`
> (`TECH_ALIAS_MAP` cũ)) — nhưng vẫn cần chạy lại `python -m scripts.export_tech_alias_seed`
> thủ công để cập nhật seed khi có alias mới, không tự động.

> **Champion/Challenger gate (Stage 3 → `register_best_model`, `src/tracking/mlflow_logger.py`)**:
> model mới **luôn** được đăng ký vào MLflow Model Registry (giữ lịch sử version để xem lại),
> nhưng chỉ được gán alias `champion` nếu `primary_metric` của nó tốt hơn hoặc bằng champion
> đang serve — cùng chiều so sánh với `select_best_trial` (`src/clustering/tuner.py`, hàm dùng
> chung `normalize_metric_for_comparison`: silhouette/calinski_harabasz/dbcv càng cao càng tốt,
> davies_bouldin càng thấp càng tốt). Chưa có champion (lần train đầu tiên) → promote thẳng.
> Model mới thua → **giữ nguyên** champion cũ, model mới vẫn nằm trong Registry nhưng không
> được serve. Trước đây gán `champion` vô điều kiện — 1 lần retrain ra kết quả tệ hơn (data
> xấu, hyperparameter kém...) sẽ âm thầm ghi đè model tốt hơn đang chạy.
>
> Quyết định này (tag `promoted_to_champion` trên parent MLflow run, đọc qua
> `is_run_promoted_to_champion` trong `src/tracking/mlflow_logger.py`) trước đây CHỈ ảnh hưởng
> alias trong Model Registry — LABEL/PUBLISH/WRITEBACK vẫn chạy full cho mọi lần train dù thua
> champion, nên 1 retrain tệ hơn vẫn tốn LLM cost + ghi Neo4j + phát đi artifact publish (dù
> AppStore cuối cùng vẫn serve champion cũ vì MLflow registry không đổi). Từ khi có Stage 6,
> `app/routes_pipeline.py` đọc tag này ngay sau TRAIN và bỏ qua hẳn LABEL/PUBLISH/WRITEBACK nếu
> không promote (trả `params.yaml` về tag cũ) — `POST /pipeline/trigger?force=true` bỏ qua gate
> này nếu thật sự muốn deploy 1 model thua champion (vd so sánh qualitative).

### 3.3 API Endpoints

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/health` | Public | Health check + snapshot info — trả **503** (không phải 200 giả) khi artifact chưa load được (`data_available=false`) |
| POST | `/pipeline/trigger` | X-Internal-Auth | Trigger pipeline retrain |
| GET | `/pipeline/status` | Public | Pipeline status |
| GET | `/clusters` | Public | Danh sách clusters |
| GET | `/clusters/{id}` | Public | Chi tiết cluster |
| GET | `/tech/{name}/cluster` | Public | Tra cứu cluster của tech — tech chưa có trong snapshot vẫn có thể trả `provisional=true` nếu khớp đủ giống tech đã biết |
| POST | `/predict/batch` | Public | Batch lookup, trả thêm `n_provisional` |

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

> **Provisional lookup (tech chưa có trong snapshot):** tech thật sự mới (chưa từng crawl,
> hoặc mới ingest sau lần train gần nhất) phải đợi tới lần retrain kế tiếp mới được HDBSCAN
> phân cụm thật — `Stage 3 — TRAIN` không hỗ trợ incremental fit. Để không trả 404 trắng
> trong lúc chờ, `AppStore.find_nearest_known_tech()` (`app/store.py`) so tên tech mới với
> mọi tên đã biết bằng `difflib` (string similarity thuần, **không** dùng embedding model —
> container serving cố tình không có `torch`/`sentence-transformers`, xem `requirements-api.txt`)
> — nếu đủ giống (`ratio() ≥ 0.72`) và tech khớp không phải noise, trả về cluster của nó kèm
> `"found": false, "provisional": true, "matched_via": "<tên đã biết>", "match_score": 0.86`.
> Đây là suy luận thô theo MẶT CHỮ, không hiểu ngữ nghĩa (khó khớp `"K8s"` ~ `"Kubernetes"` trừ
> khi đã có sẵn alias `dp_tech_alias_map` chuẩn hoá tên trước khi tới đây) — chỉ 404 khi không
> tìm được ứng viên nào đủ giống. `POST /predict/batch` trả thêm `n_provisional` tách biệt
> khỏi `n_found`/`n_not_found` để caller biết kết quả nào là tạm.

### 3.5 Pipeline Trigger

**POST /pipeline/trigger**

Khởi động pipeline retrain trong background thread. Trả về ngay lập tức. `?force=true` bỏ
qua champion gate — chạy LABEL/PUBLISH/WRITEBACK dù model mới không thắng champion hiện tại.

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
  "error": null,
  "deployed": null,
  "note": null
}
```

`deployed` chỉ có giá trị (`true`/`false`) khi `status` là `success`/`failed`: `false` nghĩa là
train xong nhưng model không thắng champion nên LABEL/PUBLISH/WRITEBACK bị bỏ qua (`note` giải
thích lý do, `status` vẫn `success` vì bản thân train không lỗi) — vẫn phục vụ tag cũ.

**Lịch tự động**: Chủ nhật 06:00 Asia/Ho_Chi_Minh (APScheduler trong data-platform). `job_retrain_clustering` không chỉ fire-and-forget: sau khi trigger, nó **poll `/pipeline/status` định kỳ** cho tới khi pipeline xong (hoặc timeout) rồi ghi kết quả thật vào `dp_pipeline_runs` — xem [`DATA_PLATFORM.md` — Clustering Retrain](./DATA_PLATFORM.md#clustering-retrain).

### 3.6 Configuration

| Env var | Default | Mô tả |
|---------|---------|-------|
| `NEO4J_URI` | — | URI Neo4j (self-hosted) |
| `NEO4J_PASSWORD` | — | Mật khẩu Neo4j |
| `OPENAI_API_KEY` | — | API key OpenAI, dùng khi là provider chính hoặc fallback (stage 4) |
| `GEMINI_API_KEY` | — | API key Gemini, dùng khi là provider chính hoặc fallback (stage 4) |
| `GROQ_API_KEY` | — | API key Groq — provider chính mặc định của `labeling.provider` trong `params.yaml` (stage 4) |
| `INTERNAL_API_TOKEN` | `""` | Token kiểm tra `/pipeline/trigger` |
| `MLCLUSTER_MINIO_BUCKET` | `""` | MinIO bucket artifacts (local nếu trống) |
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

**Khác với `ai-rag-core`'s `/embed/trigger`** — đây là 2 luồng embedding hoàn toàn tách biệt, không
phải cùng một cơ chế:
- `ai-rag-core` `POST /embed/trigger` (crawler gọi qua `X-Embed-Secret`): đọc Article trực tiếp từ
  Neo4j, embed, ghi thẳng lên property `Article.embedding` trong Neo4j (dùng cho vector search nội
  bộ khi KHÔNG bật profile `vector`).
- **`embedding-service`** (service riêng, container `techradar-embedding`, chỉ chạy khi bật profile
  `vector`): Kafka consumer trên topic `extracted_articles`/`extracted_jobs` (không phải
  `raw.articles`) → sinh embedding → publish sang topic Kafka `article_vectors`/`job_vectors` —
  KHÔNG ghi trực tiếp vào Neo4j.

### 4.3 Qdrant Writer

Kafka consumer trên `article_vectors`/`job_vectors` (output của `embedding-service` ở trên) → ghi
vào Qdrant collection (vector store thay thế cho Neo4j vector index). Chỉ chạy khi dùng profile
`vector`.

```bash
docker compose --profile vector up qdrant qdrant-writer
```

---

## 5. Tích hợp với Spring Boot

### 5.1 Spring Boot gọi Python

> **Cập nhật kiến trúc gateway:** `/chat`, `/chat/stream` có client riêng, typed
> (`RagProxyService`). Còn `/recommend /forecast /career /summarize(`/chat/summarize`) /report
> /agent /interview` giờ đi qua **một** module gateway dùng chung là `features/aiproxy`
> (`PythonAiProxyClient`/`AiProxyPort`) thay vì 6 module/client riêng biệt như trước — xem
> [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.16. Phía `ai-rag-core` (Python) **không đổi**:
> mỗi route vẫn là 1 file `routes_*.py` riêng như liệt kê ở §2.2/§2.4.

Pattern `WebClient` chung (cả `RagProxyService` lẫn `PythonAiProxyClient` đều theo pattern này,
khác biệt là `PythonAiProxyClient` forward `Map<String,Object>` nguyên văn, không có DTO):

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
| **GET** `/api/v1/forecast` *(sửa: gateway nhận GET + query params, không phải POST)* | POST `/forecast` | - |
| POST `/api/v1/career` | POST `/career` | - |
| POST `/api/v1/chat/summarize` | POST `/summarize` | - |
| GET `/api/v1/report` | POST `/report` | - |
| POST `/api/v1/agent` | POST `/agent` | - |
| POST `/api/v1/interview` **(NEW)** | POST `/interview` | - |
| POST `/api/v1/company-insight` **(NEW)** | POST `/company-insight` | - |
| GET `/api/v1/clustering/clusters` | - | GET `/clusters` |

**Auth phía gateway (SecurityConfig, không liên quan X-Internal-Auth ở §5.3):** `/forecast`,
`/report`, `/chat/summarize`, `/company-insight` là public (không cần Bearer JWT); `/recommend`,
`/career`, `/interview`, `/agent` yêu cầu Bearer JWT. Nguyên tắc: route dùng
`AiProxyRequestHandler.forward()` (nội dung chung, không gắn user) thì public; route dùng
`forwardAsCurrentUser()` (gắn `user_id` từ JWT, cá nhân hoá) thì bắt buộc auth. Cả 8 route này giờ
cũng bị rate limit (Redis, theo user id cho route auth-required / theo IP cho route public — xem
`docs/DATABASE.md` §5) để chặn spam vào các lệnh gọi LLM tốn kém. Xem `docs/API_DOCs_v1.md` mục
Phân quyền và `docs/BACKEND_GUIDE.md` §4.16.

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
| Interview (NEW) | 60 giây (`AiProxyPort.DEFAULT_TIMEOUT`) |

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

**Last Updated**: 2026-07-24

</div>
