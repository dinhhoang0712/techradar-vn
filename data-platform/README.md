# Data Platform — TechRadar VN

Pipeline thu thập và xử lý dữ liệu công nghệ Việt Nam theo kiến trúc **Medallion** (Bronze → Silver → Gold).

---

## Mục lục

1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Crawler Layer](#2-crawler-layer)
3. [Bronze Layer](#3-bronze-layer)
4. [Silver Layer](#4-silver-layer)
5. [Gold Layer](#5-gold-layer)
6. [Scheduler](#6-scheduler)
7. [Database Schema](#7-database-schema)
8. [Cấu hình](#8-cấu-hình)
9. [Khởi chạy](#9-khởi-chạy)
10. [Monitoring](#10-monitoring)

---

## 1. Tổng quan kiến trúc

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CRAWLER LAYER                                │
│  VNExpress  GenK  DanTri  ICTNews  TopCV  ITviec  Viblo  GitHub   │
│                      (knowledge-graph/crawl/)                       │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ Kafka Producer
                            ▼
              ┌─────────────────────────────┐
              │           KAFKA             │
              │   raw_articles  raw_jobs    │
              │ extracted_articles          │
              │ extracted_jobs (Spring NLP) │
              └──────┬──────────────────────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
  ┌───────────────┐     ┌─────────────────────┐
  │ BRONZE LAYER  │     │    SILVER LAYER      │
  │ writer.py     │     │    processor.py      │
  │               │     │                      │
  │ MinIO         │     │  - Dedup (URL+hash)  │
  │ techradar-    │     │  - Quality scoring   │
  │ bronze bucket │     │  - PostgreSQL        │
  │ (gzip JSON,   │     │  dp_processed_       │
  │  immutable)   │     │  articles / jobs     │
  └───────────────┘     └──────────────────────┘
                                 │
                          (Gold ETL 3:00 AM)
                                 ▼
                        ┌────────────────┐
                        │  GOLD LAYER    │
                        │  pg_etl.py     │
                        │  neo4j_        │
                        │  enricher.py   │
                        │                │
                        │  Neo4j KG      │
                        │  tech_analytics│
                        │  (PostgreSQL)  │
                        └────────────────┘
```

### Luồng dữ liệu chi tiết

| Bước | Component | Input | Output |
|------|-----------|-------|--------|
| 1 | Crawler | Websites / APIs | Kafka `raw_articles`, `raw_jobs` |
| 2 | Bronze Writer | Kafka raw topics | MinIO gzip JSON + `dp_bronze_catalog` |
| 3 | Silver Processor | Kafka raw/extracted topics | `dp_processed_articles`, `dp_processed_jobs` |
| 4 | Gold PG ETL | Neo4j Knowledge Graph | `tech_analytics` (PostgreSQL) |
| 5 | Neo4j Enricher | Silver + KG | Derived relationships, trend scores |
| 6 | Embed Trigger | `ai-rag-core` HTTP | Vector embeddings (Qdrant) |
| 7 | Clustering Retrain | `ml-clustering` HTTP | Cluster labels (weekly) |

---

## 2. Crawler Layer

**Vị trí:** `knowledge-graph/crawl/`

Mỗi crawler chạy như một subprocess riêng biệt (qua `run_all.py`) để Chrome process không bị leak. Kết quả được gửi lên Kafka **và** lưu vào file JSON local (trong Docker volume `crawler_data`).

### Danh sách Crawlers

| File | Loại | Nguồn | Giới hạn | Công nghệ |
|------|------|--------|----------|-----------|
| `VNExpress.py` | Tin tức | vnexpress.net/khoa-hoc-cong-nghe | 150 bài | Selenium |
| `GenK.py` | Tin tức | genk.vn (AI, Internet, ICT) | 150 bài | Selenium |
| `DanTri.py` | Tin tức | dantri.com.vn/cong-nghe | 150 bài | Selenium |
| `ICTNews.py` | Tin tức | ictnews.vietnamnet.vn | 150 bài | Selenium |
| `TopCV.py` | Việc làm | topcv.vn (IT category) | 150 jobs | Selenium + uc |
| `ITviec.py` | Việc làm | itviec.com | 150 jobs | Selenium + uc |
| `Viblo.py` | Forum/Blog | viblo.asia (REST API) | 150 bài | requests |
| `GitHub.py` | OSS | GitHub API (VN orgs + queries) | 200 repos | requests |

### `kafka_producer.py` — Message Format

**Article message** (topic: `raw_articles`):
```json
{
  "message_type": "article",
  "source_platform": "VNExpress",
  "crawled_at": "2026-06-29T04:16:00Z",
  "data": {
    "title": "Tiêu đề bài viết",
    "content": "Nội dung...",
    "source_url": "https://vnexpress.net/...",
    "publish_date": "2026-06-29"
  }
}
```

**Job message** (topic: `raw_jobs`):
```json
{
  "message_type": "job",
  "source_platform": "TopCV",
  "crawled_at": "2026-06-29T04:36:00Z",
  "data": {
    "job_title": "Senior Backend Engineer",
    "company_name": "VNG Corporation",
    "location": "TP.HCM",
    "salary": "Thỏa thuận",
    "level": "Senior",
    "description": "...",
    "requirement": "...",
    "benefit": "...",
    "skills": ["Python", "Golang", "Kafka"],
    "source_url": "https://topcv.vn/...",
    "posted_date": "2026-06-29"
  }
}
```

### `run_all.py` — Docker entrypoint

```python
CRAWLERS = [
    "VNExpress.py", "GenK.py", "DanTri.py", "ICTNews.py",  # Tin tức
    "TopCV.py", "ITviec.py",                                  # Việc làm
    "Viblo.py", "GitHub.py",                                  # API-based
]
```

- Chạy từng crawler **tuần tự** (tránh conflict Chrome port)
- Mỗi crawler timeout tối đa **60 phút**
- Sau khi hết vòng → sleep `CRAWL_INTERVAL_HOURS` (mặc định 6h) → lặp lại
- Kết quả lưu vào `/data/raw/<platform>/DD_MM_YYYY.json` (Docker volume)

### Chrome Options (Selenium crawlers)

Tất cả Selenium crawlers đều dùng chung bộ options để ổn định trong Docker:

```python
chrome_options.add_argument("--headless=new")
chrome_options.add_argument("--no-sandbox")
chrome_options.add_argument("--disable-dev-shm-usage")
chrome_options.add_argument("--disable-gpu")
chrome_options.add_argument("--disable-background-networking")
chrome_options.add_argument("--disable-sync")
chrome_options.add_argument("--metrics-recording-only")
chrome_options.add_argument("--mute-audio")
chrome_options.page_load_strategy = "eager"   # DOM ready là đủ, không chờ JS/images
driver.set_page_load_timeout(60)
```

> **Lưu ý:** `page_load_strategy = "eager"` là fix quan trọng cho vnexpress.net và ictnews.vietnamnet.vn — các trang này timeout với strategy mặc định `"normal"`.

### Viblo.py — API-based (không cần Selenium)

Dùng Viblo REST API:
```
GET https://viblo.asia/api/tags/{tag}/posts?page=N&limit=20
```
Response: `{"data": [...], "meta": {"total": N}}`

20 tags: `python, golang, java, javascript, typescript, docker, kubernetes, aws, devops, machine-learning, ai, backend, frontend, database, microservices, react, nodejs, laravel, spring-boot, fastapi`

### GitHub.py — API-based (không cần Selenium)

Nguồn dữ liệu:
1. **14 tổ chức VN:** `vngcloud, zalopay, tiki-miniapp, shopee, fpt-corp, vnpay, techvify-software, axon-active, nashtech-global, framgia, ...`
2. **5 search queries:** `"vietnam tech", "made in vietnam", "vietnamese developer", ...`

Hỗ trợ `GITHUB_TOKEN` env var:
- Không có token: 60 requests/giờ
- Có token: 5000 requests/giờ

Repo → Article format:
- `title`: `"{org}/{repo}: {description}"`
- `content`: language, topics, stars, forks, watchers

---

## 3. Bronze Layer

**File:** `bronze/writer.py`  
**Kafka consumer group:** `bronze-writer`  
**Topics:** `raw_articles`, `raw_jobs`

### Chức năng

Ghi **toàn bộ raw Kafka message** vào MinIO dưới dạng gzip JSON (immutable — không bao giờ xóa hay ghi đè). Đây là nguồn sự thật (source of truth) có thể replay lại bất kỳ lúc nào.

### Object path pattern

```
s3://techradar-bronze/raw/{articles|jobs}/{platform}/
  year={YYYY}/month={MM}/day={DD}/
  {md5(source_url)}_{YYYYMMDDTHHMMSSZ}.json.gz
```

Ví dụ:
```
s3://techradar-bronze/raw/articles/vnexpress/
  year=2026/month=06/day=29/
  83fd69b7..._{timestamp}.json.gz
```

### Catalog entry (PostgreSQL)

Mỗi file được đăng ký vào `dp_bronze_catalog`:

| Column | Mô tả |
|--------|-------|
| `id` | MD5(source_url) — idempotent key |
| `source_url` | URL gốc của bài viết/job |
| `minio_path` | `s3://techradar-bronze/...` |
| `file_size_bytes` | Kích thước file nén (bytes) |
| `kafka_topic` | `raw_articles` hoặc `raw_jobs` |
| `kafka_offset` | Offset Kafka để replay |

> `ON CONFLICT (id) DO NOTHING` — đảm bảo idempotency khi Bronze Writer restart.

---

## 4. Silver Layer

**File:** `silver/processor.py`  
**Kafka consumer group:** `silver-processor`  
**Topics:** `raw_articles`, `raw_jobs`, `extracted_articles`, `extracted_jobs`

> **Thiết kế dual-topic:** Silver đọc cả `raw_*` (từ crawlers trực tiếp) **và** `extracted_*` (từ Spring Boot NLP pipeline). Khi Spring Boot chạy, `extracted_*` chứa entity đã được extract; `raw_*` vẫn được xử lý song song để không bỏ sót data khi Spring Boot tắt.

### Xử lý Article (`_process_article`)

1. **Extract fields** — hỗ trợ cả wrapped format `{"data": {...}}` và flat format:
   ```python
   data = msg.get("data", msg)   # handle both formats
   ```
2. **Entity extraction** — hỗ trợ nhiều field name conventions:
   ```python
   techs = data.get("entity_techs") or entities.get("tech") or entities.get("TECH") or []
   orgs  = data.get("entity_orgs")  or entities.get("org")  or entities.get("ORG")  or []
   locs  = data.get("entity_locs")  or entities.get("loc")  or entities.get("LOC")  or []
   ```
3. **Quality scoring** (0.0 – 1.0):
   - `+0.3` nếu title ≥ 10 ký tự
   - `+0.4` nếu content ≥ 200 ký tự
   - `+0.3` nếu content ≥ 800 ký tự
4. **Near-duplicate detection** — MD5(normalize(title + content)) so sánh với DB
5. **Upsert** vào `dp_processed_articles` với `ON CONFLICT (source_url) DO NOTHING`

### Xử lý Job (`_process_job`)

Hỗ trợ cả flat fields (từ `kafka_producer.py`) và nested format:
```python
job = data.get("job", data)
title = job.get("job_title") or job.get("title")           # TopCV dùng job_title
company_name = job.get("company_name") or company_obj.get("name")
company_location = job.get("location") or company_obj.get("location")
```

### Deduplication (`silver/deduplicator.py`)

- **URL dedup (exact):** `ON CONFLICT (source_url) DO NOTHING` — handled by SQL
- **Content dedup (near):** `content_hash = MD5(normalize(title + content))`
  - normalize = lowercase + collapse whitespace
  - Nếu hash đã tồn tại → `is_duplicate=True`, `duplicate_of={id gốc}`

---

## 5. Gold Layer

### 5a. Gold PG ETL (`gold/pg_etl.py`)

**Chạy:** 3:00 AM daily (Asia/Ho_Chi_Minh)

Đọc Neo4j Knowledge Graph → rebuild bảng `tech_analytics` trong PostgreSQL. Logic tương đương `RadarAnalyticsEtlService.java` nhưng chạy độc lập từ Data Platform.

**Cypher queries:**
```cypher
-- Article mentions by tech and month
MATCH (t:Technology)<-[:MENTIONS]-(a:Article)
WHERE a.published_date IS NOT NULL
WITH t.name AS tech, substring(toString(a.published_date), 0, 7) AS ym
RETURN tech, ym, count(*) AS cnt

-- Job requirements by tech and month
MATCH (t:Technology)<-[:REQUIRES]-(j:Job)
WITH t.name AS tech, substring(toString(coalesce(j.posted_date, j.due_date)), 0, 7) AS ym
RETURN tech, ym, count(DISTINCT j) AS cnt
```

Output: Upsert vào `tech_analytics`:

| Column | Mô tả |
|--------|-------|
| `tech_name` | Tên công nghệ |
| `period` | Tháng (YYYY-MM-01) |
| `article_count` | Số bài viết đề cập |
| `job_count` | Số job yêu cầu |
| `growth_rate` | % tăng trưởng so với tháng trước |
| `snapshot_jobs` | Tổng job hiện tại (snapshot) |

### 5b. Neo4j Enricher (`gold/neo4j_enricher.py`)

**Chạy:** 5:00 AM daily

Tạo **derived relationships** và cập nhật statistics trong Knowledge Graph:

| Cypher | Mô tả |
|--------|-------|
| `(Company)-[:USES]->(Technology)` | Suy ra từ Article đề cập cả company lẫn tech |
| `(Technology)-[:RELATED_TO]->(Technology)` | Co-mention trong cùng bài viết |
| `t.mention_count = article_count + job_count` | Cập nhật mention count trên mỗi Technology node |
| `t.trend_score` | `(mention_count * 2 + job_count) / max * 100` |

### 5c. Embed Trigger (`scheduler/jobs.py → job_embed_trigger`)

**Chạy:** 4:00 AM daily

Gọi `POST {RAG_BASE_URL}/embed/trigger` với header `X-Embed-Secret`. `ai-rag-core` sẽ embed các Article mới từ Neo4j vào Qdrant (vector store). Response ngay lập tức, job chạy async trong background.

### 5d. Clustering Retrain (`scheduler/jobs.py → job_retrain_clustering`)

**Chạy:** 6:00 AM, mỗi Chủ nhật

Gọi `POST {ML_CLUSTERING_BASE_URL}/pipeline/trigger`. `ml-clustering` service chạy DVC pipeline (5 stages: snapshot → embed → cluster → evaluate → promote). Job chạy async, status track qua API của service.

---

## 6. Scheduler

**File:** `scheduler/scheduler.py`

Dùng **APScheduler** với `BackgroundScheduler` (chạy trong main thread của `main.py`).

### Lịch chạy mặc định (Asia/Ho_Chi_Minh)

| Job | Cron | Mô tả |
|-----|------|-------|
| `gold_pg_etl` | `0 3 * * *` | Rebuild tech_analytics từ Neo4j |
| `embed_trigger` | `0 4 * * *` | Trigger vector embedding mới |
| `neo4j_enricher` | `0 5 * * *` | Cập nhật derived relationships |
| `retrain_clustering` | `0 6 * * 0` | Retrain ML clustering (Chủ nhật) |

### Dev mode — chạy jobs ngay khi start

```bash
# .env
RUN_JOBS_ON_START=true
```

Khi `run_jobs_on_start=true`, tất cả jobs được trigger ngay lập tức khi container start (dùng để seed data ban đầu).

### Cấu hình cron qua env vars

```bash
GOLD_ETL_HOUR=3        GOLD_ETL_MINUTE=0
EMBED_TRIGGER_HOUR=4   EMBED_TRIGGER_MINUTE=0
NEO4J_ENRICHER_HOUR=5  NEO4J_ENRICHER_MINUTE=0
CLUSTERING_RETRAIN_HOUR=6  CLUSTERING_RETRAIN_MINUTE=0
CLUSTERING_RETRAIN_DAY_OF_WEEK=sun
```

---

## 7. Database Schema

### `dp_bronze_catalog` — Raw file registry

```sql
CREATE TABLE dp_bronze_catalog (
    id              TEXT PRIMARY KEY,      -- MD5(source_url)
    source_url      TEXT NOT NULL UNIQUE,
    source_platform TEXT NOT NULL,         -- VNExpress, GenK, ...
    content_type    TEXT NOT NULL,         -- article | job
    minio_path      TEXT NOT NULL,         -- s3://techradar-bronze/...
    file_size_bytes BIGINT,
    kafka_topic     TEXT,                  -- raw_articles | raw_jobs
    kafka_offset    BIGINT,
    crawled_at      TIMESTAMPTZ DEFAULT now()
);
```

### `dp_processed_articles` — Silver articles

```sql
CREATE TABLE dp_processed_articles (
    id              TEXT PRIMARY KEY,      -- MD5(source_url)
    source_url      TEXT NOT NULL UNIQUE,
    source_platform TEXT NOT NULL,
    title           TEXT,
    content         TEXT,
    published_at    TIMESTAMPTZ,
    content_hash    TEXT,                  -- MD5(normalize(title+content))
    is_duplicate    BOOLEAN DEFAULT FALSE,
    duplicate_of    TEXT,                  -- id của bài gốc nếu duplicate
    entity_techs    TEXT[] DEFAULT '{}',   -- ["Python", "Docker", ...]
    entity_orgs     TEXT[] DEFAULT '{}',   -- ["VNG", "Zalo", ...]
    entity_locs     TEXT[] DEFAULT '{}',   -- ["Hà Nội", "TP.HCM", ...]
    quality_score   FLOAT DEFAULT 0.0,     -- 0.0 – 1.0
    status          TEXT DEFAULT 'processed',
    processed_at    TIMESTAMPTZ DEFAULT now()
);
```

**Quality score formula:**
- `+0.3` — title ≥ 10 chars
- `+0.4` — content ≥ 200 chars
- `+0.3` — content ≥ 800 chars

### `dp_processed_jobs` — Silver jobs

```sql
CREATE TABLE dp_processed_jobs (
    id               TEXT PRIMARY KEY,     -- MD5(source_url)
    source_url       TEXT NOT NULL UNIQUE,
    source_platform  TEXT NOT NULL,        -- TopCV | ITviec
    job_title        TEXT,
    company_name     TEXT,
    company_location TEXT,
    salary           TEXT,
    level            TEXT,
    description      TEXT,
    requirement      TEXT,
    benefit          TEXT,
    skills           TEXT[] DEFAULT '{}',  -- ["React", "TypeScript", ...]
    technologies     TEXT[] DEFAULT '{}',  -- inferred from JD
    content_hash     TEXT,
    is_duplicate     BOOLEAN DEFAULT FALSE,
    quality_score    FLOAT DEFAULT 0.0,
    status           TEXT DEFAULT 'processed',
    processed_at     TIMESTAMPTZ DEFAULT now()
);
```

### `dp_pipeline_runs` — Job execution log

```sql
CREATE TABLE dp_pipeline_runs (
    id          BIGSERIAL PRIMARY KEY,
    job_name    TEXT NOT NULL,   -- gold_pg_etl | neo4j_enricher | embed_trigger
    status      TEXT NOT NULL,   -- running | success | failed
    rows_affected INT,
    error_msg   TEXT,
    started_at  TIMESTAMPTZ DEFAULT now(),
    finished_at TIMESTAMPTZ
);
```

---

## 8. Cấu hình

**File:** `data-platform/config.py` (Pydantic Settings — đọc từ env vars hoặc `.env`)

| Env var | Default | Mô tả |
|---------|---------|-------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `MINIO_ENDPOINT` | `localhost:9000` | MinIO S3 API endpoint |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin123` | MinIO secret key |
| `MINIO_SECURE` | `false` | HTTPS cho MinIO |
| `BRONZE_BUCKET` | `techradar-bronze` | Tên MinIO bucket |
| `POSTGRES_DSN` | `postgresql://postgres:postgres@localhost:5432/techradar` | PostgreSQL DSN |
| `NEO4J_URI` | `bolt://localhost:7687` | Neo4j Bolt URI |
| `NEO4J_USERNAME` | `neo4j` | Neo4j username |
| `NEO4J_PASSWORD` | `password` | Neo4j password |
| `RAG_BASE_URL` | `http://localhost:8000` | ai-rag-core base URL |
| `EMBED_SECRET` | `changeme` | Secret cho `/embed/trigger` |
| `INTERNAL_API_TOKEN` | `techradar-internal-secret` | Token cho internal APIs |
| `ML_CLUSTERING_BASE_URL` | `http://localhost:8001` | ml-clustering base URL |
| `RUN_JOBS_ON_START` | `false` | Trigger tất cả jobs khi start |

**Crawler env vars** (`knowledge-graph/crawl/`):

| Env var | Default | Mô tả |
|---------|---------|-------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` | Kafka broker (trong Docker: `kafka:9092`) |
| `CRAWL_INTERVAL_HOURS` | `6` | Khoảng thời gian giữa các crawl run |
| `GITHUB_TOKEN` | _(empty)_ | GitHub Personal Access Token (public_repo scope) |

---

## 9. Khởi chạy

### Toàn bộ stack (production)

```bash
cp .env.docker.example .env
# Điền GITHUB_TOKEN nếu có
docker compose up --build -d
```

### Bật crawler (opt-in)

```bash
docker compose --profile crawl up -d
docker logs techradar-crawler -f
```

### Theo dõi data platform

```bash
docker logs techradar-data-platform -f
```

### Chạy riêng từng crawler (debug)

```bash
# Chạy Viblo crawler trong Docker network
docker run --rm \
  --network techradar-vn_default \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  techradar/crawler:latest \
  python Viblo.py

# Chạy GitHub crawler với token
docker run --rm \
  --network techradar-vn_default \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e GITHUB_TOKEN=ghp_xxxx \
  techradar/crawler:latest \
  python GitHub.py
```

### Trigger Gold ETL ngay lập tức (dev)

```bash
# Qua env var khi start
RUN_JOBS_ON_START=true docker compose up -d data-platform

# Hoặc chạy trực tiếp trong container
docker exec techradar-data-platform python -c "
from config import get_settings
from gold.pg_etl import run
print(run(get_settings()), 'rows upserted')
"
```

### Kiểm tra data trong PostgreSQL

```bash
# Tổng quan
docker exec techradar-postgres psql -U postgres -d techradar -c "
SELECT
  source_platform,
  COUNT(*) as total,
  COUNT(CASE WHEN quality_score >= 0.7 THEN 1 END) as high_quality,
  COUNT(CASE WHEN is_duplicate THEN 1 END) as duplicates
FROM dp_processed_articles
GROUP BY source_platform ORDER BY total DESC;
"

# Bronze catalog
docker exec techradar-postgres psql -U postgres -d techradar -c "
SELECT content_type, COUNT(*) FROM dp_bronze_catalog GROUP BY content_type;
"

# Pipeline run history
docker exec techradar-postgres psql -U postgres -d techradar -c "
SELECT job_name, status, rows_affected, started_at, finished_at
FROM dp_pipeline_runs ORDER BY started_at DESC LIMIT 10;
"
```

---

## 10. Monitoring

### Logs

```bash
# Bronze + Silver processing
docker logs techradar-data-platform -f | grep -E "Bronze:|Silver:"

# Crawler progress
docker logs techradar-crawler -f | grep -E "\[OK\]|\[WARN\]|Starting|complete"

# Gold ETL
docker logs techradar-data-platform -f | grep -E "gold_pg_etl|neo4j_enricher"
```

### MinIO Console

Truy cập **http://localhost:9001** (admin: `minioadmin` / `minioadmin123`)  
Bucket: `techradar-bronze` → xem raw files theo partition `year/month/day`

### Kafka topics

```bash
# Kiểm tra consumer group lag
docker exec techradar-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --describe --group bronze-writer

docker exec techradar-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --describe --group silver-processor

# Xem messages mới nhất
docker exec techradar-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic raw_articles \
  --from-beginning \
  --max-messages 5
```

### Kiểm tra Silver dedup rate

```bash
docker exec techradar-postgres psql -U postgres -d techradar -c "
SELECT
  source_platform,
  COUNT(*) as total,
  SUM(CASE WHEN is_duplicate THEN 1 ELSE 0 END) as dupes,
  ROUND(AVG(quality_score)::numeric, 2) as avg_quality
FROM dp_processed_articles
GROUP BY source_platform
ORDER BY total DESC;
"
```

---

## Cấu trúc thư mục

```
data-platform/
├── main.py                  # Entry point: Bronze + Silver threads + Scheduler
├── config.py                # Pydantic Settings (env vars)
├── requirements.txt         # Python dependencies
├── Dockerfile
│
├── bronze/
│   └── writer.py            # Kafka consumer → MinIO + dp_bronze_catalog
│
├── silver/
│   ├── processor.py         # Kafka consumer → dp_processed_articles/jobs
│   └── deduplicator.py      # URL dedup (SQL) + content dedup (MD5 hash)
│
├── gold/
│   ├── pg_etl.py            # Neo4j → tech_analytics (3:00 AM)
│   └── neo4j_enricher.py    # Derived relationships + trend score (5:00 AM)
│
├── scheduler/
│   ├── scheduler.py         # APScheduler setup
│   └── jobs.py              # Job functions (pg_etl, enricher, embed, cluster)
│
└── common/
    ├── db.py                # get_pg_conn, get_neo4j_driver, get_minio_client
    └── logger.py            # Loguru setup

knowledge-graph/crawl/
├── run_all.py               # Docker entrypoint — chạy crawlers tuần tự
├── kafka_producer.py        # CrawlerKafkaProducer (send_article, send_job)
├── VNExpress.py             # Selenium crawler — vnexpress.net
├── GenK.py                  # Selenium crawler — genk.vn
├── DanTri.py                # Selenium crawler — dantri.com.vn
├── ICTNews.py               # Selenium crawler — ictnews.vietnamnet.vn
├── TopCV.py                 # Selenium+uc crawler — topcv.vn
├── ITviec.py                # Selenium+uc crawler — itviec.com
├── Viblo.py                 # requests crawler — viblo.asia REST API
├── GitHub.py                # requests crawler — GitHub API
├── requirements.txt
└── Dockerfile
```

---

## Dependencies chính

**data-platform:**
```
kafka-python-ng==2.2.3   # Drop-in replacement cho kafka-python (broken trên Python 3.12)
minio==7.2.7
psycopg2-binary==2.9.9
neo4j==5.20.0
apscheduler==3.10.4
pydantic-settings==2.2.1
requests==2.32.3
loguru==0.7.2
```

**crawlers:**
```
kafka-python-ng>=2.2.3
selenium>=4.15.0
undetected-chromedriver>=3.5.0
webdriver-manager>=4.0.0
requests>=2.32.0
beautifulsoup4>=4.12.0
fake_useragent>=1.4.0
```

> **Lưu ý:** `kafka-python==2.0.2` bị lỗi `ModuleNotFoundError: No module named 'kafka.vendor.six.moves'` trên Python 3.12. Phải dùng `kafka-python-ng==2.2.3`.