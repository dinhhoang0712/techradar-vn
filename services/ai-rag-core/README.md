# TechPulse RAG Service — Tài liệu cho Backend

> Microservice Python cung cấp khả năng trả lời câu hỏi về xu hướng công nghệ IT Việt Nam, kết hợp vector search và graph traversal trên Neo4j.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Chạy service](#2-chạy-service)
3. [API Endpoints](#3-api-endpoints)
4. [Luồng xử lý pipeline](#4-luồng-xử-lý-pipeline)
5. [Cấu trúc thư mục](#5-cấu-trúc-thư-mục)
6. [Biến môi trường](#6-biến-môi-trường)
7. [Tích hợp với Backend Go](#7-tích-hợp-với-backend-go)
8. [Đánh giá chất lượng](#8-đánh-giá-chất-lượng)
9. [Giới hạn & lưu ý](#9-giới-hạn--lưu-ý)

---

## 1. Tổng quan

RAG Service là một **microservice độc lập** chạy song song với backend Go. Backend Go **không xử lý AI** mà chỉ proxy request đến service này.

```
User → Backend Go → POST /chat → RAG Service → gpt-4o-mini → trả lời
```

Service trả lời câu hỏi bằng cách kết hợp 3 nguồn dữ liệu:
- **Bài báo** (Article) từ Neo4j — vector search bằng embedding.
- **Tin tuyển dụng / Công ty / Công nghệ** (Job, Company, Technology) từ Neo4j — graph traversal bằng Cypher.
- **Profile người dùng** (User, UserProfile) từ PostgreSQL — cá nhân hóa câu trả lời.

---

## 2. Chạy service

### Yêu cầu
- Python 3.11+
- Neo4j AuraDB (hoặc local Neo4j Desktop)
- PostgreSQL (tùy chọn — nếu không có, chat history không được lưu nhưng RAG vẫn hoạt động)

### Cài đặt & chạy local

```bash
# 1. Tạo virtual environment
python -m venv .venv
source .venv/bin/activate       # macOS/Linux
# .venv\Scripts\activate        # Windows

# 2. Cài dependencies
pip install -r requirements.txt

# 3. Tạo file .env (copy từ .env.example rồi điền giá trị)
cp ../../.env.example ../../.env

# 4. Chạy server (từ thư mục src/ai-rag-core/)
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Chạy bằng Docker

```bash
# Từ thư mục gốc project
docker compose up rag-service
```

### Kiểm tra đã chạy chưa

```bash
curl http://localhost:8000/health
# {"status":"ok","neo4j":true,"version":"1.0.0"}
```

Swagger UI tại: [http://localhost:8000/docs](http://localhost:8000/docs)

---

## 3. API Endpoints

### `GET /health` — Kiểm tra trạng thái service

**Response 200:**
```json
{
  "status": "ok",
  "neo4j": true,
  "version": "1.0.0"
}
```

> `neo4j: false` nghĩa là service chưa kết nối được Neo4j — câu hỏi sẽ không trả lời được. Health check **không** kiểm tra PostgreSQL.

---

### `POST /chat` — Gửi câu hỏi, nhận câu trả lời

**Request body:**
```json
{
  "query": "Python developer ở Việt Nam lương bao nhiêu?",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": "123e4567-e89b-12d3-a456-426614174000"
}
```

| Field | Type | Bắt buộc | Mô tả |
|---|---|---|---|
| `query` | string | ✅ | Câu hỏi của user (1–2000 ký tự) |
| `session_id` | UUID \| null | ❌ | UUID phiên hội thoại. Truyền `null` để tạo phiên mới |
| `user_id` | UUID \| null | ❌ | UUID user đã đăng nhập. Truyền `null` nếu ẩn danh |

**Response 200:**
```json
{
  "answer": "Mức lương Python developer tại Việt Nam dao động từ...",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "sources": [
    {
      "title": "Biết dùng AI - tiêu chí tuyển dụng mới...",
      "published_date": "2026-04-25",
      "source": "VnExpress",
      "rerank_score": 0.85
    }
  ],
  "entities": ["python"],
  "job_titles": ["developer"],
  "query": "Python developer ở Việt Nam lương bao nhiêu?"
}
```

| Field | Mô tả |
|---|---|
| `answer` | Câu trả lời dạng Markdown |
| `session_id` | UUID của phiên (trả về để dùng cho request tiếp theo) |
| `sources` | Danh sách bài báo được trích dẫn (tối đa 5) |
| `entities` | Tên công nghệ trích xuất từ câu hỏi (ví dụ: `["python", "react"]`) |
| `job_titles` | Từ khoá vị trí trích xuất (ví dụ: `["developer", "engineer"]`) |

---

### `POST /chat/stream` — Streaming (SSE)

Cùng request body với `/chat`. Trả về Server-Sent Events với 2 loại event:

| Event | Data |
|---|---|
| `token` | Từng chunk text của câu trả lời |
| `done` | JSON metadata đầy đủ (answer, session_id, sources, entities, job_titles, query) |
| `error` | JSON chứa `detail` khi có lỗi |

---

### `GET /chat/session/{session_id}/messages` — Lịch sử chat

Trả về danh sách message của một session theo thứ tự thời gian.

**Response 200:**
```json
[
  {"id": 1, "role": "user", "content": "Python lương bao nhiêu?"},
  {"id": 2, "role": "assistant", "content": "Mức lương Python..."}
]
```

---

### `POST /embed/trigger` — Kích hoạt embedding bài báo mới

> **Dành cho crawler**, không phải user. Backend Go không cần gọi endpoint này.

**Header bắt buộc:**
```
X-Embed-Secret: <EMBED_SECRET trong .env>
```

**Response 200:**
```json
{
  "status": "started",
  "message": "Embed job đã bắt đầu chạy nền."
}
```

Crawler gọi endpoint này sau mỗi lần crawl xong. Job embedding chạy **ngầm (background)** — response trả về ngay, không chờ embedding xong.

---

### `GET /embed/status` — Kiểm tra embedding có đang chạy không

**Response 200:**
```json
{
  "status": "idle",
  "message": "Không có job nào đang chạy."
}
```

`status` có thể là `"running"` hoặc `"idle"`.

---

## 4. Luồng xử lý pipeline

Mỗi request `POST /chat` đi qua pipeline sau:

```
Câu hỏi user
    │
    ├─── [Song song — asyncio.gather()] ───────────────────────┐
    │                                                          │
    │  Vector Search (Neo4j)          Graph Traversal          │  User Profile
    │  embed query → tìm top-20       Entity extraction →      │  (PostgreSQL)
    │  Article tương đồng             truy vấn Job/Company/    │
    │                                 Technology               │
    │                                                          │
    └─── [Gộp kết quả] ────────────────────────────────────────┘
    │
    ▼
Reranker (woxpas-ai/bge-reranker-v2-m3-onnx)
    │  Chấm điểm từng cặp (câu hỏi, tài liệu) → lọc ngưỡng 0.40
    ▼
Build Prompt
    │  Ghép: Article context + Job/Company data + User profile
    ▼
gpt-4o-mini (OpenAI)
    │  Sinh câu trả lời — retry tối đa 3 lần nếu gặp 503/429
    ▼
Lưu lịch sử (PostgreSQL)
    │  chat_session + chat_message
    ▼
Trả về ChatResponse
```

### Chi tiết các bước

**Bước 1 — Vector Search:**
- Embed câu hỏi bằng `intfloat/multilingual-e5-base` (768 chiều), thêm tiền tố `query:` theo quy ước E5.
- Query Neo4j vector index `article_embedding_index` → top-20 Article gần nhất về ngữ nghĩa.
- Bài viết đã được embed với tiền tố `passage:` khi lưu vào Neo4j.

**Bước 2 — Graph Traversal:**
- Trích xuất thực thể từ câu hỏi bằng kết hợp từ điển, regex và mô hình NER `NlpHust/ner-vietnamese-electra-base`.
- Các thực thể nhận diện được: công nghệ, tên công ty, vị trí công việc, địa điểm.
- Xây dựng truy vấn Cypher để lấy Job, Company, Technology liên quan từ Neo4j.

**Bước 3 — User Profile:**
- Lấy `job_role`, `technologies`, `location`, `bio` của user từ PostgreSQL (nếu có `user_id`).
- Dùng để cá nhân hóa câu trả lời.

**Bước 4 — Rerank:**
- Dùng `woxpas-ai/bge-reranker-v2-m3-onnx` chạy bằng ONNX Runtime trên CPU.
- Chấm điểm từng cặp (câu hỏi, tài liệu); chỉ giữ lại các tài liệu có điểm ≥ 0.40.
- Nếu không có tài liệu nào đạt ngưỡng, áp dụng fallback và giảm mức độ tin cậy ngữ cảnh.

**Bước 5 — Generate:**
- Gọi `gpt-4o-mini` qua LangChain để sinh câu trả lời.
- Prompt gồm: system prompt + Article context + Job/Company data + User profile + câu hỏi.
- Mô hình bị ràng buộc chỉ trả lời dựa trên context được cung cấp.
- Logic retry thủ công: tối đa 3 lần, mỗi lần cách nhau 5 giây, kích hoạt khi gặp lỗi 503 hoặc 429.

---

## 5. Cấu trúc thư mục

```
src/ai-rag-core/
├── app/
│   ├── main.py                  # FastAPI app, lifespan (startup/shutdown)
│   ├── config.py                # Settings từ .env (pydantic-settings)
│   │
│   ├── api/                     # HTTP layer
│   │   ├── routes_chat.py       # POST /chat, POST /chat/stream, GET /chat/session/{id}/messages
│   │   ├── routes_health.py     # GET /health
│   │   ├── routes_embed.py      # POST /embed/trigger, GET /embed/status
│   │   └── schemas.py           # Pydantic request/response models
│   │
│   ├── core/                    # Các mắt xích RAG
│   │   ├── pipeline.py          # ⭐ Orchestrator — hàm answer()
│   │   ├── embedder.py          # multilingual-e5-base (singleton)
│   │   ├── retriever.py         # Vector search trên Neo4j
│   │   ├── retriever_graph.py   # Graph traversal + entity extraction
│   │   ├── retriever_user.py    # Lấy user profile từ Postgres
│   │   ├── entity_extractor.py  # NER (NlpHust) + dictionary + regex
│   │   ├── reranker.py          # woxpas-ai/bge-reranker-v2-m3-onnx (singleton)
│   │   ├── prompt_builder.py    # Ghép context vào prompt template
│   │   └── generator.py         # Gọi gpt-4o-mini, retry logic
│   │
│   ├── db/                      # Connection clients
│   │   ├── neo4j_client.py      # AsyncDriver singleton + helper run_query()
│   │   └── postgres_client.py   # AsyncEngine + AsyncSession factory
│   │
│   ├── models/                  # SQLAlchemy ORM
│   │   ├── chat.py              # ChatSession, ChatMessage
│   │   └── user.py              # User, UserProfile
│   │
│   ├── services/
│   │   └── chat_service.py      # Logic nghiệp vụ cho /chat
│   │
│   └── prompts/                 # Prompt templates (không nhúng vào code)
│       ├── system_prompt.txt
│       └── rag_template.txt
│
├── scripts/                     # One-shot jobs (chạy thủ công)
│   ├── embed_articles.py        # Embed toàn bộ Article lên Neo4j
│   ├── create_vector_index.py   # Tạo vector index Neo4j
│   ├── evaluate_rag.py          # Đánh giá pipeline bằng RAGAS, log vào MLflow
│   └── test_pipeline.py         # Test end-to-end
│
├── mlflow.db                    # SQLite lưu kết quả các lần chạy evaluate_rag.py
├── Dockerfile
└── README.md                    # File này
```

---

## 6. Biến môi trường

Copy từ `.env.example` ở thư mục gốc project:

```bash
cp .env.example .env
```

| Biến | Bắt buộc | Mô tả |
|---|---|---|
| `NEO4J_URI` | ✅ | URI AuraDB, dạng `neo4j+s://...` |
| `NEO4J_PASSWORD` | ✅ | Mật khẩu AuraDB |
| `OPENAI_API_KEY` | ✅ | API key OpenAI (dùng cho gpt-4o-mini và RAGAS evaluation) |
| `LLM_PROVIDER` | ❌ | `"openai"` hoặc `"gemini"` (default: `"openai"`) |
| `POSTGRES_HOST` | ❌ | Host PostgreSQL (default: `localhost`) |
| `POSTGRES_DB` | ❌ | Tên database (default: `techpulse`) |
| `POSTGRES_USER` | ❌ | User Postgres (default: `postgres`) |
| `POSTGRES_PASSWORD` | ❌ | Mật khẩu Postgres (default: `postgres`) |
| `USE_LOCAL_NEO4J` | ❌ | `true` để dùng Neo4j local thay AuraDB |
| `EMBED_SECRET` | ❌ | Secret cho `/embed/trigger` (default: `changeme`) |

> **Lưu ý**: Nếu Postgres không kết nối được, service vẫn khởi động và xử lý câu hỏi bình thường. Chỉ mất tính năng lưu lịch sử chat.

---

## 7. Tích hợp với Backend Go

### Cách gọi `/chat`

Backend Go nhận request từ user, thêm `user_id` và `session_id`, rồi forward sang RAG service:

```
POST http://rag-service:8000/chat
Content-Type: application/json

{
  "query": "<câu hỏi user>",
  "session_id": "<UUID từ session hiện tại hoặc null>",
  "user_id": "<UUID user đang đăng nhập hoặc null>"
}
```

**Quan trọng về `session_id`:**
- Lần đầu (tạo hội thoại mới): truyền `null` → RAG service tự tạo UUID mới và trả về trong response.
- Lần tiếp theo trong cùng hội thoại: lấy `session_id` từ response trước và truyền vào.
- Backend Go cần lưu lại `session_id` từ response để dùng cho request tiếp theo.

**Quan trọng về `user_id`:**
- Truyền UUID của user đang đăng nhập để RAG cá nhân hóa câu trả lời dựa trên profile (job_role, technologies, location).
- Truyền `null` nếu user chưa đăng nhập — câu trả lời vẫn hoạt động, chỉ không cá nhân hóa.

### Lịch sử chat

RAG service **tự lưu** lịch sử vào PostgreSQL (`chat_session`, `chat_message`). Backend Go **không cần** lưu lại nội dung chat. Tuy nhiên, backend Go cần:
- Lưu `session_id` để gửi vào các request tiếp theo của cùng hội thoại.
- Nếu muốn hiển thị lịch sử cho user, gọi `GET /chat/session/{session_id}/messages`.

### Timeout

Pipeline có thể mất **30–60 giây** do:
- Lần chạy đầu: load model embedding (~10s) và reranker (~5s) vào RAM (nếu `MODEL_WARMUP=none`).
- Gọi OpenAI API: 5–30s tùy độ phức tạp. Có retry tối đa 3 lần nếu gặp lỗi rate limit.

Sau lần đầu, model đã load sẵn trong RAM, tốc độ sẽ nhanh hơn (~10–15s/query).

**Khuyến nghị**: đặt HTTP timeout phía backend Go ≥ 120 giây cho endpoint chat.

### Health check

Backend Go nên gọi `GET /health` để kiểm tra RAG service còn sống không trước khi forward request, hoặc dùng làm dependency check khi khởi động.

---

## 8. Đánh giá chất lượng

Chạy script đánh giá pipeline bằng RAGAS (judge model: gpt-4o-mini):

```bash
# Từ thư mục src/ai-rag-core/
python -m scripts.evaluate_rag
```

Script chạy 8 câu hỏi mẫu qua pipeline, tính 2 chỉ số RAGAS và log toàn bộ kết quả vào MLflow:

| Chỉ số | Ý nghĩa |
|---|---|
| `faithfulness` | Câu trả lời bám sát context (1.0 = hoàn toàn) |
| `answer_relevancy` | Câu trả lời đúng trọng tâm câu hỏi (1.0 = hoàn toàn) |
| `answered_rate` | Tỉ lệ câu hỏi có câu trả lời hợp lệ |
| `avg_latency_ms` | Thời gian phản hồi trung bình (ms) |

MLflow lưu lại: embedding model, reranker model, LLM model, judge model, metric tổng hợp, metric từng câu hỏi và git commit hash tại thời điểm thực nghiệm.

```bash
# Xem kết quả các lần chạy
mlflow ui   # mở http://localhost:5000
```

Kết quả lần chạy gần nhất (8 câu hỏi, 7 câu có câu trả lời hợp lệ):

| Chỉ số | Giá trị |
|---|---|
| Faithfulness | 0.9238 |
| Answer Relevancy | 0.7178 |
| Answered Rate | 87.5% |
| Avg Latency | ~13.9s/câu |

---

## 9. Giới hạn & lưu ý

### Giới hạn hiện tại

| Vấn đề | Chi tiết |
|---|---|
| **OpenAI rate limit** | Free tier hoặc tier thấp có giới hạn requests/phút. Nếu nhiều user hỏi đồng thời sẽ gặp lỗi 429 — pipeline tự retry tối đa 3 lần, cách nhau 5 giây. |
| **Dữ liệu lương** | Hầu hết salary_min/salary_max = 0 do lỗi crawler. Câu hỏi về lương cụ thể sẽ không có số chính xác. |
| **Article source** | Property `source` trên Article hầu hết = `"Unknown"` — không dùng làm link trích dẫn được. |

### Lưu ý khi deploy

- Service cần **RAM tối thiểu 4GB** do load 2 model ML vào RAM (embedder ~500MB, reranker ~1GB).
- Mặc định `MODEL_WARMUP=blocking` — service load toàn bộ model trước khi nhận request đầu tiên.
- Lần khởi động đầu tiên mất thêm ~2–3 phút để download model từ HuggingFace (nếu chưa cache).
- Dockerfile đã tích hợp bước pre-download model khi build image — dùng image đã build sẽ start ngay.
- `USE_LOCAL_NEO4J=false` trong `docker-compose.yml` để container luôn dùng AuraDB.
