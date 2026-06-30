# ai-rag-core — AI Inference Service

FastAPI service cung cấp toàn bộ khả năng AI cho TechRadar VN: RAG chat, recommendation, forecast, career, summarization, report, và AI agent.

> **Tài liệu đầy đủ:** [docs/AI_PLATFORM.md](../../docs/AI_PLATFORM.md#2-servicesai-rag-core)

---

## Nhanh

```bash
# Local
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

# Docker (từ project root)
docker compose up ai-rag-core
```

Swagger: http://localhost:8000/docs  
Health: `curl http://localhost:8000/health`

---

## Endpoints

| Method | Path | Mô tả |
|---|---|---|
| GET | `/health` | Kết nối Neo4j |
| GET | `/metrics` | Prometheus metrics |
| POST | `/chat` | RAG chat (non-streaming) |
| POST | `/chat/stream` | RAG chat (SSE) |
| GET | `/chat/session/{id}/messages` | Lịch sử hội thoại |
| POST | `/embed/trigger` | Trigger embedding |
| POST | `/recommend` | Gợi ý công nghệ |
| POST | `/forecast` | Dự báo xu hướng |
| POST | `/career` | Tư vấn career path |
| POST | `/summarize` | Tóm tắt theo kỳ |
| POST | `/report` | Báo cáo tổng hợp |
| POST | `/agent` | AI Agent (multi-tool) |

Tất cả endpoint (trừ `/health`, `/metrics`) yêu cầu header `X-Internal-Auth: <INTERNAL_API_TOKEN>`.

---

## RAG Pipeline

4 nguồn dữ liệu song song → rerank → LLM:

```
query
  ├── Neo4j vector search (Article)
  ├── Neo4j graph traversal (Job, Company, Technology)
  ├── PostgreSQL tech_analytics (Gold ETL)
  └── PostgreSQL user_profile (nếu đăng nhập)
        │
  BGE Reranker (ONNX, CPU)
        │
  Conversation history (sliding window 10 turns)
        │
  gpt-4o-mini / gemini-1.5-flash
        │
  RAGAS evaluation (fire-and-forget, tắt mặc định)
```

---

## Biến môi trường bắt buộc

```env
NEO4J_URI=neo4j+s://...
NEO4J_PASSWORD=...
OPENAI_API_KEY=...   # hoặc GEMINI_API_KEY nếu LLM_PROVIDER=gemini
```

Xem đầy đủ tại [docs/AI_PLATFORM.md § 2.13](../../docs/AI_PLATFORM.md#213-cấu-hình-môi-trường).

---

## Yêu cầu RAM

**4 GB tối thiểu** (embedder ~500MB + reranker ~1GB + app overhead).

Lần khởi động đầu: download model từ HuggingFace ~2-3 phút (Dockerfile đã pre-download).
