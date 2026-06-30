# AI Platform — TechRadar VN

> Tài liệu kỹ thuật đầy đủ cho 2 Python AI services: `services/ai-rag-core` và `services/ml-clustering`.

---

## Mục lục

1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [services/ai-rag-core](#2-servicesai-rag-core)
   - 2.1 [Các endpoint](#21-các-endpoint)
   - 2.2 [RAG Pipeline (Graph RAG)](#22-rag-pipeline-graph-rag)
   - 2.3 [Conversation Memory](#23-conversation-memory)
   - 2.4 [User Long-term Memory](#24-user-long-term-memory)
   - 2.5 [Recommendation Service](#25-recommendation-service)
   - 2.6 [Forecast Service](#26-forecast-service)
   - 2.7 [Career Assistant](#27-career-assistant)
   - 2.8 [Summarization Service](#28-summarization-service)
   - 2.9 [Report Generator](#29-report-generator)
   - 2.10 [AI Agent (LangChain)](#210-ai-agent-langchain)
   - 2.11 [Prometheus Monitoring](#211-prometheus-monitoring)
   - 2.12 [RAGAS Evaluation](#212-ragas-evaluation)
   - 2.13 [Cấu hình môi trường](#213-cấu-hình-môi-trường)
   - 2.14 [Cấu trúc thư mục](#214-cấu-trúc-thư-mục)
   - 2.15 [Chạy service](#215-chạy-service)
3. [services/ml-clustering](#3-servicesml-clustering)
   - 3.1 [Pipeline 5 stages](#31-pipeline-5-stages)
   - 3.2 [Các endpoint serving](#32-các-endpoint-serving)
   - 3.3 [Pipeline Trigger (retrain tự động)](#33-pipeline-trigger-retrain-tự-động)
   - 3.4 [Cấu trúc thư mục](#34-cấu-trúc-thư-mục)
   - 3.5 [Cấu hình và chạy](#35-cấu-hình-và-chạy)
4. [Supporting services](#4-supporting-services)
5. [Tích hợp với Spring Boot](#5-tích-hợp-với-spring-boot)
6. [Luồng dữ liệu tổng thể](#6-luồng-dữ-liệu-tổng-thể)
7. [Yêu cầu hệ thống & deploy](#7-yêu-cầu-hệ-thống--deploy)

---

## 1. Tổng quan kiến trúc

```
services/
├── ai-rag-core        FastAPI, port 8000 — AI inference: RAG, recommend, forecast,
│                      career, summarize, report, agent
├── ml-clustering      FastAPI, port 8001 — HDBSCAN clustering pipeline + serving
├── crawler            Worker — crawl DanTri, GenK, Viblo, GitHub, ITviec, TopCV → Kafka
├── embedding-service  Kafka consumer — nhận Article → sinh embedding → ghi Neo4j
└── qdrant-writer      Kafka consumer — nhận embedding → ghi Qdrant vector store
```

**Phân tách trách nhiệm giữa Spring Boot và Python:**

| Chức năng | Spring Boot (apps/backend) | Python (services/) |
|---|---|---|
| Authentication & Authorization | ✅ JWT, SecurityConfig | ❌ |
| Schema PostgreSQL | ✅ Flyway migrations | ❌ (chỉ đọc) |
| Business logic CRUD | ✅ | ❌ |
| RAG + LLM | ❌ | ✅ ai-rag-core |
| ML clustering | ❌ | ✅ ml-clustering |
| Data ingestion | ❌ | ✅ crawler, embedding-service |

**Bảo mật nội bộ:** Spring Boot gắn header `X-Internal-Auth: <INTERNAL_API_TOKEN>` vào tất cả request đến Python. Python kiểm tra header này — nếu token được cấu hình, request không có header hợp lệ bị từ chối 401.

---

## 2. services/ai-rag-core

### 2.1 Các endpoint

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| GET | `/health` | Public | Kiểm tra kết nối Neo4j |
| GET | `/metrics` | Public | Prometheus metrics |
| POST | `/chat` | X-Internal-Auth | Chat RAG (non-streaming) |
| POST | `/chat/stream` | X-Internal-Auth | Chat RAG (SSE streaming) |
| GET | `/chat/session/{id}/messages` | X-Internal-Auth | Lịch sử hội thoại |
| POST | `/embed/trigger` | X-Embed-Secret | Trigger embedding bài báo mới |
| GET | `/embed/status` | X-Internal-Auth | Trạng thái embedding job |
| POST | `/internal/ai/llm-summary` | X-Internal-Auth | LLM so sánh 2 công nghệ (dùng bởi Spring) |
| POST | `/recommend` | X-Internal-Auth | Gợi ý công nghệ |
| POST | `/forecast` | X-Internal-Auth | Dự báo xu hướng |
| POST | `/career` | X-Internal-Auth | Tư vấn career path |
| POST | `/summarize` | X-Internal-Auth | Tóm tắt xu hướng công nghệ |
| POST | `/report` | X-Internal-Auth | Báo cáo xu hướng theo kỳ |
| POST | `/agent` | X-Internal-Auth | AI Agent (multi-tool) |

---

### 2.2 RAG Pipeline (Graph RAG)

Pipeline `answer()` trong [app/core/pipeline.py](../services/ai-rag-core/app/core/pipeline.py) kết hợp **4 nguồn song song** trước khi sinh câu trả lời.

#### Luồng xử lý

```
query + user_id + session_id
        │
        ├── [0] Load conversation history (PostgreSQL chat_message, sliding window 10 turns)
        │
        ├── [1] Parallel asyncio.gather() ────────────────────────────┐
        │     ├── vector_search(query, top_k=5)                       │
        │     │       embed query → Neo4j vector index → top-20 Article
        │     ├── graph_search(query)                                 │
        │     │       NER → Cypher → Job + Company + Technology       │
        │     └── get_user_context(user_id)  [chỉ khi user đăng nhập]│
        │               PostgreSQL user_profile.preferences_json      │
        │                                                             │
        ├── [1b] sql_analytics_search(tech_entities, months=6)        │
        │         PostgreSQL tech_analytics (Gold ETL)                │
        │                                                             │
        ├── [2] Rerank articles                                       │
        │         woxpas-ai/bge-reranker-v2-m3-onnx (ONNX, CPU)     │
        │         threshold 0.40 → top-5, CPU-bound → thread pool    │
        │                                                             │
        ├── [3] Fallback nếu không tìm thấy gì:                      │
        │         trả về "Không tìm thấy thông tin..."               │
        │                                                             │
        ├── [4] Build prompt                                          │
        │         system_prompt + history + rag_template:            │
        │           {context} + {job_context} + {analytics_block}    │
        │           + {user_block} + {query}                         │
        │                                                             │
        ├── [5] LLM generate (OpenAI gpt-4o-mini hoặc Gemini)       │
        │         retry tối đa 3 lần khi 429/503                     │
        │                                                             │
        └── [6] RAGAS evaluation (fire-and-forget, asyncio.create_task)
```

#### Chi tiết từng bước

**Bước 1a — Vector Search** (`app/core/retriever.py`)

```python
# Embed câu hỏi với prefix "query: " (quy ước E5)
embedding = embedder.encode("query: " + query, normalize_embeddings=True)
# Tìm trong Neo4j vector index (768d cosine)
CALL db.index.vector.queryNodes('article_embedding_index', 20, $embedding)
```

**Bước 1b — Graph Traversal** (`app/core/retriever_graph.py`)

NER pipeline trích xuất entity từ câu hỏi theo thứ tự ưu tiên:
1. Dictionary lookup (từ điển kỹ thuật cố định)
2. Regex pattern (tên công nghệ uppercase, version numbers)
3. `NlpHust/ner-vietnamese-electra-base` (NER model tiếng Việt)

Alias normalization tự động: `k8s → Kubernetes`, `nodejs → Node.js`, `golang → Go`, v.v.

Cypher query tìm Job, Company, Technology liên quan đến entity đã trích xuất.

**Bước 1c — SQL Analytics** (`app/core/retriever_sql.py`)

```sql
SELECT technology_name, month, job_count, article_count,
       growth_rate, mom_growth, yoy_growth
FROM tech_analytics
WHERE technology_name = ANY(:names)
  AND month >= CURRENT_DATE - INTERVAL ':months months'
ORDER BY technology_name, month DESC
```

Bảng `tech_analytics` do Gold ETL (`data-platform/gold/pg_etl.py`) rebuild mỗi đêm. Python chỉ đọc.

**Bước 2 — Rerank** (`app/core/reranker.py`)

- Model: `woxpas-ai/bge-reranker-v2-m3-onnx` (quantized, ~1GB RAM)
- Chấm điểm cross-encoder cho từng cặp (query, article)
- Chạy trong `loop.run_in_executor()` để không block event loop
- Ngưỡng: 0.40 — bài nào dưới ngưỡng bị loại
- Nếu toàn bộ bài bị loại (query mơ hồ): dùng top-3 điểm cao nhất + `low_confidence=True` để LLM cảnh báo độ tin cậy thấp

**Bước 4 — Build Prompt** (`app/core/prompt_builder.py`)

```
messages = [
  {"role": "system", "content": system_prompt.txt},
  # Conversation history (sliding window)
  {"role": "user", "content": <turn N-k>},
  {"role": "assistant", "content": <turn N-k>},
  ...
  # Current turn với full RAG context
  {"role": "user", "content": rag_template.txt với context đầy đủ}
]
```

Format analytics block trong prompt:
```
React (2024-12): 1240 việc làm, 87 bài viết, MoM +18.2%, YoY +42.1%
Kubernetes (2024-12): 830 việc làm, 54 bài viết, MoM +5.6%
```

---

### 2.3 Conversation Memory

File: [app/memory/conversation.py](../services/ai-rag-core/app/memory/conversation.py)

**Cơ chế sliding window:**
- Đọc `LIMIT :n ORDER BY created_at DESC` → lấy `n` message gần nhất
- Reverse thành chronological order trước khi inject vào prompt
- Giới hạn: 10 turns (5 cặp user/assistant) — đủ context, không quá tốn token

**Schema bảng (Flyway, owned bởi Spring Boot):**
```sql
chat_message(
    id         UUID PRIMARY KEY,
    session_id UUID REFERENCES chat_session(id),
    role       TEXT,         -- 'user' | 'assistant'
    content    TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
)
```

**Luồng trong chat_service:**
1. Tạo hoặc lấy `ChatSession` từ PostgreSQL
2. Lưu user message
3. Gọi `answer(query, session_id=...)` → pipeline load history bên trong
4. Lưu assistant message
5. Commit

---

### 2.4 User Long-term Memory

File: [app/memory/user_context.py](../services/ai-rag-core/app/memory/user_context.py)

**Nguồn dữ liệu:** cột `preferences_json` trong `user_profile` (PostgreSQL).

**Cấu trúc JSON gợi ý:**
```json
{
  "interested_techs": ["React", "TypeScript", "Node.js"],
  "current_role": "Frontend Developer",
  "experience_years": 3,
  "tech_interactions": {
    "React": 12,
    "Next.js": 7,
    "TypeScript": 5
  }
}
```

**`increment_tech_interaction()`** — tăng bộ đếm mỗi khi user hỏi về 1 tech:

```sql
UPDATE user_profile
SET preferences_json = jsonb_set(
    COALESCE(preferences_json, '{}'),
    ARRAY['tech_interactions', :tech],
    (COALESCE((preferences_json -> 'tech_interactions' ->> :tech)::int, 0) + 1)::text::jsonb
)
WHERE user_id = :uid
```

Được gọi **fire-and-forget** sau khi chat commit xong, tránh tăng latency:
```python
asyncio.create_task(_track_interactions(user_id, result["entities"]))
```

**Format trong prompt:**
```
Thông tin người dùng:
- Vai trò: Frontend Developer
- Kinh nghiệm: 3 năm
- Công nghệ quan tâm: React, TypeScript, Node.js
- Thường hỏi về: React, Next.js, TypeScript
```

---

### 2.5 Recommendation Service

File: [app/services/recommend_service.py](../services/ai-rag-core/app/services/recommend_service.py)

**Endpoint:** `POST /recommend`

**Request:**
```json
{
  "user_id": "uuid | null",
  "current_techs": ["React", "TypeScript"],
  "limit": 10
}
```

**Response:**
```json
{
  "recommendations": [
    {
      "tech_name": "Next.js",
      "reason": "Kết hợp tự nhiên với React ecosystem, tăng trưởng +24% MoM",
      "ring": "Adopt",
      "growth_rate": 24.1,
      "co_occurrence": 87,
      "confidence": 0.823
    }
  ],
  "based_on": ["React", "TypeScript"]
}
```

**Thuật toán:**

1. **Lấy tech user đang dùng** — từ `user_profile.preferences_json.interested_techs` nếu có `user_id`, hoặc từ `current_techs` input
2. **Neo4j graph traversal:**
   ```cypher
   MATCH (t:Technology)-[:RELATED_TO]-(t2:Technology)
   WHERE toLower(t.name) IN $names
     AND NOT toLower(t2.name) IN $names
   OPTIONAL MATCH (t2)-[:IN_RING]->(r)
   WITH t2.name AS related_tech, r.name AS ring,
        count(*) AS co_occurrence
   RETURN related_tech, ring, co_occurrence
   ORDER BY co_occurrence DESC LIMIT 30
   ```
3. **SQL analytics** — lấy `growth_rate`, `mom_growth` tháng gần nhất cho các tech tìm được
4. **Weighted score:**
   ```
   score = 0.6 × (co_occurrence / max_co) + 0.4 × (mom_growth / max_growth)
   ```
5. **LLM explain** — sinh 1 câu lý giải ngắn (tiếng Việt) cho từng recommendation

---

### 2.6 Forecast Service

File: [app/services/forecast_service.py](../services/ai-rag-core/app/services/forecast_service.py)

**Endpoint:** `POST /forecast`

**Request:**
```json
{
  "technology": "React",
  "horizon_months": 6
}
```

**Response:**
```json
{
  "technology": "React",
  "current_status": {
    "job_count": 1240,
    "article_count": 87,
    "growth_rate": 42.1,
    "mom_growth": 18.2,
    "month": "2024-12"
  },
  "predicted_direction": "growing",
  "confidence": 0.82,
  "reasoning": "React duy trì momentum tăng trưởng mạnh với slope dương...",
  "signals": [
    {"signal": "Xu hướng tăng trưởng tuyến tính (linear slope)", "value": 2.3, "weight": 0.35},
    {"signal": "Momentum MoM trung bình (3 tháng gần nhất)", "value": 15.4, "weight": 0.30},
    {"signal": "Số bài viết gần đây (3 tháng): 234, sentiment: 0.72", "value": 0.72, "weight": 0.20},
    {"signal": "Thay đổi số việc làm trong kỳ", "value": 340, "weight": 0.25}
  ],
  "trend_data": [
    {"month": "2024-07", "job_count": 900, "growth_rate": 28.5, "mom_growth": 5.2},
    ...
  ]
}
```

**Statistical signals (numpy):**

| Signal | Công thức | Trọng số |
|---|---|---|
| Linear slope | `numpy.polyfit(x, growth_rates, 1)[0]` | 0.35 |
| Momentum MoM | `mean(mom_values[-3:])` — 3 tháng gần nhất | 0.30 |
| Sentiment (Neo4j) | Avg `a.sentiment_score` trong 3 tháng gần nhất | 0.20 |
| Job demand change | `job_counts[-1] - job_counts[0]` | 0.25 |
| Volatility | `stdev(growth_rates)` | 0.10 |

LLM synthesis trả về JSON:
```json
{"direction": "growing|stable|declining", "confidence": 0.82, "reasoning": "..."}
```

---

### 2.7 Career Assistant

File: [app/services/career_service.py](../services/ai-rag-core/app/services/career_service.py)

**Endpoint:** `POST /career`

**Request:**
```json
{
  "user_id": "uuid | null",
  "target_role": "Senior Backend Developer",
  "current_skills": ["Python", "FastAPI"]
}
```

**Response:**
```json
{
  "target_role": "Senior Backend Developer",
  "current_skills": ["Python", "FastAPI"],
  "skill_gap": [
    {"skill": "Docker", "priority": 1, "reason": "Được yêu cầu nhiều trong tuyển dụng", "job_demand": 840},
    {"skill": "Kubernetes", "priority": 2, "reason": "...", "job_demand": 420}
  ],
  "roadmap": "## Lộ trình 6 tháng\n\n### Tháng 1-2: Docker...",
  "estimated_months": 12
}
```

**Logic:**
1. Lấy current skills từ `user_profile.preferences_json` hoặc từ request
2. **Neo4j** — tìm skills yêu cầu cho `target_role`:
   ```cypher
   MATCH (j:Job)-[:REQUIRES]->(t)
   WHERE toLower(j.title) CONTAINS toLower($role)
     AND (t:Technology OR t:Skill)
   RETURN t.name AS skill, count(*) AS demand
   ORDER BY demand DESC LIMIT 15
   ```
3. Tính skill gap: `required_skills - current_skills`
4. **SQL analytics** — lấy `job_count` từ `tech_analytics` cho các skill thiếu
5. **LLM** — sinh roadmap markdown từ template `career_template.txt`

---

### 2.8 Summarization Service

File: [app/services/summarize_service.py](../services/ai-rag-core/app/services/summarize_service.py)

**Endpoint:** `POST /summarize`

**Request:**
```json
{
  "tech_name": "Kubernetes",
  "period": "2024-Q4",
  "format": "bullet"
}
```

Các giá trị `period` hợp lệ:
- `"2024-Q4"` → 2024-10-01 đến 2024-12-31
- `"2024-12"` → 2024-12-01 đến 2024-12-31
- `"2024"` → cả năm 2024
- `null` → 3 tháng gần nhất

**Response:**
```json
{
  "tech_name": "Kubernetes",
  "period": "2024-Q4",
  "summary": "Kubernetes tiếp tục dẫn đầu trong container orchestration...",
  "key_points": [
    "Nhu cầu tuyển dụng tăng 15% so với quý trước",
    "Nhiều doanh nghiệp Việt Nam chuyển sang managed K8s trên cloud"
  ],
  "sources_used": 12
}
```

**Neo4j query:**
```cypher
MATCH (a:Article)-[:MENTIONS]->(t:Technology)
WHERE toLower(t.name) = toLower($name)
  AND a.published_date >= date($start)
  AND a.published_date <= date($end)
RETURN a.title, a.content, a.published_date, a.sentiment_score
ORDER BY a.published_date DESC LIMIT 20
```

**MapReduce để tránh token overflow:** mỗi article truncate ở 600 ký tự, tối đa 20 articles. Sau khi sinh summary, gọi LLM lần 2 để trích 3-5 key points.

---

### 2.9 Report Generator

File: [app/services/report_service.py](../services/ai-rag-core/app/services/report_service.py)

**Endpoint:** `POST /report`

**Request:**
```json
{
  "period": "2024-Q4",
  "top_n": 10,
  "format": "markdown"
}
```

**Response:**
```json
{
  "period": "2024-Q4",
  "report": "# Báo cáo xu hướng công nghệ Q4/2024\n\n## Tổng quan...",
  "top_techs": [
    {"name": "React", "growth_rate": 42.1, "job_count": 4960, "source": "analytics"},
    {"name": "Kubernetes", "mention_count": 234, "source": "articles"}
  ],
  "generated_at": "2025-01-15 08:30 UTC"
}
```

**2 nguồn dữ liệu:**
1. **PostgreSQL** — top N tech tăng trưởng nhất theo `AVG(growth_rate)` trong kỳ
2. **Neo4j** — top tech được `MENTIONS` nhiều nhất trong kỳ

Hai nguồn được merge (dedup theo tên) trước khi truyền vào LLM để sinh báo cáo markdown 500-800 từ.

---

### 2.10 AI Agent (LangChain)

Files: [app/agent/executor.py](../services/ai-rag-core/app/agent/executor.py), [app/agent/tools.py](../services/ai-rag-core/app/agent/tools.py)

**Endpoint:** `POST /agent`

**Request:**
```json
{
  "query": "So sánh React và Vue cho startup năm 2025, nên chọn cái nào?",
  "user_id": "uuid | null"
}
```

**Response:**
```json
{
  "answer": "Dựa trên dữ liệu tuyển dụng và xu hướng thị trường Việt Nam...",
  "steps": [
    {"tool": "forecast_technology", "input": "React", "output": "React: GROWING (82%)..."},
    {"tool": "forecast_technology", "input": "Vue", "output": "Vue: STABLE (65%)..."},
    {"tool": "search_knowledge", "input": "React vs Vue startup 2025", "output": "..."}
  ]
}
```

**4 tools có sẵn:**

| Tool | Mục đích | Khi nào dùng |
|---|---|---|
| `search_knowledge` | RAG search (article + job + analytics) | Câu hỏi tổng quan, giải thích, so sánh |
| `recommend_technologies` | Gợi ý tech liên quan | "Tôi nên học gì tiếp?" |
| `forecast_technology` | Dự báo xu hướng | "Tương lai của X?", "Có nên đầu tư vào X?" |
| `summarize_technology` | Tóm tắt tin tức | "Gần đây X có gì mới?" |

**Cấu hình agent:**
- Pattern: `create_tool_calling_agent` (LangChain)
- `max_iterations=5` — tránh vòng lặp vô hạn
- `early_stopping_method="generate"` — khi hết iterations, LLM vẫn tạo output
- `return_intermediate_steps=True` — trả về từng bước đã thực hiện

---

### 2.11 Prometheus Monitoring

File: [app/monitoring/metrics.py](../services/ai-rag-core/app/monitoring/metrics.py)

Endpoint: `GET /metrics` (Prometheus text format)

**4 metrics được instrument:**

| Metric | Type | Labels | Mô tả |
|---|---|---|---|
| `ai_rag_requests_total` | Counter | endpoint, status, llm_provider | Tổng requests |
| `ai_rag_latency_seconds` | Histogram | endpoint, stage | Latency theo từng stage |
| `ai_rag_llm_tokens_total` | Counter | provider, model, token_type | Token consumption |
| `ai_rag_retrieval_results` | Histogram | source | Số kết quả mỗi retrieval source |

**Stages được đo trong pipeline:**

```
stage="retrieval"  → thời gian gather(vector + graph + user_profile)
stage="rerank"     → thời gian BGE reranker
stage="llm"        → thời gian gọi LLM (OpenAI/Gemini)
stage="total"      → tổng end-to-end
```

**Ví dụ Prometheus scrape config:**
```yaml
- job_name: 'ai-rag-core'
  static_configs:
    - targets: ['ai-rag-core:8000']
  metrics_path: /metrics
```

**Retrieval source labels:**
- `source="vector"` — số article từ Neo4j vector search
- `source="graph"` — số jobs + companies từ graph traversal
- `source="sql"` — số rows từ tech_analytics

---

### 2.12 RAGAS Evaluation

File: [app/evaluation/ragas_scorer.py](../services/ai-rag-core/app/evaluation/ragas_scorer.py)

**Mặc định tắt** (`EVAL_ENABLED=false`) để không tăng latency. Bật khi cần đánh giá chất lượng.

**Cơ chế:** Fire-and-forget sau khi LLM trả về response:
```python
if settings.eval_enabled:
    asyncio.create_task(_eval(question, answer, contexts, latency_ms, model))
```

**LLM judge faithfulness** — gọi LLM để đánh giá câu trả lời có trung thực với context không:
```
Câu hỏi: ...
Context: [1] ... [2] ... [3] ...
Câu trả lời: ...
Faithfulness score (0.0-1.0):
```

**MLflow logging** — ghi vào `services/ai-rag-core/mlflow.db`:
```python
mlflow.set_tracking_uri("sqlite:///mlflow.db")
with mlflow.start_run(experiment_id="1", run_name="rag_eval"):
    mlflow.log_metric("faithfulness", score)
    mlflow.log_metric("latency_ms", latency)
    mlflow.log_param("model", model_name)
```

**Xem kết quả:**
```bash
cd services/ai-rag-core
mlflow ui   # http://localhost:5000
```

---

### 2.13 Cấu hình môi trường

File: [app/config.py](../services/ai-rag-core/app/config.py) — đọc từ file `.env` ở project root.

| Biến | Bắt buộc | Default | Mô tả |
|---|---|---|---|
| `NEO4J_URI` | ✅ | — | URI AuraDB, dạng `neo4j+s://...` |
| `NEO4J_PASSWORD` | ✅ | — | Mật khẩu AuraDB |
| `OPENAI_API_KEY` | ✅ (nếu OpenAI) | — | API key OpenAI |
| `GEMINI_API_KEY` | ✅ (nếu Gemini) | — | API key Gemini |
| `LLM_PROVIDER` | ❌ | `openai` | `"openai"` hoặc `"gemini"` |
| `LLM_MODEL` | ❌ | `gpt-4o-mini` | Model LLM (VD: `gpt-4o`, `gemini-1.5-flash`) |
| `POSTGRES_HOST` | ❌ | `localhost` | PostgreSQL host |
| `POSTGRES_PORT` | ❌ | `5432` | PostgreSQL port |
| `POSTGRES_DB` | ❌ | `techpulse` | Tên database |
| `POSTGRES_USER` | ❌ | `postgres` | |
| `POSTGRES_PASSWORD` | ❌ | `postgres` | |
| `USE_LOCAL_NEO4J` | ❌ | `false` | `true` để dùng Neo4j local |
| `NEO4J_LOCAL_URI` | ❌ | `bolt://localhost:7687` | URI Neo4j local |
| `REDIS_URL` | ❌ | `redis://localhost:6379` | Redis URL |
| `INTERNAL_API_TOKEN` | ❌ | `""` | Token kiểm tra từ Spring. Để trống = bỏ qua kiểm tra |
| `EMBED_SECRET` | ❌ | `changeme` | Secret cho `/embed/trigger` (từ crawler) |
| `CORS_ORIGINS` | ❌ | `*` | Origins CORS, phân cách bằng dấu phẩy |
| `MODEL_WARMUP` | ❌ | `blocking` | `none` / `background` / `blocking` |
| `WARMUP_NER_MODEL` | ❌ | `true` | Load NER model khi warmup |
| `EVAL_ENABLED` | ❌ | `false` | Bật RAGAS evaluation (tăng ~2-5s latency) |
| `SQL_ANALYTICS_MONTHS` | ❌ | `6` | Khoảng thời gian đọc tech_analytics |
| `RECOMMEND_TOP_K` | ❌ | `10` | Số recommendation tối đa |
| `FORECAST_MIN_DATA_POINTS` | ❌ | `3` | Số data points tối thiểu để forecast |

---

### 2.14 Cấu trúc thư mục

```
services/ai-rag-core/
├── app/
│   ├── main.py                      # FastAPI app, lifespan, /metrics endpoint
│   ├── config.py                    # Settings (pydantic-settings, .env)
│   ├── observability.py             # RequestContextMiddleware, trace-id logging
│   │
│   ├── api/
│   │   ├── schemas.py               # Tất cả Pydantic request/response models
│   │   ├── security.py              # require_internal_auth dependency
│   │   ├── routes_chat.py           # POST /chat, /chat/stream, GET /chat/session/{id}
│   │   ├── routes_embed.py          # POST /embed/trigger, GET /embed/status
│   │   ├── routes_health.py         # GET /health
│   │   ├── routes_internal.py       # POST /internal/ai/llm-summary
│   │   ├── routes_recommend.py      # POST /recommend
│   │   ├── routes_forecast.py       # POST /forecast
│   │   ├── routes_career.py         # POST /career
│   │   ├── routes_summarize.py      # POST /summarize
│   │   ├── routes_report.py         # POST /report
│   │   └── routes_agent.py          # POST /agent
│   │
│   ├── core/                        # RAG pipeline components
│   │   ├── pipeline.py              # Orchestrator answer() — 4-source parallel
│   │   ├── pipeline_stream.py       # Streaming version answer_stream()
│   │   ├── embedder.py              # multilingual-e5-base singleton
│   │   ├── retriever.py             # Neo4j vector search
│   │   ├── retriever_graph.py       # NER + Cypher graph traversal
│   │   ├── retriever_sql.py         # PostgreSQL tech_analytics (3 functions)
│   │   ├── retriever_user.py        # user_profile từ PostgreSQL
│   │   ├── entity_extractor.py      # Dictionary + regex + NlpHust NER
│   │   ├── reranker.py              # BGE reranker ONNX singleton
│   │   ├── prompt_builder.py        # Build messages cho LLM (4 sources + history)
│   │   └── generator.py             # LLM factory (OpenAI/Gemini) + generate()
│   │
│   ├── services/                    # Business logic
│   │   ├── chat_service.py          # handle_chat(), handle_chat_stream()
│   │   ├── recommend_service.py     # Recommendation (graph + analytics + LLM)
│   │   ├── forecast_service.py      # Forecast (time-series + numpy + LLM)
│   │   ├── career_service.py        # Career path (skill gap + LLM roadmap)
│   │   ├── summarize_service.py     # Summarization (MapReduce)
│   │   └── report_service.py        # Report (PG top-growing + Neo4j top-mentioned)
│   │
│   ├── agent/
│   │   ├── executor.py              # LangChain AgentExecutor + run_agent()
│   │   └── tools.py                 # 4 tools: search, recommend, forecast, summarize
│   │
│   ├── memory/
│   │   ├── conversation.py          # Sliding window từ chat_message (10 turns)
│   │   └── user_context.py          # preferences_json + increment_tech_interaction()
│   │
│   ├── evaluation/
│   │   └── ragas_scorer.py          # LLM judge faithfulness + MLflow logging
│   │
│   ├── monitoring/
│   │   └── metrics.py               # 4 Prometheus metrics
│   │
│   ├── db/
│   │   ├── neo4j_client.py          # AsyncDriver singleton + run_query()
│   │   ├── postgres_client.py       # AsyncEngine + session factory
│   │   └── graph_queries.py         # Cypher query constants
│   │
│   ├── models/
│   │   ├── chat.py                  # ChatSession, ChatMessage (SQLAlchemy ORM)
│   │   └── user.py                  # User, UserProfile (read-only mirror)
│   │
│   └── prompts/
│       ├── system_prompt.txt        # System prompt chung cho RAG
│       ├── rag_template.txt         # Template prompt với 4 context blocks
│       ├── recommend_template.txt   # Prompt cho LLM explain recommendation
│       ├── forecast_template.txt    # Prompt cho LLM synthesis forecast
│       ├── career_template.txt      # Prompt cho LLM sinh roadmap
│       └── summarize_template.txt   # Prompt cho LLM summarization
│
├── scripts/
│   ├── embed_articles.py            # Embed toàn bộ Article lên Neo4j (one-shot)
│   ├── create_vector_index.py       # Tạo Neo4j vector index (one-shot)
│   ├── evaluate_rag.py              # Chạy RAGAS evaluation thủ công
│   ├── test_pipeline.py             # Test end-to-end pipeline
│   └── inspect_schema.py            # Kiểm tra schema Neo4j + PostgreSQL
│
├── tests/
│   └── test_routes_internal.py
│
├── mlflow.db                        # SQLite — kết quả evaluation runs
├── mlruns/                          # MLflow artifacts
├── requirements.txt
├── requirements-deploy.txt          # Subset nhỏ hơn cho production
└── Dockerfile
```

---

### 2.15 Chạy service

**Local (development):**

```bash
cd services/ai-rag-core

# Tạo virtual environment
python -m venv .venv
source .venv/bin/activate

# Cài dependencies
pip install -r requirements.txt

# .env đã có ở project root, không cần copy

# Model warmup chạy ngầm (không chặn server start)
MODEL_WARMUP=background uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

**Docker:**
```bash
# Từ project root
docker compose up ai-rag-core
```

**Kiểm tra:**
```bash
curl http://localhost:8000/health
# {"status":"ok","neo4j":true,"version":"1.0.0"}

curl http://localhost:8000/metrics
# Prometheus text format

# Test chat (cần INTERNAL_API_TOKEN để khớp)
curl -X POST http://localhost:8000/chat \
  -H "X-Internal-Auth: techradar-internal-secret" \
  -H "Content-Type: application/json" \
  -d '{"query": "React có bao nhiêu việc làm tháng này?"}'
```

**Swagger UI:** [http://localhost:8000/docs](http://localhost:8000/docs)

**Yêu cầu RAM:** tối thiểu 4GB (embedder ~500MB + reranker ~1GB + app ~500MB).

**Lần khởi động đầu:** download model từ HuggingFace (~2-3 phút). Dockerfile đã pre-download để image build sẵn model.

---

## 3. services/ml-clustering

### 3.1 Pipeline 5 stages

Pipeline HDBSCAN chạy tuần tự qua 5 stages, được định nghĩa trong `dvc.yaml`:

```
Neo4j AuraDB
     │
     ▼
Stage 1 — EXTRACT   (pipelines/stage_01_extract.py)
  Tải dữ liệu từ Neo4j xuống Parquet:
    data/raw/<tag>/
      ├── technologies.parquet   (name, elementId, job_count, article_count)
      ├── jobs.parquet           (id, title, company, tech_requirements)
      ├── articles.parquet       (id, title, content, published_date)
      ├── companies.parquet
      └── edges_*.parquet        (RELATED_TO, MENTIONS, REQUIRES edges)
     │
     ▼
Stage 2 — FEATURES  (pipelines/stage_02_features.py)
  Xây dựng feature matrix:
    1. Alias normalization: k8s → Kubernetes, reactjs → React, ...
    2. Noise filter: loại tech có < min_job_count=3 việc, blocklist (HTML, CSS cơ bản, ...)
    3. Name embedding: intfloat/multilingual-e5-base → 768d → PCA 64d
    4. Graph features: degree centrality, job_count, article_count, company_count
    5. Job TF-IDF: 500 features từ job descriptions
    6. Concatenate → StandardScaler → UMAP 32d
  Output: data/features/<tag>/X.npy, tech_ids.parquet, feature_meta.json
     │
     ▼
Stage 3 — TRAIN     (pipelines/stage_03_train.py)
  Grid search 18 tổ hợp HDBSCAN hyperparameters:
    min_cluster_size: [10, 15, 20]
    min_samples:      [3, 5, 8]
    cluster_selection_epsilon: [0.0, 0.3]
  Constraints: 12-28 clusters, noise ratio ≤ 60%
  Chọn best theo Silhouette Score
  MLflow log toàn bộ trials + register best model
  Output: data/models/<tag>/best_model.pkl, best_labels.parquet
     │
     ▼
Stage 4 — LABEL     (pipelines/stage_04_label.py)
  GPT-4o-mini đọc danh sách tech members của mỗi cluster → sinh:
    - label (tiếng Việt): "Frontend Frameworks"
    - label_en (tiếng Anh): "Frontend Frameworks"
    - domain: "Frontend" / "Backend" / "DevOps" / "Data" / "Mobile" / "Other"
    - description: mô tả 1-2 câu
    - confidence: 0.0-1.0
    - is_coherent: true/false (cluster có gắn kết không)
    - coherence_reason: lý do nếu không coherent
  Output: data/labels/<tag>/cluster_labels.json
     │
     ▼
Stage 5 — WRITEBACK (pipelines/stage_05_writeback.py)
  Ghi kết quả cluster_id + label về Neo4j cho từng Technology node
  (stage này hiện không sử dụng trong production)
```

---

### 3.2 Các endpoint serving

**GET /health**
```json
{
  "status": "ok",
  "snapshot_tag": "2024-12-15",
  "artifact_source": "local",
  "n_techs_total": 487,
  "n_clustered": 412,
  "n_noise": 75,
  "n_clusters": 18
}
```

**GET /clusters** — Danh sách tất cả cluster (bỏ noise cluster -1)
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

**GET /clusters/{cluster_id}** — Chi tiết 1 cluster + danh sách members
```json
{
  "cluster_id": 0,
  "label": "Frontend Frameworks",
  "description": "Các framework và thư viện xây dựng giao diện web",
  "members": ["React", "Vue", "Angular", "Svelte", "Next.js", ...],
  "n_members": 24,
  "outliers": ["Alpine.js"]
}
```

**GET /tech/{tech_name}/cluster** — Tra cứu cluster của 1 tech
```json
{
  "tech_name": "React",
  "tech_id": "4:abc123",
  "cluster_id": 0,
  "label": "Frontend Frameworks",
  "label_en": "Frontend Frameworks",
  "domain": "Frontend",
  "found": true
}
```

**POST /predict/batch** — Batch lookup
```json
// Request
{"tech_names": ["React", "Kubernetes", "UnknownTech"]}

// Response
{
  "results": [
    {"tech_name": "React", "cluster_id": 0, "label": "Frontend Frameworks", "found": true},
    {"tech_name": "Kubernetes", "cluster_id": 5, "label": "Container & Orchestration", "found": true},
    {"tech_name": "UnknownTech", "cluster_id": null, "label": null, "found": false}
  ],
  "n_found": 2,
  "n_not_found": 1,
  "snapshot_tag": "2024-12-15"
}
```

---

### 3.3 Pipeline Trigger (retrain tự động)

File: [app/routes_pipeline.py](../services/ml-clustering/app/routes_pipeline.py)

**POST /pipeline/trigger** — Khởi động pipeline retrain trong background thread

```bash
curl -X POST http://localhost:8001/pipeline/trigger \
  -H "X-Internal-Auth: techradar-internal-secret"
# {"status": "started", "message": "Pipeline retraining started in background"}
```

Trả về **ngay lập tức** — pipeline chạy ngầm trong daemon thread.

Nếu pipeline đang chạy: trả về `409 Conflict`.

**GET /pipeline/status** — Theo dõi tiến độ

```json
{
  "status": "running",
  "started_at": "2025-01-15T06:00:00+00:00",
  "finished_at": null,
  "duration_s": null,
  "current_stage": "pipelines.stage_03_train",
  "error": null
}
```

Sau khi hoàn thành:
```json
{
  "status": "success",
  "started_at": "2025-01-15T06:00:00+00:00",
  "finished_at": "2025-01-15T06:47:23+00:00",
  "duration_s": 2843,
  "current_stage": null,
  "error": null
}
```

Nếu thất bại:
```json
{
  "status": "failed",
  "error": "Stage pipelines.stage_03_train failed (exit 1): ...",
  "duration_s": 1205
}
```

**Cơ chế:**
- Mỗi stage chạy qua `subprocess.run([sys.executable, "-m", stage, "--params", params.yaml])`
- Sau khi hoàn thành: `get_store.cache_clear()` + `get_store()` để API serving tải ngay artifacts mới
- Thread daemon — tự kết thúc khi process chính dừng

**Lịch tự động (APScheduler trong data-platform):**
```
Chủ nhật 06:00 Asia/Ho_Chi_Minh — job_retrain_clustering()
  → POST http://ml-clustering:8001/pipeline/trigger
  → X-Internal-Auth: <INTERNAL_API_TOKEN>
  → Logs 409 gracefully nếu đang chạy
```

---

### 3.4 Cấu trúc thư mục

```
services/ml-clustering/
├── params.yaml                       # Toàn bộ hyperparameters (DVC tracked)
├── dvc.yaml                          # 5 stage definitions
├── dvc.lock                          # Lockfile (DVC)
│
├── app/                              # FastAPI serving
│   ├── main.py                       # App + tất cả routes
│   ├── store.py                      # Load + cache artifacts (lru_cache)
│   ├── schemas.py                    # Pydantic models
│   ├── routes_pipeline.py            # POST /pipeline/trigger, GET /pipeline/status
│   └── observability.py              # Trace-id middleware + JSON logging
│
├── conf/
│   └── config.py                     # Settings từ .env + params.yaml
│
├── pipelines/
│   ├── stage_01_extract.py
│   ├── stage_02_features.py
│   ├── stage_03_train.py
│   ├── stage_04_label.py
│   └── stage_05_writeback.py
│
├── src/
│   ├── data/
│   │   ├── neo4j_loader.py           # Tải dữ liệu từ Neo4j
│   │   └── snapshot.py               # Quản lý snapshot tags
│   ├── features/
│   │   ├── feature_pipeline.py       # Orchestrator feature extraction
│   │   ├── content_features.py       # Name embedding + TF-IDF
│   │   ├── graph_features.py         # Graph statistics
│   │   ├── noise_filter.py           # Loại tech ít nhu cầu / không liên quan
│   │   ├── tech_aliases.py           # Alias normalization map
│   │   └── acronym_map.py            # Viết tắt → tên đầy đủ
│   ├── clustering/
│   │   ├── trainer.py                # HDBSCAN fit + predict
│   │   ├── tuner.py                  # Grid search hyperparameters
│   │   └── evaluator.py              # Silhouette + noise ratio metrics
│   ├── labeling/
│   │   ├── llm_labeler.py            # GPT-4o-mini cluster naming
│   │   └── prompts/cluster_label.txt # Prompt template
│   └── tracking/
│       └── mlflow_logger.py          # MLflow experiment tracking
│
├── scripts/
│   └── publish_s3_artifacts.sh       # Đẩy artifacts lên S3
│
├── data/                             # gitignored, DVC-tracked
│   ├── raw/<tag>/                    # Stage 1 output
│   ├── features/<tag>/               # Stage 2 output
│   ├── models/<tag>/                 # Stage 3 output
│   └── labels/<tag>/                 # Stage 4 output
│
├── mlruns.db                         # SQLite MLflow backend
├── requirements.txt                  # Pipeline dependencies (full)
├── requirements-api.txt              # Serving-only dependencies (nhỏ hơn)
└── Dockerfile
```

---

### 3.5 Cấu hình và chạy

**Biến môi trường:**

| Biến | Bắt buộc | Default | Mô tả |
|---|---|---|---|
| `NEO4J_URI` | ✅ | — | URI AuraDB |
| `NEO4J_PASSWORD` | ✅ | — | Mật khẩu Neo4j |
| `OPENAI_API_KEY` | ✅ (stage 4) | — | API key GPT-4o-mini |
| `INTERNAL_API_TOKEN` | ❌ | `""` | Token kiểm tra `/pipeline/trigger` |
| `MLCLUSTER_S3_BUCKET` | ❌ | `""` | S3 bucket artifacts (bỏ trống = local) |
| `MLCLUSTER_SNAPSHOT_TAG` | ❌ | `latest` | Tag snapshot để load khi serving |

**Hyperparameters chính trong `params.yaml`:**

```yaml
extract:
  snapshot_tag: ""              # Để trống = auto-generate từ timestamp
features:
  min_job_count: 3              # Noise filter threshold
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

**Chạy pipeline thủ công:**
```bash
cd services/ml-clustering
pip install -r requirements.txt

# Toàn bộ pipeline (DVC cache từng stage)
dvc repro

# Trigger qua API (khi service đang chạy)
curl -X POST http://localhost:8001/pipeline/trigger \
  -H "X-Internal-Auth: techradar-internal-secret"

# Theo dõi
watch -n 5 'curl -s http://localhost:8001/pipeline/status | python -m json.tool'
```

**Chạy API serving:**
```bash
# Chỉ cần requirements-api.txt (nhẹ hơn nhiều)
pip install -r requirements-api.txt
uvicorn app.main:app --port 8001 --reload
```

---

## 4. Supporting services

### services/crawler

Crawler chạy 8 nguồn dữ liệu, đẩy event vào Kafka topic `raw.articles` và `raw.jobs`:

| File | Nguồn | Loại dữ liệu |
|---|---|---|
| `DanTri.py` | dantri.com.vn | Bài viết công nghệ |
| `GenK.py` | genk.vn | Bài viết công nghệ |
| `VNExpress.py` | vnexpress.net | Bài viết công nghệ |
| `ICTNews.py` | ictnews.vn | Bài viết ICT |
| `Viblo.py` | viblo.asia | Bài viết kỹ thuật |
| `GitHub.py` | api.github.com | Repository trending |
| `ITviec.py` | itviec.com | Tin tuyển dụng IT |
| `TopCV.py` | topcv.vn | Tin tuyển dụng |

**Chạy theo lịch:** `run_all.py` chạy tuần tự tất cả crawlers, lặp mỗi `CRAWL_INTERVAL_HOURS` (default: 6 giờ).

**Docker (opt-in):**
```bash
docker compose --profile crawl up crawler
```

### services/embedding-service

Kafka consumer (`raw.articles` topic) → sinh embedding bằng `multilingual-e5-base` → ghi vector vào Neo4j node `Article.embedding`.

### services/qdrant-writer

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

### 5.2 Mapping endpoint

| Spring Boot (apps/backend) | Python (ai-rag-core) |
|---|---|
| POST `/api/v1/chat` | → POST `/chat` |
| POST `/api/v1/chat/stream` | → POST `/chat/stream` |
| POST `/api/v1/recommend` | → POST `/recommend` |
| POST `/api/v1/forecast` | → POST `/forecast` |
| POST `/api/v1/career` | → POST `/career` |
| POST `/api/v1/chat/summarize` | → POST `/summarize` |
| GET `/api/v1/report` | → POST `/report` |
| POST `/api/v1/agent` | → POST `/agent` |
| GET `/api/v1/clustering/clusters` | → GET `/clusters` (ml-clustering) |

### 5.3 Security

- Spring Boot bảo vệ endpoint bằng JWT
- Python chỉ nhận request từ Spring (internal network) — kiểm tra `X-Internal-Auth`
- Public paths trong Spring (`/forecast`, `/report`, `/chat/summarize`) vẫn cần X-Internal-Auth khi gọi Python — Spring tự thêm header

### 5.4 Timeout

| Module | Timeout Spring WebClient |
|---|---|
| Chat | 120 giây |
| Agent | 120 giây |
| Recommend | 60 giây |
| Forecast | 60 giây |
| Career | 60 giây |
| Summarize | 60 giây |
| Report | 60 giây |

---

## 6. Luồng dữ liệu tổng thể

```
Internet
  │
  ├── [crawler] ───────────────────────────────────────────────────────────────┐
  │     DanTri, GenK, VNExpress, Viblo, GitHub, ITviec, TopCV                 │
  │     → Kafka topic: raw.articles, raw.jobs                                  │
  │                                                                             │
  │     [embedding-service] ← raw.articles ───────────────────────────────────┐│
  │     multilingual-e5-base → Neo4j Article.embedding                         ││
  │                                                                             ││
  │     [qdrant-writer] ← raw.articles ─────────────────────────────────────┐  ││
  │     → Qdrant collection (optional)                                       │  ││
  │                                                                          │  ││
  └─────────────────────────────────────────────────────────────────────────────┘│
                                                                                  │
                                           Neo4j (Article, Job, Tech, Company) ◄─┘
                                           + PostgreSQL tech_analytics (Gold ETL daily)
                                                    │
                        ┌───────────────────────────┘
                        │
              [ai-rag-core]
                        │
     ┌──────────────────┼──────────────────────────────────────┐
     │                  │                                       │
   /chat          /recommend                              /forecast
   /agent         /career                                /summarize
                  /report
                        │
              [Spring Boot gateway]
                        │
              [React Web / Mobile App]
                        │
                      User
```

---

## 7. Yêu cầu hệ thống & deploy

### Yêu cầu RAM

| Service | RAM tối thiểu | Ghi chú |
|---|---|---|
| ai-rag-core | 4 GB | embedder ~500MB + reranker ~1GB + overhead |
| ml-clustering (serving) | 512 MB | Chỉ load artifacts JSON/parquet |
| ml-clustering (pipeline) | 8 GB | SentenceTransformers + UMAP + HDBSCAN |

### Docker Compose commands

```bash
# Core stack (web + spring + ai-rag-core + ml-clustering + datastores)
docker compose up --build

# Thêm crawler (crawl dữ liệu mới)
docker compose --profile crawl up crawler

# Thêm Qdrant pipeline (vector store thay thế)
docker compose --profile vector up qdrant qdrant-writer

# Thêm observability (Grafana + Loki + Promtail)
docker compose --profile observability up loki promtail grafana

# Tất cả profiles
docker compose --profile crawl --profile vector --profile observability up
```

### Lần đầu chạy

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

# 4. Chạy embedding bài báo (lần đầu, sau khi có dữ liệu trong Neo4j)
docker compose exec ai-rag-core python -m scripts.embed_articles

# 5. Chạy ML clustering pipeline (lần đầu)
curl -X POST http://localhost:8001/pipeline/trigger \
  -H "X-Internal-Auth: $INTERNAL_API_TOKEN"

# Theo dõi pipeline
curl http://localhost:8001/pipeline/status
```

### URLs

| Service | URL |
|---|---|
| Web UI | http://localhost:5173 |
| Spring Boot API | http://localhost:8080 |
| ai-rag-core Swagger | http://localhost:8000/docs |
| ai-rag-core Metrics | http://localhost:8000/metrics |
| ml-clustering Swagger | http://localhost:8001/docs |
| Neo4j Browser | http://localhost:7474 |
| Grafana | http://localhost:3001 |
| MailHog | http://localhost:8025 |
| MinIO Console | http://localhost:9001 |
| MLflow UI (ai-rag-core) | `cd services/ai-rag-core && mlflow ui` → http://localhost:5000 |
| MLflow UI (ml-clustering) | `cd services/ml-clustering && mlflow ui --backend-store-uri sqlite:///mlruns.db` |
