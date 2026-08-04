# Backend Development Guide — TechRadar VN

> Tài liệu chi tiết về kiến trúc, phát triển và best practices cho Spring Boot backend.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Kiến trúc Hexagonal](#2-kiến-trúc-hexagonal)
3. [Cấu trúc dự án](#3-cấu-trúc-dự-án)
4. [Feature Modules](#4-feature-modules)
5. [Database Layer](#5-database-layer)
6. [Security & Authentication](#6-security--authentication)
7. [API Design](#7-api-design)
8. [External Service Integration](#8-external-service-integration)
9. [Testing](#9-testing)
10. [Deployment](#10-deployment)

---

## 1. Tổng quan

Backend TechRadar VN được xây dựng với:

- **Java 21** với các tính năng modern (records, pattern matching, virtual threads)
- **Spring Boot 3.4** với WebFlux cho reactive programming
- **Hexagonal Architecture** (Ports & Adapters)
- **Feature-Based Modularization**
- **R2DBC** cho non-blocking database access
- **Neo4j** cho Knowledge Graph

### Mục tiêu thiết kế

- **Clean Architecture**: Tách biệt domain logic khỏi infrastructure
- **Reactive**: Non-blocking I/O cho high throughput
- **Testable**: Dependency injection, testcontainers cho integration tests
- **Maintainable**: Clear separation of concerns, consistent naming

---

## 2. Kiến trúc Hexagonal

### 2.1 Ports & Adapters Pattern

```
┌─────────────────────────────────────────────────────────────┐
│                     APPLICATION LAYER                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Domain (Business Logic)                   │  │
│  │  - Entities                                            │  │
│  │  - Value Objects                                       │  │
│  │  - Domain Services                                     │  │
│  └──────────────────────────────────────────────────────┘  │
│                            │                                 │
│                    ┌───────┴───────┐                         │
│                    │   Ports      │                         │
│              ┌─────┴──────┬──────┴─────┐                    │
│              │  Input     │   Output    │                    │
│              │  Ports     │   Ports     │                    │
│              └─────┬──────┴──────┬─────┘                    │
└────────────────────┼───────────────┼─────────────────────────┘
                     │               │
        ┌────────────▼─────┐ ┌─────▼────────────┐
        │  Input Adapters  │ │  Output Adapters │
        │  - REST Controllers│ │ - Repositories  │
        │  - Event Listeners│ │ - External APIs │
        └──────────────────┘ └──────────────────┘
```

### 2.2 Dependency Rule

- **Domain** không phụ thuộc bất kỳ layer nào
- **Application** chỉ phụ thuộc vào Domain
- **Adapters** phụ thuộc vào Application và Domain
- **Infrastructure** implements Adapters

### 2.3 Implementation trong Spring Boot

```java
// Output Port (Repository Interface) — features/auth/ports/UserRepository.java
public interface UserRepository {
    Mono<User> findByEmail(String email);
    Mono<User> save(User user);
}

// Use Case (application layer — no separate "input port" interface, the class itself is the
// entry point other layers call) — features/auth/application/LoginUseCase.java
@Component
@RequiredArgsConstructor
public class LoginUseCase {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public Mono<LoginResponse> execute(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.error(new InvalidCredentialsException("Invalid email or password")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                        return Mono.error(new InvalidCredentialsException("Invalid email or password"));
                    }
                    return tokenIssuer.issueFor(user);
                });
    }
}

// Input Adapter (REST Controller) — features/auth/adapters/input/AuthController.java
// @RequestMapping("/auth") — NOT "/api/v1/auth": spring.webflux.base-path (/api/v1) is stripped
// before matchers run, so every controller in this codebase maps without the /api/v1 prefix.
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<LoginResponse>>> login(@Valid @RequestBody LoginRequest request) {
        return loginUseCase.execute(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Login successful")));
    }
}

// Output Adapter (Repository Implementation) — features/auth/adapters/output/PostgresUserRepository.java
// Plain DatabaseClient + hand-written SQL (no R2dbcEntityTemplate/JPA-style mapping anywhere
// in this codebase's Postgres repositories).
@Repository
@RequiredArgsConstructor
public class PostgresUserRepository implements UserRepository {
    private final DatabaseClient dbClient;

    @Override
    public Mono<User> findByEmail(String email) {
        return dbClient.sql("SELECT * FROM users WHERE email = :email")
                .bind("email", email)
                .map(this::mapRow)
                .one();
    }

    // save(...) similarly hand-written INSERT/UPDATE — see the real file for the full mapping.
}
```

---

## 3. Cấu trúc dự án

```
apps/backend/src/main/java/com/techpulse/techradar/
├── TechRadarApplication.java          # Main entry point
├── features/                           # Feature modules — mỗi module: domain/ports/application/
│   │                                    adapters/{input,output} (flat, KHÔNG có port/in|out
│   │                                    hay adapter/in/web như một số tài liệu cũ từng mô tả)
│   ├── auth/                          # Authentication feature
│   │   ├── domain/
│   │   │   └── User.java              # role/status là String phẳng, không phải enum
│   │   ├── ports/
│   │   │   ├── UserRepository.java
│   │   │   └── RolePermissionRepository.java
│   │   ├── application/               # Use case CŨNG LÀ input port — không có interface riêng
│   │   │   ├── LoginUseCase.java
│   │   │   ├── RegisterUseCase.java
│   │   │   ├── RefreshTokenUseCase.java
│   │   │   ├── ResetPasswordUseCase.java
│   │   │   ├── TokenIssuer.java
│   │   │   ├── LoginRequest.java / LoginResponse.java   # DTO ở application, không phải adapters
│   │   │   └── ...
│   │   └── adapters/
│   │       ├── input/
│   │       │   └── AuthController.java
│   │       └── output/
│   │           ├── PostgresUserRepository.java
│   │           └── PostgresRolePermissionRepository.java
│   │
│   ├── radar/                         # Tech radar feature
│   ├── compare/                       # Technology comparison
│   ├── graph/                         # Knowledge graph explorer + salary/sentiment filter
│   │                                    + graph analytics (PageRank/Louvain qua Neo4j GDS)
│   ├── chat/                          # RAG chat
│   ├── clustering/                    # ML clustering
│   ├── salary/                        # Salary insights (Neo4j, salary-text parsing)
│   ├── notification/                  # In-app/email notifications + trend-alert dispatch
│   ├── company/                       # Company Explorer (Neo4j)
│   ├── job/                           # Job Matching (Neo4j)
│   ├── roadmap/                       # Career Roadmap + "what-if" skill simulation
│   ├── messaging/                      # 1-1 direct messages (Postgres + SSE broadcaster)
│   ├── social/                         # posts/follow/like/comment feed (Postgres)
│   ├── aiproxy/                        # Consolidates career/forecast/recommend/report/
│   │                                    # summarize/agent/company-insight: one generic forwarder
│   ├── user/                          # User management
│   ├── system/                        # Settings, admin dashboard, activity log, CMS, health/status
│   └── kafka/                         # Kafka event handling (domain/ports/adapters/event)
│
├── config/                            # Cross-cutting Spring config (KHÔNG nằm trong shared/)
│   ├── JwtTokenProvider.java
│   ├── SecurityConfig.java
│   ├── security/                      # JwtReactiveAuthenticationManager, converters
│   ├── KafkaConfig.java
│   ├── RedisConfig.java
│   ├── Neo4jConfig.java
│   ├── PostgresConfig.java
│   ├── JacksonConfig.java
│   ├── SchedulingConfig.java
│   ├── WebFluxConfig.java
│   └── ActivityTrackingFilter.java
│
└── shared/                            # Shared infrastructure, không có logic riêng của feature nào
    ├── client/                        # WebClient factory cho Python services
    ├── dto/
    │   └── ApiResponse.java           # class (Lombok @Data @Builder), KHÔNG phải record
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   ├── ErrorCode.java
    │   └── ...
    ├── http/
    ├── logging/
    ├── neo4j/
    │   └── Neo4jReadTemplate.java     # shared Mono.fromCallable(...).subscribeOn(boundedElastic()) helper
    ├── paging/
    ├── redis/                         # ReactiveRedisCache, rate limiters, RedisJsonStatus
    ├── security/
    └── util/
```

Flyway migrations sống ở `src/main/resources/db/migration/` (không phải
`infrastructure/flyway/migrations/`), đánh số tuần tự thật `V1__init_schema.sql` … hiện tại tới
`V35__cms_content_body.sql` — không có version nhảy cóc kiểu `V900`/`V901`. Danh sách này đổi
thường xuyên hơn tài liệu; luôn `ls` trực tiếp thư mục để lấy version mới nhất thay vì tin số ở đây.

---

## 4. Feature Modules

### 4.1 Auth Feature

**Responsibilities:**
- User registration and login
- JWT token generation and validation
- Password reset flow
- Refresh token rotation

**Key Components:**
- `LoginUseCase` / `RegisterUseCase` / `RefreshTokenUseCase` / `ForgotPasswordUseCase` /
  `ResetPasswordUseCase` (`features/auth/application/`): authenticate, register, rotate refresh
  token, forgot/reset password flow
- `TokenIssuer`: issues access + refresh token pair for a `User`
- `JwtTokenProvider` (`config/JwtTokenProvider.java`, not under `features/auth`): generate/validate
  JWT, embeds `userId`/`email`/`role`/`permissions`/`securityStamp`
- `JwtReactiveAuthenticationManager` + `config/security/*`: reactive auth filter chain (no class
  named `JwtAuthenticationFilter` exists)

**Database Tables:**
- `users`: User credentials and status
- `user_profile`: User profile information (singular table name — not `user_profiles`)
- `user_avatar`: Avatar images (BYTEA)
- `password_reset`: Reset tokens

### 4.2 Radar Feature

**Responsibilities:**
- Tech trend analytics
- Top technologies by growth rate
- Search and filter technologies
- Export radar data (PNG, CSV)

**Key Components:**
- `GetTopTechnologiesUseCase` / `SearchTrendUseCase` (`features/radar/application/`): top-N by
  growth, keyword search
- `RadarExporter` (`features/radar/domain/`): PNG/CSV export
- `RadarAnalyticsEtlService` (`features/radar/etl/`): rebuilds `tech_analytics` from Neo4j
  (admin-triggered, `AnalyticsAdminController`). Two side effects fire after every successful
  rebuild, both best-effort (failures swallowed, never fail the rebuild itself): `emitTrendAlerts()`
  publishes a `trend.alerts` Kafka event for each technology whose current-month MoM growth clears
  `app.notifications.trend-threshold` (default 30%) — consumed by `TrendAlertDispatcher`, §4.10;
  and `writeKeywordDigest()` writes a `cms_content` row (`type="Keyword"`, `status="Analyzed"`,
  title `"Từ khóa nổi bật: ..."`) listing the top `KEYWORD_DIGEST_TOP_N` (5) ranked technologies for
  the current month — before this, the CMS "Keyword" entries were static seed data that nothing
  ever refreshed; an admin still has to review/publish it.

**Data Source:**
- PostgreSQL `tech_analytics` table (populated by Gold ETL)

### 4.3 Graph Feature

**Responsibilities:**
- Knowledge graph exploration
- Graph traversal queries
- Node and edge filtering
- Shortest path analysis
- Graph analytics (PageRank/Louvain community/degree centrality via Neo4j GDS)

**Key Components:**
- `ExploreGraphUseCase` / `FilterGraphUseCase` / `RoadAnalysisUseCase` — thin, validate input,
  delegate to the `GraphRepository` port
- `Neo4jGraphRepository` (implements `GraphRepository`) — all reads wrapped via
  `Neo4jReadTemplate.read(driver, ...)` (`Mono.fromCallable` + `Schedulers.boundedElastic()`
  around a `Session`, shared by every Neo4j-backed repository in the codebase)
- `GraphController` — `GET /graph/explore`, `GET /graph/road_analysis`, `POST /graph/filter`
  (all public, no `@PreAuthorize`)
- **Graph analytics (NEW)** — `RebuildGraphAnalyticsUseCase` delegates to
  `Neo4jGraphAnalyticsAdapter` (implements `GraphAnalyticsPort`), which projects `Technology` +
  `RELATED_TO` into a GDS in-memory graph (`gds.graph.project`, `orientation: 'UNDIRECTED'`,
  weighted by `co_mention_count`), streams `gds.pageRank.stream`/`gds.louvain.stream`/
  `gds.degree.stream`, remaps Louvain's arbitrary community ids to a compact 0-5 index (10
  largest→0-5's actual cap is `MAX_DISPLAY_COMMUNITIES = 6`; everything else collapses to sentinel
  `99`, "other"), and writes `pagerank_score`/`community_id`/`degree_centrality` back onto
  `Technology` nodes in one `session.executeWrite(...)` — not wrapped in `Neo4jReadTemplate` (that
  helper is read-only by contract). `GraphAnalyticsAdminController`
  (`POST /admin/graph-analytics/rebuild`, `@PreAuthorize("hasAuthority('graph:manage')")`)
  triggers it. Requires the GDS plugin (`docker-compose.yml` `NEO4J_PLUGINS`) — not installed by
  default on a fresh clone before this feature, since nothing else in the stack used GDS. No new
  read endpoint: every existing graph read already serializes `Technology` node properties
  verbatim (`Neo4jGraphRepository`'s `node.asMap()`), so `GET /graph/explore` returns the new
  properties for free once a rebuild has run.

**Data Source:**
- Neo4j Knowledge Graph (+ Neo4j GDS plugin for the analytics rebuild)

### 4.4 Chat Feature

**Responsibilities:**
- RAG chat session management
- Message history
- Proxy to ai-rag-core service
- SSE streaming for real-time responses

**Key Components:**
- `ChatUseCase` (`features/chat/application/`): create session, send message, fetch history
- `PythonChatClient` (implements `ChatPort`): proxies to ai-rag-core `/chat` and `/chat/stream`
  (SSE) — a **separate**, typed proxy from the generic `aiproxy` module in §4.16
- `PostgresChatRepository` (`features/chat/adapters/output/`): `chat_session`/`chat_message` reads

**Database Tables:**
- `chat_session` (singular): written by backend — lifecycle (create/list/delete/ownership check)
- `chat_message` (singular): **written by `ai-rag-core`, not backend** — backend only reads it
  to serve history. See [`docs/DATABASE.md`](./DATABASE.md) for the full ownership rationale
  (this split fixed a prior double-write bug).

### 4.5 Clustering Feature

**Responsibilities:**
- Technology clustering results
- Cluster information retrieval
- Batch prediction for technologies

**Key Components:**
- `GetClustersUseCase` / `GetClusterUseCase` (`features/clustering/application/`): list/get cluster
- `PredictClusterUseCase` / `BatchPredictClusterUseCase`: predict cluster for technologies
- `UpdateClusterLabelUseCase` / `TriggerPipelineUseCase` / `GetPipelineStatusUseCase` /
  `GetPipelineRunsUseCase`: admin operations (`AdminClusteringController`)
- `PythonClusteringClient` (implements `ClusteringServicePort`): proxy to ml-clustering

**Data Source:**
- ml-clustering service (FastAPI)

### 4.6 User Feature

**Responsibilities:**
- User profile management
- Avatar upload
- Preference management
- Notification settings

**Key Components:**
- `ProfileService` (`features/user/application/ProfileService.java`): CRUD user profile,
  technology preferences, notification settings — no separate `PreferenceService` exists
- `AvatarService`: Handle avatar upload/retrieval

### 4.7 System Feature

**Responsibilities:**
- Application settings
- Feature flags
- Activity logging
- Notification management

**Key Components:**
- `AdminService`: CRUD application settings (backed by `PostgresSettingsRepository`) — no separate
  `SettingsService`/`ActivityLogService` classes; activity logging is `PostgresActivityLogRepository`
  written directly by `ActivityTrackingFilter`
- `HealthController` / `StatusController` (`features/system/adapters/input/`): `GET /health`
  (no dependencies, hardcoded status), `GET /status` (feature flags via `AdminService`) — there is
  no separate `health` feature module, this lives inside `system`
- `AdminDashboardController` (NEW additions — `hasRole('ADMIN')`): backed by 6 focused `system/application` services instead of one aggregator class (`SiteMetricsService` for user-count/visits/monthly-visits/top-keywords, `SocialEngagementMetricsService`, `JobMarketMetricsService`, `PipelineHealthService`, `MessagingMetricsService`, `LiveMetricsService` — split out of a former single `DashboardMetricsService` god-class that had grown 13 dependencies across 8 unrelated features). Beyond the original `user-count`/`visits`/`monthly-visits`/`top-keywords`, now also:
  - `GET /admin/dashboard/social` → `SocialEngagementStats` (`total_posts`, `posts_today`, `total_comments`, `total_likes`, `total_follows`, `top_posters[]` (`user_id`,`full_name`,`post_count`), `pending_reports`) — reads across `PostRepository`/`CommentRepository`/`FollowRepository`/`ReportRepository`
  - `GET /admin/dashboard/jobs` → `JobMarketStats` (`total_jobs_indexed`, `top_technologies[]` (`name`,`job_count`), `job_match_alerts_sent`) — the last field is `NotificationRepository.countGroupedByType()` filtered to `JOB_MATCH`
  - `GET /admin/dashboard/pipeline` → `KafkaSyncStatus` (`articles_processed/failed`, `jobs_processed/failed`, `last_article_processed_at`, `last_job_processed_at`, `last_failure_at`, `last_failure_message`) — **in-process counters only** (`AtomicLong`/`AtomicReference` fields on `KafkaNeo4jWriterService`), reset to zero on every backend restart, NOT persisted anywhere
  - `GET /admin/dashboard/messaging` → `MessagingStats` (`total_conversations`, `total_messages`, `messages_today`, `notifications_by_type[]` (`type`,`count`))
- `SocialModerationService` + `AdminSocialController` (NEW, feature `system`, delegates into `features/social` ports) — admin-only moderation over the social feed: list/delete ANY post or comment (bypassing ownership), list/dismiss pending `content_report`s. See §4.15 for the endpoint list.
- `CacheAdminController` (NEW, `/admin/cache`, `hasRole('ADMIN')`) — `POST /admin/cache/companies/evict` (single key `cache:company:all`), `POST /admin/cache/jobs/evict` (pattern-evicts every `cache:job:match:*` entry), `POST /admin/cache/roadmap/evict` (pattern-evicts every `cache:roadmap:*` entry — one per user's computed career roadmap) via `ReactiveRedisCache.evictByPattern`. Exists because `company`/`job`/roadmap (§4.12/§4.13) have no ETL/rebuild step to hang a cache invalidation off of, unlike `radar`'s `AnalyticsAdminController`.

**Database Tables:**
- `settings`: Application settings + feature flags (also read by public `/status`)
- `activity_log`: User activity logs (visits/searches, populated by `ActivityTrackingFilter`)
- `cms_content`: AdminCMS content (Report/Job/Keyword). `body TEXT` (V35, nullable) holds the full
  generated content — currently only populated for "Report" rows written by
  `MonthlyReportSchedulerService` (§4.16); crawler-seeded rows and the radar ETL's "Keyword" digest
  (§4.2) have no body of their own
- `content_report` (NEW, read/updated via `AdminSocialController`, owned by `features/social` — see §4.15)

> Notifications are their **own** feature module (`features/notification`), not part of
> System — see §4.10.

### 4.8 Health

Không phải một feature module riêng — `HealthController`/`StatusController` sống trong
`features/system` (xem §4.7). `GET /health` không check dependency nào (trả status hardcoded);
Spring Boot Actuator (`/actuator/**`) chạy song song, độc lập, không đi qua 2 controller này.

### 4.9 Kafka Feature

**Responsibilities:**
- Event publishing
- Event consumption
- Trend alert notifications

**Package layout (hex-architecture, matches every other feature — this module used to be a flat
package with no `domain/ports/adapters` split; restructured for DIP/SRP):**
- `domain/` — `EntityExtractionService` (pure algorithm, no I/O), `KafkaSyncStatus` (record).
- `ports/` — `TechAliasResolver` (resolve a raw tech name to canonical), `ExtractionWriter` (persist an extracted article/job to the graph).
- `adapters/input/` — `ArticleExtractorService` (`@KafkaListener` on `raw_articles`), `JobExtractorService` (`@KafkaListener` on `raw_jobs`), `KafkaNeo4jWriterService` (`@KafkaListener` on `extracted_articles`/`extracted_jobs`).
- `adapters/output/` — `TechAliasCache` (implements `TechAliasResolver`), `Neo4jExtractionWriter` (implements `ExtractionWriter`), `KafkaProducerService` (generic `KafkaTemplate` wrapper used by all 3 input adapters above to publish their output topic).
- `event/` — the Kafka message-schema DTOs (`RawArticle`/`RawJob`, `ExtractedArticle`/`ExtractedJob` + their nested data classes, `Entities`), snake_case-annotated to match the Python crawler's wire format.
- `KafkaTopicConstants` stays at the feature root (topic name constants referenced by several OTHER features too — `notification`, `radar`, `roadmap` — so moving it would ripple for no DIP benefit).

**Key Components:**
- `ArticleExtractorService` / `JobExtractorService` — split out of a former single `KafkaExtractorService` (article and job pipelines shared nothing but the producer/hash-util dependencies, so each became its own single-responsibility listener). Each consumes its raw topic, runs entity extraction (`EntityExtractionService`, keyword/regex-based NER — không phải LLM dù tên biến `entities` gợi ý vậy), publishes to its extracted topic.
- `EntityExtractionService` — `extractTech()`/`extractEntities()` resolve mỗi tên công nghệ tách được qua `TechAliasResolver` (impl `TechAliasCache`) **trước khi** trả về (vd "Golang" → "Go", "ML" → "Machine Learning") để `KafkaNeo4jWriterService` phía dưới không bao giờ ghi 2 node `:Technology` khác nhau cho cùng 1 công nghệ. Xem [`docs/DATABASE.md`](./DATABASE.md) §4.3 cho bức tranh full (cả phía Python `data-platform`).
  > **Bug thật đã fix:** `extractEntities()` từng LUÔN trả `ORG`/`LOC` rỗng (chưa bao giờ triển
  > khai) dù `Neo4jExtractionWriter` đã có sẵn code chờ ghi `MENTIONS(Article→Company/Location)`
  > từ 2 field này — hệ quả thực tế: `USES` (Company→Technology, suy từ Article co-mention) gần
  > như không bao giờ được tạo (xem `docs/DATABASE.md` §4.1). Đã thêm: `extractOrg()` so khớp
  > với danh sách tên Company đã biết trong Neo4j (`CompanyNameCache`, tạo qua đường Job — chỉ
  > phát hiện được Company đã từng biết, không phát hiện Company hoàn toàn mới) và `extractLoc()`
  > so khớp dictionary tĩnh 63 tỉnh/thành (`LOCATION_KEYWORDS`, không cần cache vì danh sách này
  > cố định).
- `CompanyNameCache` — cache in-memory tên `:Company` đã có trong Neo4j (`@Scheduled` refresh mỗi `app.company-name-cache.refresh-ms`, mặc định 15 phút — dài hơn `TechAliasCache` vì quét toàn bộ Company node, nặng hơn đọc 1 bảng Postgres nhỏ). Dùng làm dictionary cho `EntityExtractionService.extractOrg()` — xem ghi chú ở trên.
- `TechAliasCache` — cache in-memory bảng Postgres `dp_tech_alias_map` (`@Scheduled` refresh mỗi `app.tech-alias.refresh-ms`, mặc định 5 phút; `@PostConstruct` load lần đầu). `resolve(rawName)` casefold+trim rồi tra cache, fallback về tên gốc (trimmed) nếu không có alias — không bao giờ throw hay trả null. Đây là bảng Postgres **duy nhất** mà `apps/backend` và `data-platform` cùng ghi/đọc chung (ngoại lệ có chủ đích, vì 2 service có Docker build-context tách biệt nên không share được 1 file cấu hình).
- `KafkaNeo4jWriterService` — consumes `extracted_articles`/`extracted_jobs`, delegates the actual Cypher write to `ExtractionWriter` (impl `Neo4jExtractionWriter`); also publishes `job.match.alerts` the first time a job is genuinely new (checked via a `MATCH` before the `MERGE`, so re-crawled/updated listings don't re-fire), and tracks in-process throughput/error counters exposed via `syncStatus()` (`KafkaSyncStatus` — see `GET /admin/dashboard/pipeline`, §4.7, now served by `system/application/PipelineHealthService`). Counters reset on restart, not persisted.
- `JobMatchDispatcher` (in `notification`, not `kafka`) — consumes `job.match.alerts`, fans out to users whose profile technologies overlap the job's, mirroring `TrendAlertDispatcher`. See §4.10.

### 4.10 Notification Feature

**Responsibilities:**
- In-app + email notifications from multiple producers (Kafka trend/job alerts, direct calls from social/messaging use cases)
- Unread count, mark-as-read/read-all
- Live delivery via SSE, **fanned out across backend instances via Redis Pub/Sub** (not just in-process)
- Admin-facing notification-type counts (for the dashboard, §4.7-equivalent under System)

**Key Components:**
- `NotificationService` (`application/`): list/unread-count/mark-read/`save()`. `save()` persists to Postgres then publishes the notification on Redis channel `live:notifications`; every backend instance is subscribed and re-emits to whichever local SSE clients (`streamFor`) it's holding — see [`docs/DATABASE.md`](./DATABASE.md) §5. Same fire-and-forget contract as before: a missed live push just means the client sees it on its next `GET /notifications`.
- `TrendAlertDispatcher`: consumes Kafka `trend.alerts` → `NotificationService.save()` with `type=TREND_ALERT`
- `JobMatchDispatcher` (NEW): consumes Kafka `job.match.alerts` (published by `KafkaNeo4jWriterService` the first time a job posting is newly written to Neo4j — skipped on MERGE-update of an already-known job) → looks up `findJobMatchSubscribers` (users whose `user_profile.technologies` overlaps the job's required techs) → `type=JOB_MATCH`, `link=/career`
- **Notification-on-social-action (NEW, not Kafka — called directly from the triggering use case, best-effort/`onErrorResume`-swallowed so a notification failure never fails the parent action):**
  - `ToggleLikeUseCase` → `POST_LIKE` on first-like only (no notification on unlike or repeat like), skipped if liking your own post
  - `AddCommentUseCase` → `POST_COMMENT` with a 140-char preview of the comment, skipped on self-comment
  - `ToggleFollowUseCase` → `NEW_FOLLOWER`, `link=/users/{followerId}`
  - `SendMessageUseCase` → `NEW_MESSAGE` with a 140-char preview, `link=/messages?conversation={id}` — fired alongside (not instead of) the `MessageBroadcaster` live push
- `NotificationController` — `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all`, `GET /notifications/stream` (SSE)
- `NotificationRepository.countGroupedByType()` — used only by `AdminDashboardController` (§4.7) for `/admin/dashboard/jobs` and `/admin/dashboard/messaging`

**Notification `type` values in use:** `TREND_ALERT`, `JOB_MATCH`, `POST_LIKE`, `POST_COMMENT`, `NEW_FOLLOWER`, `NEW_MESSAGE`.

**Database Tables:**
- `notification` (singular)
- `user_profile.notify_inapp` / `notify_email` (per-user channel prefs) — only checked by `TrendAlertDispatcher`/`JobMatchDispatcher` (both gate in-app AND email fan-out on these flags); the 4 direct social/messaging notifications above ignore both flags and never send email — they always write an in-app `notification` row unconditionally

### 4.11 Salary Feature

**Responsibilities:**
- Salary insights ranked by technology (top techs, min-job threshold)
- Per-technology salary detail (median/avg/P25-P75/min-max, co-occurring techs)
- Free-text salary parsing (Vietnamese job postings write salary as unstructured text)

**Key Components:**
- `GetSalaryInsightsUseCase`, `GetTechSalaryDetailUseCase`
- `SalaryParser` / `SalaryRange` / `SalaryStats` (domain): parses raw strings like `"15-25 triệu"` into numeric ranges — also reused by `features/job`'s min-salary filter
- `Neo4jSalaryRepository` — `GET /salary/top?limit=&min_jobs=`, `GET /salary/tech/{techName}`
- Cached via `ReactiveRedisCache` (see [`docs/DATABASE.md`](./DATABASE.md) §5)

**Data Source:**
- Neo4j (`Job.salary` free text)

### 4.12 Company Feature (NEW)

**Responsibilities:**
- Company directory (ranked by job count)
- Similar-company recommendations (Jaccard similarity of tech stacks)

**Key Components:**
- `CompanyController` — `GET /companies?page=&size=`, `GET /companies/{id}/similar?limit=`
- `GetCompaniesUseCase` — Neo4j result cached whole in Redis (`cache:company:all`, TTL `app.redis.company-cache-ttl`, default 1800s) since it only changes as often as ingestion runs; pagination (`page`/`size`) is applied in-memory on top of the cached list
- `GetSimilarCompaniesUseCase` — in-memory Jaccard similarity, reuses `GetCompaniesUseCase`'s cached list instead of re-querying Neo4j
- `Neo4jCompanyRepository` — infers a company's tech stack via `Company<-[:POSTED_BY|HIRES_FOR]-Job-[:REQUIRES]->Technology` rather than reading the `USES` relationship directly (see [`docs/DATABASE.md`](./DATABASE.md) §4.1 for why this is worth double-checking — `USES` is in fact populated by the data-platform Gold enricher). `POSTED_BY` is the live edge; `HIRES_FOR` is legacy-only data from a removed batch importer, matched too so older jobs aren't dropped.
- `CompanyNames.clean()` — strips a crawler-appended badge line from display names

**Data Source:**
- Neo4j, look-aside cached in Redis (see [`docs/DATABASE.md`](./DATABASE.md) §5) — newly ingested companies/jobs may take up to the cache TTL to appear

### 4.13 Job Feature (NEW — Job Matching)

**Responsibilities:**
- Match job postings to the current user's profile skills, ranked by overlap score
- Optional location / min-salary filtering

**Key Components:**
- `JobController` — `GET /jobs/matches?location=&min_salary=&limit=` (auth required, uses caller's `user_profile.technologies`)
- `GetJobMatchesUseCase` — over-fetches from Neo4j at a fixed `MAX_LIMIT*3`, cached in Redis per distinct (sorted) skill set (`cache:job:match:<skills>`, TTL `app.redis.job-cache-ttl`, default 1800s) so any requested `limit` is served from the same cache entry; location/min-salary filtering happens in Java AFTER the cache read (so it doesn't fragment the cache key) since Cypher can't reliably parse free-text salary
- `Neo4jJobRepository` — `Job-[:REQUIRES]->(Technology|Skill)` matched against the user's lower-cased skill set

**Data Source:**
- Neo4j, look-aside cached in Redis per skill set (see [`docs/DATABASE.md`](./DATABASE.md) §5)

### 4.14 Messaging Feature (NEW — Direct Messages)

**Responsibilities:**
- 1-1 conversations, message history, read receipts
- Real-time delivery
- Emoji reactions on individual messages (NEW)
- File/image attachments on messages (NEW)

**Key Components:**
- `ConversationController` — `GET /conversations?page=&size=`, `POST /conversations/with/{userId}`,
  `GET /conversations/{id}/messages?page=&size=`, `POST /conversations/{id}/messages` (body now
  optionally carries a base64 `attachment` payload — see below), `GET
  /conversations/{conversationId}/messages/{messageId}/attachment` (NEW — serves the raw
  attachment bytes; only the conversation's two participants can fetch it), `POST`/`DELETE
  /conversations/{conversationId}/messages/{messageId}/reactions` (NEW — set/replace vs. remove the
  current user's reaction), `POST /conversations/{id}/read`, `GET /conversations/stream` (SSE)
- `SendMessageUseCase` — persists the message (with an optional attachment, see below), pushes it
  live via `MessageBroadcaster.publish`, AND (best-effort, failure-swallowed) creates a
  `NEW_MESSAGE` notification for the recipient (§4.10)
- `GetConversationsUseCase` (now paginated), `GetMessagesUseCase`, `GetOrCreateConversationUseCase`, `MarkReadUseCase`
- **Reactions (NEW)** — `SetMessageReactionUseCase.execute(conversationId, messageId, userId, emoji)`:
  validates `emoji` against a fixed palette, `SetMessageReactionUseCase.ALLOWED_EMOJI` (`👍 ❤️ 😂 😮
  😢 😡` — a small Messenger-style set rather than arbitrary unicode, to keep aggregated counts
  predictable), confirms the caller is a participant and the message belongs to the conversation,
  then `MessageReactionRepository.upsert()`s the reaction (one row per `(message_id, user_id)`,
  `ON CONFLICT` replaces the emoji — a user can only have one active reaction per message,
  re-reacting swaps it rather than adding a second). `RemoveMessageReactionUseCase.execute(...)`
  mirrors it for deletion. Both re-read all reactions for the message afterward, fold them through
  the package-private `ReactionSummaries.summarize(rows, viewerId)` helper (groups by emoji into a
  `List<MessageReactionSummary>` of `(emoji, count, reactedByMe)` from the caller's own
  perspective), return that to the caller, AND broadcast a viewer-specific summary (`reactedByMe`
  recomputed for the *other* participant) to them via `MessageBroadcaster` — the broadcast is
  best-effort (`onErrorResume` + warn-log, never fails the request).
- **Attachments (NEW)** — `SendMessageUseCase` decodes/validates an optional attachment via the
  shared `shared/util/FileUploadValidator` (max 10 MB, content-type allowlist covering common
  images/PDF/Office docs/`text/plain`/`zip` — deliberately excludes `image/svg+xml` because SVG can
  carry embedded `<script>`, a stored-XSS risk on the public serve endpoint; sibling to
  `ImageUploadValidator`, kept separate because messages allow a broader type set and a larger size
  cap than avatars/post images) and stores the bytes as `direct_message.attachment_data` (BYTEA)
  alongside content-type/filename/size columns. `GetMessageAttachmentUseCase.execute(...)` re-checks
  conversation participancy before returning the bytes; the controller serves them with
  `Content-Disposition: inline` and `X-Content-Type-Options: nosniff`. The attachment columns are
  never selected by the conversation history list query — only the dedicated attachment endpoint
  reads `attachment_data` — so paging through message history stays lightweight.
- `MessageBroadcaster` — cross-instance, backed by Redis Pub/Sub (channel `live:messages`, shared
  `ReactiveRedisMessageListenerContainer` bean). Each instance still holds a local per-user
  `Sinks.Many` for its own SSE subscribers, but `publish()` always goes over Redis first so any
  instance can deliver regardless of where the sender/recipient's SSE connection landed. The SSE
  stream now multiplexes two event kinds through one discriminated payload,
  `MessageLiveEvent(type, message, conversationId, messageId, reactions)` — `type` is
  `NEW_MESSAGE` (carries the full `DirectMessage` in `message`) or `REACTIONS_CHANGED` (carries
  `conversationId`/`messageId`/the recomputed `reactions` list; `message` is null) — mirroring
  `features/social/realtime/FeedEvent`'s flat-record-with-enum-tag shape so Jackson needs no
  polymorphic type annotations. Fire-and-forget by design either way: Postgres remains the source
  of truth.
- `PostgresConversationRepository` / `PostgresMessageRepository` — canonicalizes `user_a_id < user_b_id` to avoid duplicate conversations for the same pair
- `MessageReactionRepository` (port, NEW) / `PostgresMessageReactionRepository` (adapter, NEW) —
  `upsert`/`remove`/`findByMessageId`/`findByMessageIds` (the latter a batch fetch so a page of
  message history can attach reactions in one query instead of N)

**Database Tables:**
- `conversation`, `direct_message` (see [`docs/DATABASE.md`](./DATABASE.md)) — `direct_message`
  gained `attachment_content_type`/`attachment_filename`/`attachment_size`/`attachment_data`
  columns (V31)
- `message_reaction` (NEW, V32) — PK `(message_id, user_id)` so a user has at most one reaction per
  message; `ON DELETE CASCADE` off both `direct_message` and `users`

### 4.15 Social Feature (NEW — Feed / Follow / Like / Comment / Report)

**Responsibilities:**
- Post creation/deletion, feed (self + followees)
- Follow/unfollow, suggested users
- Like/unlike, comments
- Public profile summary (follower/following/post counts)
- User-submitted content reports (moderation flags) on posts/comments — **NEW**
- Triggers in-app notifications on like/comment/follow (§4.10) — **NEW**

**Key Components:**
- `PostController` (bare root paths, no `/social` prefix) — `GET /feed`, `POST /posts`, `DELETE /posts/{id}`, `POST|DELETE /posts/{id}/like`, `GET|POST /posts/{id}/comments`, `POST /posts/{id}/report` (NEW), `POST /comments/{id}/report` (NEW)
- `UserSocialController` (`/users` prefix) — `GET /users/{id}/profile-summary`, `GET /users/{id}/posts`, `POST|DELETE /users/{id}/follow`, `GET /users/suggested?limit=`
- `GetFeedUseCase`, `CreatePostUseCase`, `GetProfileSummaryUseCase`, `GetSuggestedUsersUseCase`
- `ToggleLikeUseCase` — like/unlike; on a genuinely NEW like (not a repeat or unlike), fires a best-effort `POST_LIKE` notification to the post author (skipped if liking your own post)
- `AddCommentUseCase` — validates + inserts, then fires a best-effort `POST_COMMENT` notification (140-char preview) to the post author (skipped on self-comment)
- `ToggleFollowUseCase` — on a genuinely new follow, fires a best-effort `NEW_FOLLOWER` notification to the followee
- `ReportContentUseCase` (NEW) — validates non-empty/≤500-char `reason`, inserts into `content_report` via `ON CONFLICT DO NOTHING` (silently a no-op if this user already has a PENDING report on the same target — see V11/V12 unique-index history in [`docs/DATABASE.md`](./DATABASE.md) §3.2); exactly one of `post_id`/`comment_id` is set
- `PostgresPostRepository` / `PostgresFollowRepository` / `PostgresCommentRepository` / `PostgresReportRepository` (NEW). `PostgresFollowRepository` implements TWO ports: `FollowRepository` (follow/unfollow/isFollowing/counts — the actual follow-relationship graph) and `UserDirectoryRepository` (`findProfileBasics`, `suggested`, `searchByName` — profile lookup/search, used by `GetProfileSummaryUseCase`/`GetSuggestedUsersUseCase`/`SearchUsersUseCase`), split out from a single fat `FollowRepository` interface (ISP fix — profile-search consumers no longer have to depend on follow-mutation methods they never call, and vice versa). One physical class backing two logical ports, same pattern as `PostgresUserRepository implements UserRepository, UserStatsRepository`.

**Admin moderation over this feature lives in the `system` module, not here** — see §4.7-equivalent: `SocialModerationService` + `AdminSocialController` (`/admin/posts`, `/admin/posts/{id}/comments`, `/admin/comments/{id}`, `/admin/reports`, `/admin/reports/{id}/dismiss`) can view/delete ANY post or comment (bypassing the ownership check `DeletePostUseCase` enforces for normal users) and review/dismiss the report queue.

**Database Tables:**
- `post`, `follow`, `post_like`, `post_comment`, `content_report` (NEW, V11/V12) — see [`docs/DATABASE.md`](./DATABASE.md)

### 4.16 AiProxy Feature (NEW — replaces career/forecast/recommend/report/summarize/agent)

**Responsibilities:**
- Forward AI-capability requests to `ai-rag-core` behind a single, generic mechanism instead of
  one bespoke module per capability. **This is a refactor, not a new capability** — the 6
  previously separate feature modules (each with its own `ModuleConfig` + `ServicePort` +
  typed `PythonXClient`) were deleted and replaced by thin controllers over one shared handler.

**Key Components:**
- `AiProxyRequestHandler` — shared plumbing; `forwardAsCurrentUser(...)` attaches `user_id` from the JWT when present, `forward(...)` passes the body through unmodified. Both wrap the Python response as `ApiResponse<Map<String,Object>>` (double-wrapped — whatever JSON `ai-rag-core` returns becomes `data` verbatim) and turn ANY upstream error into a generic `503 SERVICE_UNAVAILABLE`.
- **Rate limiting (NEW)** — `AiProxyRateLimiterService` (Redis INCR+EXPIRE, same mechanism as `AuthRateLimiterService`/`ChatRateLimiterService`), gated inside `AiProxyRequestHandler` itself so every controller gets it for free. `forwardAsCurrentUser(...)` routes are keyed by user id (`ratelimit:aiproxy:user:<id>`); `forward(...)` routes have no user id so they're keyed by client IP (`ratelimit:aiproxy:ip:<ip>`, resolved by each public controller via `ClientIpUtils.resolveClientIp(httpRequest)` and passed in as an extra `forward(...)` parameter). Default 20 req/60s (`app.redis.aiproxy-rate-limit.*`). The gate sits OUTSIDE the upstream-error `onErrorResume`, so a throttled request surfaces as a real `429 RATE_LIMIT_EXCEEDED`, not the generic 503 — get this ordering wrong and the rate limit silently stops working (looks like a 503 instead).
- `PythonAiProxyClient` (implements `AiProxyPort`) — one generic `WebClient.post()` per call, no per-endpoint typed request/response classes anymore.
- Thin controllers, one per legacy path: `AgentController` (`POST /agent`), `CareerController` (`POST /career`), `ForecastController` (`GET /forecast`), `InterviewController` (`POST /interview`, NEW), `RecommendController` (`POST /recommend`), `ReportController` (`GET /report`), `SummarizeController` (`POST /chat/summarize`), `CompanyInsightController` (`POST /company-insight`, NEW).
- **`MonthlyReportSchedulerService` (NEW, `features/aiproxy/application/`)** — a `@Scheduled` cron
  job, disabled by default (`@ConditionalOnProperty(name = "app.report.monthly.enabled",
  havingValue = "true")`, real LLM cost) and configurable via `app.report.monthly.cron` (default
  `0 0 5 1 * *`, i.e. 05:00 on the 1st of each month). On each run it calls
  `AiProxyPort.forward("/report", {period, top_n: 10, format: "markdown"}, ...)` for the *previous*
  calendar month, then persists the returned markdown into `cms_content` (`type="Report"`,
  `status="Pending"`, title `"Báo cáo xu hướng công nghệ tháng M/YYYY"`) via `CmsService.create(...,
  body)` — populating `cms_content.body` (§4.7, V35). An empty/blank report body is logged and
  discarded rather than saved; upstream failures are logged and swallowed (`onErrorResume`), never
  propagated. Before this, every "Report" row in `cms_content` was static seed data with no real
  generator behind it, and the public/ad-hoc `GET /report` (`ReportController`) was never persisted
  anywhere.

**Public vs. authenticated split (`SecurityConfig.PUBLIC_ROUTES`):** the dividing line is which
`AiProxyRequestHandler` method a controller calls. `forward(...)` passes the body through
unmodified — general content with no per-user personalization — and those routes are public:
`/forecast`, `/report`, `/chat/summarize`, `/company-insight`. `forwardAsCurrentUser(...)` attaches
`user_id` from the JWT — the response is personalized to whoever is signed in — so those routes
require auth: `/career`, `/recommend`, `/interview`, `/agent`. `/company-insight` was added after
this principle was already in place but initially left out of `PUBLIC_ROUTES` by oversight; since
it's rendered on the public `/companies` page (itself public), anonymous visitors got a spurious
401 that the web client's interceptor treated as a logged-out session — fixed by adding it
alongside the other `forward()`-only routes.

**Data Source:**
- `ai-rag-core` (FastAPI) — see [`docs/AI_PLATFORM.md`](./AI_PLATFORM.md)

---

## 5. Database Layer

> Đây là phần **implementation patterns** (config mẫu, cách viết repository). Cho schema đầy đủ
> (mọi bảng, ai sở hữu/ghi/đọc, Neo4j node/relationship, Redis key) xem tài liệu riêng
> [`docs/DATABASE.md`](./DATABASE.md).

### 5.1 PostgreSQL (R2DBC)

**Configuration:**
```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
```

**Repository Pattern:**
```java
@Repository
public class UserRepositoryImpl implements UserRepository {
    private final R2dbcEntityTemplate template;
    
    @Override
    public Mono<User> findById(UUID id) {
        return template.select(User.class)
            .matching(Query.query(Criteria.where("id").is(id)))
            .one();
    }
    
    @Override
    public Flux<User> findAll() {
        return template.select(User.class).all();
    }
    
    @Override
    public Mono<User> save(User user) {
        return template.insert(user);
    }
    
    @Override
    public Mono<User> update(User user) {
        return template.update(user);
    }
    
    @Override
    public Mono<Void> deleteById(UUID id) {
        return template.delete(User.class)
            .matching(Query.query(Criteria.where("id").is(id)))
            .all()
            .then();
    }
}
```

**Flyway Migrations:**
- Located in `src/main/resources/db/migration/`
- Naming convention: `V{version}__{description}.sql`
- Executed at startup via Flyway auto-configuration

### 5.2 Neo4j

**Configuration:**
```java
@Configuration
public class Neo4jConfig {
    
    @Bean
    public Driver neo4jDriver(
            @Value("${neo4j.uri}") String uri,
            @Value("${neo4j.username}") String username,
            @Value("${neo4j.password}") String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
    
    @Bean
    public SessionFactory neo4jSessionFactory(Driver driver) {
        return new SessionFactory(driver, "com.techpulse.features.graph.domain.model");
    }
}
```

**Cypher Query Execution:**
```java
@Repository
public class GraphRepositoryImpl implements GraphRepository {
    private final Driver driver;
    
    @Override
    public Flux<GraphNode> exploreGraph(List<String> keywords, int depth) {
        return Mono.fromCallable(() -> driver.session())
            .flatMapMany(session -> Flux.using(
                session,
                s -> {
                    String cypher = """
                        MATCH (n)
                        WHERE any(keyword IN $keywords WHERE toLower(n.name) CONTAINS toLower(keyword))
                        CALL apoc.path.subgraphAll(n, {
                            maxLevel: $depth,
                            relationshipFilter: "MENTIONS|REQUIRES|RELATED_TO"
                        })
                        YIELD nodes, relationships
                        RETURN nodes, relationships
                        """;
                    return Flux.from(s.run(cypher, 
                        Map.of("keywords", keywords, "depth", depth)))
                        .flatMap(record -> Mono.just(record));
                },
                Session::close
            ));
    }
}
```

### 5.3 Redis

**Configuration:**
```java
@Configuration
public class RedisConfig {
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.redis.host}") String host,
            @Value("${spring.redis.port}") int port) {
        return new LettuceConnectionFactory(host, port);
    }
    
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            LettuceConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }
}
```

**Use Cases:**
- Token blacklist (logout/refresh) — `TokenBlacklistService`, key `blacklist:token:<hashCode>`
- Chat rate limiting — `ChatRateLimiterService`, key `ratelimit:chat:<userId>` (fixed window `INCR`+`EXPIRE`)
- Look-aside cache for `radar`/`salary`/`clustering`/**`company`**/**`job`** reads — generic `ReactiveRedisCache` (`getOrLoad`/`getOrLoadMono`); admin-evictable for company/job via `CacheAdminController` (§4.7)
- **Cross-instance SSE fan-out (Pub/Sub, NEW)** — `MessageBroadcaster` (channel `live:messages`) and `NotificationService` (channel `live:notifications`) both publish via Redis instead of writing their local `Sinks.Many` directly, so every backend instance delivers to whichever SSE clients it's holding locally. This is what makes messaging/notification realtime actually work in a multi-instance deployment — see [`docs/DATABASE.md`](./DATABASE.md) §5.
- **Not used** by `social`/`aiproxy`/`interview` — they read Neo4j/Postgres or proxy to `ai-rag-core` directly, uncached, on every request.
- Session storage (optional)

---

## 6. Security & Authentication

### 6.1 JWT Authentication

**Token Provider** (`config/JwtTokenProvider.java`, real code trimmed):
```java
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public enum TokenType { ACCESS("access"), REFRESH("refresh"); /* ... */ }

    @Value("${app.jwt.secret}") private String jwtSecret;
    @Value("${app.jwt.expiration}") private long jwtExpiration;             // default 86400000 (24h)
    @Value("${app.jwt.refresh-expiration}") private long refreshExpiration; // default 604800000 (7d)

    // Access token: embeds role + full RBAC permission list + security stamp, so authorization
    // never needs a DB round-trip per request.
    public String generateToken(String userId, String email, String role,
                                 List<String> permissions, String securityStamp) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("email", email);
        claims.put("role", role);
        claims.put("permissions", permissions);
        claims.put("stamp", securityStamp);
        claims.put("token_type", TokenType.ACCESS.claimValue());
        return createToken(claims, userId, jwtExpiration);
    }

    public String generateRefreshToken(String userId) { /* token_type=REFRESH, no permissions claim */ }

    public boolean isTokenValid(String token) { /* try/catch parseSignedClaims */ }
    public boolean isAccessToken(String token) { /* token_type claim == "access" */ }
    // + getUserIdFromToken / getEmailFromToken / getRoleFromToken / getPermissionsFromToken /
    //   getStampFromToken / isTokenExpired
}
```

**Authentication** — không có `WebFilter` tự viết tên `JwtAuthenticationFilter`. Cơ chế thật:
`config/security/JwtServerAuthenticationConverter` trích Bearer token thành một
`Authentication` chưa xác thực, rồi `JwtReactiveAuthenticationManager` (implements
`ReactiveAuthenticationManager`) xác thực nó:

```java
@Component
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityStampService securityStampService;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        return Mono.fromCallable(() -> decode(token))          // isTokenValid + isAccessToken
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::verifyStampAndBuildAuthentication);
    }

    // Rejects the token if users.security_stamp has been bumped since issuance (role/status/
    // password change by an admin) — this is how a token gets revoked BEFORE it naturally
    // expires, without a Redis blacklist lookup on every request.
    private Mono<Authentication> verifyStampAndBuildAuthentication(DecodedToken decoded) {
        return securityStampService.currentStamp(decoded.userId())
                .flatMap(currentStamp -> currentStamp.equals(decoded.stamp())
                        ? Mono.just(buildAuthentication(decoded))
                        : Mono.error(new BadCredentialsException("Token revoked: security stamp mismatch")))
                .switchIfEmpty(Mono.fromCallable(() -> buildAuthentication(decoded)));
    }

    // Authorities: ROLE_<ROLE> + one SimpleGrantedAuthority per RBAC permission code carried in
    // the token — this is what makes @PreAuthorize("hasAuthority('user:manage')") work.
}
```

### 6.2 Security Configuration

Thật ra không có danh sách `pathMatchers(...)` rải rác — mọi route public là MỘT nguồn sự thật
duy nhất, `SecurityConfig.PUBLIC_ROUTES` (`List<PublicRoute>`, mỗi entry có method + pattern),
dùng chung bởi cả JWT filter (bỏ qua xác thực) lẫn `authorizeExchange` (permitAll) — nên 2 chỗ
không thể lệch nhau:

```java
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // spring.webflux.base-path (/api/v1) bị strip TRƯỚC khi security filter chạy, nên pattern ở
    // đây KHÔNG kèm /api/v1.
    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            PublicRoute.anyMethod("/auth/login"),
            PublicRoute.anyMethod("/auth/register"),
            PublicRoute.anyMethod("/auth/refresh"),
            PublicRoute.anyMethod("/health"), PublicRoute.anyMethod("/status"),
            PublicRoute.anyMethod("/actuator/**"),
            PublicRoute.anyMethod("/forecast"), PublicRoute.anyMethod("/report"),
            PublicRoute.anyMethod("/company-insight"),
            new PublicRoute(HttpMethod.GET, "/companies/**"),
            new PublicRoute(HttpMethod.GET, "/posts/*/comments")
            // ... đầy đủ ở SecurityConfig.java thật, xem §11 API_DOCs_v1.md
    );

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http, JwtReactiveAuthenticationManager authenticationManager,
            JwtServerAuthenticationConverter authenticationConverter,
            CorsConfigurationSource corsConfigurationSource) {

        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authenticationManager);
        jwtFilter.setServerAuthenticationConverter(authenticationConverter);
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        jwtFilter.setRequiresAuthenticationMatcher(
                new NegatedServerWebExchangeMatcher(ServerWebExchangeMatchers.matchers(publicRouteMatchers())));

        return http
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(a -> a.matchers(publicRouteMatchers()).permitAll().anyExchange().authenticated())
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .build();
    }
}
```

Không có `hasRole("ADMIN")` khai báo tĩnh trong `SecurityConfig` cho `/admin/**` — mỗi admin
controller tự gate bằng `@PreAuthorize("hasAuthority('<permission-code>')")` theo permission RBAC
(xem migration `V24__rbac_permissions.sql`, `AdminPermissionMappingTest`).

### 6.3 Role-Based Access Control

Permission-based, không phải `hasRole("ADMIN")` tĩnh — mỗi admin controller gate bằng permission
code riêng của nó (`UserAdminController` thật, `features/user/adapters/input/`):

```java
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public Flux<UserSummaryResponse> listUsers() { /* ... */ }

    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public Mono<ResponseEntity<ApiResponse<UserSummaryResponse>>> createUser(
            @Valid @RequestBody CreateUserRequest request) { /* ... */ }
}
```

13 permission code hiện có (`roles`/`permissions`/`role_permissions` bảng Postgres, migration
`V24`/`V27`): `user:manage`, `notification:manage`, `analytics:manage`, `cms:manage`,
`crawler:manage`, `cache:manage`, `system:settings`, `datapipeline:manage`, `social:moderate`,
`audit:view`, `dashboard:view`, `clustering:manage`, `graph:manage` — role `admin` có tất cả, role
`moderator` chỉ có `social:moderate` + `audit:view`. `AdminPermissionMappingTest` là regression
guard: mọi method `@PreAuthorize` trên admin controller phải khớp đúng permission code này.

---

## 7. API Design

### 7.1 Response Format

**Standard Response** (`shared/dto/ApiResponse.java` — Lombok `@Data @Builder` class, KHÔNG phải
`record`, vì cần builder + `@JsonInclude(NON_NULL)` để bỏ field null khỏi JSON):
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private String errorCode;
    private List<FieldErrorDetail> errors;   // per-field validation errors; null mọi trường hợp khác
    private long timestamp;

    public record FieldErrorDetail(String field, String message) {}

    public static <T> ApiResponse<T> success(T data) { /* success=true, timestamp=now */ }
    public static <T> ApiResponse<T> success(T data, String message) { /* + message */ }
    public static <T> ApiResponse<T> error(String message, String errorCode) { /* success=false */ }
    public static <T> ApiResponse<T> error(String message, String errorCode, List<FieldErrorDetail> errors) { }
}
```

**Auth response** (`features/auth/application/LoginResponse.java` — cũng là Lombok class, không
phải record; `userId`/`role` là `String` phẳng, không phải `UUID`/enum):
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String email;
    private String role;
    private long expiresIn;
}
```

### 7.2 Controller Pattern

Principal là **user id dạng String** (`JwtReactiveAuthenticationManager` set nó làm
`Authentication.getName()`), KHÔNG phải một `User` object — không có `@AuthenticationPrincipal`
kèm cast `.cast(User.class)` ở đâu trong codebase. Đọc user id hiện tại luôn qua
`SecurityUtils.currentUserId(): Mono<String>` (`shared/security/SecurityUtils.java`). Trả về
`Mono<ResponseEntity<ApiResponse<T>>>`, không phải bare `Mono<ApiResponse<T>>`:

```java
// features/user/adapters/input/UserController.java (thật, rút gọn)
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final ProfileService profileService;

    @GetMapping("/profile")
    public Mono<ResponseEntity<ApiResponse<ProfileResponse>>> getProfile() {
        return SecurityUtils.currentUserId()
                .flatMap(profileService::getProfile)
                .map(profile -> ResponseEntity.ok(ApiResponse.success(profile)));
    }

    @PutMapping("/profile")
    public Mono<ResponseEntity<ApiResponse<ProfileResponse>>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> profileService.updateProfile(userId, request))
                .map(profile -> ResponseEntity.ok(ApiResponse.success(profile, "Profile updated")));
    }
}
```

### 7.3 Validation

Request DTO ở `features/auth/application/` là Lombok `@Data @Builder` class (không phải
`record` — cần `@NoArgsConstructor` để Jackson deserialize + `@Builder` cho test fixture):

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

### 7.4 Error Handling

Không dùng `@ControllerAdvice` + `ServerResponse`/`ServerRequest` (đó là kiểu functional
`RouterFunction` endpoint — codebase này toàn `@RestController` annotated). Thật là
`@RestControllerAdvice` trả thẳng `ResponseEntity<ApiResponse<Void>>`, và mọi exception nghiệp vụ
kế thừa `AppException` (mang sẵn `ErrorCode` → HTTP status, xem `shared/exception/ErrorCode.java`)
thay vì mỗi loại lỗi một `@ExceptionHandler` liệt kê tay
(`shared/exception/GlobalExceptionHandler.java`, rút gọn):

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        if (ex.getStatusCode() >= 500) log.error("Application error", ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(WebExchangeBindException ex) {
        return validationResponse(ex.getBindingResult().getFieldErrors());
    }

    // + AccessDeniedException/AuthorizationDeniedException → 403, ConstraintViolationException,
    // ServerWebInputException, DataBufferLimitException → 413, và một catch-all Exception.class
    // cuối cùng cho bug/lỗi thư viện thứ ba chưa map riêng.
}
```

