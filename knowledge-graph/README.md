# Knowledge Graph Module

Module này xây dựng **Knowledge Graph** trên Neo4j và **vector store** trên Qdrant từ dữ liệu
công nghệ / tuyển dụng đã crawl. Đây là nguồn dữ liệu trung tâm cho Trend Analytics, Graph Explorer,
Graph RAG và Technology Clustering.

## Cấu trúc

```text
knowledge-graph/
├── crawl/                       # Crawlers + producer Kafka
│   ├── base_crawler.py
│   ├── VNExpress.py · GenK.py · DanTri.py · TopCV.py
│   ├── kafka_producer.py
│   └── Dockerfile
├── services/
│   ├── embedding_service/       # Sinh embedding (768-dim), đẩy ra Kafka
│   │   └── embedding_service.py
│   └── qdrant_writer/           # Consume article_vectors/job_vectors → upsert Qdrant
│       └── qdrant_writer.py
├── utils/
│   ├── database_connection.py   # Tạo constraints + index (gồm vector index) trên Neo4j
│   ├── import_multi_source.py   # Import node + tạo relationship từ nhiều nguồn
│   ├── schema_define.py         # Định nghĩa schema node/relationship
│   ├── neo4j_config.py
│   └── run_complete_pipeline.py # Import toàn bộ + tạo relationship + in thống kê
├── scripts/
│   ├── seed_sample_graph.cypher # Seed đồ thị mẫu
│   ├── fix_json_files.py
│   └── move_url_files.py
├── Makefile
└── requirements.txt
```

## Luồng dữ liệu

```
crawl/*  ──► Kafka ──►  embedding_service  ──► Kafka(article_vectors / job_vectors)
                                                        │
                                                        ▼
   import_multi_source.py ──► Neo4j (nodes + rels)   qdrant_writer ──► Qdrant (768-dim, cosine)
```

- **Crawlers** thu thập bài viết / tin tuyển dụng, đẩy qua Kafka (`kafka_producer.py`).
- **embedding_service** sinh vector 768 chiều (e5-base) và phát ra topic `article_vectors` / `job_vectors`.
- **qdrant_writer** consume các topic đó, map `md5 → UUID`, upsert vào Qdrant.
- **import_multi_source.py** nạp dữ liệu vào Neo4j: tạo node và các relationship bên dưới.

## Schema đồ thị

**Nodes:** `Article`, `Technology`, `Company`, `Job`, `Skill`, `Person`

**Relationships** (tạo bởi `import_multi_source.py`):

| Relationship | Ý nghĩa |
| --- | --- |
| `(:Job)-[:HIRES_FOR]->(:Company)` | Vị trí tuyển dụng thuộc công ty nào |
| `(:Job)-[:REQUIRES]->(:Technology\|:Skill)` | Công việc yêu cầu công nghệ / kỹ năng |
| `(:Company)-[:USES]->(:Technology)` | Công ty sử dụng công nghệ |
| `(:Person)-[:WORKS_AT]->(:Company)` | Người làm việc tại công ty |
| `(:Person)-[:WROTE]->(:Article)` | Tác giả bài viết |
| `(:Article)-[:MENTIONS]->(:Technology\|:Company)` | Bài viết nhắc đến thực thể |
| `(:Technology)-[:RELATED_TO]->(:Technology)` | Quan hệ giữa các công nghệ |

## Chạy

```bash
# 1) Tạo constraints + index trên Neo4j (chạy một lần)
python -m knowledge-graph.utils.database_connection

# 2) Import toàn bộ + tạo relationship + in thống kê
python knowledge-graph/utils/run_complete_pipeline.py

# Seed đồ thị mẫu (tuỳ chọn) — chạy trong Neo4j Browser hoặc cypher-shell
cypher-shell -u neo4j -p password -f knowledge-graph/scripts/seed_sample_graph.cypher
```

Pipeline vector (Kafka + Qdrant + qdrant_writer) chạy qua compose ở thư mục gốc:

```bash
docker compose --profile vector up --build
```

> `Makefile` chứa các shortcut `docker-*`; hiện pipeline vector được hợp nhất vào
> `docker-compose.yml` ở repo root (xem [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md)).

## Kiểm tra nhanh

```cypher
// Thống kê node theo nhãn
MATCH (n) RETURN labels(n)[0] AS label, count(*) AS count ORDER BY count DESC;

// Thống kê relationship theo loại
MATCH ()-[r]->() RETURN type(r) AS rel_type, count(r) AS count ORDER BY count DESC;

// Công nghệ một công ty sử dụng
MATCH (c:Company)-[:USES]->(t:Technology) RETURN c.name, collect(t.name);
```
