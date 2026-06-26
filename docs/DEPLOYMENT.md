# Deployment — Docker Compose

Toàn bộ hệ thống TechRadar VN được đóng gói bằng **một** file [`docker-compose.yml`](../docker-compose.yml)
ở thư mục gốc. Một lệnh dựng tất cả: frontend, API gateway, 2 service Python và toàn bộ datastore.

---

## 1. Yêu cầu

- Docker Engine 24+ và Docker Compose v2 (`docker compose`, không phải `docker-compose`).
- ~6 GB RAM trống. Lần build đầu **nặng**: `ai-rag-core` tải sẵn các model HuggingFace
  (e5-base, bge-reranker ONNX, NER tiếng Việt), `ml-clustering` kéo PyTorch.

## 2. Khởi động nhanh

```bash
cp .env.docker.example .env       # khai báo secret/toggle (xem mục 4)
docker compose up --build         # core stack
```

Bật thêm pipeline vector (Kafka → qdrant-writer → Qdrant):

```bash
docker compose --profile vector up --build
```

Dừng và xoá (giữ dữ liệu trong volume):

```bash
docker compose down               # thêm -v để xoá luôn volume (mất dữ liệu)
```

## 3. Cổng & dịch vụ

| Service | Container | Cổng host | Ghi chú |
| --- | --- | --- | --- |
| `web` | techradar-web | 5173 → 80 | SPA + Nginx, proxy `/api` → gateway |
| `spring-api` | techradar-spring-api | 8080 | Gateway `/api/v1`, Swagger `/swagger-ui.html` |
| `ai-rag-core` | techradar-rag | 8000 | Graph RAG chat (FastAPI) |
| `ml-clustering` | techradar-clustering | 8001 | Technology clustering (FastAPI) |
| `postgres` | techradar-postgres | 5432 | DB `techradar`, user/pass `postgres` |
| `neo4j` | techradar-neo4j | 7474 / 7687 | Browser / Bolt, auth `neo4j/password` |
| `redis` | techradar-redis | 6379 | Cache cho ai-rag-core |
| `mailhog` | techradar-mailhog | 1025 / 8025 | SMTP / Web UI (xem mail reset mật khẩu) |
| `qdrant` *(vector)* | techradar-qdrant | 6333 / 6334 | Vector store |
| `kafka` *(vector)* | techradar-kafka | 9092 | Broker KRaft |
| `qdrant-writer` *(vector)* | techradar-qdrant-writer | — | Consumer Kafka → Qdrant |

Tài khoản dev có sẵn khi `APP_ENV=dev` (Flyway seed `V900`): **admin@techradar.vn / Admin@12345**.

## 4. Cấu hình (`.env`)

Compose **hard-code** toàn bộ wiring nội bộ (hostname giữa các container + credential của datastore
bundled), nên một `.env` kiểu cloud không thể làm hỏng kết nối. Chỉ các giá trị sau được đọc từ `.env`
(xem [`.env.docker.example`](../.env.docker.example)):

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `APP_ENV` | `dev` | `dev` = bật seed + log chi tiết; `prod` = không seed |
| `JWT_SECRET` | (đổi khi prod) | Khoá ký JWT (≥ 256-bit cho production) |
| `INTERNAL_API_TOKEN` | `techradar-internal-secret` | Shared secret `X-Internal-Auth` giữa gateway và ai-rag-core — **inject vào cả hai** |
| `LLM_PROVIDER` | `openai` | `openai` \| `gemini` |
| `OPENAI_API_KEY` / `GEMINI_API_KEY` | rỗng | Thiếu thì stack vẫn chạy nhưng chat trả lời lỗi |
| `ALLOWED_ORIGINS` / `CORS_ORIGINS` | `*` | CORS gateway / RAG |
| `WEB_RESET_URL` | `http://localhost:5173/login` | Link trong email reset mật khẩu |
| `MLCLUSTER_S3_*` | rỗng | (Tuỳ chọn) artifact clustering trên S3 |

## 5. Thứ tự khởi động & health

- `postgres` có healthcheck (`pg_isready`); `spring-api` và `ai-rag-core` chờ `service_healthy`
  vì Flyway/ORM cần DB sẵn sàng.
- `neo4j` có healthcheck (`cypher-shell 'RETURN 1'`, `start_period` 30s). Backend phụ thuộc
  `service_started` (driver kết nối lười) để không chờ quá lâu.
- `spring-api` chạy Flyway migration `V1..V5` (+ `V900/V901` ở profile dev) lúc khởi động.

## 6. Luồng request

```
Browser → :5173 (Nginx)
        → proxy location /api → spring-api:8080  (giữ nguyên path /api/v1/*)
        → gateway:
            • Postgres (R2DBC) / Neo4j (Bolt)        — auth, radar, graph, dashboard…
            • ai-rag-core:8000  (header X-Internal-Auth)  — /chat, /compare/llm-summary
            • ml-clustering:8001                          — /clustering/*
```
Nginx tắt buffering cho `location /api` để **SSE** (`/api/v1/chat/session/{id}/messages/stream`)
đẩy token tức thời. Cấu hình: [`apps/web/nginx.conf`](../apps/web/nginx.conf).

## 7. Production lưu ý

- Đặt `APP_ENV=prod`, `JWT_SECRET` ngẫu nhiên mạnh, `INTERNAL_API_TOKEN` riêng, `ALLOWED_ORIGINS`/`CORS_ORIGINS` giới hạn domain thật.
- Đổi mật khẩu Postgres/Neo4j mặc định (đang hard-code cho môi trường dev) và đưa qua secret manager.
- MailHog chỉ dành cho dev — thay bằng SMTP thật qua các biến `MAIL_*` (xem `apps/backend/src/main/resources/application.yml`).
- Cân nhắc đặt sau reverse proxy/TLS; chỉ expose cổng `web` (và `8080` nếu cần gọi API trực tiếp).