---

## 8. External Service Integration

### 8.1 ai-rag-core Integration

**WebClient**: mọi client Python (`ai-rag-core`, `ml-clustering`) đi qua MỘT factory dùng chung,
không phải mỗi client tự khai báo `@Bean WebClient` riêng
(`shared/client/PythonServiceWebClientFactory.java`):

```java
public final class PythonServiceWebClientFactory {
    /** internalToken được gửi làm header X-Internal-Auth, bỏ qua nếu null/blank. */
    public static WebClient build(WebClient.Builder webClientBuilder, String baseUrl, String internalToken) {
        WebClient.Builder builder = webClientBuilder.baseUrl(baseUrl);
        if (internalToken != null && !internalToken.isBlank()) {
            builder = builder.defaultHeader("X-Internal-Auth", internalToken);
        }
        return builder.build();
    }
}
```

**Service Integration** (`features/chat/adapters/output/PythonChatClient.java`, rút gọn — userId
là `String`, không phải `UUID`, khớp với principal ở §6.1):
```java
@Component
@RequiredArgsConstructor
public class PythonChatClient implements ChatPort {

    private final WebClient chatWebClient;

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        return chatWebClient.post()
                .uri("/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(Duration.ofSeconds(120));
    }

    // + createSession/getHealth — không có onErrorMap map lỗi HTTP cụ thể nào ở tầng này; lỗi
    // upstream được xử lý ở GlobalExceptionHandler (§7.4).
}
```

