# 📊 TechPulse VN - Database Module

## 🎯 Tổng Quan

**Database Module** là thành phần trung tâm của hệ thống TechPulse VN, chịu trách nhiệm thu thập, xử lý và lưu trữ dữ liệu thị trường việc làm công nghệ. Module sử dụng kiến trúc **microservices** với **event-driven** pattern, đảm bảo tính mở rộng và độ tin cậy cao.

### Chức Năng Chính

| Chức năng | Mô tả |
|-----------|-------|
| **Data Collection** | Thu thập tin tức công nghệ từ VNExpress, GenK, DanTri và việc làm từ TopCV |
| **Entity Extraction** | Trích xuất thực thể (công nghệ, công ty, vị trí, kỹ năng) từ dữ liệu thô |
| **Graph Storage** | Lưu trữ dữ liệu dạng đồ thị trong Neo4j |
| **Vector Storage** | Tạo và lưu trữ vector embeddings trong Qdrant |
| **Orchestration** | Điều phối và lập lịch pipeline với Apache Airflow |

## 📂 Cấu Trúc Thư Mục

```
src/database/
├── __init__.py                 # Package marker
├── docker-compose.yml          # Docker Compose configuration
├── go.mod / go.sum             # Go module dependencies
├── Makefile                    # Build automation
├── requirements.txt            # Python dependencies
├── README.md                   # Documentation (this file)
├── workflow.md                 # Workflow description
│
├── docs/                       # 📄 Documentation
│   ├── architecture.md         # Kiến trúc hệ thống
│   ├── crawlers.md             # Hệ thống crawler
│   ├── entity_extractor.md     # Entity extraction service
│   ├── graph_schema.md         # Neo4j graph schema
│   ├── kafka_topics.md         # Kafka topics configuration
│   └── deployment.md           # Deployment guide
│
├── crawl/                      # 🕷️ Crawler Services (Python)
│   ├── base_crawler.py         # Base crawler class
│   ├── kafka_producer.py       # Kafka producer utility
│   ├── VNExpress.py            # VNExpress crawler
│   ├── GenK.py                 # GenK crawler
│   ├── DanTri.py               # DanTri crawler
│   ├── TopCV.py                # TopCV crawler
│   ├── Dockerfile              # Crawler Docker image
│   └── requirements.txt        # Python dependencies
│
├── cmd/                        # 🚀 Entry Points (Go)
│   └── orchestrator/
│       └── main.go             # Main orchestrator entry point
│
├── internal/                   # 🔧 Internal Packages (Go)
│   ├── entity_extractor/       # Entity extraction logic
│   │   └── extractor.go
│   ├── kafka/                  # Kafka utilities
│   │   └── producer.go
│   ├── neo4j_writer/           # Neo4j writer service
│   │   └── writer.go
│   └── qdrant_writer/          # Qdrant writer service
│       └── writer.go
│
├── orchestrator/               # 🎼 Airflow DAGs
│   ├── Dockerfile
│   ├── entrypoint.sh
│   └── dags/
│       └── scraper_dag.py      # Daily scraping DAG
│
├── pkg/                        # 📦 Public Packages (Go)
│   ├── config/
│   │   └── config.go           # Configuration loader
│   └── models/
│       └── article.go          # Data models
│
├── services/                   # 🛠️ Microservices
│   └── embedding_service/      # Embedding service (Python)
│       ├── Dockerfile
│       ├── embedding_service.py
│       └── requirements.txt
│
├── utils/                      # 🔨 Utilities (Python)
│   ├── schema_define.py        # Graph schema definitions
│   ├── data_transform.py       # Data transformation
│   ├── database_connection.py  # Neo4j connection
│   ├── neo4j_config.py         # Neo4j configuration
│   ├── create_relationships.py # Create relationships
│   ├── import_multi_source.py  # Multi-source import
│   └── run_complete_pipeline.py # Pipeline orchestrator
│
├── scripts/                    # 📜 Utility Scripts
│   ├── fix_json_files.py
│   └── move_url_files.py
│
├── docker/                     # 🐳 Docker Configs
│   └── Dockerfile.orchestrator
│
├── assets/                     # 🖼️ Static Assets
├── bin/                        # ⚙️ Compiled Binaries
│   └── orchestrator
│
└── data/                       # 💾 Data Storage
    ├── raw/                    # Raw crawled data
    │   ├── dantri/
    │   ├── genk/
    │   ├── topcv/
    │   └── vnexpress/
    └── processed/              # Processed data
        └── sample.json
```

