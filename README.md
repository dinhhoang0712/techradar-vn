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
                      React Web
                           │
                           ▼

               Spring Boot API Gateway
                 Hexagonal Architecture
                           │

      ┌────────────────────┼────────────────────┐
      │                    │                    │
      ▼                    ▼                    ▼

 PostgreSQL           Neo4j Graph           Redis

      │                    │
      └──────────┬─────────┘
                 ▼

         Graph RAG Service
              FastAPI

                 │

      ┌──────────┴──────────┐
      │                     │

      ▼                     ▼

 OpenAI / Gemini      Vector Search

                 │
                 ▼

      Technology Clustering
            FastAPI

                 ▲
                 │

          Data Pipeline

                 ▲
                 │

 VNExpress • GenK • Dân Trí • TopCV
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
│   ├── backend/
│   ├── web/
│   └── mobile/
│
├── services/
│   ├── ai-rag-core/
│   └── ml-clustering/
│
├── pipelines/
│   └── data-ingestion/
│
├── knowledge-graph/
│   ├── schema/
│   ├── migrations/
│   ├── queries/
│   └── seeds/
│
├── infrastructure/
│   ├── docker/
│   ├── nginx/
│   ├── postgres/
│   └── redis/
│
├── docs/
├── tests/
│
├── .env.example
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

### Khởi động toàn bộ hệ thống

```bash
docker compose up --build
```

### Chạy Spring API

```bash
cd apps/spring-api

mvn spring-boot:run
```

### Chạy Frontend

```bash
cd apps/web

npm install
npm run dev
```

### Chạy Graph RAG Service

```bash
cd services/ai-rag-core

uvicorn app.main:app --reload
```

### Chạy Clustering Service

```bash
cd services/ml-clustering

uvicorn app.main:app --reload
```

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
