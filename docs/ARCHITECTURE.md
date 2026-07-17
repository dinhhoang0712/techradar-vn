# Architecture Overview — TechRadar VN

> Tài liệu kiến trúc tổng thể của hệ thống TechRadar VN, bao gồm các thành phần, luồng dữ liệu và thiết kế kỹ thuật.

---

## Mục lục

1. [Tổng quan hệ thống](#1-tổng-quan-hệ-thống)
2. [Kiến trúc high-level](#2-kiến-trúc-high-level)
3. [Thành phần hệ thống](#3-thành-phần-hệ-thống)
4. [Luồng dữ liệu](#4-luồng-dữ-liệu)
5. [Kiến trúc Backend](#5-kiến-trúc-backend)
6. [Kiến trúc Frontend](#6-kiến-trúc-frontend)
7. [Kiến trúc AI Services](#7-kiến-trúc-ai-services)
8. [Kiến trúc Knowledge Graph](#8-kiến-trúc-knowledge-graph)
9. [Kiến trúc Data Platform](#9-kiến-trúc-data-platform)
10. [Security & Authentication](#10-security--authentication)
11. [Scalability & Performance](#11-scalability--performance)
12. [Monitoring & Observability](#12-monitoring--observability)

---

## 1. Tổng quan hệ thống

TechRadar VN là nền tảng phân tích xu hướng công nghệ và thị trường tuyển dụng IT tại Việt Nam, sử dụng kết hợp:

- **Knowledge Graph** trên Neo4j để lưu trữ mối quan hệ giữa công nghệ, doanh nghiệp, việc làm
- **Graph RAG** để hỏi đáp trên dữ liệu thực tế
- **Machine Learning** để phân cụm công nghệ và dự báo xu hướng
- **Data Pipeline** để thu thập và xử lý dữ liệu từ nhiều nguồn

### Mục tiêu thiết kế

- **Modular Architecture**: Mỗi thành phần hoạt động độc lập, dễ mở rộng
- **Reactive Programming**: WebFlux cho backend, async/await cho Python services
- **Event-Driven**: Kafka cho message passing giữa services
- **Microservices**: Spring Boot API Gateway + Python AI services
- **Hexagonal Architecture**: Dependency inversion, clean separation

---

## 2. Kiến trúc High-level

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

---

## 3. Thành phần hệ thống

### 3.1 Frontend Layer

| Component | Tech Stack | Port | Mô tả |
|-----------|------------|------|-------|
| **React Web** | React 19, Vite, TypeScript | 5173 | SPA chính, served by Nginx |
| **Expo Mobile** | React Native, Expo | - | Mobile app (tương lai) |

### 3.2 API Gateway Layer

| Component | Tech Stack | Port | Mô tả |
|-----------|------------|------|-------|
| **Spring Boot API** | Java 21, Spring Boot 3.4, WebFlux | 8080 | API Gateway, Hexagonal Architecture |
| **Nginx** | Reverse Proxy | 5173→80 | Proxy /api → gateway, serve static assets |

### 3.3 AI Services Layer

| Component | Tech Stack | Port | Mô tả |
|-----------|------------|------|-------|
| **ai-rag-core** | FastAPI, Python 3.11+ | 8000 | Graph RAG chat, recommendation, forecast, career/interview coaching, summarize, report, agent |
| **ml-clustering** | FastAPI, Python 3.11+, DVC | 8001 | HDBSCAN clustering pipeline |

### 3.4 Data Layer

| Component | Tech Stack | Port | Mô tả |
|-----------|------------|------|-------|
| **PostgreSQL** | PostgreSQL 16 | 5432 | Users, chat, analytics, CMS |
| **Neo4j** | Neo4j 5 | 7474/7687 | Knowledge Graph |
| **Redis** | Redis 7 | 6379 | Cache, token blacklist, rate limiting |
| **Qdrant** (optional) | Qdrant Vector DB | 6333/6334 | Vector store for RAG |

### 3.5 Data Platform Layer

| Component | Tech Stack | Mô tả |
|-----------|------------|-------|
| **Crawlers** | Selenium, requests | 8 sources: VNExpress, GenK, DanTri, ICTNews, TopCV, ITviec, Viblo, GitHub |
| **Bronze Writer** | Kafka consumer, MinIO | Immutable raw data storage |
| **Silver Processor** | Kafka consumer, PostgreSQL | Dedup, quality scoring |
| **Gold ETL** | Neo4j → PostgreSQL | Analytics table rebuild |
| **Scheduler** | APScheduler | Cron jobs for ETL, embed, clustering |

### 3.6 Infrastructure Layer

| Component | Tech Stack | Mô tả |
|-----------|------------|-------|
| **Kafka** | Apache Kafka 3.7 | Event bus (raw_articles, raw_jobs, extracted_*) |
| **MinIO** | MinIO | S3-compatible object storage |
| **MailHog** (dev) | MailHog | SMTP server for email testing |
| **Grafana/Loki/Promtail** (opt) | Observability stack | Centralized logging |

---

## 4. Luồng dữ liệu

### 4.1 Data Ingestion Pipeline

```
Crawlers (8 sources)
    │
    ▼  Kafka: raw_articles, raw_jobs
Kafka Broker
    │
    ├───────────────────┬──────────────────────────┐
    │                   │                          │
    ▼                   ▼                          ▼
Bronze Writer      Silver Processor        KafkaExtractorService
(Kafka consumer)   (Kafka consumer)*       (Spring Boot, LLM NER)
    │                   │                          │
    ▼                   ▼                          ▼  Kafka: extracted_articles, extracted_jobs
MinIO (immutable)  PostgreSQL                  Kafka Broker
s3://techradar-    (dp_processed_articles,          │
bronze/             dp_processed_jobs)              │
                        ▲                            │
                        └──────────────*─────────────┤
                                                      │
                    ┌─────────────────────┬───────────┴──────────┐
                    │                     │                      │
                    ▼                     ▼                      ▼
          KafkaNeo4jWriterService   embedding-service      (Silver Processor
          (Spring Boot)             (Kafka consumer)        also consumes
                    │                     │                  extracted_*, see *)
                    ▼                     ▼  Kafka: article_vectors, job_vectors
          Neo4j Knowledge Graph     Kafka Broker
            │             │              │
            │             ▼              ▼
            │   (job MERGE'd — is it   qdrant-writer (Kafka consumer)
            │    brand new? if yes:)        │
            │             ▼                 ▼
            │   Kafka: job.match.alerts   Qdrant Vector DB
            │             │              (optional, --profile vector)
            │             ▼
            │   JobMatchDispatcher (feature notification, NEW)
            │             │
            ▼             ▼
    Gold ETL (3:00 AM daily)   Fan-out: in-app + email
            │                 (theo user_profile.technologies /
            ▼                  notify_inapp / notify_email)
    PostgreSQL tech_analytics
            │
            ▼  MoM growth ≥ 20% threshold
    Kafka: trend.alerts
            │
            ▼
    TrendAlertDispatcher (feature notification)
            │
            ▼
    Fan-out: in-app + email
    (theo user_profile.technologies /
     notify_inapp / notify_email)
```

\* **Silver Processor đọc dual-topic**: cả `raw_*` (từ crawler, khi Spring Boot tắt) lẫn `extracted_*`
(từ `KafkaExtractorService`, khi Spring Boot chạy) — tránh bỏ sót dữ liệu, xem chi tiết ở
[DATA_PLATFORM.md §5](./DATA_PLATFORM.md).

### 4.2 RAG Query Pipeline

```
User Query (React)
    │
    ▼  JWT auth
Spring Boot API Gateway
    │
    ▼  X-Internal-Auth header
ai-rag-core (/chat)
    │
    ├─[1] Vector Search (Neo4j)
    ├─[2] Graph Traversal (NER + Cypher)
    ├─[3] SQL Analytics (tech_analytics)
    ├─[4] User Context (user_profile)
    │
    ▼
Rerank (BGE reranker)
    │
    ▼
Build Prompt (4 sources + history)
    │
    ▼
LLM Generate (OpenAI/Gemini)
    │
    ▼
Response + Sources
```

### 4.3 Clustering Pipeline

```
Neo4j Knowledge Graph
    │
    ▼  Snapshot (Stage 1)
Parquet files
    │
    ▼  Feature Engineering (Stage 2)
- Alias normalization
- Name embedding (E5 → PCA)
- Graph features
- Job TF-IDF
    │
    ▼  HDBSCAN Clustering (Stage 3)
Grid search hyperparameters
    │
    ▼  Evaluation (Stage 4)
Silhouette score, DBI
    │
    ▼  Promote (Stage 5)
Cluster labels → serving
```

---

## 5. Kiến trúc Backend

### 5.1 Hexagonal Architecture

Spring Boot backend được xây dựng theo mô hình **Hexagonal Architecture (Ports & Adapters)** kết hợp **Feature-Based Modular Architecture**.

```
apps/backend/src/main/java/com/techpulse/
├── features/                    # Feature modules
│   ├── auth/
│   │   ├── domain/             # Domain entities & business logic
│   │   ├── application/
│   │   │   ├── port/in/        # Input ports (use cases)
│   │   │   ├── port/out/       # Output ports (repositories)
│   │   │   └── service/        # Application services
│   │   └── adapter/
│   │       ├── in/web/         # REST controllers
│   │       └── out/persistence # Repository implementations
│   ├── radar/
│   ├── compare/
│   ├── graph/                  # + salary/sentiment filter (SalaryOverlap, SentimentBand)
│   ├── chat/
│   ├── clustering/
│   ├── salary/
│   ├── notification/
│   ├── company/                 # NEW — Company Explorer (Neo4j)
│   ├── job/                     # NEW — Job Matching (Neo4j, ranks by skill overlap)
│   ├── messaging/                # NEW — 1-1 direct messages (Postgres + SSE fan-out via Redis Pub/Sub)
│   ├── social/                   # NEW — posts/follow/like/comment feed + content reports (Postgres)
│   ├── aiproxy/                  # Consolidates the old career/forecast/recommend/report/
│   │                              # summarize/agent modules into ONE generic forwarder
│   │                              # (AiProxyRequestHandler + single PythonAiProxyClient);
│   │                              # thin controllers still expose /career /forecast /recommend
│   │                              # /report /interview /agent /chat/summarize
│   ├── user/
│   ├── system/
│   ├── health/
│   └── kafka/
│
├── shared/                      # Shared infrastructure
│   ├── config/
│   ├── security/
│   ├── exception/
│   └── common/
│
└── TechRadarApplication.java   # Main entry point
```

### 5.2 Design Principles

- **Hexagonal Architecture**: Domain logic độc lập với infrastructure
- **Dependency Inversion**: High-level modules không phụ thuộc low-level modules
- **Domain-Driven Design**: Bounded contexts theo feature
- **Feature-Based Modularization**: Mỗi feature là một module độc lập
- **Reactive Programming**: WebFlux + R2DBC cho non-blocking I/O
- **Separation of Concerns**: Clear separation giữa domain, application, adapter

### 5.3 Technology Stack

| Layer | Technology |
|-------|------------|
| **Framework** | Spring Boot 3.4, Spring WebFlux |
| **Language** | Java 21 |
| **Database Access** | Spring Data R2DBC (PostgreSQL) |
| **Graph Database** | Neo4j Java Driver 5.28 |
| **Security** | Spring Security, JWT (jjwt 0.12.5) |
| **Validation** | Spring Boot Validation |
| **API Documentation** | Springdoc OpenAPI 3 |
| **Database Migration** | Flyway |
| **Caching** | Spring Data Redis Reactive |
| **Message Queue** | Spring Kafka |
| **Email** | Spring Boot Mail |
| **Resilience** | Resilience4j (Circuit Breaker) |
| **Logging** | Logback + Logstash (JSON for prod) |
| **Testing** | Testcontainers, WireMock, Reactor Test |

### 5.4 Database Schema (PostgreSQL)

> Xem [`docs/DATABASE.md`](./DATABASE.md) cho tài liệu đầy đủ về schema (Postgres + Neo4j +
> Redis), quy ước sở hữu dữ liệu giữa các service, và Flyway migration ledger. Tóm tắt nhanh:

```sql
-- Users & Authentication
users (id, email, password_hash, role, status, created_at)
user_profile (user_id, full_name, avatar_url, bio, job_role, location,
              technologies[], notify_inapp, notify_email)
user_avatar (user_id, content_type, data)
password_reset (token, user_id, expires_at, used)

-- Chat & AI (chat_message ghi bởi ai-rag-core, không phải backend — xem DATABASE.md)
chat_session (id, user_id, title, model_used, system_prompt, created_at)
chat_message (id, session_id, role, content, prompt_tokens, completion_tokens, created_at)

-- Analytics
tech_analytics (technology_name, month, job_count, article_count,
                growth_rate, mom_growth, yoy_growth, ranking)
activity_log (id, type, user_id, path, keyword, created_at)

-- CMS & System
cms_content (id, title, type, content_date, status, created_at)
settings (key, value, description)
notification (id, user_id, type, title, body, link, is_read, created_at)

-- Social Feed (NEW, V8)
post (id, user_id, content, created_at)
follow (follower_id, followee_id, created_at)
post_like (post_id, user_id, created_at)
post_comment (id, post_id, user_id, content, created_at)

-- Content Moderation (NEW, V11/V12)
content_report (id, reporter_id, post_id, comment_id, reason, status, resolved_at, resolved_by)

-- Direct Messaging (NEW, V9)
conversation (id, user_a_id, user_b_id, created_at)
direct_message (id, conversation_id, sender_id, content, created_at, read_at)

-- Data Platform catalog (sở hữu bởi service `data-platform`, không phải backend)
dp_bronze_catalog / dp_processed_articles / dp_processed_jobs / dp_pipeline_runs
```

---

## 6. Kiến trúc Frontend

### 6.1 Tech Stack

| Category | Technology |
|----------|-----------|
| **Framework** | React 19 |
| **Build Tool** | Vite 7 |
| **Language** | JavaScript (ES6+) |
| **Routing** | React Router DOM 7 |
| **Charts** | Recharts 3 |
| **Graph Visualization** | D3.js 7, react-force-graph-2d |
| **HTTP Client** | Fetch API |
| **Testing** | Vitest, Testing Library |
| **Styling** | CSS Modules |

### 6.2 Page Structure

```
apps/web/src/pages/
├── auth/
│   ├── LoginPage.jsx
│   ├── RegisterPage.jsx
│   └── ForgotPasswordPage.jsx
├── TrendDashboard.jsx          # Tech radar dashboard
├── GraphExplorer.jsx            # Knowledge graph (Explore / Road Analysis / Browse Filters)
├── ChatbotPage.jsx              # Graph RAG chat + Agent mode toggle
├── ClusterDashboard.jsx         # Technology clustering visualization
├── ComparePage.jsx              # Technology comparison
├── CareerPage.jsx               # Career path assistant + job-match card
├── InterviewPage.jsx            # NEW — AI mock interview (turn-based, /interview)
├── ReportPage.jsx               # Trend reports
├── SalaryPage.jsx               # Salary analytics
├── CompanyExplorer.jsx          # NEW — company directory + similar-company panel
├── FeedPage.jsx                 # NEW — social feed (posts/likes/comments)
├── MessagesPage.jsx             # NEW — direct messaging (SSE)
├── PublicProfilePage.jsx        # NEW — public profile (/users/:id), follow + message entry point
├── UserProfile.jsx              # User profile management (own profile)
├── admin/
│   ├── AdminDashboard.jsx        # + Social Engagement / Job Market / Pipeline Health / Messaging Volume tabs (NEW)
│   ├── AdminModeration.jsx       # NEW — view/delete any post/comment
│   ├── AdminReports.jsx          # NEW — content_report moderation queue (dismiss)
│   ├── AdminUsers.jsx
│   ├── AdminCMS.jsx
│   └── AdminSettings.jsx
└── MaintenancePage.jsx          # Maintenance mode
```

### 6.3 Component Architecture

```
apps/web/src/
├── layouts/
│   ├── UserLayout.jsx           # wraps Header/Footer + <MessagingProvider> for all user pages
│   └── AdminLayout.jsx
├── components/
│   ├── layout/
│   │   ├── Header.jsx           # top nav — gained Bảng tin/Tin nhắn/Công ty/Phỏng vấn thử links
│   │   ├── AdminSidebar.jsx
│   │   └── Footer.jsx
│   ├── notifications/
│   │   ├── NotificationBell.jsx
│   │   └── NotificationPanel.jsx
│   ├── social/
│   │   └── PostCard.jsx         # NEW — shared like/comment/delete card (Feed + PublicProfile)
│   └── common/
│       ├── Avatar.jsx           # NEW — shared avatar-or-fallback-icon
│       ├── Modal.jsx            # confirm dialogs (replaces window.confirm call sites)
│       └── ToastProvider.jsx / toastContext.js   # split apart for Fast-Refresh compatibility
├── contexts/
│   ├── AppContext.jsx / appContextStore.js       # auth/app state
│   └── MessagingContext.jsx / messagingStore.js  # NEW — single app-wide SSE connection (unread badges)
├── api/
│   ├── apiClient.js             # HTTP client with interceptors (bearer token, auto-refresh)
│   ├── authService.js, radarService.js, graphService.js, chatService.js, clusterService.js
│   ├── companyService.js        # NEW
│   ├── jobService.js            # NEW
│   ├── messagingService.js      # NEW
│   ├── socialService.js         # NEW
│   ├── interviewService.js      # NEW
│   └── agentService.js          # NEW — one-shot Agent-mode chat
├── utils/
│   ├── formatters.js
│   └── validators.js
└── data/
    └── mockData.js              # Development mock data
```

### 6.4 State Management

- **Auth/App State**: React Context (`AppContext`)
- **Messaging State**: React Context (`MessagingContext`) — opens ONE persistent SSE connection
  (`GET /conversations/stream`, via raw `fetch`+`ReadableStream` since `EventSource` can't set
  `Authorization`) at `UserLayout` mount; feeds unread badges + live message delivery app-wide,
  not just on `/messages`.
- **Local State**: React hooks (`useState`, `useReducer`)
- **Server State**: Fetch API with caching (future: React Query)
- **Form State**: Controlled components

---

## 7. Kiến trúc AI Services

### 7.1 ai-rag-core (FastAPI)

```
services/ai-rag-core/app/
├── main.py                      # FastAPI app, lifespan
├── config.py                    # Pydantic Settings
├── observability.py             # RequestContextMiddleware
├── api/
│   ├── schemas.py               # Pydantic models
│   ├── security.py              # require_internal_auth
│   ├── routes_chat.py           # /chat endpoints
│   ├── routes_embed.py          # /embed/trigger
│   ├── routes_internal.py       # /internal/ai/*
│   ├── routes_recommend.py      # /recommend
│   ├── routes_forecast.py       # /forecast
│   ├── routes_career.py         # /career
│   ├── routes_summarize.py      # /summarize
│   ├── routes_report.py         # /report
│   ├── routes_interview.py      # /interview — NEW, stateless AI mock-interview turn machine
│   └── routes_agent.py          # /agent (LangChain)
├── core/
│   ├── pipeline.py              # RAG orchestrator
│   ├── pipeline_stream.py       # Streaming version
│   ├── embedder.py              # E5-base singleton
│   ├── retriever.py             # Vector search
│   ├── retriever_graph.py       # NER + Cypher
│   ├── retriever_sql.py         # Analytics queries
│   ├── retriever_user.py        # User context
│   ├── entity_extractor.py      # NER pipeline
│   ├── reranker.py              # BGE reranker
│   ├── prompt_builder.py        # Prompt templates
│   └── generator.py             # LLM factory
├── services/
│   ├── chat_service.py
│   ├── recommend_service.py
│   ├── forecast_service.py
│   ├── career_service.py
│   ├── summarize_service.py
│   ├── report_service.py
│   └── interview_service.py     # NEW — opening/turn/final state machine (stateless, driven by history[])
├── agent/
│   ├── executor.py              # LangChain AgentExecutor
│   └── tools.py                 # 4 tools
├── memory/
│   ├── conversation.py          # Sliding window
│   └── user_context.py          # Long-term memory
├── evaluation/
│   └── ragas_scorer.py          # RAGAS evaluation
├── monitoring/
│   └── metrics.py               # Prometheus metrics
├── db/
│   ├── neo4j_client.py
│   ├── postgres_client.py
│   └── graph_queries.py
├── models/
│   ├── chat.py
│   └── user.py
└── prompts/
    ├── system_prompt.txt
    ├── rag_template.txt
    ├── interview_opening_template.txt   # NEW — turn 0, sinh câu hỏi mở đầu
    ├── interview_turn_template.txt      # NEW — feedback + câu hỏi tiếp theo
    ├── interview_final_template.txt     # NEW — nhận xét tổng kết + SCORE: N/10
    └── ...
```

> **Ghi chú kiến trúc (aiproxy, phía Spring Boot):** các route `/recommend /forecast /career
> /summarize /report /agent` (và `/interview` mới) không đổi ở phía `ai-rag-core` (Python) —
> thay đổi nằm ở **gateway** (`apps/backend`): 6 module riêng biệt trước đây
> (mỗi module có `ModuleConfig` + `ServicePort` + `PythonXClient` typed riêng) đã được gộp
> thành một module `features/aiproxy` dùng chung MỘT `PythonAiProxyClient`/`AiProxyPort`
> (forward `Map<String,Object>` nguyên văn, không có typed request/response phía Java nữa).
> Xem [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4 để biết chi tiết.

### 7.2 ml-clustering (FastAPI)

```
services/ml-clustering/
├── app/
│   ├── main.py                  # FastAPI app
│   ├── config.py                # Settings
│   └── api/
│       ├── routes_pipeline.py   # /pipeline/*
│       └── routes_serving.py    # /clusters/*
├── pipelines/
│   ├── stage_01_extract.py      # Neo4j → Parquet
│   ├── stage_02_features.py     # Feature engineering
│   ├── stage_03_train.py        # HDBSCAN grid search
│   ├── stage_04_evaluate.py     # Evaluation metrics
│   └── stage_05_promote.py      # Promote to serving
├── src/
│   ├── clustering.py            # HDBSCAN wrapper
│   ├── embeddings.py             # E5-base embeddings
│   ├── features.py              # Feature extraction
│   └── utils.py
├── dvc.yaml                     # DVC pipeline definition
├── params.yaml                   # Hyperparameters
└── visualize_clusters.py        # Visualization script
```

---

## 8. Kiến trúc Knowledge Graph

### 8.1 Graph Schema

**Node Types:**
- `Article`: title, content, source, published_date, sentiment_score, embedding (768d)
- `Technology`: name, category, subcategory, description, trend_score, demand_score
- `Skill`: name, category, demand_score
- `Company`: name, field, size, location, rating
- `Job`: title, description, requirement, benefit, salary, due_date, source_url
- `Person`: name, role

**Relationship Types:**
- `MENTIONS`: Article → Technology/Company/Person
- `REQUIRES`: Job → Technology/Skill — dùng bởi backend **Job Matching** (`features/job`)
- `HIRES_FOR`: Job → Company — dùng bởi backend **Company Explorer** (`features/company`) và
  làm ngữ cảnh cho **AI Interview** (`ai-rag-core` `graph_queries.JOBS_BY_TITLE_AND_COMPANY`)
- `USES`: Company → Technology (derived, ghi bởi `data-platform/gold/neo4j_enricher.py`) —
  **lưu ý:** backend hiện KHÔNG đọc quan hệ này; `features/company` tự suy ra tech stack của
  công ty gián tiếp qua `Company<-[HIRES_FOR]-Job-[REQUIRES]->Technology`. Chi tiết + phân tích
  sự khác biệt này ở [`docs/DATABASE.md`](./DATABASE.md) §4.1.
- `RELATED_TO`: Technology → Technology (derived)
- `WORKS_AT`: Person → Company (derived)
- `WROTE`: Person → Article (derived)

### 8.2 Modules

Không còn thư mục `knowledge-graph/` riêng (đã xoá — đó là bản đầu tiên, độc lập
của pipeline này). Việc ghi vào graph hiện nằm ở:

- `services/crawler/` — crawl 8 nguồn (VNExpress, GenK, DanTri, ICTNews, TopCV, ITviec, Viblo, GitHub), publish Kafka.
- `apps/backend` (`features/kafka/KafkaNeo4jWriterService.java`) — consume Kafka real-time, MERGE node gốc + cạnh trực tiếp.
- `data-platform/gold/{neo4j_article_sync,neo4j_job_sync}.py` — batch/nightly, MERGE lại cùng loại node/cạnh.
- `data-platform/gold/neo4j_enricher.py` — cạnh derived (`USES`, `RELATED_TO`) + stats.

Chi tiết "ai ghi gì" ở [`docs/DATABASE.md`](./DATABASE.md) §4.2.

---

## 9. Kiến trúc Data Platform

### 9.1 Medallion Architecture

```
Bronze Layer (Immutable Raw)
├── Input: Kafka raw_articles, raw_jobs
├── Storage: MinIO (gzip JSON)
├── Catalog: dp_bronze_catalog
└── Purpose: Source of truth, replayable

Silver Layer (Processed)
├── Input: Kafka raw_*, extracted_*
├── Storage: PostgreSQL
├── Tables: dp_processed_articles, dp_processed_jobs
├── Processing: Dedup, quality scoring
└── Purpose: Clean, queryable data

Gold Layer (Analytics)
├── Input: Neo4j Knowledge Graph
├── Storage: PostgreSQL tech_analytics
├── Schedule: 3:00 AM daily
└── Purpose: Aggregated analytics for radar
```

### 9.2 Modules

```
data-platform/
├── main.py                      # Entry point
├── config.py                    # Pydantic Settings
├── bronze/
│   └── writer.py                # Kafka → MinIO
├── silver/
│   ├── processor.py             # Kafka → PostgreSQL
│   └── deduplicator.py          # Dedup logic
├── gold/
│   ├── pg_etl.py                # Neo4j → tech_analytics
│   └── neo4j_enricher.py        # Derived relationships
├── scheduler/
│   ├── scheduler.py             # APScheduler
│   └── jobs.py                  # Job functions
└── common/
    ├── db.py                    # DB connections
    └── logger.py                # Loguru setup
```

---

## 10. Security & Authentication

### 10.1 Authentication Flow

```
1. User registers/logs in
   ↓
2. Spring Boot validates credentials
   ↓
3. JWT access token + refresh token generated
   ↓
4. Client stores tokens (localStorage/cookie)
   ↓
5. Each request includes: Authorization: Bearer <access_token>
   ↓
6. Spring Security validates JWT
   ↓
7. Request proceeds to handler
```

### 10.2 Authorization

- **Public**: `/auth/login`, `/auth/register`, `/health`, `/status`
- **Authenticated**: All other endpoints (valid JWT required)
- **Admin**: `/admin/**` (requires `ROLE_ADMIN`)

### 10.3 Internal API Security

Spring Boot → Python services communication:
- Header: `X-Internal-Auth: <INTERNAL_API_TOKEN>`
- Token configured via `INTERNAL_API_TOKEN` env var
- Python services validate before processing requests

### 10.4 Token Management

- **Access Token**: 15 minutes expiry
- **Refresh Token**: 7 days expiry
- **Blacklist**: Redis stores revoked refresh tokens
- **Rotation**: New refresh token issued on refresh

---

## 11. Scalability & Performance

### 11.1 Horizontal Scaling

| Component | Scalable | Notes |
|-----------|----------|-------|
| React Web (Nginx) | ✅ | Stateless, can scale horizontally |
| Spring Boot API | ✅ | Stateless, R2DBC connection pooling |
| ai-rag-core | ✅ | Stateless, model warmup on startup |
| ml-clustering | ✅ | Stateless, cache in S3/volume |
| PostgreSQL | ⚠️ | Read replicas for scaling reads |
| Neo4j | ⚠️ | Causal clustering for HA |
| Redis | ✅ | Cluster mode |
| Kafka | ✅ | Partition scaling |

### 11.2 Performance Optimizations

**Backend:**
- R2DBC for non-blocking database access
- Redis caching for frequently accessed data
- Connection pooling (R2DBC HikariCP, Neo4j driver)
- Async processing for AI calls (WebFlux)

**Frontend:**
- Code splitting (React.lazy)
- Lazy loading for routes
- Image optimization
- Debouncing for search inputs

**AI Services:**
- Model singleton (embedder, reranker)
- Thread pool for CPU-bound operations
- Async/await for I/O operations
- Vector index in Neo4j for fast retrieval

---

## 12. Monitoring & Observability

### 12.1 Logging

**Spring Boot:**
- Logback with JSON encoder (prod profile)
- Structured logging with trace IDs
- Log levels: ERROR, WARN, INFO, DEBUG

**Python Services:**
- Loguru for structured logging
- Request context middleware for trace IDs

**Centralized Logging (optional):**
- Loki + Promtail + Grafana
- Docker Compose profile: `observability`

### 12.2 Metrics

**Spring Boot Actuator:**
- `/actuator/health` - Health checks
- `/actuator/metrics` - Prometheus metrics
- Custom metrics for business logic

**ai-rag-core:**
- `/metrics` endpoint (Prometheus format)
- Metrics: requests, latency, tokens, retrieval results

### 12.3 Health Checks

| Service | Endpoint | Dependencies |
|---------|----------|--------------|
| Spring Boot | `/health` | PostgreSQL, Neo4j, Redis |
| ai-rag-core | `/health` | Neo4j, PostgreSQL, Redis |
| ml-clustering | `/health` | Neo4j |

---

## Conclusion

TechRadar VN sử dụng kiến trúc microservices với sự kết hợp giữa:

- **Spring Boot WebFlux** cho API gateway reactive
- **FastAPI** cho AI services
- **Neo4j** cho Knowledge Graph
- **PostgreSQL** cho relational data
- **Kafka** cho event-driven architecture
- **Docker Compose** cho containerization

Kiến trúc này cho phép:
- Độc lập giữa các thành phần
- Dễ dàng mở rộng (horizontal scaling)
- Reactive, non-blocking I/O
- Event-driven communication
- Clean separation of concerns

Xem thêm:
- [AI Platform Documentation](./AI_PLATFORM.md) - Chi tiết về AI services
- [API Documentation](./API_DOCs_v1.md) - API endpoints
- [Deployment Guide](./DEPLOYMENT.md) - Docker Compose deployment
- [Data Platform Documentation](../data-platform/README.md) - Data pipeline details
