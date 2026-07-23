# Deployment — Docker Compose

Toàn bộ hệ thống TechRadar VN được đóng gói bằng **một** file [`docker-compose.yml`](../docker-compose.yml)
ở thư mục gốc. Một lệnh dựng tất cả: frontend, API gateway, **6 service Python** (`ai-rag-core`,
`ml-clustering`, `data-platform`, `crawler`, `embedding-service`, `qdrant-writer`) và toàn bộ datastore.

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

Bật thêm các profile opt-in (kết hợp nhiều profile bằng `COMPOSE_PROFILES=crawl,vector,observability`
hoặc lặp lại `--profile <name>`):

```bash
docker compose --profile crawl up --build          # crawler lấy dữ liệu thật từ 9 nguồn
docker compose --profile vector up --build         # embedding-service + qdrant + qdrant-writer
docker compose --profile observability up --build  # Grafana + Prometheus + Loki + Promtail
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
| `neo4j` | techradar-neo4j | 7474 / 7687 | Browser / Bolt, auth `neo4j/password`, GDS plugin (graph analytics) |
| `redis` | techradar-redis | 6379 | Cache, token blacklist, rate limiting, SSE fan-out Pub/Sub, cross-service trigger |
| `mailhog` | techradar-mailhog | 1025 / 8025 | SMTP / Web UI (xem mail reset mật khẩu) |
| `minio` | techradar-minio | 9000 / 9001 | S3-compatible object storage — Bronze layer (raw crawl data) + `ml-clustering` artifacts |
| `data-platform` | techradar-data-platform | — | APScheduler: ETL Bronze/Silver/Gold, dedup, sync bù Kafka |
| `kafka` | techradar-kafka | 9092 | Broker KRaft — **service mặc định** (không profile), backbone cho ingestion + notification events; backend vẫn chạy được nếu thiếu nhưng mất các tính năng event-driven |
| `crawler` *(profile `crawl`)* | techradar-crawler | — | Crawl 9 nguồn bài/job VN, publish Kafka |
| `embedding-service` *(profile `vector`)* | techradar-embedding | — | Kafka consumer (`extracted_articles`/`extracted_jobs`) → embed → publish `article_vectors`/`job_vectors` |
| `qdrant` *(profile `vector`)* | techradar-qdrant | 6333 / 6334 | Vector store |
| `qdrant-writer` *(profile `vector`)* | techradar-qdrant-writer | — | Consumer `article_vectors`/`job_vectors` → ghi Qdrant |
| `loki`/`promtail`/`prometheus`/`grafana` *(profile `observability`)* | — | 3100, —, 9090, 3001 | Log tập trung (Loki+Promtail) + metrics (Prometheus+Grafana) |

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
| `LLM_PROVIDER` | `openai` | `openai` \| `gemini` \| `groq` |
| `OPENAI_API_KEY` / `GEMINI_API_KEY` / `GROQ_API_KEY` | rỗng | Thiếu thì stack vẫn chạy nhưng chat trả lời lỗi |
| `CORS_ORIGINS` | `*` | CORS gateway |
| `WEB_RESET_URL` | `http://localhost:5173/login` | Link trong email reset mật khẩu — **prod đổi thành domain frontend thật** |
| `MAIL_HOST` / `MAIL_PORT` | `mailhog` / `1025` | SMTP server. Prod đổi sang SMTP thật (Gmail, SendGrid, SES...) |
| `MAIL_USER` / `MAIL_PASSWORD` | rỗng | Tài khoản/API key đăng nhập SMTP thật (secret — không commit) |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `false` / `false` | Prod với SMTP thật **luôn set `true`** |
| `MAIL_FROM` | `no-reply@techradar.vn` | Địa chỉ "From" — nên dùng domain đã xác thực SPF/DKIM |
| `MLCLUSTER_MINIO_BUCKET` | `ml-clustering` | Bucket MinIO cho artifact clustering |
| `EMBED_SECRET` | `changeme` | Header `X-Embed-Secret` giữa crawler và `ai-rag-core` `/embed/trigger` |
| `CRAWL_INTERVAL_HOURS` | `6` | Chu kỳ crawl khi bật profile `crawl` |
| `RUN_JOBS_ON_START` | `false` | `true` = `data-platform` chạy ngay các job đồng bộ/rebuild thay vì đợi lịch đêm |
| `TECH_DEDUP_LLM_PROVIDER` | `gemini` | `gemini` \| `openai` \| `groq` — provider riêng cho job gộp Technology trùng lặp |
| `GITHUB_TOKEN` | rỗng | Cho GitHub crawler (tuỳ chọn) |
| `GRAFANA_PASSWORD` | (đổi khi prod) | Mật khẩu admin Grafana, profile `observability` |
| `MODEL_WARMUP` | — | Có tải sẵn model HuggingFace lúc container start hay lazy-load |

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
- MailHog chỉ dành cho dev — set các biến `MAIL_*` trong `.env` (xem chú thích chi tiết trong [`.env.docker.example`](../.env.docker.example)) để dùng SMTP thật.
- Cân nhắc đặt sau reverse proxy/TLS; chỉ expose cổng `web` (và `8080` nếu cần gọi API trực tiếp).
