# TechRadar VN

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.4-green" />
  <img src="https://img.shields.io/badge/WebFlux-Reactive-success" />
  <img src="https://img.shields.io/badge/React-19-blue" />
  <img src="https://img.shields.io/badge/Neo4j-Knowledge_Graph-brightgreen" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-blue" />
  <img src="https://img.shields.io/badge/FastAPI-AI-teal" />
  <img src="https://img.shields.io/badge/Docker-Containerized-2496ED" />
  <img src="https://img.shields.io/badge/License-MIT-green" />
</p>

<p align="center">
  <strong>Technology Trend Analytics Platform powered by Knowledge Graph, Graph RAG and Machine Learning</strong>
</p>

---

## Tổng quan

TechRadar VN là nền tảng phân tích xu hướng công nghệ và thị trường tuyển dụng IT tại Việt Nam.

Hệ thống thu thập dữ liệu từ các nguồn tin công nghệ và tuyển dụng, sử dụng NLP để trích xuất thực thể, xây dựng Knowledge Graph trên Neo4j, sau đó cung cấp các công cụ phân tích xu hướng, khám phá đồ thị tri thức, phân cụm công nghệ và chatbot Graph RAG.

### Mục tiêu

- Theo dõi công nghệ đang tăng trưởng
- Phân tích nhu cầu tuyển dụng IT
- Khám phá mối liên hệ giữa công nghệ, kỹ năng và doanh nghiệp
- Hỏi đáp trên dữ liệu thực tế thay vì dữ liệu tổng quát của LLM
- Hỗ trợ định hướng học tập và nghề nghiệp

---

## Tính năng chính

### 📈 Trend Radar

- Theo dõi xu hướng công nghệ theo thời gian
- Top technologies theo mức tăng trưởng
- Thống kê số lượng việc làm
- Dashboard trực quan

### 🕸 Knowledge Graph Explorer

- Trực quan hóa đồ thị tri thức
- Khám phá quan hệ giữa:
  - Technology
  - Company
  - Job
  - Skill
  - Article
  - Location
- Hỗ trợ graph traversal và graph analytics

### 🤖 Graph RAG Chatbot

- Chatbot hỏi đáp trên dữ liệu thực tế
- Kết hợp:
  - Vector Retrieval
  - Knowledge Graph Retrieval
  - Reranking
  - LLM Generation
- Trả lời có nguồn tham chiếu
- Hỗ trợ lưu lịch sử hội thoại

### 🧠 Technology Clustering

- Gom nhóm công nghệ tương đồng
- HDBSCAN / DBSCAN / K-Means
- LLM-based cluster labeling
- Hỗ trợ recommendation và trend analytics

### 👤 User Management

- Đăng ký
- Đăng nhập
- JWT Authentication
- Refresh Token
- Quản lý hồ sơ cá nhân

---

## Kiến trúc hệ thống

```text
      React Web (Vite)            Expo Mobile
            │   Nginx proxy /api      │
            └────────────┬────────────┘
                         ▼
            Spring Boot API Gateway  (WebFlux, hexagonal)   ──► MailHog (SMTP, dev)
                         │   single entry point /api/v1
        ┌────────────────┼─────────────────────────────┐
        ▼                ▼                              ▼
   PostgreSQL        Neo4j Graph        X-Internal-Auth │  (Redis: cache)
  (R2DBC + Flyway)  (Bolt driver)                       ▼
   users, chat,      knowledge        ┌──────────────────────────────┐
   analytics, CMS    graph + ETL      │ ai-rag-core (FastAPI :8000)   │  RAG chat
                                      │ ml-clustering (FastAPI :8001) │  clustering
                                      └──────────────────────────────┘
                                                 │ OpenAI / Gemini
                                                 ▼
                              Qdrant vector store ◄── qdrant_writer ◄── Kafka
                                                                          ▲
                  Data Pipeline ──► Knowledge Graph (Neo4j)  embedding_service
            (VNExpress · GenK · Dân Trí · TopCV → PhoBERT → ELECTRA NER)
```

---

## Backend Architecture

Backend được xây dựng bằng Spring Boot 3.4 theo mô hình **Hexagonal Architecture (Ports & Adapters)** kết hợp **Feature-Based Modular Architecture**.

### Feature Structure

```text
features/
├── auth/
├── radar/
├── compare/
├── graph/
├── chat/
├── clustering/
├── system/
└── health/
├── user/
└── kafka/
```

### Cấu trúc một module

```text
auth/
├── domain/
│
├── application/
│   ├── port/in
│   ├── port/out
│   └── service
│
├── adapter/
│   ├── in/web
│   └── out/persistence
│
└── AuthModuleConfig.java
```

### Design Principles

- Hexagonal Architecture
- Dependency Inversion Principle
- Domain-Driven Design Concepts
- Feature-Based Modularization
- Reactive Programming (WebFlux)
- Separation of Concerns
- Clean Architecture

---

## Data Pipeline

```text
Technology Sources
(VNExpress, GenK, Dân Trí)

Job Sources
(TopCV, ITviec)

            │
            ▼

         Crawlers

            │
            ▼

   IT Content Classification
          (PhoBERT)

            │
            ▼

     Entity Extraction
        (ELECTRA)

            │
            ▼

      Data Cleaning

            │
            ▼

      Knowledge Graph

            │
            ▼

Analytics / RAG / ML
```