> `PythonChatClient` above is specific to `/chat` + `/chat/stream` (typed request/response,
> §4.4). Every OTHER ai-rag-core capability (`/recommend /forecast /career /summarize /report
> /agent /interview`) goes through the generic, untyped `PythonAiProxyClient`/`AiProxyPort`
> described in §4.16 — one `WebClient.post()` forwarding a raw `Map<String,Object>`, no
> per-endpoint DTOs. Don't use `PythonChatClient` as a template for adding a new AI endpoint;
> extend `aiproxy` instead unless the new endpoint genuinely needs typed request/response
> handling like chat does.

### 8.2 ml-clustering Integration

`features/clustering/adapters/output/PythonClusteringClient.java` (implements
`ClusteringServicePort`), rút gọn:

```java
@Component
@RequiredArgsConstructor
public class PythonClusteringClient implements ClusteringServicePort {

    private final WebClient clusteringWebClient;

    @Override
    public Flux<Map<String, Object>> getClusters(boolean isCoherent) {
        return clusteringWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/clusters").queryParam("is_coherent", isCoherent).build())
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(60));
    }

    @Override
    public Mono<Map<String, Object>> predictBatch(List<String> techNames) {
        return clusteringWebClient.post()
                .uri("/predict/batch")
                .bodyValue(Map.of("tech_names", techNames))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(60));
    }
}
```

