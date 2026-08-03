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

\*\* **Tech name canonicalization (write-time)**: cả `EntityExtractionService.java` (bên trong
`KafkaExtractorService`, dùng `TechAliasCache`) lẫn Silver Processor (`_process_article`/`_process_job`,
dùng `common/tech_alias_cache.py`) đều resolve tên công nghệ qua cùng bảng `dp_tech_alias_map`
("Golang" → "Go", "ML" → "Machine Learning"...) **trước khi** ghi/publish — để `KafkaNeo4jWriterService`
và các Gold gap-filler (`neo4j_article_sync.py`/`neo4j_job_sync.py`) không bao giờ tạo 2 node
`:Technology` khác nhau cho cùng 1 công nghệ. Phần còn sót lại (node trùng tạo từ trước khi có
cơ chế này, hoặc case LLM mới phát hiện) được dọn định kỳ bởi `data-platform/gold/tech_dedup.py`
(5:30 AM daily) — xem [DATA_PLATFORM.md §5e](./DATA_PLATFORM.md).

### 4.2 RAG Query Pipeline (Adaptive Hybrid Graph RAG)

```
User Query (React)
    │
    ▼  JWT auth
Spring Boot API Gateway
    │
    ▼  X-Internal-Auth header
ai-rag-core (/chat)
    │
    ├─[0] Extract entities (NER, 1 lần) + Strategy Selector (rule-based)
    │       quyết định ĐỘC LẬP: use_graph? graph_expansion_depth (0/1/2)? use_sql_analytics?
    │       — một câu hỏi có thể bật nhiều nhánh cùng lúc, không phân loại theo 1 type cố định
    │
    ├─[1] Vector Search (Neo4j)                — luôn chạy
    ├─[2] Graph Traversal (Cypher, 1-hop)       — nếu use_graph
    ├─[3] Graph Expansion (1-2 hop, PageRank)   — nếu graph_expansion_depth > 0
    ├─[4] SQL Analytics (tech_analytics)        — nếu use_sql_analytics
    ├─[5] User Context (user_profile)           — nếu có user_id
    │
    ▼
Unified Rerank (BGE reranker, riêng từng loại nguồn: article/job/company/analytics)
    │
    ▼
Build Prompt (article + job/company + analytics + subgraph theo hop + history)
    │
    ▼
LLM Generate (OpenAI/Gemini/Groq/Claude qua llm-gateway fallback chain)
    │
    ▼
Response + Sources + subgraph (JSON-LD) + strategy (explainability)
```