## 🏗️ Kiến Trúc Hệ Thống

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TECHPULSE VN - DATABASE MODULE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                        DATA COLLECTION                               │   │
│   │                                                                      │   │
│   │   VNExpress ──┐                                                      │   │
│   │   GenK ───────┼──▶ Kafka ──▶ Entity Extractor ──▶ Neo4j + Qdrant   │   │
│   │   DanTri ─────┤        │              │                              │   │
│   │   TopCV ──────┘        │              ▼                              │   │
│   │                         │     Embedding Service                      │   │
│   └─────────────────────────┴────────────────────────────────────────────┘   │
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                        ORCHESTRATION                                 │   │
│   │                                                                      │   │
│   │   Apache Airflow ──▶ DAG: techpulse_daily_pipeline                  │   │
│   │   Schedule: 22:00 VN Time (15:00 UTC)                               │   │
│   │   Timeout: 2 hours                                                   │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

👉 **Chi tiết kiến trúc:** [docs/architecture.md](./docs/architecture.md)

## 🔧 Tech Stack

| Thành phần | Công nghệ | Vai trò |
|------------|-----------|---------|
| **Orchestrator** | Apache Airflow 2.8.0 | Điều phối và lập lịch |
| **Message Broker** | Apache Kafka 7.5.0 | Xử lý luồng dữ liệu |
| **Graph Database** | Neo4j Aura (Cloud) | Lưu trữ đồ thị |
| **Vector Database** | Qdrant Cloud (Free) | Lưu trữ vector embeddings |
| **NLP Engine** | PhoBERT/ELECTRA (Python) | Trích xuất thực thể |
| **Embedding Model** | SentenceTransformers | Tạo vector (768 dims) |
| **Languages** | Go 1.21+, Python 3.10 | Implementation |

> **Lưu ý:** Neo4j Aura và Qdrant Cloud là managed cloud services. Bạn cần tạo tài khoản và cấu hình connection trong file `.env`.

## 🚀 Quick Start

### 1. Prerequisites

```bash
# Check Docker
docker --version
docker-compose --version

# Clone repository
git clone https://github.com/your-repo/Final_Data_Mining.git
cd Final_Data_Mining/src/database
```

### 2. Cloud Services Setup

#### Neo4j Aura (Free Tier)

1. Đăng ký tại https://neo4j.com/cloud/aura/
2. Tạo instance mới (Free tier available)
3. Lấy connection details:
   ```bash
   NEO4J_URI=neo4j+s://xxx.databases.neo4j.io
   NEO4J_USERNAME=neo4j
   NEO4J_PASSWORD=your_password
   ```

#### Qdrant Cloud (Free Tier)

1. Đăng ký tại https://cloud.qdrant.io/
2. Tạo cluster mới (Free tier: 1GB storage)
3. Lấy connection details:
   ```bash
   QDRANT_URL=https://xxx.qdrant.io
   QDRANT_API_KEY=your_api_key
   ```

### 3. Configuration

```bash
# Create .env file
cp ../../.env.example ../../.env

# Edit .env with your configurations
# Required: NEO4J_URI, NEO4J_PASSWORD, QDRANT_URL, QDRANT_API_KEY
```

### 3. Start Services

```bash
# Build and start all services
docker-compose up -d

# Check status
docker-compose ps
```

### 4. Verify

```bash
# Airflow UI
open http://localhost:8080

# Kafka topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9094
```

👉 **Chi tiết deployment:** [docs/deployment.md](./docs/deployment.md)

## 🚀 Kích Hoạt Pipeline

### Cách 1: Qua Airflow UI

Truy cập `http://localhost:8080` → DAGs → `techpulse_daily_pipeline` → Trigger DAG

### Cách 2: Qua CLI (không cần UI)

```bash
# Kích hoạt qua script Python (API)
cd scripts
python trigger_pipeline.py

# Hoặc qua CLI trong container
python trigger_pipeline.py --cli

# Kiểm tra trạng thái DAG
python trigger_pipeline.py --status

# Xem các lần chạy gần đây
python trigger_pipeline.py --runs
```

### Cách 3: Chạy trực tiếp không qua Airflow

```bash
# Script bash chạy pipeline trực tiếp
cd scripts
chmod +x run_pipeline.sh
./run_pipeline.sh

# Với options
./run_pipeline.sh --skip-crawl      # Bỏ qua crawl, chỉ extract và write
./run_pipeline.sh --timeout 3600    # Timeout 1 giờ
./run_pipeline.sh --help            # Xem tất cả options
```

### Cách 4: Docker commands thủ công

```bash
# 1. Crawl dữ liệu
docker exec crawler-vnexpress python /app/VNExpress.py
docker exec crawler-genk python /app/GenK.py
docker exec crawler-dantri python /app/DanTri.py

# 2. Entity extraction
docker exec entity-extractor python entity_extractor_service.py

# 3. Graph write to Neo4j
docker exec graph-writer /app/orchestrator
```

## 📊 Data Pipeline

### Luồng Dữ Liệu