### 8.3 Resilience

**Resilience4j circuit breaker** (`resilience4j-spring-boot3` + `resilience4j-reactor` trong
`pom.xml`) bọc quanh mọi lệnh gọi tới `ai-rag-core`/`ml-clustering`, ở đúng 1 điểm chung:
`AbstractPythonServiceClient.mapMono`/`mapFlux` (`shared/http/`) — 5 client con
(`PythonChatClient`, `PythonAiClient`, `PythonAiProxyClient`, `PythonModerationClient`,
`PythonClusteringClient`) không tự viết logic circuit breaker, chỉ inject đúng bean qua
`@Qualifier`. Chi tiết + lý do thiết kế: xem
[`docs/adr/0007-circuit-breaker-for-python-service-calls.md`](./adr/0007-circuit-breaker-for-python-service-calls.md).
Tóm tắt:
- **2 circuit breaker instance, theo SERVICE đích chứ không theo client class**:
  `aiRagCoreCircuitBreaker` (dùng chung bởi cả 4 client gọi `ai-rag-core`) và
  `mlClusteringCircuitBreaker` (`ml-clustering`) — bean định nghĩa ở `config/Resilience4jConfig.java`,
  ngưỡng cấu hình ở `application.yml` (`resilience4j.circuitbreaker.instances.*`).
