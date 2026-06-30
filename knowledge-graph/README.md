# Knowledge Graph Subsystem

Hệ thống xây dựng và khai thác **Knowledge Graph** về thị trường công nghệ Việt Nam, lưu trữ trên **Neo4j**. Đây là "nguồn tri thức" trung tâm của TechRadar VN, cung cấp dữ liệu cho RAG pipeline, tech radar, phân tích xu hướng và tìm kiếm việc làm.

---

## Mục lục

1. [Kiến trúc tổng quan](#1-kiến-trúc-tổng-quan)
2. [Graph Schema](#2-graph-schema)
3. [Cấu trúc thư mục](#3-cấu-trúc-thư-mục)
4. [Modules](#4-modules)
   - [entity_resolution](#41-entity_resolution)
   - [ontology](#42-ontology)
   - [cypher_repo](#43-cypher_repo)
   - [analytics](#44-analytics)
   - [crawl](#45-crawl)
   - [services](#46-services)
   - [utils](#47-utils)
5. [Data Flow](#5-data-flow)
6. [Cấu hình](#6-cấu-hình)
7. [Chạy pipeline](#7-chạy-pipeline)
8. [Mở rộng](#8-mở-rộng)

---

## 1. Kiến trúc tổng quan

```
Crawlers (8 nguồn)
    │
    ▼  raw JSON / Kafka
Data Pipeline (NER + extract)
    │
    ▼  extracted_data/*.json
entity_resolution ──► ontology
    │                    │
    ▼                    ▼
    RelationshipBuilder (graph_builder)
         │
         ▼
      Neo4j (Knowledge Graph)
         │
    ┌────┴─────┐
    │          │
    ▼          ▼
analytics   ai-rag-core
(scores)    (graph_search)
```

**Luồng dữ liệu:**

1. Crawlers thu thập bài báo và job postings từ 8 nguồn.
2. Data pipeline chạy NER → sinh `extracted_data/*.json`.
3. `import_multi_source.py` đọc JSON, áp dụng **entity_resolution** (chuẩn hóa tên) + **ontology** (phân loại category), rồi import vào Neo4j.
4. **analytics** tính `trend_score` / `demand_score` và ghi lại vào graph.
5. `ai-rag-core` truy vấn graph qua **graph_queries** để phục vụ RAG.

---

## 2. Graph Schema

### Node types

| Label | Unique key | Thuộc tính chính |
|---|---|---|
| `Article` | `title` | `content`, `source`, `published_date`, `sentiment_score`, `embedding` (768d) |
| `Technology` | `name` | `category`, `subcategory`, `description`, `trend_score`, `demand_score` |
| `Skill` | `name` | `category`, `demand_score` |
| `Company` | `name` | `field`, `size`, `location`, `rating` |
| `Job` | `title` | `description`, `requirement`, `benefit`, `salary`, `due_date`, `source_url` |
| `Person` | `name` | `role` |

### Relationship types

| Relationship | Từ | Đến | Nguồn gốc |
|---|---|---|---|
| `MENTIONS` | Article | Technology / Company / Person | NER trích xuất |
| `REQUIRES` | Job | Technology / Skill | Job posting |
| `HIRES_FOR` | Job | Company | Job posting |
| `USES` | Company | Technology | Co-mention heuristic (≥ 2 bài) |
| `RELATED_TO` | Technology | Technology | Co-mention heuristic (≥ 1 bài) |
| `WORKS_AT` | Person | Company | Co-mention heuristic (≥ 2 bài) |
| `WROTE` | Person | Article | Co-mention heuristic |

### Indexes & Constraints

```cypher
-- Unique constraints (tất cả node types)
CREATE CONSTRAINT FOR (t:Technology) REQUIRE t.name IS UNIQUE;
-- ... tương tự cho Article, Company, Job, Skill, Person

-- Property indexes
CREATE INDEX technology_category       FOR (t:Technology) ON (t.category);
CREATE INDEX technology_subcategory    FOR (t:Technology) ON (t.subcategory);
CREATE INDEX article_published_date    FOR (a:Article)    ON (a.published_date);
CREATE INDEX company_location          FOR (c:Company)    ON (c.location);

-- Vector index (768-dim cosine, dùng cho RAG)
CREATE VECTOR INDEX article_embedding_index
  FOR (a:Article) ON (a.embedding)
  OPTIONS {indexConfig: {`vector.dimensions`: 768, `vector.similarity_function`: 'cosine'}};
```

---

## 3. Cấu trúc thư mục

```
knowledge-graph/
├── entity_resolution/          # Chuẩn hóa tên trước khi import
│   ├── aliases.json            # ~100 tech aliases + company aliases
│   ├── tech_resolver.py        # resolve_tech(), resolve_tech_list()
│   ├── company_resolver.py     # resolve_company(), resolve_company_list()
│   └── __init__.py
│
├── ontology/                   # Taxonomy 2 cấp
│   ├── taxonomy.py             # TAXONOMY dict (10 categories, 50+ subcategories)
│   ├── tech_classifier.py      # classify_tech() → (category, subcategory)
│   └── __init__.py
│
├── cypher_repo/                # Tập trung tất cả Cypher queries
│   ├── repository.py           # Constants: MERGE_*, REL_*, SCHEMA_*, ANALYTICS_*
│   └── __init__.py
│
├── analytics/                  # Tính scores từ data thực
│   ├── trend_scorer.py         # compute_and_update_trend_scores()
│   ├── demand_scorer.py        # compute_and_update_demand_scores()
│   └── __init__.py
│
├── crawl/                      # Web crawlers
│   ├── base_crawler.py         # Abstract base + Kafka integration
│   ├── VNExpress.py
│   ├── DanTri.py
│   ├── GenK.py
│   ├── ICTNews.py
│   ├── Viblo.py
│   ├── TopCV.py
│   ├── ITviec.py
│   ├── GitHub.py
│   ├── kafka_producer.py
│   └── run_all.py              # Entry point (chạy tất cả crawlers)
│
├── services/
│   ├── embedding_service/      # Kafka consumer → multilingual-e5-base → vectors
│   └── qdrant_writer/          # Kafka consumer → Qdrant upsert
│
├── utils/
│   ├── neo4j_config.py         # Đọc env vars Neo4j
│   ├── schema_define.py        # Python dataclasses cho node types
│   ├── database_connection.py  # Neo4jJobImporter: kết nối + import nodes
│   ├── import_multi_source.py  # RelationshipBuilder: import pipeline
│   └── run_complete_pipeline.py # Entry point: import + analytics
│
├── scripts/
│   ├── seed_sample_graph.cypher # Seed dữ liệu mẫu
│   ├── fix_json_files.py
│   └── move_url_files.py
│
├── Makefile
├── requirements.txt
└── README.md
```

---

## 4. Modules

### 4.1 entity_resolution

Chuẩn hóa tên entity **trước khi ghi vào Neo4j** để tránh duplicate nodes.

#### `tech_resolver.py`

```python
from entity_resolution import resolve_tech, resolve_tech_list

# Chuẩn hóa một tên
resolve_tech("k8s")       # → "Kubernetes"
resolve_tech("ReactJS")   # → "React"
resolve_tech("Vue.js")    # → "Vue"
resolve_tech("Postgres")  # → "PostgreSQL"
resolve_tech("py")        # → "Python"
resolve_tech("")          # → None

# Chuẩn hóa + dedup một list
resolve_tech_list(["React", "ReactJS", "react", "Vue.js"])
# → ["React", "Vue"]
```

**Cơ chế:**
1. Đọc `aliases.json` lúc import module (O(1) mỗi lần gọi).
2. So sánh case-insensitive.
3. Nếu không có alias → trả về tên gốc (đã strip whitespace).

#### `company_resolver.py`

```python
from entity_resolution import resolve_company, resolve_company_list

resolve_company("FPT Corp")            # → "FPT"
resolve_company("Tập đoàn FPT")        # → "FPT"
resolve_company("Axon Active Vietnam") # → "Axon Active"  (suffix strip)
resolve_company("AB")                  # → None  (quá ngắn)
resolve_company("")                    # → None
```

**Cơ chế:**
1. Tra alias dictionary (explicit mapping).
2. Nếu không có alias → strip legal suffixes bằng regex: `Corporation`, `Corp`, `Co. Ltd`, `Limited`, `JSC`, `Công ty`, `Tập đoàn`, `Vietnam`, `Việt Nam`, ...
3. Tên ngắn hơn 3 ký tự → trả `None`.

#### `aliases.json`

File JSON hai section:

```json
{
  "tech_aliases": {
    "k8s": "Kubernetes",
    "reactjs": "React",
    ...
  },
  "company_aliases": {
    "fpt corp": "FPT",
    "vng corp": "VNG",
    ...
  }
}
```

> **Thêm alias mới:** Chỉnh sửa `aliases.json`. Key là lowercase, value là canonical form. Module tự reload khi khởi động lại process.

---

### 4.2 ontology

Phân loại công nghệ vào taxonomy 2 cấp: **category → subcategory**.

#### `taxonomy.py`

Định nghĩa `TAXONOMY` dict với 10 top-level categories:

| Category | Subcategories |
|---|---|
| `AI/ML` | Machine Learning, Deep Learning, NLP, Generative AI, Computer Vision, MLOps |
| `Cloud` | AWS, GCP, Azure, Cloud Native |
| `Frontend` | Framework, Language, Styling, Build Tools |
| `Backend` | Python, Java/JVM, Go, Node.js, PHP, Rust, .NET, Ruby |
| `Mobile` | Cross-platform, iOS, Android |
| `Database` | Relational, NoSQL, Cache, Search, Vector DB, Graph DB, Time Series |
| `DevOps` | CI/CD, Container, Orchestration, Infrastructure, Monitoring |
| `Data Engineering` | Processing, Streaming, Orchestration, Data Warehouse, ETL |
| `Security` | Auth, Network, AppSec |
| `Blockchain` | Platform, Development |

#### `tech_classifier.py`

```python
from ontology import classify_tech, get_all_categories, get_subcategories

classify_tech("React")          # → ("Frontend", "Framework")
classify_tech("Kubernetes")     # → ("DevOps", "Orchestration")
classify_tech("TensorFlow")     # → ("AI/ML", "Deep Learning")
classify_tech("LLM")            # → ("AI/ML", "Generative AI")
classify_tech("PostgreSQL")     # → ("Database", "Relational")
classify_tech("Apache Kafka")   # → ("Data Engineering", "Streaming")
classify_tech("UnknownTech")    # → ("Other", "General")

get_all_categories()
# → ["AI/ML", "Backend", "Blockchain", "Cloud", ...]

get_subcategories("Database")
# → ["Relational", "NoSQL", "Cache", "Search", "Vector DB", "Graph DB", "Time Series"]
```

**Cơ chế:**
1. Build reverse index `keyword → (category, subcategory)` lúc import module.
2. Exact match trước (O(1)).
3. Substring match: kiểm tra từng keyword (dài nhất trước) có trong tên hoặc ngược lại.
4. Fallback: `("Other", "General")`.

> **Thêm taxonomy mới:** Chỉnh sửa `taxonomy.py`, thêm entry vào `TAXONOMY`. Module tự rebuild index khi khởi động lại.

---

### 4.3 cypher_repo

Tập trung tất cả Cypher queries vào một file — không để inline trong code Python.

#### `repository.py` — Nhóm constants

**SCHEMA (DDL, idempotent):**
```python
from cypher_repo.repository import SCHEMA_CONSTRAINTS, SCHEMA_INDEXES, SCHEMA_VECTOR_INDEX

# SCHEMA_CONSTRAINTS: list[str] — 6 unique constraints
# SCHEMA_INDEXES: list[str]     — 7 property indexes (bao gồm subcategory)
# SCHEMA_VECTOR_INDEX: str      — 768-dim cosine vector index cho Article.embedding
```

**MERGE — node upserts:**
```python
MERGE_TECHNOLOGY   # params: name, category, subcategory, description, trend_score
MERGE_COMPANY      # params: name, field, size, location, rating
MERGE_JOB          # params: title, description, requirement, benefit, salary, due_date, source_url
MERGE_SKILL        # params: name, category, demand_score
MERGE_ARTICLE      # params: title, content, source, published_date, sentiment_score
MERGE_PERSON       # params: name, role
```

**REL — relationship creation (batch UNWIND):**
```python
REL_ARTICLE_MENTIONS_BATCH   # params: pairs [{article_title, node_name}]
REL_JOB_HIRES_FOR_BATCH      # params: pairs [{job_title, company_name}]
REL_JOB_REQUIRES_TECH_BATCH  # params: pairs [{job_title, tech_name}]
REL_JOB_REQUIRES_SKILL_BATCH # params: pairs [{job_title, skill_name}]
REL_COMPANY_USES_TECHNOLOGY  # derived, no params
REL_TECHNOLOGY_RELATED_TO    # derived, no params
REL_PERSON_WORKS_AT          # derived, no params
REL_PERSON_WROTE_ARTICLE     # derived, no params
```

**ANALYTICS:**
```python
ANALYTICS_TECH_MENTION_COUNTS        # params: cutoff_date (YYYY-MM-DD)
ANALYTICS_TECH_DEMAND_COUNTS         # no params
ANALYTICS_SKILL_DEMAND_COUNTS        # no params
ANALYTICS_UPDATE_TECH_TREND_SCORES   # params: scores [{name, score}]
ANALYTICS_UPDATE_TECH_DEMAND_SCORES  # params: scores [{name, score}]
ANALYTICS_UPDATE_SKILL_DEMAND_SCORES # params: scores [{name, score}]
```

> **Quy tắc:** Mọi Cypher query mới phải được thêm vào đây trước khi dùng. Không viết inline Cypher trong business logic.

---

### 4.4 analytics

Tính và ghi `trend_score` / `demand_score` vào Neo4j sau mỗi import cycle.

#### `trend_scorer.py`

```python
from analytics import compute_and_update_trend_scores

scores = compute_and_update_trend_scores(
    driver,
    database="neo4j",
    window_days=30,   # rolling window (mặc định 30 ngày)
    cutoff_date=None, # tự tính từ window_days, hoặc override cho testing
)
# → {"Python": 1.0, "React": 0.85, "Go": 0.52, ...}
```

**Công thức:**

```
score(t) = log(1 + mentions_30d(t)) / log(1 + max_mentions_30d)
```

- Nguồn: đếm `Article -[:MENTIONS]-> Technology` có `published_date >= cutoff`.
- Scale: `[0.0, 1.0]`. Tech được đề cập nhiều nhất = 1.0.
- Log scale: 100 mentions → 1.0 · 50 mentions → 0.85 · 10 mentions → 0.52 · 1 mention → 0.15 · 0 mentions → 0.0.

#### `demand_scorer.py`

```python
from analytics import compute_and_update_demand_scores

result = compute_and_update_demand_scores(driver, database="neo4j")
# → {
#     "technologies": {"Python": 1.0, "Java": 0.91, ...},
#     "skills":       {"REST API": 1.0, "Git": 0.78, ...}
#   }
```

**Công thức:**

```
score(t) = log(1 + job_count(t)) / log(1 + max_job_count)
```

- Nguồn: đếm `Job -[:REQUIRES]-> Technology/Skill`.
- Cập nhật cả `Technology.demand_score` và `Skill.demand_score`.

> Cả hai scorer log top-10 ở INFO level sau khi chạy xong.

---

### 4.5 crawl

Web crawlers thu thập dữ liệu từ 8 nguồn.

| File | Nguồn | Phương pháp |
|---|---|---|
| `VNExpress.py` | vnexpress.net | Selenium (headless Chrome) |
| `DanTri.py` | dantri.com.vn | Selenium |
| `GenK.py` | genk.vn | undetected-chromedriver |
| `ICTNews.py` | ictnews.vietnamnet.vn | Selenium |
| `TopCV.py` | topcv.vn | Selenium, scrape job postings |
| `ITviec.py` | itviec.com | Selenium |
| `Viblo.py` | viblo.asia | REST API (không cần Selenium) |
| `GitHub.py` | github.com | GitHub API |

Mỗi crawler kế thừa `base_crawler.py`, tự deduplicate URL, publish data lên Kafka và lưu JSON vào `data/raw/`.

---

### 4.6 services

#### `embedding_service/`

Kafka consumer pipeline:
- Subscribe: `extracted_articles`, `extracted_jobs`
- Model: `intfloat/multilingual-e5-base` (768 dimensions)
- Publish: `article_vectors`, `job_vectors`
- ID: MD5 hash của `source_url`

#### `qdrant_writer/`

Kafka consumer pipeline:
- Subscribe: `article_vectors`, `job_vectors`
- Upsert vào Qdrant: collections `articles`, `jobs`
- Similarity: cosine, 768-dim

> Qdrant là optional — vector search cũng chạy qua Neo4j vector index. Bật bằng `--profile vector` trong docker compose.

---

### 4.7 utils

#### `neo4j_config.py`

```python
from utils.neo4j_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE, BATCH_SIZE
```

#### `database_connection.py` — `Neo4jJobImporter`

```python
importer = Neo4jJobImporter(NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE)
importer.connect()
importer.create_constraints_and_indexes()

importer.import_technologies_list(techs)
importer.import_companies_list(companies)
importer.import_jobs_list(jobs)
importer.import_skills_list(skills)
importer.import_articles(articles)
importer.import_persons(persons)

# Derived relationships
importer.create_company_uses_technology_relationships()
importer.create_technology_related_to_relationships()
importer.create_person_works_at_relationships()
importer.create_person_wrote_article_relationships()

stats = importer.get_statistics()
# → {"Article": 1200, "Technology": 340, "Relationships": 8500, ...}

importer.disconnect()
```

#### `import_multi_source.py` — `RelationshipBuilder`

```python
from import_multi_source import RelationshipBuilder, find_latest_data_files

news_paths, topcv_path = find_latest_data_files(extracted_dir)

builder = RelationshipBuilder()
builder.run_import_pipeline(news_paths, topcv_path)
```

**Thứ tự xử lý trong `run_import_pipeline`:**

1. Import news JSON (VNExpress, GenK, DanTri, ICTNews, Viblo)
2. Import TopCV job postings
3. Kết nối Neo4j
4. Tạo constraints + indexes
5. Import nodes (Technology, Company, Job, Skill, Person, Article)
6. Tạo direct relationships (MENTIONS, HIRES_FOR, REQUIRES)
7. Tạo derived relationships (USES, RELATED_TO, WORKS_AT, WROTE)

Entity resolution và ontology được áp dụng tại bước 1–2:
- `resolve_tech_list()` trước khi tạo `TechNode`
- `resolve_company()` trước khi tạo `CompanyNode`
- `classify_tech()` xác định `category` + `subcategory`

---

## 5. Data Flow

### Import pipeline (batch, chạy định kỳ)

```
extracted_data/DD_MM_YYYY_VNExpress.json
extracted_data/DD_MM_YYYY_TopCV.json
        │
        ▼
RelationshipBuilder.import_news_data()
  └── resolve_tech_list(entities["TECH"])     ← entity_resolution
  └── resolve_company_list(entities["ORG"])   ← entity_resolution
  └── classify_tech(tech_name)                ← ontology
        │
        ▼
Neo4jJobImporter
  └── MERGE_TECHNOLOGY / MERGE_COMPANY / ...  ← cypher_repo
  └── REL_JOB_REQUIRES_TECH_BATCH / ...       ← cypher_repo
        │
        ▼
compute_and_update_trend_scores()             ← analytics
compute_and_update_demand_scores()            ← analytics
```

### RAG query pipeline (realtime)

```
User: "tìm việc Python k8s ở Hà Nội"
        │
        ▼
entity_extractor.py
  └── extracted: ["Python", "k8s"], loc=["Hà Nội"]
        │
        ▼
_normalize_tech_entities()    ← retriever_graph.py
  └── "k8s" → "Kubernetes"
        │
        ▼
graph_queries.py
  └── JOBS_BY_TECH, JOBS_BY_LOCATION, TECH_RELATED
        │
        ▼
Neo4j → jobs + related_tech + companies → LLM prompt
```

---

## 6. Cấu hình

### Environment variables (`.env`)

```env
NEO4J_URI=neo4j+s://xxxxxxxx.databases.neo4j.io
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=your-password
NEO4J_DATABASE=neo4j
BATCH_SIZE=100
```

### Docker

```bash
make docker-infra     # Kafka + Zookeeper
make docker-crawlers  # Tất cả crawlers
make docker-up        # Tất cả services
make docker-down      # Tắt
make logs             # Xem logs
```

---

## 7. Chạy pipeline

### Full pipeline (khuyến nghị)

```bash
cd knowledge-graph
python utils/run_complete_pipeline.py
```

Chạy theo thứ tự: import nodes → relationships → trend scores → demand scores → print stats.

### Từng bước

```bash
# Seed dữ liệu mẫu (không cần crawler)
cypher-shell -u neo4j -p password < scripts/seed_sample_graph.cypher

# Chỉ import
python utils/import_multi_source.py

# Chỉ analytics
python -c "
from neo4j import GraphDatabase
from utils.neo4j_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE
from analytics import compute_and_update_trend_scores, compute_and_update_demand_scores
driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))
compute_and_update_trend_scores(driver, NEO4J_DATABASE)
compute_and_update_demand_scores(driver, NEO4J_DATABASE)
driver.close()
"
```

### Verify sau import

```cypher
-- Node counts
MATCH (n) RETURN labels(n)[0] AS label, count(n) AS count ORDER BY count DESC;

-- Top trending technologies
MATCH (t:Technology)
WHERE t.trend_score > 0
RETURN t.name, t.category, t.subcategory, t.trend_score, t.demand_score
ORDER BY t.trend_score DESC LIMIT 20;

-- Jobs với required technologies
MATCH (j:Job)-[:REQUIRES]->(t:Technology)
RETURN j.title, collect(t.name)[..5] AS techs LIMIT 10;

-- Tech co-occurrence network
MATCH (t1:Technology)-[r:RELATED_TO]->(t2:Technology)
RETURN t1.name, t2.name, r.frequency ORDER BY r.frequency DESC LIMIT 20;
```

---

## 8. Mở rộng

### Thêm tech alias

Chỉnh sửa `entity_resolution/aliases.json`:
```json
{
  "tech_aliases": {
    "new-alias": "Canonical Name"
  }
}
```
Không cần sửa code. Áp dụng ngay khi restart process.

Nếu alias cũng xuất hiện trong user queries, thêm vào `_QUERY_TECH_ALIASES` trong `services/ai-rag-core/app/core/retriever_graph.py` (hai service chạy độc lập, không share file).

### Thêm category/subcategory

Chỉnh sửa `ontology/taxonomy.py`:
```python
TAXONOMY = {
    "NewCategory": {
        "NewSubcategory": ["keyword1", "keyword2"],
    }
}
```
Module tự rebuild reverse index khi restart.

### Thêm Cypher query

1. Thêm constant vào `cypher_repo/repository.py` với naming convention `SCHEMA_*`, `MERGE_*`, `REL_*`, `ANALYTICS_*`.
2. Import constant vào nơi sử dụng.
3. Không viết Cypher inline trong business logic.

### Thêm crawler

1. Tạo file mới trong `crawl/`, kế thừa `base_crawler.py`.
2. Override method `crawl()`.
3. Thêm vào `crawl/run_all.py`.
4. Thêm display name vào `SOURCE_DISPLAY_NAMES` trong `import_multi_source.py`.

### Điều chỉnh analytics window

```python
# Tăng window để trend_score phản ánh dài hạn hơn
compute_and_update_trend_scores(driver, database, window_days=90)

# Test với ngày cố định
from datetime import datetime
compute_and_update_trend_scores(driver, database, cutoff_date=datetime(2026, 1, 1))
```