```
1. CRAWL (Python)
   └─▶ VNExpress, GenK, DanTri, TopCV
       └─▶ Kafka: raw_articles, raw_jobs

2. EXTRACT (Python)
   └─▶ Entity Extractor (PhoBERT/ELECTRA + Rule-based)
       └─▶ PER, ORG, LOC (NER) + TECH, DATE, JOB_ROLE, SALARY (Rule-based)
           └─▶ Kafka: extracted_articles, extracted_jobs

3. EMBED (Python)
   └─▶ Embedding Service (multilingual-e5-base)
       └─▶ 768-dimensional vectors
           └─▶ Kafka: article_vectors, job_vectors

4. STORE (Go)
   └─▶ Neo4j Writer ─▶ Graph Database
   └─▶ Qdrant Writer ─▶ Vector Database
```

### Kafka Topics

| Topic | Producer | Consumer |
|-------|----------|----------|
| `raw_articles` | Crawlers | Entity Extractor |
| `raw_jobs` | Crawlers | Entity Extractor |
| `extracted_articles` | Entity Extractor | Neo4j, Embedding |
| `extracted_jobs` | Entity Extractor | Neo4j, Embedding |
| `article_vectors` | Embedding | Qdrant |
| `job_vectors` | Embedding | Qdrant |

👉 **Chi tiết Kafka:** [docs/kafka_topics.md](./docs/kafka_topics.md)

## 🗄️ Graph Schema

### Node Types

| Node | Properties | Ví dụ |
|------|-----------|-------|
| **Article** | id, title, content, url, source_platform | Bài viết từ VNExpress |
| **Technology** | name, mention_count | Python, AI, Docker |
| **Company** | id, name, location | FPT, VNG, OpenAI |
| **Job** | id, name, salary, url | Senior AI Engineer |
| **Skill** | name, mention_count | Problem Solving |
| **Location** | name | Hà Nội, TP.HCM |

### Relationships

```
Article ──MENTIONS──▶ Technology
Article ──MENTIONS──▶ Company
Article ──MENTIONS──▶ Location
Job ──POSTED_BY──▶ Company
Job ──REQUIRES──▶ Technology
Job ──REQUIRES──▶ Skill
```

👉 **Chi tiết Schema:** [docs/graph_schema.md](./docs/graph_schema.md)

## 📖 Documentation

| File | Nội dung |
|------|----------|
| [architecture.md](./docs/architecture.md) | Kiến trúc hệ thống, tech stack, data flow |
| [crawlers.md](./docs/crawlers.md) | Hệ thống crawler, message formats |
| [entity_extractor.md](./docs/entity_extractor.md) | Entity extraction, NER patterns |
| [graph_schema.md](./docs/graph_schema.md) | Neo4j schema, Cypher queries |
| [kafka_topics.md](./docs/kafka_topics.md) | Kafka topics, consumer groups |
| [deployment.md](./docs/deployment.md) | Deployment guide, troubleshooting |

## 🧪 Testing

```bash
# Run all tests
pytest tests/test_database/

# Run specific test
pytest tests/test_database/test_connection.py -v

# Run with coverage
pytest tests/test_database/ --cov=src/database
```

## 📝 Makefile Commands

```bash
make build      # Build all Docker images
make up         # Start all services
make down       # Stop all services
make logs       # View logs
make reset      # Full reset
make test       # Run tests
```

## 🔍 Monitoring & Debugging

### Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f kafka
docker-compose logs -f orchestrator
```

### Health Checks

```bash
# Airflow
curl http://localhost:8080/health

# Kafka
docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9094

# Neo4j
docker exec neo4j cypher-shell -u neo4j -p password "RETURN 1;"
```

## 📋 API Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| Airflow UI | `http://localhost:8080` | DAG management |
| Neo4j Browser | `http://localhost:7474` | Graph query UI |
| Qdrant Dashboard | `http://localhost:6333/dashboard` | Vector search UI |

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](../../LICENSE) file for details.

---

**Created:** March 2026  
**Version:** 2.0  
**Last Updated:** May 2026

---

## 🖼️ Hình Ảnh Minh Họa

> **Ghi chú:** Thêm các ảnh minh họa sau vào thư mục `docs/images/`

| Ảnh | Mô tả |
|-----|-------|
| `architecture_overview.png` | Kiến trúc tổng quan hệ thống |
| `data_flow.png` | Sơ đồ luồng dữ liệu |
| `crawler_workflow.png` | Quy trình hoạt động crawler |
| `entity_extraction_flow.png` | Quy trình trích xuất thực thể |
| `graph_visualization.png` | Minh họa đồ thị Neo4j |
| `kafka_topics.png` | Tổng quan Kafka topics |
| `deployment_diagram.png` | Sơ đồ triển khai |

```markdown
<!-- Example usage -->
![Architecture Overview](./docs/images/architecture_overview.png)
*Hình 1: Kiến trúc tổng quan hệ thống TechPulse VN Database Module*