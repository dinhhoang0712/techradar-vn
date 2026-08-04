# Data Platform — TechRadar VN

Tài liệu này mô tả kiến trúc và hoạt động của Data Platform trong hệ thống TechRadar VN, chịu trách nhiệm thu thập, xử lý và lưu trữ dữ liệu công nghệ từ các nguồn Việt Nam.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Kiến trúc Medallion](#2-kiến-trúc-medallion)
3. [Crawler Layer](#3-crawler-layer)
4. [Bronze Layer](#4-bronze-layer)
5. [Silver Layer](#5-silver-layer)
6. [Gold Layer](#6-gold-layer)
7. [Scheduler](#7-scheduler)
8. [Database Schema](#8-database-schema)
9. [Cấu hình](#9-cấu-hình)
10. [Khởi chạy](#10-khởi-chạy)
11. [Monitoring](#11-monitoring)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Tổng quan

Data Platform là hệ thống pipeline thu thập và xử lý dữ liệu công nghệ Việt Nam theo kiến trúc **Medallion** (Bronze → Silver → Gold), đảm bảo dữ liệu chất lượng cao, có thể replay và scalable.

### Mục tiêu

- Thu thập dữ liệu từ 8 nguồn (tin tức, việc làm, OSS)
- Xử lý và làm sạch dữ liệu tự động
- Lưu trữ immutable raw data cho khả năng replay
- Cung cấp dữ liệu chất lượng cao cho Knowledge Graph và Analytics
- Tự động hóa pipeline với scheduler

### Vị trí trong hệ thống

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CRAWLER LAYER                                │
│  VNExpress  GenK  DanTri  ICTNews  TopCV  ITviec  Viblo  GitHub   │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ Kafka Producer
                            ▼
              ┌─────────────────────────────┐
              │           KAFKA             │
              │   raw_articles  raw_jobs    │
              └──────┬──────────────────────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
  ┌───────────────┐     ┌─────────────────────┐
  │ BRONZE LAYER  │     │    SILVER LAYER      │
  │ MinIO (raw)   │     │  PostgreSQL (clean)   │
  └───────┬───────┘     └──────────┬──────────┘
          │                        │
          └────────────────────────┘
                            │
                    ┌───────▼────────┐
                    │  GOLD LAYER    │
                    │  Neo4j KG      │
                    │  Analytics     │
                    └────────────────┘
```

---

## 2. Kiến trúc Medallion

### Bronze Layer (Raw Data)

- **Mục đích**: Lưu trữ toàn bộ raw data từ crawlers
- **Storage**: MinIO (S3-compatible object storage)
- **Format**: Gzip JSON (immutable)
- **Catalog**: PostgreSQL `dp_bronze_catalog`
- **Đặc điểm**: Không bao giờ xóa hoặc ghi đè, có thể replay bất kỳ lúc nào

### Silver Layer (Processed Data)

- **Mục đích**: Dữ liệu đã làm sạch và dedup
- **Storage**: PostgreSQL
- **Tables**: `dp_processed_articles`, `dp_processed_jobs`
- **Xử lý**: Deduplication, quality scoring, entity extraction
- **Đặc điểm**: Dữ liệu chất lượng cao cho downstream systems

### Gold Layer (Aggregated Data)

- **Mục đích**: Dữ liệu tổng hợp cho analytics và Knowledge Graph
- **Storage**: Neo4j (Knowledge Graph), PostgreSQL (`tech_analytics`)
- **Jobs**: Gold ETL, Neo4j Enricher, Embed Trigger, Clustering Retrain
- **Đặc điểm**: Optimized cho query và visualization

---

## 3. Crawler Layer

### Danh sách Crawlers

| Crawler | Loại | Nguồn | Giới hạn | Công nghệ |
|---------|------|--------|----------|-----------|
| VNExpress.py | Tin tức | vnexpress.net/khoa-hoc-cong-nghe | 150 bài | Selenium |
| GenK.py | Tin tức | genk.vn (AI, Internet, ICT) | 150 bài | Selenium |
| DanTri.py | Tin tức | dantri.com.vn/cong-nghe | 150 bài | Selenium |
| ICTNews.py | Tin tức | ictnews.vietnamnet.vn | 150 bài | Selenium |
| TopCV.py | Việc làm | topcv.vn (IT category) | 150 jobs | Selenium + uc |
| ITviec.py | Việc làm | itviec.com | 150 jobs | requests + BeautifulSoup (đổi từ Selenium+uc sau khi trang redesign 2026-07) |
| VietnamWorks.py | Việc làm | vietnamworks.com | 150 jobs | requests (trang không chặn bot như topdev.vn) — đọc JSON nhúng trong Next.js RSC "flight" stream (`self.__next_f.push(...)`), không parse DOM |
| JobsGO.py | Việc làm | jobsgo.vn | 150 jobs | Selenium + uc, parse JSON-LD `JobPosting` schema.org |
| TopDev.py | Việc làm | topdev.vn | 150 jobs | Selenium + uc — **viết xong nhưng CHƯA đăng ký trong `run_all.py`**: topdev.vn (không www) chặn/treo kết nối, www.topdev.vn redirect `/viec-lam-it` sang login-wall. Selector DOM trong file này chưa từng verify với HTML thật |
| Viblo.py | Forum/Blog | viblo.asia (REST API) | 150 bài | requests |
| GitHub.py | OSS | GitHub API (VN orgs) | 200 repos | requests |

**Trường `level` (cấp độ kinh nghiệm job)** — chỉ **VietnamWorks.py** scrape được trực tiếp
(field JSON `jobLevelVI`, đã có sẵn trong response SSR, không cần suy luận). **JobsGO.py**,
**ITviec.py**, **TopDev.py** đọc field chuẩn schema.org `experienceRequirements` trong JSON-LD
`JobPosting` (nếu trang nguồn có điền) — cùng cơ chế parse JSON-LD các crawler này đã dùng cho
title/company/salary, nên gần như miễn phí để thêm, nhưng **chưa xác nhận được các trang này có
thực sự điền field đó không** (cần chạy crawler thật để biết). TopCV.py chưa lấy field này (biết
có thể suy ra từ title nhưng chưa làm — xem comment trong file). Giá trị `level` gửi lên Kafka
luôn là **free-text thô, chưa chuẩn hoá** — xem "Entity Canonicalization" ở mục 5 cho nơi chuẩn
hoá về enum cố định.

### Kafka Message Format

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

### Chrome Options (Selenium Crawlers)

Tất cả Selenium crawlers dùng chung configuration để ổn định trong Docker:

```python
chrome_options.add_argument("--headless=new")
chrome_options.add_argument("--no-sandbox")
chrome_options.add_argument("--disable-dev-shm-usage")
chrome_options.add_argument("--disable-gpu")
chrome_options.add_argument("--disable-background-networking")
chrome_options.add_argument("--disable-sync")
chrome_options.add_argument("--metrics-recording-only")
chrome_options.add_argument("--mute-audio")
chrome_options.page_load_strategy = "eager"  # DOM ready là đủ
driver.set_page_load_timeout(60)
```

**Lưu ý quan trọng**: `page_load_strategy = "eager"` là fix quan trọng cho vnexpress.net và ictnews.vietnamnet.vn — các trang này timeout với strategy mặc định `"normal"`.

### API-based Crawlers

**Viblo.py**: Dùng Viblo REST API
```
GET https://viblo.asia/api/tags/{tag}/posts?page=N&limit=20
```
20 tags: `python, golang, java, javascript, typescript, docker, kubernetes, aws, devops, machine-learning, ai, backend, frontend, database, microservices, react, nodejs, laravel, spring-boot, fastapi`

**GitHub.py**: Dùng GitHub API
- 14 tổ chức VN: `vngcloud, zalopay, tiki-miniapp, shopee, fpt-corp, vnpay, techvify-software, axon-active, nashtech-global, framgia`
- 5 search queries: `"vietnam tech", "made in vietnam", "vietnamese developer"`

Hỗ trợ `GITHUB_TOKEN`:
- Không có token: 60 requests/giờ
- Có token: 5000 requests/giờ

---

## 4. Bronze Layer

### Chức năng

Ghi **toàn bộ raw Kafka message** vào MinIO dưới dạng gzip JSON (immutable). Đây là nguồn sự thật (source of truth) có thể replay lại bất kỳ lúc nào.

### Object Path Pattern

```
s3://techradar-bronze/raw/{articles|jobs}/{platform}/
  year={YYYY}/month={MM}/day={DD}/
  {md5(source_url)}_{YYYYMMDDTHHMMSSZ}.json.gz
```

Ví dụ:
```
s3://techradar-bronze/raw/articles/vnexpress/
  year=2026/month=06/day=29/
  83fd69b7..._20260629T041600Z.json.gz
```

### Catalog Entry (PostgreSQL)

Mỗi file được đăng ký vào `dp_bronze_catalog`:

| Column | Mô tả |
|--------|-------|
| `id` | MD5(source_url) — idempotent key |
| `source_url` | URL gốc của bài viết/job |
| `minio_path` | `s3://techradar-bronze/...` |
| `file_size_bytes` | Kích thước file nén (bytes) |
| `kafka_topic` | `raw_articles` hoặc `raw_jobs` |
| `kafka_offset` | Offset Kafka để replay |

**Idempotency**: `ON CONFLICT (id) DO NOTHING` — đảm bảo không duplicate khi Bronze Writer restart.

---

## 5. Silver Layer

### Chức năng

Đọc từ Kafka, xử lý và làm sạch dữ liệu, lưu vào PostgreSQL.

**Topics**: `raw_articles`, `raw_jobs`, `extracted_articles`, `extracted_jobs`

**Thiết kế dual-topic**: Silver đọc cả `raw_*` (từ crawlers trực tiếp) **và** `extracted_*` (từ Spring Boot NLP pipeline). Khi Spring Boot chạy, `extracted_*` chứa entity đã được extract; `raw_*` vẫn được xử lý song song để không bỏ sót data khi Spring Boot tắt.

### Xử lý Article

1. **Extract fields** — hỗ trợ cả wrapped format và flat format:
   ```python
   data = msg.get("data", msg)  # handle both formats
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

### Xử lý Job

Hỗ trợ cả flat fields (từ `kafka_producer.py`) và nested format:
```python
job = data.get("job", data)
title = job.get("job_title") or job.get("title")  # TopCV dùng job_title
company_name = job.get("company_name") or company_obj.get("name")
company_location = job.get("location") or company_obj.get("location")
```

### Deduplication

- **URL dedup (exact)**: `ON CONFLICT (source_url) DO NOTHING` — handled by SQL
- **Content dedup (near)**: `content_hash = MD5(normalize(title + content))`
  - normalize = lowercase + collapse whitespace
  - Nếu hash đã tồn tại → `is_duplicate=True`, `duplicate_of={id gốc}`

### Level Normalization (cấp độ kinh nghiệm job)

Trước khi INSERT, `silver/processor.py::_process_job()` gọi `common/level_normalizer.py::normalize_level()`
để chuẩn hoá free-text `level` (crawler gửi, xem mục 3) về 1 trong 6 giá trị cố định
(`Intern`/`Fresher`/`Junior`/`Middle`/`Senior`/`Lead`) bằng keyword matching (case-insensitive
substring, tiếng Việt + tiếng Anh, ưu tiên cấp cao nhất khớp trước — "Senior Team Lead" → `Lead`).
Không khớp bucket nào (phần lớn job hiện tại, vì hầu hết crawler gửi rỗng) → `None`/NULL, không
gán "Unknown" giả. Bản Java tương đương (`LevelNormalizer.java`, dùng ở đường ghi realtime) giữ
cùng bảng keyword, phải sửa đồng thời cả 2 bên khi mở rộng — cùng nguyên tắc "duplicate có chủ
đích" như `tech_alias_cache.py`/`TechAliasCache.java`, khác ở chỗ level là 1 tập bucket cố định
nhỏ nên không cần bảng DB alias riêng như tech name.

`dp_processed_jobs.level` có CHECK constraint (V38, Postgres) ràng buộc đúng 6 giá trị này (cho
phép NULL) — xem [`docs/DATABASE.md`](./DATABASE.md) §3.2. `gold/neo4j_job_sync.py` đồng bộ tiếp
giá trị đã chuẩn hoá này lên `Job.level` (Neo4j).

### Entity Canonicalization (tech name)

Trước khi lưu `entity_techs`/`technologies`, Silver tra `common/tech_alias_cache.py`
(cache RAM của bảng `dp_tech_alias_map`, refresh mỗi ~5 phút) để gộp các tên
khác nhau cho CÙNG 1 công nghệ (vd `Golang` → `Go`, `ML` → `Machine Learning`).
Đây là lý do Silver được coi là tầng "conform" theo đúng tinh thần Medallion —
dữ liệu vào Gold/Neo4j đã sạch tên thực thể, không cần vá lại sau.

Đường ghi Neo4j realtime (`EntityExtractionService.java` trong Spring Boot,
xem [Backend Guide](BACKEND_GUIDE.md)) **bỏ qua Silver hoàn toàn** nên tự tra
CÙNG bảng `dp_tech_alias_map` qua `TechAliasCache.java` (cache Java riêng,
cùng nguồn dữ liệu) — không có 2 đường tạo Technology node nào bỏ sót bước
chuẩn hoá này.

---

## 6. Gold Layer

### Gold PG ETL

**Chạy**: 3:00 AM daily (Asia/Ho_Chi_Minh)

Đọc Neo4j Knowledge Graph → rebuild bảng `tech_analytics` trong PostgreSQL.

**Cypher queries**:
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

**Output**: Upsert vào `tech_analytics` (`UNIQUE(technology_name, month)`, xem
`V1__init_schema.sql`)

| Column | Mô tả |
|--------|-------|
| `technology_name` | Tên công nghệ (KHÔNG phải `tech_name`) |
| `month` | Tháng (DATE, YYYY-MM-01 — KHÔNG phải cột `period`) |
| `article_count` | Số bài viết đề cập |
| `job_count` | Số job yêu cầu |
| `growth_rate` | % tăng trưởng so với tháng trước |
| `yoy_growth` | % tăng trưởng so với cùng kỳ năm trước |
| `mom_growth` | % tăng trưởng tháng-qua-tháng |
| `ranking` | Thứ hạng công nghệ trong tháng đó |

Không có cột `snapshot_jobs` — giá trị "tổng job hiện tại" được gộp thẳng vào `job_count` của
tháng hiện tại trong code (`data-platform/gold/pg_etl.py`), không lưu riêng.

### Neo4j Enricher

**Chạy**: 5:00 AM daily

Tạo **derived relationships** và cập nhật statistics trong Knowledge Graph:

| Cypher | Mô tả |
|--------|-------|
| `(Company)-[:USES]->(Technology)` | Suy ra từ Article đề cập cả company lẫn tech |
| `(Technology)-[:RELATED_TO]->(Technology)` | Co-mention trong cùng bài viết |
| `t.mention_count = article_count + job_count` | Cập nhật mention count trên mỗi Technology node |
| `t.trend_score` | `(mention_count * 2 + job_count) / max * 100` |
| `t.category` | Đọc từ Postgres `dp_tech_category` (V29), ghi bởi Tech Dedup + Tech Category Backfill bên dưới |

> **Độ trễ ~24h cho `category`:** job này chạy 5:00 AM, còn Tech Dedup (nguồn ghi `dp_tech_category`
> cho tên mới) chạy SAU, 5:30 AM cùng ngày — nên category của 1 tên Technology mới phát hiện đêm
> nay chỉ được `neo4j_enricher` đẩy lên Neo4j vào lần chạy NGÀY MAI, không phải cùng đêm đó.

### Tech Dedup

**Chạy**: 5:30 AM daily (ngay sau Neo4j Enricher)

Gộp các `:Technology` node trùng lặp do khác cách viết (`Go`/`Golang`,
`ML`/`Machine Learning`, `K8s`/`Kubernetes`...) — dù Silver + đường ghi
realtime đã chặn phần lớn duplicate NGAY LÚC GHI (xem Entity
Canonicalization ở mục 5), vẫn cần job này vì:
- Case CHƯA từng biết trước (không có trong `dp_tech_alias_map`) — cần LLM
  phán đoán, việc mà 2 đường ghi realtime không làm (tốn phí/độ trễ nếu gọi
  LLM trên từng message).
- Node ĐÃ TỒN TẠI SẴN trong Neo4j từ TRƯỚC KHI 1 alias được biết đến — Cypher
  `MERGE` không tự tìm và xoá node cũ.

**2 giai đoạn, chạy nối tiếp trong 1 lần** (`gold/tech_dedup.py`):
1. Áp `dp_tech_alias_map` đã biết trực tiếp vào Neo4j hiện có (rẻ, không cần LLM)
2. Tên còn lại chưa có trong alias map → gửi LLM 1 lần → nhóm tự tin cao thì
   merge thẳng + ghi vào `dp_tech_alias_map` (lần sau rẻ hơn); nhóm không
   chắc thì đưa vào `dp_tech_alias_review_queue` cho người duyệt

Merge = chuyển hướng toàn bộ cạnh (`MENTIONS`/`REQUIRES`/`USES`/`IS_TECHNOLOGY`/`RELATED_TO`)
từ node phụ sang node đại diện rồi xoá node phụ — viết bằng Cypher thuần
(không dùng APOC, vì plugin APOC không có sẵn trên Neo4j Docker local, chỉ
AuraDB cloud mới có).

**Category classification (cùng 1 lệnh gọi LLM ở giai đoạn 2):** prompt LLM ở giai đoạn 2 giờ hỏi
đồng thời cả nhóm trùng lặp LẪN category (language/framework/tool/cloud/database/...) cho mỗi tên
mới — không tốn thêm lệnh gọi LLM riêng. Kết quả category ghi vào Postgres `dp_tech_category`
(V29), KHÔNG ghi thẳng vào Neo4j (Neo4j Enricher mới là nơi đọc bảng này và đẩy lên
`Technology.category`, xem trên).

### Tech Category Backfill (chạy tay, một lần)

Script `gold/tech_category_backfill.py` — **không** nằm trong lịch nightly (mục 7), chạy thủ công
1 lần: `python -m gold.tech_category_backfill`. Dùng lại đúng logic phân loại của Tech Dedup
(`_call_llm`/`_parse_llm_categories`/`_save_categories`) nhưng áp cho **toàn bộ catalog
Technology hiện có** (không chỉ tên mới phát hiện) — dùng để lấp `category` cho các node đã tồn
tại từ trước khi có cơ chế phân loại này, tránh phải chờ node đó "được nhắc tới lại" để Tech Dedup
tự nhiên xử lý. Idempotent (bỏ qua tên đã có category), batch ~50-100 tên/lần gọi LLM.

> **Nên spot-check kết quả:** phân loại category bằng LLM cho các trường hợp mơ hồ (vd
> "GraphQL", "React Native" — framework hay tool?) vẫn có thể sai; nên kiểm tra thủ công một mẫu
> nhỏ trước khi dùng số liệu độ phủ category cho báo cáo/luận án chính thức.

### KG Health Audit (chạy tay, on-demand)

Script `gold/kg_health_audit.py` — **không** nằm trong lịch nightly, chạy khi cần kiểm tra chất
lượng đồ thị: `python -m gold.kg_health_audit` (in JSON ra stdout). Thiết kế trực tiếp từ 1 bug
thật từng phát hiện trong dự án (`HIRES_FOR` — quan hệ chết vẫn còn dữ liệu cũ, một số Cypher
từng match nhầm gây mất dữ liệu âm thầm), tự động hoá đúng loại phát hiện đó cho mọi lần chạy sau:

| Kiểm tra | Ý nghĩa |
|---|---|
| Quan hệ "lạ"/chết | Loại quan hệ tồn tại trong graph nhưng không thuộc danh sách writer đang hoạt động (`_KNOWN_ACTIVE_REL_TYPES`) — dấu hiệu writer cũ đã bị gỡ, giống hệt `HIRES_FOR` |
| Node mồ côi | Technology/Company không có quan hệ nào |
| Độ phủ property | % Technology có `category` (V29), % có `pagerank_score`, và riêng % **dùng được** (loại `NaN` — `count()` của Cypher đếm cả NaN vì nó khác `NULL`) |
| Độ phủ `Job.level` theo `source_platform` | % Job đã phân loại cấp độ kinh nghiệm, tách theo nguồn crawl — coverage thấp ở phần lớn platform là kỳ vọng (chỉ VietnamWorks scrape được `level` thật), theo dõi số này để biết khi nào cần mở rộng keyword dictionary của `normalize_level()` hoặc thêm scrape thật cho platform khác |
| Tên trùng chỉ khác hoa/thường | Technology chưa được `tech_dedup`/alias map gộp |

**Hạn chế đã biết:** chỉ bắt trùng tên ở `Technology`, **chưa** bắt trùng tên `Company`/`Job` (vd
nhiều pháp nhân cùng tên gốc như "FPT Software"/"Công Ty Cổ Phần Viễn Thông FPT" bị lưu thành
Company node riêng biệt) — ghi nhận là hướng phát triển tiếp theo, không phải lỗi của script.

### Embed Trigger

**Chạy**: 4:00 AM daily

Gọi `POST {RAG_BASE_URL}/embed/trigger` với header `X-Embed-Secret`. `ai-rag-core` đọc Article
trực tiếp từ Neo4j, embed bằng `multilingual-e5-base`, rồi ghi vector **thẳng vào property
`Article.embedding` trong Neo4j** — KHÔNG phải Qdrant. Response ngay lập tức, job chạy async trong
background. (Qdrant là một pipeline hoàn toàn khác, riêng biệt: `embedding-service` — Kafka
consumer trên `extracted_articles`/`extracted_jobs`, publish `article_vectors`/`job_vectors` —
rồi `qdrant-writer` consume và ghi vào Qdrant; chỉ chạy khi bật profile `vector`, xem
[`AI_PLATFORM.md` §4.2-4.3](./AI_PLATFORM.md).)

### Clustering Retrain

**Chạy**: 6:00 AM, mỗi Chủ nhật

Gọi `POST {ML_CLUSTERING_BASE_URL}/pipeline/trigger`. `ml-clustering` service chạy pipeline 6 stage: `stage_01_extract` → `stage_02_features` → `stage_03_train` → (champion gate) → `stage_04_label` → `stage_06_publish` → `stage_05_writeback`. Sau khi trigger thành công, job block lại và **poll `GET /pipeline/status`** định kỳ (`CLUSTERING_RETRAIN_POLL_INTERVAL_S`, mặc định 30s) cho tới khi pipeline xong hoặc hết `CLUSTERING_RETRAIN_MAX_WAIT_S` (mặc định 7200s), rồi ghi kết quả thật vào `dp_pipeline_runs` — trước đây chỉ log việc trigger HTTP thành công, không biết pipeline có thật sự chạy xong hay lỗi (vd hết quota LLM ở Stage 4).

Trong `ml-clustering`, model mới chỉ được gán alias `champion` trong MLflow Model Registry nếu tốt hơn (hoặc bằng) champion hiện tại theo cùng `primary_metric` dùng để chọn best trial — model cũ vẫn được đăng ký (giữ lịch sử) nhưng không ghi đè champion nếu kém hơn. Kể từ khi có Stage 6 (publish), quyết định này còn quyết định pipeline có chạy tiếp LABEL/PUBLISH/WRITEBACK hay dừng lại ở TRAIN (`dp_pipeline_runs` sẽ thấy `deployed=false` — xem `GET /pipeline/status`). Xem [`AI_PLATFORM.md` §3.2](./AI_PLATFORM.md#32-pipeline-6-stages).

---

## 7. Scheduler

Dùng **APScheduler** với `BackgroundScheduler` (chạy trong main thread của `main.py`).

### Lịch chạy mặc định (Asia/Ho_Chi_Minh)

| Job | Cron | Mô tả |
|-----|------|-------|
| `neo4j_article_sync` | `0 2 * * *` | Bù đồng bộ Bài viết → Neo4j (Silver → Graph, độc lập với Kafka realtime) |
| `neo4j_job_sync` | `30 2 * * *` | Bù đồng bộ Tin tuyển dụng → Neo4j (tương tự) |
| `gold_pg_etl` | `0 3 * * *` | Rebuild tech_analytics từ Neo4j |
| `embed_trigger` | `0 4 * * *` | Trigger vector embedding mới |
| `neo4j_enricher` | `0 5 * * *` | Cập nhật derived relationships |
| `tech_dedup` | `30 5 * * *` | Gộp Technology node trùng lặp (alias map + LLM) |
| `retrain_clustering` | `0 6 * * 0` (Chủ nhật) | Trigger + poll pipeline retrain clustering (xem mục Clustering Retrain) |
| `retrain_clustering` | `0 6 * * 0` | Retrain ML clustering (Chủ nhật) |

### Dev Mode — Chạy jobs ngay khi start

```bash
# .env
RUN_JOBS_ON_START=true
```

Khi `run_jobs_on_start=true`, tất cả jobs được trigger ngay lập tức khi container start (dùng để seed data ban đầu).

### Cấu hình cron qua env vars

```bash
ARTICLE_SYNC_HOUR=2    ARTICLE_SYNC_MINUTE=0
JOB_SYNC_HOUR=2        JOB_SYNC_MINUTE=30
GOLD_ETL_HOUR=3        GOLD_ETL_MINUTE=0
EMBED_TRIGGER_HOUR=4   EMBED_TRIGGER_MINUTE=0
NEO4J_ENRICHER_HOUR=5  NEO4J_ENRICHER_MINUTE=0
TECH_DEDUP_HOUR=5      TECH_DEDUP_MINUTE=30
CLUSTERING_RETRAIN_HOUR=6  CLUSTERING_RETRAIN_MINUTE=0
CLUSTERING_RETRAIN_DAY_OF_WEEK=sun
```

**LLM cho Tech Dedup** (Giai đoạn B — case chưa có trong alias map):
```bash
TECH_DEDUP_LLM_PROVIDER=gemini   # "gemini" | "openai" | "groq" (mặc định model groq: llama-3.3-70b-versatile)
GEMINI_API_KEY=...
OPENAI_API_KEY=...
```

---

## 8. Database Schema

### dp_bronze_catalog

Raw file registry:

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

### dp_processed_articles

Silver articles:

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

### dp_processed_jobs

Silver jobs:

```sql
CREATE TABLE dp_processed_jobs (
    id               TEXT PRIMARY KEY,     -- MD5(source_url)
    source_url       TEXT NOT NULL UNIQUE,
    source_platform  TEXT NOT NULL,        -- TopCV | ITviec
    job_title        TEXT,
    company_name     TEXT,
    company_location TEXT,
    salary           TEXT,
    level            TEXT,          -- CHECK (V38): NULL hoặc 1 trong Intern/Fresher/Junior/Middle/Senior/Lead
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

### dp_tech_alias_map

Nguồn chuẩn hoá tên Technology duy nhất — dùng chung giữa
`EntityExtractionService.java` (Java Kafka realtime), `silver/processor.py`
(Python Silver), và `gold/tech_dedup.py` (Gold):

```sql
CREATE TABLE dp_tech_alias_map (
    alias_normalized TEXT PRIMARY KEY,          -- casefold + trim, vd "golang"
    canonical_name   TEXT NOT NULL,              -- vd "Go"
    source           TEXT NOT NULL DEFAULT 'seed', -- seed | llm_auto | human_review
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### dp_tech_category

Category (language/framework/tool/cloud/database/...) cho Technology, ghi bởi Tech Dedup (tên
mới) + Tech Category Backfill (script chạy tay 1 lần, toàn bộ catalog cũ) — xem mục 6. Key theo
`canonical_name` (không phải `alias_normalized` như `dp_tech_alias_map`) vì category là thuộc
tính của **1 công nghệ**, không nên lặp/trôi theo từng cách viết khác nhau của cùng công nghệ đó.
Chỉ dùng nội bộ `data-platform` — không chia sẻ với `apps/backend` như `dp_tech_alias_map`.

```sql
CREATE TABLE dp_tech_category (
    canonical_name TEXT PRIMARY KEY,
    category       TEXT NOT NULL,
    source         TEXT NOT NULL DEFAULT 'llm_auto', -- llm_auto | human_review
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### dp_tech_alias_review_queue

Case LLM (Tech Dedup Giai đoạn B) không tự tin — chờ người duyệt qua
`AdminClusteringController` hoặc tương tự; quyết định được ghi nhớ, không hỏi
lại cặp đã duyệt:

```sql
CREATE TABLE dp_tech_alias_review_queue (
    id            BIGSERIAL PRIMARY KEY,
    name_a        TEXT NOT NULL,
    name_b        TEXT NOT NULL,
    llm_reasoning TEXT,
    status        TEXT NOT NULL DEFAULT 'pending', -- pending | approved | rejected
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at    TIMESTAMPTZ
);
```

### dp_pipeline_runs

Job execution log:

```sql
CREATE TABLE dp_pipeline_runs (
    id          BIGSERIAL PRIMARY KEY,
    job_name    TEXT NOT NULL,   -- neo4j_article_sync | neo4j_job_sync | gold_pg_etl | embed_trigger | neo4j_enricher | tech_dedup | retrain_clustering
    status      TEXT NOT NULL,   -- running | success | failed
    rows_affected INT,
    error_msg   TEXT,
    started_at  TIMESTAMPTZ DEFAULT now(),
    finished_at TIMESTAMPTZ
);
```

---

## 9. Cấu hình

### Environment Variables

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

### Crawler Env Vars

| Env var | Default | Mô tả |
|---------|---------|-------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` | Kafka broker (trong Docker: `kafka:9092`) |
| `CRAWL_INTERVAL_HOURS` | `6` | Khoảng thời gian giữa các crawl run |
| `GITHUB_TOKEN` | _(empty)_ | GitHub Personal Access Token (public_repo scope) |

---

## 10. Khởi chạy

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

## 11. Monitoring

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

### Kafka Topics

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

### Kiểm tra Silver Dedup Rate

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

## 12. Troubleshooting

### Crawler không chạy

**Symptom**: Crawler không xuất ra logs hoặc không gửi messages lên Kafka

**Solutions**:
1. Kiểm tra Kafka connection: `docker logs techradar-crawler | grep Kafka`
2. Kiểm tra Chrome options trong Docker (headless mode)
3. Xem logs chi tiết: `docker logs techradar-crawler -f`
4. Chạy crawler manually trong container để debug

### Bronze Writer không ghi file

**Symptom**: Kafka messages được nhận nhưng không có file trong MinIO

**Solutions**:
1. Kiểm tra MinIO connection: `docker logs techradar-data-platform | grep MinIO`
2. Kiểm tra bucket tồn tại: Truy cập MinIO console
3. Kiểm tra Kafka consumer lag: `kafka-consumer-groups.sh --describe --group bronze-writer`

### Silver Processor không xử lý

**Symptom**: Messages trong Kafka nhưng không có rows trong `dp_processed_articles`

**Solutions**:
1. Kiểm tra PostgreSQL connection
2. Kiểm tra message format (wrapped vs flat)
3. Xem logs: `docker logs techradar-data-platform | grep Silver`
4. Kiểm tra consumer lag: `kafka-consumer-groups.sh --describe --group silver-processor`

### Gold ETL không cập nhật

**Symptom**: `tech_analytics` không có data mới

**Solutions**:
1. Kiểm tra scheduler logs: `docker logs techradar-data-platform | grep gold_pg_etl`
2. Trigger manual: `RUN_JOBS_ON_START=true`
3. Kiểm tra Neo4j connection
4. Kiểm tra `dp_pipeline_runs` table để xem error messages

### Dedup rate quá cao

**Symptom**: Quá nhiều articles bị đánh dấu duplicate

**Solutions**:
1. Kiểm tra `content_hash` logic trong `silver/deduplicator.py`
2. Điều chỉnh normalization function
3. Kiểm tra xem có phải crawlers đang gửi duplicate URLs không

### Kafka Consumer Lag

**Symptom**: Consumer lag tăng liên tục

**Solutions**:
1. Kiểm tra consumer health: `kafka-consumer-groups.sh --describe`
2. Tăng consumer instances (nếu cần)
3. Kiểm tra processing time per message
4. Xem logs để tìm bottleneck

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
│   ├── neo4j_article_sync.py # dp_processed_articles → Neo4j (bù khi Kafka rớt, 2:00 AM)
│   ├── neo4j_job_sync.py    # dp_processed_jobs → Neo4j (bù khi Kafka rớt, 2:30 AM)
│   ├── neo4j_enricher.py    # Derived relationships + trend score + category (5:00 AM)
│   ├── tech_dedup.py        # Gộp Technology node trùng lặp + phân loại category (alias map + LLM, 5:30 AM)
│   ├── tech_category_backfill.py  # Backfill category cho catalog cũ (chạy tay, 1 lần, KHÔNG trong lịch)
│   └── kg_health_audit.py   # Audit chất lượng đồ thị (chạy tay, on-demand, KHÔNG trong lịch)
│
├── scheduler/
│   ├── scheduler.py         # APScheduler setup
│   └── jobs.py              # Job functions (pg_etl, enricher, tech_dedup, embed, cluster)
│
└── common/
    ├── db.py                # get_pg_conn, get_neo4j_driver, get_minio_client
    ├── tech_alias_cache.py  # Cache RAM dp_tech_alias_map — dùng chung Silver + Tech Dedup
    ├── level_normalizer.py  # normalize_level() — free-text job level → enum 6 mức, dùng bởi Silver
    └── logger.py            # Loguru setup

services/crawler/
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
kafka-python-ng==2.2.3   # Drop-in replacement cho kafka-python
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

**Lưu ý**: `kafka-python==2.0.2` bị lỗi `ModuleNotFoundError: No module named 'kafka.vendor.six.moves'` trên Python 3.12. Phải dùng `kafka-python-ng==2.2.3`.

---

## Liên hệ

Nếu bạn có câu hỏi về Data Platform, hãy:
1. Kiểm tra file `data-platform/README.md` cho chi tiết implementation
2. Mở issue trên GitHub repository
3. Liên hệ team qua Discord hoặc email