- **Timeout + retry (cho lệnh gọi idempotent) vẫn giữ nguyên** như trước, circuit breaker bọc
  NGOÀI cùng pipeline retry+timeout đã có — nên nó ghi nhận đúng 1 kết quả/lệnh gọi (không phải
  1 kết quả/lần retry), và coi timeout cũng là 1 lỗi.
- **`CallNotPermittedException`** (circuit đang mở) được map riêng thành
  `DatabaseUnavailableException` với message chứa "circuit breaker open" — phân biệt được với lỗi
  mạng/timeout thật khi đọc log.
- Trạng thái circuit phơi ra qua `/actuator/health` (`management.health.circuitbreakers.enabled=true`).
- **`AiProxyRequestHandler`** (`features/aiproxy`) vẫn gộp MỌI lỗi từ `ai-rag-core` (kể cả
  `CallNotPermittedException` đã map) thành `503 SERVICE_UNAVAILABLE` chung phía client, không
  phân biệt loại lỗi upstream ở tầng response (xem §4.16) — phân biệt chỉ có ở log/metrics.
- **Rate limiting** (khác circuit breaker — chặn *trước* khi gọi, không phải phản ứng *sau* khi
  upstream lỗi) qua Redis INCR+EXPIRE: `AuthRateLimiterService`, `ChatRateLimiterService`,
  `AiProxyRateLimiterService` (`shared/redis/`).