Chi tiết đầy đủ (bug fix nền tảng, thiết kế Strategy Selector, ablation evaluation) xem
[`AI_PLATFORM.md` §2.3](./AI_PLATFORM.md#23-rag-pipeline-adaptive-hybrid-graph-rag) và
[`GRAPH_RAG_KG_REPORT.md`](./GRAPH_RAG_KG_REPORT.md).

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
apps/backend/src/main/java/com/techpulse/techradar/
├── features/                    # Feature modules — mỗi module: domain/ports/application/
│   │                              adapters/{input,output} (flat, KHÔNG có port/in|out hay adapter/in/web)
│   ├── auth/
│   │   ├── domain/             # Domain entities & business logic (User, ...)
│   │   ├── ports/              # Input/output ports (UserRepository, RolePermissionRepository...)
│   │   ├── application/        # Use cases (LoginUseCase, RegisterUseCase, TokenIssuer...)
│   │   └── adapters/
│   │       ├── input/          # REST controllers (AuthController)
│   │       └── output/         # Repository implementations (PostgresUserRepository...)
│   ├── radar/
│   ├── compare/
│   ├── graph/                  # + salary/sentiment filter (SalaryOverlap, SentimentBand)
│   │                              + graph analytics (PageRank/Louvain qua Neo4j GDS)
│   ├── chat/
│   ├── clustering/
│   ├── salary/
│   ├── notification/
│   ├── company/                 # Company Explorer (Neo4j)
│   ├── job/                     # Job Matching (Neo4j, ranks by skill overlap)
│   ├── roadmap/                 # Career Roadmap + "what-if" skill simulation
│   ├── messaging/                # 1-1 direct messages (Postgres + SSE fan-out via Redis Pub/Sub)
│   ├── social/                   # posts/follow/like/comment feed + content reports (Postgres)
│   ├── aiproxy/                  # Consolidates the old career/forecast/recommend/report/
│   │                              # summarize/agent modules into ONE generic forwarder
│   │                              # (AiProxyRequestHandler + single PythonAiProxyClient);
│   │                              # thin controllers still expose /career /forecast /recommend
│   │                              # /report /interview /agent /chat/summarize /company-insight
│   ├── user/
│   ├── system/                   # settings, admin dashboard, activity log, CMS, health/status
│   └── kafka/
│
├── config/                       # Security, JWT, Kafka, Redis, Postgres, Neo4j, Jackson config
│   ├── JwtTokenProvider.java
│   ├── SecurityConfig.java
│   ├── security/                 # JwtReactiveAuthenticationManager, converters
│   └── ...
│
├── shared/                       # Shared infrastructure (no feature-specific logic)
│   ├── client/                   # WebClient factories for Python services
│   ├── dto/                      # ApiResponse envelope
│   ├── exception/                # ErrorCode, custom exceptions, GlobalExceptionHandler
│   ├── http/
│   ├── logging/
│   ├── neo4j/                    # Neo4jReadTemplate
│   ├── paging/
│   ├── redis/                    # ReactiveRedisCache, rate limiters, RedisJsonStatus
│   ├── security/
│   └── util/
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
| **Language** | TypeScript (strict, `allowJs: false`) |
| **Routing** | React Router DOM 7 |
| **Charts** | Recharts 3 |
| **Graph Visualization** | react-force-graph-2d (dùng d3-force nội bộ) |
| **HTTP Client** | Fetch API |
| **Testing** | Vitest, Testing Library |
| **Styling** | Plain CSS per component |

### 6.2 Page Structure

```
apps/web/src/pages/
├── auth/
│   ├── LoginPage.tsx
│   └── RegisterPage.tsx
├── TrendDashboard.tsx          # Tech radar dashboard
├── GraphExplorer.tsx            # Knowledge graph (Explore / Road Analysis / Browse Filters / Graph Analytics)
├── ChatbotPage.tsx              # Graph RAG chat + Agent mode toggle
├── ClusterDashboard.tsx         # Technology clustering visualization
├── ComparePage.tsx              # Technology comparison
├── CareerPage.tsx               # Career path assistant + job-match card
├── InterviewPage.tsx            # AI mock interview (turn-based, /interview)
├── ReportPage.tsx               # Trend reports
├── SalaryPage.tsx               # Salary analytics
├── CompanyExplorer.tsx          # Company directory + similar-company panel
├── FeedPage.tsx                 # Social feed (posts/likes/comments)
├── MessagesPage.tsx             # Direct messaging (SSE)
├── NotificationsPage.tsx        # Notification center
├── PublicProfilePage.tsx        # Public profile (/users/:id), follow + message entry point
├── UserProfile.tsx              # User profile management (own profile)
├── ForbiddenPage.tsx            # 403
├── NotFoundPage.tsx             # 404
├── admin/
│   ├── AdminDashboard.tsx        # Social Engagement / Job Market / Pipeline Health / Messaging Volume tabs
│   ├── AdminAutomation.tsx       # Manual job triggers (crawler, analytics, clustering, graph analytics, data-platform)
│   ├── AdminModeration.tsx       # View/delete any post/comment
│   ├── AdminReports.tsx          # content_report moderation queue (dismiss)
│   ├── AdminClusters.tsx         # Cluster label override
│   ├── AdminUsers.tsx
│   ├── AdminCMS.tsx
│   └── AdminSettings.tsx
└── MaintenancePage.tsx          # Maintenance mode
```

Không có `ForgotPasswordPage` riêng — reset mật khẩu nằm trong luồng `LoginPage`/API `POST
/auth/forgot-password` (`AuthController`), không phải trang riêng.

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
│   ├── routes_internal.py       # /internal/ai/* (llm-summary, moderation-suggestion)
│   ├── routes_recommend.py      # /recommend
│   ├── routes_forecast.py       # /forecast
│   ├── routes_career.py         # /career
│   ├── routes_summarize.py      # /summarize
│   ├── routes_report.py         # /report
│   ├── routes_interview.py      # /interview — stateless AI mock-interview turn machine
│   ├── routes_company_insight.py # /company-insight — AI narrative về hồ sơ tuyển dụng/tech stack công ty
│   ├── routes_health.py         # /health
│   └── routes_agent.py          # /agent (LangChain)
├── core/
│   ├── pipeline.py              # RAG orchestrator (adaptive: Strategy Selector + conditional gather)
│   ├── pipeline_stream.py       # Streaming version (trùng lặp có chủ đích, xem AI_PLATFORM.md §2.3)
│   ├── strategy_selector.py     # Rule-based Strategy Selector (capability-based, không type-based)
│   ├── embedder.py              # E5-base singleton
│   ├── retriever.py             # Vector search
│   ├── retriever_graph.py       # NER + Cypher (1-hop)
│   ├── retriever_graph_expand.py # Graph expansion 1-2 hop, đọc pagerank_score (GDS) để rank
│   ├── retriever_sql.py         # Analytics queries
│   ├── retriever_user.py        # User context
│   ├── entity_extractor.py      # NER pipeline + intent patterns (analytics/multihop)
│   ├── reranker.py              # BGE reranker
│   ├── context_ranker.py        # Unified rerank riêng từng loại nguồn (article/job/company/analytics)
│   ├── graph_serializer.py      # Triple → JSON-LD tối giản cho response API
│   ├── prompt_builder.py        # Prompt templates (gồm subgraph block nhóm theo hop)
│   ├── generator.py             # get_gateway(): xây LLMGateway (services/llm-gateway) — provider chính
│   │                             # LLM_PROVIDER + fallback OpenAI/Groq/Gemini/Claude còn API key
│   └── generator_stream.py      # Streaming LLM generation
├── services/
│   ├── chat_service.py
│   ├── recommend_service.py
│   ├── forecast_service.py
│   ├── career_service.py
│   ├── summarize_service.py
│   ├── report_service.py
│   ├── company_insight_service.py # tóm tắt hồ sơ công ty bằng AI (/company-insight)
│   └── interview_service.py     # opening/turn/final state machine (stateless, driven by history[])
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

> **Ghi chú kiến trúc (llm-gateway, phía Python):** cùng tinh thần "gateway dùng chung" như
> `features/aiproxy` ở trên nhưng phía Python — `services/llm-gateway` là một **thư viện**
> Python thuần (không phải service chạy network, không port, không HTTP/gRPC), cài editable
> vào image của 3 consumer: `ai-rag-core` (`app/core/generator.py`), `ml-clustering`
> (`src/labeling/llm_labeler.py`), và `data-platform` (`gold/tech_dedup.py`). Cả 3 Dockerfile
> đều `COPY services/llm-gateway` nên build context của chúng trong `docker-compose.yml` đã
> đổi sang repo root (`.`). `LLMGateway` nhận 1 danh sách provider có thứ tự (index 0 = chính,
> còn lại = fallback), tự retry cùng provider theo `max_retries` trước khi rơi sang provider kế
> tiếp, rate limit theo token/phút qua Redis (`RedisRateLimiter`, fixed-window), và tính cost
> USD từ bảng giá tĩnh (`pricing.py`) rồi báo ra ngoài qua callback `on_usage` — gateway tự nó
> KHÔNG viết gì vào Postgres/Prometheus, việc đó do caller (từng service) quyết định. 4 provider
> hỗ trợ: OpenAI, Groq, Gemini, Claude — nhưng không phải consumer nào cũng dùng cả 4 (xem §7.1,
> §7.2, §9.2).

### 7.2 ml-clustering (FastAPI)

```
services/ml-clustering/
├── app/
│   ├── main.py                  # FastAPI app (cluster serving routes + /pipeline/*)
│   ├── routes_pipeline.py       # POST /pipeline/trigger, GET /pipeline/status, /pipeline/runs
│   ├── schemas.py, security.py, store.py
├── conf/
│   └── config.py                # Settings
├── pipelines/                   # 5 DVC stages
│   ├── stage_01_extract.py      # Neo4j → Parquet
│   ├── stage_02_features.py     # Feature engineering
│   ├── stage_03_train.py        # HDBSCAN grid search (DBCV, không phải silhouette)
│   ├── stage_04_label.py        # Gán nhãn AI (LLM)
│   ├── stage_05_writeback.py    # Ghi kết quả (Neo4j + serving store)
│   └── graph_writeback_utils.py
├── src/
│   ├── clustering/               # trainer.py, tuner.py, evaluator.py
│   ├── data/                     # neo4j_loader.py, snapshot.py
│   ├── features/                 # feature_pipeline.py, graph_features.py, gds_features.py (disabled), ...
│   ├── labeling/                 # llm_labeler.py — build_gateway(): LLMGateway (services/llm-gateway),
│   │                             # provider chính params.provider + fallback gemini→openai→groq
│   │                             # (KHÔNG có Claude trong chain này)
│   └── tracking/                 # mlflow_logger.py
├── dvc.yaml                     # DVC pipeline definition
├── params.yaml                   # Hyperparameters
└── visualize_clusters.py        # Visualization script
```

---

## 8. Kiến trúc Knowledge Graph

### 8.1 Graph Schema

**Node Types:**
- `Article`: title, content, source, published_date, sentiment_score, embedding (768d)
- `Technology`: name, category, subcategory, description, trend_score, demand_score,
  article_count, job_count (derived), pagerank_score, community_id, degree_centrality (derived —
  Neo4j GDS, ghi bởi `apps/backend` `Neo4jGraphAnalyticsAdapter`, admin-triggered)
- `Skill`: name, category, demand_score
- `Company`: name, **industry** (không phải `field`), size, location, rating
- `Job`: **name** + **url** (không phải `title`/`source_url` — tên cột legacy; mọi reader phải
  `coalesce(j.name, j.title)`/`coalesce(j.url, j.source_url)`, cả Java lẫn Python — chi tiết +
  bug thật đã fix xem [`docs/DATABASE.md`](./DATABASE.md#41-node--relationship-types) §4.1),
  description, requirement, benefit, salary, due_date

**Relationship Types:**
- `MENTIONS`: Article → Technology/Company
- `REQUIRES`: Job → Technology/Skill — dùng bởi backend **Job Matching** (`features/job`)
- `POSTED_BY`: Job → Company — **cạnh sống**, ghi real-time bởi Kafka pipeline
  (`features/kafka`) và batch bởi `data-platform/gold/neo4j_job_sync.py`; dùng bởi backend
  **Company Explorer** (`features/company`) và **Job Matching**
- `HIRES_FOR`: Job → Company — **không còn được ghi mới** (tạo bởi batch importer
  `knowledge-graph/` cũ, đã xoá khỏi repo); các job cũ có thể chỉ có cạnh này, nên
  `Neo4jCompanyRepository`/`Neo4jJobRepository` match cả `POSTED_BY|HIRES_FOR` để không bỏ sót
- `USES`: Company → Technology (derived, ghi bởi `data-platform/gold/neo4j_enricher.py`) —
  **lưu ý:** backend hiện KHÔNG đọc quan hệ này; `features/company` tự suy ra tech stack của
  công ty gián tiếp qua `Company<-[POSTED_BY|HIRES_FOR]-Job-[REQUIRES]->Technology`. Chi tiết +
  phân tích sự khác biệt này ở [`docs/DATABASE.md`](./DATABASE.md) §4.1.
- `RELATED_TO`: Technology → Technology (derived, co-mention count — dùng làm trọng số cho
  PageRank/Louvain GDS ở trên)

### 8.2 Modules

Không còn thư mục `knowledge-graph/` riêng (đã xoá — đó là bản đầu tiên, độc lập
của pipeline này). Việc ghi vào graph hiện nằm ở:

- `services/crawler/` — crawl 8 nguồn (VNExpress, GenK, DanTri, ICTNews, TopCV, ITviec, Viblo, GitHub), publish Kafka.
- `apps/backend` (`features/kafka/adapters/input/KafkaNeo4jWriterService.java`) — consume Kafka real-time, MERGE node gốc + cạnh trực tiếp qua port `ExtractionWriter` (impl `features/kafka/adapters/output/Neo4jExtractionWriter.java`). Tên công nghệ đã được `features/kafka/domain/EntityExtractionService.java` canonical hoá trước đó qua port `TechAliasResolver` (impl `features/kafka/adapters/output/TechAliasCache.java`) — xem §4.1 footnote **.
- `data-platform/gold/{neo4j_article_sync,neo4j_job_sync}.py` — batch/nightly, MERGE lại cùng loại node/cạnh. Đọc từ Silver Postgres nên cũng đã nhận tech name đã canonical hoá.
- `data-platform/gold/neo4j_enricher.py` — cạnh derived (`USES`, `RELATED_TO`) + stats.
- `data-platform/gold/tech_dedup.py` — dọn node `:Technology` trùng lặp còn sót lại trong graph (alias map đã biết + LLM discovery cho case chưa biết), 5:30 AM daily.

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
├── Tables: dp_processed_articles, dp_processed_jobs, dp_tech_alias_map
├── Processing: Dedup, quality scoring, tech name canonicalization
└── Purpose: Clean, queryable data

Gold Layer (Analytics)
├── Input: Neo4j Knowledge Graph
├── Storage: PostgreSQL tech_analytics, dp_tech_alias_map, dp_tech_alias_review_queue
├── Schedule: pg_etl 3:00 AM, neo4j_enricher 5:00 AM, tech_dedup 5:30 AM daily
└── Purpose: Aggregated analytics + Technology node dedup cho radar
```

### 9.2 Modules

```
data-platform/
├── main.py                      # Entry point
├── config.py                    # Pydantic Settings
├── bronze/
│   └── writer.py                # Kafka → MinIO
├── silver/
│   ├── processor.py             # Kafka → PostgreSQL (+ tech name canonicalization)
│   └── deduplicator.py          # Dedup logic
├── gold/
│   ├── pg_etl.py                # Neo4j → tech_analytics
│   ├── neo4j_article_sync.py    # Backfill Article/Technology (2:00 AM)
│   ├── neo4j_job_sync.py        # Backfill Job/Company (2:30 AM)
│   ├── neo4j_enricher.py        # Derived relationships
│   └── tech_dedup.py            # Gộp Technology node trùng lặp (5:30 AM) — Stage B (LLM discovery
│                                 # cho tên chưa có trong alias map) đi qua services/llm-gateway,
│                                 # provider chính TECH_DEDUP_LLM_PROVIDER + fallback gemini/openai/groq
├── scheduler/
│   ├── scheduler.py             # APScheduler
│   └── jobs.py                  # Job functions
└── common/
    ├── db.py                    # DB connections
    ├── tech_alias_cache.py      # dp_tech_alias_map cache — canonicalize_techs()
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

- **Access Token**: 24 hours expiry (`app.jwt.expiration`, env `JWT_EXPIRATION`, default `86400000`ms)
- **Refresh Token**: 7 days expiry (`app.jwt.refresh-expiration`, env `JWT_REFRESH_EXPIRATION`, default `604800000`ms)
- **Blacklist**: Redis stores revoked refresh tokens
- **Rotation**: New refresh token issued on refresh
- **Security stamp**: token bị vô hiệu hoá ngay lập tức khi admin đổi role/khoá tài khoản (`users.security_stamp`), không cần chờ hết hạn

---

## 11. Scalability & Performance

### 11.1 Horizontal Scaling

| Component | Scalable | Notes |
|-----------|----------|-------|
| React Web (Nginx) | ✅ | Stateless, can scale horizontally |
| Spring Boot API | ✅ | Stateless, R2DBC connection pooling |
| ai-rag-core | ✅ | Stateless, model warmup on startup |
| ml-clustering | ✅ | Stateless, cache in MinIO/volume |
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
