# TechRadar VN

<div align="center">

  ![Java](https://img.shields.io/badge/Java-21-orange)
  ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-green)
  ![WebFlux](https://img.shields.io/badge/WebFlux-Reactive-success)
  ![React](https://img.shields.io/badge/React-19-blue)
  ![Neo4j](https://img.shields.io/badge/Neo4j-Knowledge_Graph-brightgreen)
  ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
  ![FastAPI](https://img.shields.io/badge/FastAPI-AI-teal)
  ![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED)
  ![License](https://img.shields.io/badge/License-MIT-green)

  **Technology Trend Analytics Platform powered by Knowledge Graph, Graph RAG and Machine Learning**

  [Documentation](docs/README.md) • [API Docs](docs/API_DOCs_v1.md) • [Architecture](docs/ARCHITECTURE.md) • [Deployment](docs/DEPLOYMENT.md)

</div>

---

## 📖 Giới thiệu

**TechRadar VN** là nền tảng phân tích xu hướng công nghệ và thị trường tuyển dụng IT tại Việt Nam, sử dụng kết hợp **Knowledge Graph**, **Graph RAG** và **Machine Learning** để cung cấp insights thực tế cho developers, recruiters và decision-makers.

### 🎯 Vấn đề giải quyết

- **Developers**: Không biết công nghệ nào đang hot, nên học gì để tăng cơ hội việc làm
- **Recruiters**: Khó xác định kỹ năng cần thiết, mức lương thị trường
- **Decision-makers**: Thiếu dữ liệu để ra quyết định về training, hiring, technology adoption

### 💡 Giải pháp

TechRadar VN thu thập dữ liệu từ các nguồn tin công nghệ và tuyển dụng IT tại Việt Nam, sử dụng NLP để trích xuất thực thể, xây dựng Knowledge Graph trên Neo4j, sau đó cung cấp:

- **Trend Analytics**: Theo dõi xu hướng công nghệ theo thời gian
- **Knowledge Graph Explorer**: Khám phá mối liên hệ giữa công nghệ, kỹ năng, doanh nghiệp
- **Graph RAG Chatbot**: Hỏi đáp trên dữ liệu thực tế với nguồn tham chiếu
- **Technology Clustering**: Phân cụm công nghệ tương đồng
- **Career Assistant**: Định hướng học tập và nghề nghiệp

### 🌟 Điểm khác biệt

- **Dữ liệu thực tế**: Thu thập từ các nguồn Việt Nam (VNExpress, GenK, TopCV, ITviec, v.v.)
- **Knowledge Graph**: Mối quan hệ sâu giữa công nghệ, kỹ năng, doanh nghiệp, việc làm
- **Graph RAG**: Hỏi đáp chính xác hơn với context từ graph
- **Real-time**: Cập nhật dữ liệu liên tục từ crawlers
- **Vietnam-focused**: Tối ưu cho thị trường Việt Nam

---

## ✨ Tính năng chính

### � Trend Radar Dashboard

Theo dõi xu hướng công nghệ theo thời gian với dashboard trực quan.

- **Top Technologies**: Xem top công nghệ theo mức tăng trưởng YoY, MoM
- **Job Analytics**: Thống kê số lượng việc làm theo công nghệ
- **Growth Metrics**: Tỷ lệ tăng trưởng, số bài viết, số việc làm
- **Export**: Xuất dữ liệu dưới dạng PNG, CSV
- **Time Series**: Xem xu hướng theo thời gian (6 tháng, 12 tháng)

**Use Case**: Developer muốn biết React có đang tăng trưởng không, có bao nhiêu việc làm React hiện tại.

---

### 🕸 Knowledge Graph Explorer

Trực quan hóa và khám phá đồ thị tri thức với force-directed graph.

- **Interactive Graph**: Zoom, pan, drag nodes, filter edges
- **Entity Types**: Technology, Company, Job, Skill, Article, Location
- **Relationships**: MENTIONS, REQUIRES, USES, RELATED_TO, POSTED_BY
- **Graph Traversal**: Tìm đường đi ngắn nhất giữa 2 công nghệ
- **Advanced Filtering**: Lọc theo location, salary, sentiment, node types
- **Node Details**: Xem chi tiết thông tin từng node

**Use Case**: Developer muốn biết React có liên quan đến những công nghệ nào, những công ty nào đang dùng React.

---

### 🤖 Graph RAG Chatbot

Chatbot hỏi đáp thông minh trên dữ liệu thực tế với Graph RAG.

- **Multi-Source Retrieval**: Kết hợp vector search, graph traversal, SQL analytics, user context
- **Reranking**: BGE reranker để cải thiện relevance
- **Streaming Response**: Real-time streaming cho trải nghiệm tốt hơn
- **Source Citations**: Trả lời có nguồn tham chiếu (articles, jobs)
- **Conversation Memory**: Lưu lịch sử hội thoại theo session
- **Entity Extraction**: Tự động trích xuất entities từ câu hỏi

**Use Case**: Developer hỏi "React developer ở Việt Nam lương bao nhiêu?", chatbot trả lời với số liệu từ jobs và articles.

---

### 🧠 Technology Clustering

Phân cụm công nghệ tương đồng bằng Machine Learning.

- **HDBSCAN Clustering**: Density-based clustering cho các cluster có kích thước khác nhau
- **Feature Engineering**: Alias normalization, name embedding, graph features, job TF-IDF
- **LLM Labeling**: Tự động đặt tên cluster bằng LLM
- **Coherent Clusters**: Chỉ hiển thị clusters chất lượng cao
- **Batch Prediction**: Dự đoán cluster cho nhiều công nghệ cùng lúc
- **Visualization**: Visualize clusters trên 2D space

**Use Case**: Developer muốn biết React thuộc nhóm nào, có những công nghệ nào tương tự.

---

### ⚖️ Technology Comparison

So sánh chi tiết giữa các công nghệ.

- **Side-by-Side Comparison**: So sánh growth rate, job count, article count
- **Time Series Comparison**: So sánh xu hướng theo thời gian
- **LLM Summary**: Tóm tắt so sánh bằng LLM
- **Similarity Score**: Điểm tương đồng giữa các công nghệ

**Use Case**: Developer đang phân vân giữa React và Vue, muốn so sánh chi tiết.

---

### 🚀 Career Assistant

Định hướng học tập và nghề nghiệp cá nhân hóa.

- **Skill Gap Analysis**: Phân tích thiếu hụt kỹ năng
- **Learning Path**: Đề xuất lộ trình học tập
- **Job Matching**: Gợi ý việc làm phù hợp
- **Salary Insights**: Thông tin lương theo kỹ năng, location
- **Career Roadmap**: Lộ trình phát triển nghề nghiệp

**Use Case**: Developer muốn biết từ Junior Frontend nên học gì để trở thành Senior Full-stack.

---

### 👤 User Management

Quản lý tài khoản và hồ sơ cá nhân.

- **Authentication**: JWT với refresh token rotation
- **Profile Management**: Quản lý thông tin cá nhân, avatar
- **Technology Preferences**: Đăng ký công nghệ quan tâm
- **Notification Settings**: Bật/tắt thông báo in-app và email
- **Subscription Tiers**: FREE, PRO, ENTERPRISE với các tính năng khác nhau

---

### 🔔 Notifications

Hệ thống thông báo realtime.

- **Trend Alerts**: Thông báo khi công nghệ quan tâm tăng trưởng vượt threshold
- **System Notifications**: Thông báo hệ thống (maintenance, new features)
- **Real-time Streaming**: SSE để push notification realtime
- **Multi-channel**: In-app + Email
- **Notification Center**: Quản lý và đánh dấu đã đọc

---

## 🏗 Kiến trúc hệ thống

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  React Web   │  │ Expo Mobile  │  │  Admin UI    │  │  Public API  │     │
│  │  (Vite)      │  │  (React Nat) │  │  (React)     │  │  (Swagger)   │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                 │                 │                 │             │
└─────────┼─────────────────┼─────────────────┼─────────────────┼─────────────┘
          │                 │                 │                 │
          └─────────────────┴─────────────────┴─────────────────┘
                            │
                    ┌───────▼────────┐
                    │  Nginx Proxy  │
                    │  (Reverse)    │
                    └───────┬────────┘
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
┌─────────▼─────────┐ ┌────▼────┐ ┌────────▼────────┐
│  Spring Boot API  │ │  Redis  │ │  MailHog (dev)  │
│  Gateway          │ │ (Cache) │ │  (SMTP)         │
│  (WebFlux)        │ └─────────┘ └─────────────────┘
│  /api/v1/*        │
└─────────┬─────────┘
          │
    ┌─────┼─────┬──────────────┬──────────────┐
    │     │     │              │              │
┌───▼───┐ │ ┌───▼────┐  ┌─────▼─────┐  ┌─────▼─────┐
│PostgreSQL│ │ │ Neo4j  │  │ai-rag-core│  │ml-clustering│
│(R2DBC)   │ │ │ (Graph)│  │(FastAPI)  │  │(FastAPI)   │
│- users   │ │ │        │  │:8000      │  │:8001       │
│- chat    │ │ │        │  │- RAG chat │  │- HDBSCAN   │
│- analytics│ │ │        │  │- Recommend│  │- Cluster   │
│- CMS     │ │ │        │  │- Forecast │  │  serving   │
└─────────┘ │ └────────┘  └─────┬─────┘  └─────┬─────┘
            │                   │               │
            └───────────────────┴───────────────┘
                            │
                    ┌───────▼────────┐
                    │     Kafka      │
                    │  (Event Bus)   │
                    └───────┬────────┘
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
┌─────────▼─────────┐ ┌───▼────┐ ┌────────▼────────┐
│  Data Platform     │ │Crawlers│ │  Qdrant (opt)   │
│  - Bronze Writer   │ │(8 sources)│  (Vector Store) │
│  - Silver Processor│ │        │ └─────────────────┘
│  - Gold ETL        │ │        │
│  - Scheduler       │ │        │
└─────────┬─────────┘ └────────┘
          │
    ┌─────▼─────┐
    │  MinIO    │
    │ (S3-like) │
    └───────────┘
```

### Backend Architecture

Backend được xây dựng với **Spring Boot 3.4** theo mô hình **Hexagonal Architecture (Ports & Adapters)** kết hợp **Feature-Based Modular Architecture**.

**Design Principles:**
- Hexagonal Architecture: Domain logic độc lập với infrastructure
- Dependency Inversion: High-level modules không phụ thuộc low-level modules
- Domain-Driven Design: Bounded contexts theo feature
- Feature-Based Modularization: Mỗi feature là một module độc lập
- Reactive Programming: WebFlux + R2DBC cho non-blocking I/O

**Feature Modules:**
- `auth`: Authentication & Authorization
- `radar`: Trend Analytics
- `compare`: Technology Comparison
- `graph`: Knowledge Graph Explorer
- `chat`: RAG Chatbot
- `clustering`: Technology Clustering
- `user`: User Management
- `system`: System Settings
- `health`: Health Checks
- `kafka`: Event Handling

---

## 📊 Data Pipeline

### Data Sources

**Article Sources:**
- VNExpress
- GenK
- Dân Trí
- ICTNews
- Viblo

**Job Sources:**
- TopCV
- ITviec
- GitHub (optional)

### Pipeline Stages

```
┌─────────────┐
│   Crawlers  │ ──► Kafka (raw_articles, raw_jobs)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│Bronze Writer│ ──► MinIO (immutable raw data)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│Silver       │ ──► PostgreSQL (dp_processed_*)
│Processor    │     - Deduplication
│             │     - Quality scoring
└──────┬──────┘
       │
       ▼
┌─────────────┐
│Knowledge    │ ──► Neo4j (Knowledge Graph)
│Graph Import │     - Nodes: Technology, Company, Job, Skill
│             │     - Relationships: MENTIONS, REQUIRES, USES
└──────┬──────┘
       │
       ▼
┌─────────────┐
│Gold ETL     │ ──► PostgreSQL (tech_analytics)
│             │     - Aggregated analytics
│             │     - Trend metrics
└─────────────┘
```

### NLP Processing

- **IT Content Classification**: PhoBERT để phân loại nội dung IT
- **Entity Extraction**: ELECTRA NER để trích xuất entities
- **Embedding**: Sentence Transformers (E5-base) cho vector search
- **Reranking**: BGE reranker để cải thiện relevance

---

## 🗄 Knowledge Graph

### Node Types

- **Technology**: Tên công nghệ, category, trend score, demand score
- **Company**: Tên công ty, field, size, location, rating
- **Job**: Title, description, requirement, benefit, salary
- **Skill**: Tên skill, category, demand score
- **Article**: Title, content, source, published_date, sentiment
- **Location**: Tên địa điểm
- **Person**: Tên người (author, etc.)

### Relationship Types

- **MENTIONS**: Article → Technology/Company/Person
- **REQUIRES**: Job → Technology/Skill
- **HIRES_FOR**: Job → Company
- **USES**: Company → Technology (derived)
- **RELATED_TO**: Technology → Technology (derived)
- **WORKS_AT**: Person → Company (derived)
- **WROTE**: Person → Article (derived)

### Graph Analytics

- Trend scoring: Tính điểm xu hướng dựa trên article count, job count
- Demand scoring: Tính điểm nhu cầu dựa trên job count, salary
- Path analysis: Tìm đường đi ngắn nhất giữa công nghệ
- Community detection: Phát hiện cộng đồng công nghệ

---

## 🛠 Tech Stack

### Frontend

- **Framework**: React 19
- **Build Tool**: Vite 7
- **Routing**: React Router DOM 7
- **Charts**: Recharts 3
- **Graph Visualization**: D3.js 7, react-force-graph-2d
- **HTTP**: Fetch API
- **Testing**: Vitest, Testing Library

### Backend

- **Language**: Java 21
- **Framework**: Spring Boot 3.4
- **Reactive**: Spring WebFlux
- **Security**: Spring Security, JWT (jjwt 0.12.5)
- **Database Access**: R2DBC (PostgreSQL), Neo4j Java Driver
- **Validation**: Spring Boot Validation
- **API Docs**: Springdoc OpenAPI 3
- **Migration**: Flyway
- **Caching**: Spring Data Redis Reactive
- **Messaging**: Spring Kafka
- **Email**: Spring Boot Mail
- **Resilience**: Resilience4j
- **Testing**: Testcontainers, WireMock

### Databases

- **PostgreSQL 16**: Users, chat, analytics, CMS
- **Neo4j 5**: Knowledge Graph
- **Redis 7**: Cache, token blacklist, rate limiting
- **Qdrant** (optional): Vector store for RAG

### AI & NLP

- **Framework**: FastAPI
- **Embeddings**: Sentence Transformers (E5-base)
- **Reranking**: BGE reranker
- **NER**: ELECTRA NER
- **Classification**: PhoBERT
- **LLM**: OpenAI, Gemini
- **Vector DB**: Qdrant (optional)

### Machine Learning

- **Clustering**: HDBSCAN, DBSCAN, K-Means
- **Feature Engineering**: Scikit-Learn
- **Experiment Tracking**: MLflow
- **Pipeline Management**: DVC
- **Visualization**: Matplotlib, Seaborn

### DevOps

- **Containerization**: Docker, Docker Compose
- **CI/CD**: GitHub Actions
- **Testing**: Testcontainers
- **Monitoring**: Prometheus, Grafana (optional)
- **Logging**: Logback + Logstash (JSON for prod)

---

## 📁 Cấu trúc dự án

```
TECH-RADAR/
├── apps/
│   ├── backend/              # Spring Boot WebFlux API Gateway
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── java/com/techpulse/
│   │   │       │   ├── features/       # Feature modules
│   │   │       │   │   ├── auth/
│   │   │       │   │   ├── radar/
│   │   │       │   │   ├── compare/
│   │   │       │   │   ├── graph/
│   │   │       │   │   ├── chat/
│   │   │       │   │   ├── clustering/
│   │   │       │   │   ├── user/
│   │   │       │   │   ├── system/
│   │   │       │   │   ├── health/
│   │   │       │   │   └── kafka/
│   │   │       │   └── shared/         # Shared infrastructure
│   │   │       └── resources/
│   │   │           ├── application.yml
│   │   │           ├── db/migrations/   # Flyway migrations
│   │   │           └── logback-spring.xml
│   │   └── pom.xml
│   │
│   ├── web/                  # React 19 + Vite SPA
│   │   ├── src/
│   │   │   ├── api/             # API client layer
│   │   │   ├── components/      # Reusable components
│   │   │   ├── contexts/        # React contexts
│   │   │   ├── pages/           # Page components
│   │   │   ├── layouts/         # Page layouts
│   │   │   └── utils/           # Utility functions
│   │   └── package.json
│   │
│   └── mobile/               # Expo / React Native app (future)
│
├── services/
│   ├── ai-rag-core/          # FastAPI — Graph RAG chat (port 8000)
│   │   ├── app/
│   │   │   ├── api/           # API routes
│   │   │   ├── core/          # RAG pipeline
│   │   │   ├── services/       # Business logic
│   │   │   ├── agent/         # LangChain Agent
│   │   │   ├── memory/        # Conversation memory
│   │   │   └── db/            # Database clients
│   │   ├── requirements.txt
│   │   └── Dockerfile
│   │
│   └── ml-clustering/        # FastAPI — Technology clustering (port 8001)
│       ├── app/
│       │   ├── api/           # API routes
│       │   ├── pipelines/     # ML pipelines
│       │   └── src/           # ML code
│       ├── dvc.yaml           # DVC pipeline
│       ├── params.yaml         # Hyperparameters
│       ├── requirements.txt
│       └── Dockerfile
│
├── knowledge-graph/          # Knowledge Graph subsystem
│   ├── crawl/                # Web crawlers
│   │   ├── VNExpress.py
│   │   ├── GenK.py
│   │   ├── DanTri.py
│   │   ├── ICTNews.py
│   │   ├── TopCV.py
│   │   ├── ITviec.py
│   │   ├── Viblo.py
│   │   └── GitHub.py
│   ├── entity_resolution/    # Alias normalization
│   ├── ontology/             # Taxonomy classification
│   ├── cypher_repo/          # Cypher query constants
│   ├── analytics/            # Score computation
│   ├── services/             # Embedding, Qdrant writer
│   ├── utils/                # Neo4j config, schema
│   └── scripts/              # Seed, data-fix helpers
│
├── data-platform/           # Data Platform (Bronze/Silver/Gold)
│   ├── bronze/               # Kafka → MinIO writer
│   ├── silver/               # Kafka → PostgreSQL processor
│   ├── gold/                 # Neo4j → PostgreSQL ETL
│   ├── scheduler/            # APScheduler jobs
│   └── common/               # Shared utilities
│
├── docs/                     # Documentation
│   ├── README.md             # Documentation index
│   ├── ARCHITECTURE.md       # Architecture overview
│   ├── BACKEND_GUIDE.md      # Backend development guide
│   ├── FRONTEND_GUIDE.md     # Frontend development guide
│   ├── DEVELOPMENT_GUIDE.md  # Development guide
│   ├── AI_PLATFORM.md        # AI services documentation
│   ├── API_DOCs_v1.md       # API documentation
│   └── DEPLOYMENT.md         # Deployment guide
│
├── tests/                    # Cross-service tests
│
├── docker-compose.yml        # Full stack orchestration
├── .env.docker.example       # Environment variables template
└── README.md
```

---

## 🚀 Bắt đầu

### Yêu cầu hệ thống

**Docker Compose (khuyến nghị):**
- Docker Engine 20.10+
- Docker Compose 2.0+
- RAM tối thiểu: 8 GB (khuyến nghị 16 GB)
- Disk: 20 GB free space

**Development (chạy từng service):**
- Java 21+ (OpenJDK hoặc Oracle JDK)
- Node.js 20+ (npm 10+)
- Python 3.11+
- PostgreSQL 16+
- Neo4j 5.x
- Redis 7+

### Quick Start với Docker Compose

Cách nhanh nhất để chạy toàn bộ hệ thống:

```bash
# 1. Clone repository
git clone https://github.com/your-org/tech-radar.git
cd tech-radar

# 2. Điền environment variables
cp .env.docker.example .env
# Edit .env và điền OPENAI_API_KEY hoặc GEMINI_API_KEY cho chatbot

# 3. Khởi động toàn bộ hệ thống
docker compose up --build

# 4. Truy ứng dụng
# Web: http://localhost:5173
# API: http://localhost:8080/api/v1
# Swagger: http://localhost:8080/swagger-ui.html
# Neo4j Browser: http://localhost:7474
# MailHog: http://localhost:8025
```

**Docker Compose Profiles:**

```bash
# App stack (khuyến nghị cho development)
docker compose up --build

# Full stack với data pipeline (cần nhiều resources hơn)
docker compose --profile vector up --build

# Chỉ database services
docker compose up postgres neo4j redis qdrant
```

**Tài khoản dev mặc định:**
- Email: `admin@techradar.vn`
- Password: `Admin@12345`

### Development Mode

Chạy từng service khi phát triển:

```bash
# Terminal 1: PostgreSQL + Neo4j + Redis
docker compose up postgres neo4j redis

# Terminal 2: Spring Boot API Gateway
cd apps/backend
mvn spring-boot:run

# Terminal 3: Frontend
cd apps/web
npm install
npm run dev

# Terminal 4: ai-rag-core (RAG service)
cd services/ai-rag-core
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000

# Terminal 5: ml-clustering (Clustering service)
cd services/ml-clustering
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

### Environment Variables

Biến môi trường quan trọng trong `.env`:

```bash
# Application
APP_ENV=dev
JWT_SECRET=your-secret-key-change-in-production
INTERNAL_API_TOKEN=your-internal-token-for-python-services

# LLM Provider (chọn một)
LLM_PROVIDER=openai  # hoặc gemini
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=...

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000

# ML Clustering
ML_CLUSTERING_S3_BUCKET=techradar-clustering

# Data Platform
EMBED_SECRET=your-embed-secret

# Crawlers
CRAWLER_INTERVAL_HOURS=6
GITHUB_TOKEN=ghp_...  # cho GitHub crawler
```

Xem `.env.docker.example` cho đầy đủ các biến.

---

## 🧪 Kiểm thử

### Backend Tests (Spring Boot)

```bash
cd apps/backend

# Chạy tất cả tests
mvn test

# Chạy tests với coverage
mvn test jacoco:report

# Chạy tests cho một module cụ thể
mvn test -Dtest=AuthControllerTest

# Chạy integration tests
mvn verify -Pintegration-test
```

**Test Coverage:**
- Unit tests cho business logic
- Integration tests với Testcontainers
- WebFlux controller tests
- Repository tests với embedded databases

### Frontend Tests (React + Vitest)

```bash
cd apps/web

# Chạy tất cả tests
npm test

# Chạy tests với coverage
npm test -- --coverage

# Chạy tests trong watch mode
npm test -- --watch

# Chạy E2E tests (nếu có)
npm run test:e2e
```

### Python Services Tests

```bash
cd services/ai-rag-core
pytest
pytest --cov=app tests/

cd services/ml-clustering
pytest
pytest --cov=app tests/
```

### Cross-Service Tests

```bash
cd tests
pytest
```

---

## 📚 Tài liệu

- **[Documentation Index](docs/README.md)** - Mục lục tài liệu đầy đủ
- **[Architecture](docs/ARCHITECTURE.md)** - Kiến trúc hệ thống chi tiết
- **[Backend Guide](docs/BACKEND_GUIDE.md)** - Hướng dẫn phát triển backend
- **[Frontend Guide](docs/FRONTEND_GUIDE.md)** - Hướng dẫn phát triển frontend
- **[Development Guide](docs/DEVELOPMENT_GUIDE.md)** - Hướng dẫn phát triển chung
- **[Data Platform Guide](docs/DATA_PLATFORM.md)** - Hướng dẫn phát triển Data Platform
- **[AI Platform](docs/AI_PLATFORM.md)** - Tài liệu AI services
- **[API Documentation](docs/API_DOCs_v1.md)** - API endpoints chi tiết
- **[Deployment](docs/DEPLOYMENT.md)** - Hướng dẫn deployment

---

## 🗺 Roadmap

### Phase 1: Core Features ✅ (Hoàn thành)

- [x] Trend Radar Dashboard
- [x] Knowledge Graph Explorer
- [x] Graph RAG Chatbot
- [x] Technology Clustering
- [x] Technology Comparison
- [x] User Management
- [x] Notifications

### Phase 2: Enhanced Features (Đang phát triển)

- [x] Career Assistant
- [x] Recommendation Engine
- [x] Personalized Learning Path (roadmap trong Career Assistant)
- [x] Skill Gap Analysis (skill gap trong Career Assistant)
- [ ] Job Matching System
- [x] Salary Analytics

### Phase 3: Advanced Features (Lên kế hoạch)

- [ ] Graph Embeddings
- [ ] Real-time Trend Detection
- [ ] Multi-source Knowledge Graph
- [ ] Knowledge Graph Versioning
- [ ] Graph Analytics Dashboard
- [x] Mobile App (React Native)
- [x] API Rate Limiting
- [x] Advanced Monitoring (Prometheus + Grafana + Loki)

### Phase 4: Enterprise Features (Lên kế hoạch)

- [ ] SSO Integration (SAML, OAuth2)
- [ ] RBAC Advanced
- [x] Audit Logging (activity_log + ActivityTrackingFilter)
- [ ] Data Export (PDF, Excel)
- [x] Custom Reports (report feature + ReportPage)
- [ ] Webhooks
- [ ] API Keys Management
- [ ] Multi-tenancy

---


## 📞 Liên hệ

- **Website**: https://vuhoang.click
- **Email**: vuhoang5053@gmail.com
- **GitHub**: https://github.com/dinhhoang0712
- **Phone**: 0343721388

---

## 🙏 Acknowledgments

- **OpenAI** - GPT models cho RAG
- **Google** - Gemini models
- **Neo4j** - Knowledge Graph database
- **Spring Team** - Spring Boot framework
- **React Team** - React framework
- **Vietnamese IT Community** - Dữ liệu và feedback

---

## ⭐ Star History

Nếu dự án này hữu ích cho bạn, hãy star nó trên GitHub!