---

## 9. Testing

### 9.1 Unit Testing

Chuẩn thật trong repo: JUnit 5 + Mockito + `StepVerifier` (Reactor), test trực tiếp tầng
`application` (use case). Khởi tạo class thật bằng `new XxxUseCase(mock1, mock2, ...)` qua
constructor — KHÔNG dùng `@InjectMocks` (nhiều use case có nhiều constructor param cùng kiểu,
`@InjectMocks` dễ wire nhầm). `User.role`/`User.status` là `String` phẳng (không có enum
`Role`/`Status`) — build fixture bằng `.role("user").status("active")`. Rút gọn từ
`LoginUseCaseTest` (`features/auth/application/`):

```java
@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenIssuer tokenIssuer;

    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LoginUseCase(userRepository, passwordEncoder, tokenIssuer);
    }

    private User activeUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("dev@example.com")
                .passwordHash("hashed")
                .role("user")
                .status("active")
                .build();
    }

    @Test
    void execute_returnsTokens_whenCredentialsValid() {
        User user = activeUser();
        LoginResponse response = LoginResponse.builder()
                .accessToken("access").refreshToken("refresh")
                .userId(user.getId().toString()).email(user.getEmail()).role(user.getRole())
                .expiresIn(3600L)
                .build();
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(tokenIssuer.issueFor(user)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(
                        LoginRequest.builder().email("dev@example.com").password("correct").build()))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void execute_fails_whenEmailNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(
                        LoginRequest.builder().email("ghost@example.com").password("x").build()))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }
}
```