### Extracted Entities

- Technology
- Company
- Job
- Skill
- Location
- Salary
- Date

---

## Knowledge Graph

### Nodes

- Technology
- Company
- Job
- Skill
- Article
- Location

### Relationships

- MENTIONS
- REQUIRES
- USES
- RELATED_TO
- POSTED_BY

Knowledge Graph là nguồn dữ liệu trung tâm của toàn hệ thống.

Được sử dụng bởi:

- Trend Analytics
- Graph Explorer
- Graph RAG
- Technology Clustering

---

## Công nghệ sử dụng

### Frontend

- React 19
- TypeScript
- Vite
- React Router
- Recharts
- D3.js
- Force Graph

### Backend

- Java 21
- Spring Boot 3.4
- Spring WebFlux
- Spring Security
- JWT Authentication
- R2DBC
- OpenAPI / Swagger

### Databases

- PostgreSQL
- Neo4j
- Redis

### AI & NLP

- FastAPI
- Sentence Transformers
- PhoBERT
- ELECTRA NER
- OpenAI
- Gemini
- Cross Encoder Reranker

### Machine Learning

- Scikit-Learn
- HDBSCAN
- DBSCAN
- K-Means
- DVC
- MLflow

### DevOps

- Docker
- Docker Compose
- GitHub Actions
- Testcontainers

---

## Cấu trúc dự án

```text
TECH-RADAR/
├── apps/
│   ├── backend/          # Spring Boot WebFlux API gateway (Java 21, hexagonal)
│   ├── web/              # React 19 + Vite SPA (served by Nginx in prod)
│   └── mobile/           # Expo / React Native app
│
├── services/
│   ├── ai-rag-core/      # FastAPI — Graph RAG chat            (port 8000)
│   └── ml-clustering/    # FastAPI — technology clustering     (port 8001)
│
├── knowledge-graph/      # Crawlers + embedding + Neo4j/Qdrant writers
│   ├── crawl/            # VNExpress · GenK · DanTri · TopCV + Kafka producer
│   ├── services/         # embedding_service, qdrant_writer
│   ├── utils/            # Neo4j import, schema, relationships
│   └── scripts/          # seed + data-fix helpers
│
├── pipelines/
│   └── data-pipeline/    # Scrape → filter (PhoBERT) → NER (ELECTRA) → S3
│
├── infrastructure/
│   └── nginx/            # Reverse-proxy assets (compose now lives at repo root)
│
├── docs/                 # API_DOCs_v1.md, DEPLOYMENT.md, design PDFs
├── tests/                # Cross-service pytest suites
│
├── docker-compose.yml    # ⭐ One-command full stack (+ `--profile vector`)
├── .env.docker.example   # Compose secrets / toggles
└── README.md
```

---

## Chạy hệ thống

### Yêu cầu

- Java 21+
- Node.js 20+
- Python 3.11+
- Docker
- Docker Compose

### Khởi động toàn bộ hệ thống (khuyến nghị)

Toàn bộ hệ thống được đóng gói bằng một `docker-compose.yml` ở thư mục gốc.
Xem chi tiết tại [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

```bash
cp .env.docker.example .env      # điền OPENAI_API_KEY nếu muốn chat trả lời thật
docker compose up --build        # app stack
# docker compose --profile vector up --build   # + pipeline Kafka → qdrant-writer → Qdrant
```

| URL | Dịch vụ |
| --- | --- |
| http://localhost:5173 | Web (Nginx → gateway) |
| http://localhost:8080/api/v1 | Spring gateway · Swagger tại `/swagger-ui.html` |
| http://localhost:8000 · :8001 | ai-rag-core · ml-clustering |
| http://localhost:8025 | MailHog (xem email reset mật khẩu) |
| :5432 · :7474 · :6379 | PostgreSQL · Neo4j Browser · Redis |

Tài khoản dev có sẵn (khi `APP_ENV=dev`): **admin@techradar.vn / Admin@12345**.

### Chạy từng service khi phát triển

```bash
# Spring API gateway
cd apps/backend && mvn spring-boot:run            # :8080  (cần Postgres + Neo4j)

# Frontend
cd apps/web && npm install && npm run dev          # :5173

# Graph RAG service
cd services/ai-rag-core && uvicorn app.main:app --reload --port 8000

# Clustering service
cd services/ml-clustering && uvicorn app.main:app --reload --port 8001
```

> Hợp đồng API: mọi route nằm dưới `/api/v1`, body theo **snake_case**.
> Hầu hết response được bọc trong `ApiResponse{success, data, message}`; riêng các
> endpoint auth (`/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/me`) và
> `/status` trả về **object thuần (bare)**. Chi tiết: [docs/API_DOCs_v1.md](docs/API_DOCs_v1.md).

---

## Kiểm thử

### Spring Boot

```bash
mvn test
```

### Frontend

```bash
npm test
```

### Python Services

```bash
pytest
```

---

## Roadmap

- Recommendation Engine
- Personalized Learning Path
- Graph Embeddings
- Real-time Trend Detection
- AI Career Assistant
- Multi-source Knowledge Graph
- Knowledge Graph Versioning
- Graph Analytics Dashboard