### 9.2 Integration Testing (Testcontainers — Postgres/Neo4j/Redis thật, tự quản lý)

Mỗi integration test dùng **Postgres/Neo4j/Redis thật chạy trong container Testcontainers** —
không cần cài/khởi động gì thủ công, không cần Docker Compose hay set env var trước, chỉ cần
Docker chạy được trên máy:

```bash
mvn test
```

`IntegrationTestSupport` khai báo 3 container (`PostgreSQLContainer`, `Neo4jContainer`,
`GenericContainer` cho Redis) là `static final` + `start()` trong static initializer — **singleton
container pattern**, không dùng `@Testcontainers`/`@Container` (annotation đó dừng container sau
mỗi test class, trong khi pattern này cần dùng chung 1 bộ container cho mọi `*IntegrationTest`
subclass, giống cách Spring cache chung 1 `@SpringBootTest` context). Port/URL thật được wire vào
Spring qua `@DynamicPropertySource` (`spring.r2dbc.url`, `spring.flyway.url`, `app.neo4j.uri`,
`spring.data.redis.host/port`). Testcontainers' Ryuk reaper tự dọn container khi JVM thoát, không
cần gọi `stop()`.

Mỗi test class (`AuthIntegrationTest`, `ChatIntegrationTest`, `CompanyIntegrationTest`,
`FeedIntegrationTest`, `JobMatchIntegrationTest`, ... — package
`com.techpulse.techradar.integration`) extends `IntegrationTestSupport`
(`@SpringBootTest(webEnvironment = RANDOM_PORT)`). `ClusteringServicePort`/`ChatPort` (proxy sang
Python) được mock qua `@MockitoBean`; chỉ Postgres/Neo4j/Redis là thật. Request đi qua
`WebTestClient` bind thẳng vào server đang chạy (`WebTestClient.bindToServer()`, không phải
`bindToController`), nên chạy qua toàn bộ filter chain thật (security, rate limit...).
`IntegrationTestSupport` có sẵn helper `registerAndLogin`/`adminToken`/`seedGraph`/`seedCompany`
dùng chung cho mọi subclass:

```java
class AuthIntegrationTest extends IntegrationTestSupport {

    @Test
    void register_thenLogin_returnsAccessToken() {
        String token = registerAndLogin("dev-" + UUID.randomUUID() + "@example.com");
        assertThat(token).isNotBlank();
    }
}
```

### 9.3 WebFlux Controller Testing

Chuẩn thật trong repo: mock các use case, khởi tạo controller trực tiếp qua constructor (`new
XxxController(mock1, ...)`) — không có `@InjectMocks`, không dùng `WebTestClient` — rồi gọi thẳng
method của controller và verify `Mono<ResponseEntity<ApiResponse<T>>>` trả về bằng `StepVerifier`.
Rút gọn từ `RadarControllerTest` (`features/radar/adapters/input/`):

```java
@ExtendWith(MockitoExtension.class)
class RadarControllerTest {

    @Mock private GetTopTechnologiesUseCase getTopTechnologiesUseCase;
    @Mock private SearchTrendUseCase searchTrendUseCase;
    @Mock private RadarExporter radarExporter;
    @Mock private RadarBroadcaster radarBroadcaster;

    private RadarController controller;

    @BeforeEach
    void setUp() {
        controller = new RadarController(
                getTopTechnologiesUseCase, searchTrendUseCase, radarExporter, radarBroadcaster);
    }

    @Test
    void getTop4_mapsSnapshotsToTop4Items() {
        when(getTopTechnologiesUseCase.execute(4)).thenReturn(Flux.just(
                new TechSnapshot("Kotlin", 120, 45.0, 32.0, 40)));

        StepVerifier.create(controller.getTop4())
                .assertNext(response -> {
                    ApiResponse<List<RadarDtos.Top4Item>> body = response.getBody();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0).getIndustry()).isEqualTo("Kotlin");
                })
                .verifyComplete();
    }
}
```

---

## 10. Deployment

### 10.1 Docker Build

```dockerfile
# apps/backend/Dockerfile
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline    # cache layer riêng — chỉ re-run khi pom.xml đổi

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENV APP_ENV=prod
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 10.2 Environment Variables

```yaml
# docker-compose.yml
spring-api:
  environment:
    APP_ENV: ${APP_ENV:-dev}
    POSTGRES_HOST: postgres
    POSTGRES_PORT: "5432"
    POSTGRES_DB: techradar
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: postgres
    NEO4J_URI: bolt://neo4j:7687
    NEO4J_USERNAME: neo4j
    NEO4J_PASSWORD: password
    REDIS_HOST: redis
    REDIS_PORT: "6379"
    JWT_SECRET: ${JWT_SECRET:-change-this-in-production}
    PYTHON_RAG_BASE_URL: http://ai-rag-core:8000
    PYTHON_ML_CLUSTERING_BASE_URL: http://ml-clustering:8001
    PYTHON_INTERNAL_TOKEN: ${INTERNAL_API_TOKEN:-techradar-internal-secret}
```

### 10.3 Health Checks

```bash
# Check health
curl http://localhost:8080/health

# Check actuator endpoints
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
```

---

## Best Practices

### 1. Use Reactive Programming

```java
// Good
public Mono<User> findById(UUID id) {
    return userRepository.findById(id);
}

// Bad (blocking)
public User findById(UUID id) {
    return userRepository.findById(id).block();
}
```

### 2. Handle Errors Properly

```java
// Good
userRepository.findById(id)
    .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
    .flatMap(user -> processUser(user))
    .onErrorResume(ResourceNotFoundException.class, ex -> 
        Mono.just(ApiResponse.error(ex.getMessage(), "NOT_FOUND")));

// Bad
userRepository.findById(id)
    .flatMap(user -> processUser(user));
```

### 3. Use DTOs for API Layer

```java
// Good
public record LoginRequest(String email, String password) {}
public record LoginResponse(String accessToken, String refreshToken) {}

// Bad (exposing domain entities)
public Mono<User> login(String email, String password) {}
```

### 4. Keep Controllers Thin

```java
// Good
@RestController
public class UserController {
    private final UserProfileService userProfileService;
    
    @GetMapping("/profile")
    public Mono<ApiResponse<UserProfile>> getProfile(
            @AuthenticationPrincipal Mono<Authentication> auth) {
        return auth
            .map(Authentication::getPrincipal)
            .cast(User.class)
            .flatMap(userProfileService::getProfile)
            .map(ApiResponse::success);
    }
}

// Bad (business logic in controller)
@RestController
public class UserController {
    @GetMapping("/profile")
    public Mono<ApiResponse<UserProfile>> getProfile(
            @AuthenticationPrincipal Mono<Authentication> auth) {
        return auth
            .map(Authentication::getPrincipal)
            .cast(User.class)
            .flatMap(user -> {
                // Business logic here...
                return Mono.just(profile);
            })
            .map(ApiResponse::success);
    }
}
```

### 5. Use Configuration Classes

```java
// Good
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(...)
            .build();
    }
}

// Bad (configuration in main class)
@SpringBootApplication
public class TechRadarApplication {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // Configuration here...
    }
}
```

---

## Resources

- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Data R2DBC](https://docs.spring.io/spring-data/r2dbc/reference/)
- [Neo4j Java Driver](https://neo4j.com/docs/java-manual/current/)
- [Reactive Programming with Project Reactor](https://projectreactor.io/docs)
