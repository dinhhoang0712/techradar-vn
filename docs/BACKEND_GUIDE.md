# Backend Development Guide — TechRadar VN

> Tài liệu chi tiết về kiến trúc, phát triển và best practices cho Spring Boot backend.

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Kiến trúc Hexagonal](#2-kiến-trúc-hexagonal)
3. [Cấu trúc dự án](#3-cấu-trúc-dự án)
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
// Input Port (Use Case Interface)
public interface LoginUseCase {
    Mono<LoginResponse> login(LoginRequest request);
}

// Output Port (Repository Interface)
public interface UserRepository {
    Mono<User> findById(UUID id);
    Mono<User> save(User user);
}

// Application Service
@Service
public class LoginService implements LoginUseCase {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    
    public LoginService(UserRepository userRepository, 
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    @Override
    public Mono<LoginResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
            .switchIfEmpty(Mono.error(new AuthenticationException("User not found")))
            .flatMap(user -> {
                if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                    return Mono.error(new AuthenticationException("Invalid password"));
                }
                String accessToken = jwtTokenProvider.generateAccessToken(user);
                String refreshToken = jwtTokenProvider.generateRefreshToken(user);
                return Mono.just(LoginResponse.from(user, accessToken, refreshToken));
            });
    }
}

// Input Adapter (REST Controller)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final LoginUseCase loginUseCase;
    
    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }
    
    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return loginUseCase.login(request);
    }
}

// Output Adapter (Repository Implementation)
@Repository
public class UserRepositoryImpl implements UserRepository {
    private final R2dbcEntityTemplate template;
    
    public UserRepositoryImpl(R2dbcEntityTemplate template) {
        this.template = template;
    }
    
    @Override
    public Mono<User> findById(UUID id) {
        return template.select(User.class)
            .matching(Query.query(Criteria.where("id").is(id)))
            .one();
    }
    
    @Override
    public Mono<User> save(User user) {
        return template.insert(user);
    }
}
```

---

## 3. Cấu trúc dự án

```
apps/backend/src/main/java/com/techpulse/
├── TechRadarApplication.java          # Main entry point
├── features/                           # Feature modules
│   ├── auth/                          # Authentication feature
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── User.java
│   │   │   │   ├── Role.java
│   │   │   │   └── UserProfile.java
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java
│   │   │   └── service/
│   │   │       ├── LoginService.java
│   │   │       ├── RegisterService.java
│   │   │       └── PasswordResetService.java
│   │   ├── application/
│   │   │   ├── port/in/
│   │   │   │   ├── LoginUseCase.java
│   │   │   │   └── RegisterUseCase.java
│   │   │   └── port/out/
│   │   │       └── UserRepository.java
│   │   └── adapter/
│   │       ├── in/web/
│   │       │   ├── AuthController.java
│   │       │   └── dto/
│   │       │       ├── LoginRequest.java
│   │       │       ├── LoginResponse.java
│   │       │       └── RegisterRequest.java
│   │       └── out/persistence/
│   │           ├── UserRepositoryImpl.java
│   │           └── mapper/
│   │               └── UserMapper.java
│   │   └── AuthModuleConfig.java
│   │
│   ├── radar/                         # Tech radar feature
│   ├── compare/                       # Technology comparison
│   ├── graph/                         # Knowledge graph explorer + salary/sentiment filter
│   ├── chat/                          # RAG chat
│   ├── clustering/                    # ML clustering
│   ├── salary/                        # Salary insights (Neo4j, salary-text parsing)
│   ├── notification/                  # In-app/email notifications + trend-alert dispatch
│   ├── company/                       # NEW — Company Explorer (Neo4j)
│   ├── job/                           # NEW — Job Matching (Neo4j)
│   ├── messaging/                      # NEW — 1-1 direct messages (Postgres + SSE broadcaster)
│   ├── social/                         # NEW — posts/follow/like/comment feed (Postgres)
│   ├── aiproxy/                        # NEW — replaces career/forecast/recommend/report/
│   │                                    # summarize/agent: one generic forwarder to ai-rag-core
│   ├── user/                          # User management
│   ├── system/                        # System settings
│   ├── health/                        # Health checks
│   └── kafka/                         # Kafka event handling
│
├── shared/                            # Shared infrastructure
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── R2dbcConfig.java
│   │   ├── Neo4jConfig.java
│   │   ├── RedisConfig.java
│   │   ├── KafkaConfig.java
│   │   └── WebFluxConfig.java
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtTokenProvider.java
│   │   └── PasswordEncoder.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── AuthenticationException.java
│   │   └── ResourceNotFoundException.java
│   ├── common/
│   │   ├── ApiResponse.java
│   │   ├── PageResponse.java
│   │   └── Constants.java
│   └── util/
│       └── DateUtil.java
│
└── infrastructure/
    ├── flyway/
    │   └── migrations/
    │       ├── V1__create_users.sql
    │       ├── V2__create_chat_tables.sql
    │       ├── V3__create_analytics.sql
    │       ├── V4__create_cms.sql
    │       ├── V5__create_system.sql
    │       ├── V900__seed_admin_user.sql
    │       └── V901__seed_settings.sql
    └── kafka/
        ├── producer/
        │   └── KafkaEventPublisher.java
        └── consumer/
            └── KafkaEventListener.java
```

---

## 4. Feature Modules

### 4.1 Auth Feature

**Responsibilities:**
- User registration and login
- JWT token generation and validation
- Password reset flow
- Refresh token rotation

**Key Components:**
- `LoginService`: Authenticate user, generate tokens
- `RegisterService`: Create new user, hash password
- `PasswordResetService`: Handle forgot/reset password
- `JwtTokenProvider`: Generate and validate JWT tokens
- `JwtAuthenticationFilter`: Filter for JWT authentication

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
- `RadarAnalyticsService`: Compute trend analytics
- `RadarSearchService`: Search technologies by keywords
- `RadarExportService`: Export radar visualizations

**Data Source:**
- PostgreSQL `tech_analytics` table (populated by Gold ETL)

### 4.3 Graph Feature

**Responsibilities:**
- Knowledge graph exploration
- Graph traversal queries
- Node and edge filtering
- Shortest path analysis

**Key Components:**
- `GraphExplorerService`: Execute Cypher queries
- `GraphFilterService`: Filter nodes/edges by criteria
- `GraphPathService`: Find shortest paths

**Data Source:**
- Neo4j Knowledge Graph

### 4.4 Chat Feature

**Responsibilities:**
- RAG chat session management
- Message history
- Proxy to ai-rag-core service
- SSE streaming for real-time responses

**Key Components:**
- `ChatSessionService`: Create and manage sessions
- `ChatMessageService`: Store and retrieve messages
- `RagProxyService`: Proxy requests to ai-rag-core `/chat` and `/chat/stream` (SSE) — a
  **separate** proxy from the `aiproxy` module in §4.16; only chat has its own dedicated proxy.

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
- `ClusteringService`: Retrieve cluster information
- `ClusteringPredictionService`: Predict cluster for technologies

**Data Source:**
- ml-clustering service (FastAPI)

### 4.6 User Feature

**Responsibilities:**
- User profile management
- Avatar upload
- Preference management
- Notification settings

**Key Components:**
- `UserProfileService`: CRUD user profiles
- `AvatarService`: Handle avatar upload/retrieval
- `PreferenceService`: Manage user preferences

### 4.7 System Feature

**Responsibilities:**
- Application settings
- Feature flags
- Activity logging
- Notification management

**Key Components:**
- `SettingsService`: CRUD application settings
- `ActivityLogService`: Log user activities
- `AdminDashboardController` (NEW additions — `hasRole('ADMIN')`): beyond the original `user-count`/`visits`/`monthly-visits`/`top-keywords`, now also:
  - `GET /admin/dashboard/social` → `SocialEngagementStats` (`total_posts`, `posts_today`, `total_comments`, `total_likes`, `total_follows`, `top_posters[]` (`user_id`,`full_name`,`post_count`), `pending_reports`) — reads across `PostRepository`/`CommentRepository`/`FollowRepository`/`ReportRepository`
  - `GET /admin/dashboard/jobs` → `JobMarketStats` (`total_jobs_indexed`, `top_technologies[]` (`name`,`job_count`), `job_match_alerts_sent`) — the last field is `NotificationRepository.countGroupedByType()` filtered to `JOB_MATCH`
  - `GET /admin/dashboard/pipeline` → `KafkaSyncStatus` (`articles_processed/failed`, `jobs_processed/failed`, `last_article_processed_at`, `last_job_processed_at`, `last_failure_at`, `last_failure_message`) — **in-process counters only** (`AtomicLong`/`AtomicReference` fields on `KafkaNeo4jWriterService`), reset to zero on every backend restart, NOT persisted anywhere
  - `GET /admin/dashboard/messaging` → `MessagingStats` (`total_conversations`, `total_messages`, `messages_today`, `notifications_by_type[]` (`type`,`count`))
- `SocialModerationService` + `AdminSocialController` (NEW, feature `system`, delegates into `features/social` ports) — admin-only moderation over the social feed: list/delete ANY post or comment (bypassing ownership), list/dismiss pending `content_report`s. See §4.15 for the endpoint list.
- `CacheAdminController` (NEW, `/admin/cache`, `hasRole('ADMIN')`) — `POST /admin/cache/companies/evict` (single key `cache:company:all`), `POST /admin/cache/jobs/evict` (pattern-evicts every `cache:job:match:*` entry via `ReactiveRedisCache.evictByPattern`). Exists because `company`/`job` (§4.12/§4.13) have no ETL/rebuild step to hang a cache invalidation off of, unlike `radar`'s `AnalyticsAdminController`.

**Database Tables:**
- `settings`: Application settings + feature flags (also read by public `/status`)
- `activity_log`: User activity logs (visits/searches, populated by `ActivityTrackingFilter`)
- `cms_content`: AdminCMS content (Report/Job/Keyword)
- `content_report` (NEW, read/updated via `AdminSocialController`, owned by `features/social` — see §4.15)

> Notifications are their **own** feature module (`features/notification`), not part of
> System — see §4.10.

### 4.8 Health Feature

**Responsibilities:**
- Health check endpoints
- Dependency health checks
- Actuator integration

**Key Components:**
- `HealthCheckService`: Check all dependencies
- `StatusService`: Return feature flags

### 4.9 Kafka Feature

**Responsibilities:**
- Event publishing
- Event consumption
- Trend alert notifications

**Key Components:**
- `KafkaEventPublisher`: Publish events to Kafka
- `KafkaEventListener`: Consume events from Kafka
- `TrendAlertDispatcher`: Dispatch trend alerts to users
- `KafkaNeo4jWriterService` — consumes `extracted_articles`/`extracted_jobs`, writes to Neo4j; **NEW**: also publishes `job.match.alerts` the first time a job is genuinely new (checked via a `MATCH` before the `MERGE`, so re-crawled/updated listings don't re-fire), and tracks in-process throughput/error counters exposed via `syncStatus()` (`KafkaSyncStatus` record — see `GET /admin/dashboard/pipeline`, §4.7). Counters reset on restart, not persisted.
- `JobMatchDispatcher` (NEW) — consumes `job.match.alerts`, fans out to users whose profile technologies overlap the job's, mirroring `TrendAlertDispatcher`. See §4.10.

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
- `Neo4jCompanyRepository` — infers a company's tech stack via `Company<-[:HIRES_FOR]-Job-[:REQUIRES]->Technology` rather than reading the `USES` relationship directly (see [`docs/DATABASE.md`](./DATABASE.md) §4.1 for why this is worth double-checking — `USES` is in fact populated by the data-platform Gold enricher)
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

**Key Components:**
- `ConversationController` — `GET /conversations?page=&size=`, `POST /conversations/with/{userId}`, `GET /conversations/{id}/messages?page=&size=`, `POST /conversations/{id}/messages`, `POST /conversations/{id}/read`, `GET /conversations/stream` (SSE)
- `SendMessageUseCase` — persists the message, pushes it live via `MessageBroadcaster.publish`, AND (best-effort, failure-swallowed) creates a `NEW_MESSAGE` notification for the recipient (§4.10)
- `GetConversationsUseCase` (now paginated), `GetMessagesUseCase`, `GetOrCreateConversationUseCase`, `MarkReadUseCase`
- `MessageBroadcaster` — **updated: cross-instance now**, backed by Redis Pub/Sub (channel `live:messages`, shared `ReactiveRedisMessageListenerContainer` bean). Each instance still holds a local per-user `Sinks.Many` for its own SSE subscribers, but `publish()` always goes over Redis first so any instance can deliver regardless of where the sender/recipient's SSE connection landed — this now DOES fan out correctly in a horizontally-scaled deployment (superseded the earlier in-memory-only design). Fire-and-forget by design either way: Postgres remains the source of truth.
- `PostgresConversationRepository` / `PostgresMessageRepository` — canonicalizes `user_a_id < user_b_id` to avoid duplicate conversations for the same pair

**Database Tables:**
- `conversation`, `direct_message` (see [`docs/DATABASE.md`](./DATABASE.md))

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
- `PostgresPostRepository` / `PostgresFollowRepository` / `PostgresCommentRepository` / `PostgresReportRepository` (NEW)

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
- `PythonAiProxyClient` (implements `AiProxyPort`) — one generic `WebClient.post()` per call, no per-endpoint typed request/response classes anymore.
- Thin controllers, one per legacy path: `AgentController` (`POST /agent`), `CareerController` (`POST /career`), `ForecastController` (`GET /forecast`), `InterviewController` (`POST /interview`, NEW), `RecommendController` (`POST /recommend`), `ReportController` (`GET /report`), `SummarizeController` (`POST /chat/summarize`).

**Known inconsistency worth flagging:** `/forecast`, `/report`, and `/chat/summarize` are public
(`SecurityConfig.PUBLIC_PATHS`) while `/career`, `/recommend`, `/interview`, and `/agent` require
auth — this split predates the refactor (the path strings were simply carried over from the
deleted modules) and was not re-evaluated when consolidating into `aiproxy`.

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

**Token Provider:**
```java
@Component
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.access-token-expiration:900000}") // 15 minutes
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:604800000}") // 7 days
    private long refreshTokenExpiration;
    
    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusMillis(accessTokenExpiration)))
            .signWith(getSigningKey())
            .compact();
    }
    
    public String generateRefreshToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusMillis(refreshTokenExpiration)))
            .signWith(getSigningKey())
            .compact();
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
    
    public Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
    }
}
```

**Authentication Filter:**
```java
@Component
public class JwtAuthenticationFilter implements WebFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveStringRedisTemplate redisTemplate;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Skip public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        
        String token = extractToken(exchange.getRequest());
        
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return Mono.error(new AuthenticationException("Invalid or missing token"));
        }
        
        // Check if token is blacklisted (refresh token)
        Claims claims = jwtTokenProvider.getClaims(token);
        String userId = claims.getSubject();
        
        return redisTemplate.opsForValue().get("blacklist:refresh:" + token)
            .flatMap(blacklisted -> {
                if (blacklisted != null) {
                    return Mono.error(new AuthenticationException("Token has been revoked"));
                }
                return chain.filter(exchange);
            })
            .switchIfEmpty(chain.filter(exchange));
    }
    
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private boolean isPublicPath(String path) {
        return path.equals("/api/v1/auth/login") ||
               path.equals("/api/v1/auth/register") ||
               path.equals("/api/v1/auth/refresh") ||
               path.equals("/health") ||
               path.equals("/status");
    }
}
```

### 6.2 Security Configuration

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/v1/auth/login", "/api/v1/auth/register", 
                              "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
                              "/api/v1/auth/reset-password", "/health", "/status",
                              "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll()
                .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 6.3 Role-Based Access Control

```java
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    @GetMapping("/users")
    public Flux<User> getAllUsers() {
        return userService.getAllUsers();
    }
    
    @PostMapping("/users")
    public Mono<User> createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }
}
```

---

## 7. API Design

### 7.1 Response Format

**Standard Response:**
```java
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    String errorCode,
    long timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null, System.currentTimeMillis());
    }
    
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, null, message, errorCode, System.currentTimeMillis());
    }
}
```

**Bare Response (for auth endpoints):**
```java
public record LoginResponse(
    String accessToken,
    String refreshToken,
    UUID userId,
    String email,
    String role,
    long expiresIn
) {
    public static LoginResponse from(User user, String accessToken, String refreshToken) {
        return new LoginResponse(
            accessToken,
            refreshToken,
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            900000 // 15 minutes
        );
    }
}
```

### 7.2 Controller Pattern

```java
@RestController
@RequestMapping("/api/v1/user")
public class UserController {
    
    private final UserProfileService userProfileService;
    
    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }
    
    @GetMapping("/profile")
    public Mono<ApiResponse<UserProfile>> getProfile(
            @AuthenticationPrincipal Mono<Authentication> auth) {
        return auth
            .map(Authentication::getPrincipal)
            .cast(User.class)
            .flatMap(userProfileService::getProfile)
            .map(ApiResponse::success);
    }
    
    @PutMapping("/profile")
    public Mono<ApiResponse<UserProfile>> updateProfile(
            @AuthenticationPrincipal Mono<Authentication> auth,
            @Valid @RequestBody UpdateProfileRequest request) {
        return auth
            .map(Authentication::getPrincipal)
            .cast(User.class)
            .flatMap(user -> userProfileService.updateProfile(user.getId(), request))
            .map(ApiResponse::success);
    }
}
```

### 7.3 Validation

```java
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    String password
) {}

public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    String fullName,
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).{8,}$",
             message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
    String password,
    
    SubscriptionTier subscriptionTier
) {}
```

### 7.4 Error Handling

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AuthenticationException.class)
    public Mono<ServerResponse> handleAuthenticationException(
            AuthenticationException ex, ServerRequest request) {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
            .bodyValue(ApiResponse.error(ex.getMessage(), "AUTH_ERROR"));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ServerResponse> handleNotFoundException(
            ResourceNotFoundException ex, ServerRequest request) {
        return ServerResponse.status(HttpStatus.NOT_FOUND)
            .bodyValue(ApiResponse.error(ex.getMessage(), "NOT_FOUND"));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ServerResponse> handleValidationException(
            MethodArgumentNotValidException ex, ServerRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return ServerResponse.status(HttpStatus.BAD_REQUEST)
            .bodyValue(ApiResponse.error(message, "VALIDATION_ERROR"));
    }
    
    @ExceptionHandler(Exception.class)
    public Mono<ServerResponse> handleGenericException(
            Exception ex, ServerRequest request) {
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .bodyValue(ApiResponse.error("Internal server error", "INTERNAL_ERROR"));
    }
}
```

---

## 8. External Service Integration

### 8.1 ai-rag-core Integration

**WebClient Configuration:**
```java
@Configuration
public class AiRagClientConfig {
    
    @Value("${python.rag.base-url}")
    private String ragBaseUrl;
    
    @Value("${python.internal.token}")
    private String internalToken;
    
    @Bean
    public WebClient ragWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl(ragBaseUrl)
            .defaultHeader("X-Internal-Auth", internalToken)
            .build();
    }
}
```

**Service Integration:**
```java
@Service
public class RagProxyService {
    
    private final WebClient ragWebClient;
    
    public RagProxyService(WebClient ragWebClient) {
        this.ragWebClient = ragWebClient;
    }
    
    public Mono<RagResponse> chat(String query, UUID userId, UUID sessionId) {
        return ragWebClient.post()
            .uri("/chat")
            .bodyValue(Map.of(
                "query", query,
                "user_id", userId != null ? userId.toString() : null,
                "session_id", sessionId.toString()
            ))
            .retrieve()
            .bodyToMono(RagResponse.class)
            .timeout(Duration.ofSeconds(120))
            .onErrorMap(WebClientResponseException.class, ex -> {
                if (ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                    return new ServiceUnavailableException("AI service unavailable");
                }
                return ex;
            });
    }
    
    public Flux<String> chatStream(String query, UUID userId, UUID sessionId) {
        return ragWebClient.post()
            .uri("/chat/stream")
            .bodyValue(Map.of(
                "query", query,
                "user_id", userId != null ? userId.toString() : null,
                "session_id", sessionId.toString()
            ))
            .retrieve()
            .bodyToFlux(String.class)
            .timeout(Duration.ofSeconds(120));
    }
}
```

> `RagProxyService` above is specific to `/chat` + `/chat/stream` (typed request/response,
> §4.4). Every OTHER ai-rag-core capability (`/recommend /forecast /career /summarize /report
> /agent /interview`) goes through the generic, untyped `PythonAiProxyClient`/`AiProxyPort`
> described in §4.16 — one `WebClient.post()` forwarding a raw `Map<String,Object>`, no
> per-endpoint DTOs. Don't use `RagProxyService` as a template for adding a new AI endpoint;
> extend `aiproxy` instead unless the new endpoint genuinely needs typed request/response
> handling like chat does.

### 8.2 ml-clustering Integration

```java
@Service
public class ClusteringProxyService {
    
    private final WebClient clusteringWebClient;
    
    public ClusteringProxyService(WebClient clusteringWebClient) {
        this.clusteringWebClient = clusteringWebClient;
    }
    
    public Mono<ClustersResponse> getClusters(boolean isCoherent) {
        return clusteringWebClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/clustering/clusters")
                .queryParam("is_coherent", isCoherent)
                .build())
            .retrieve()
            .bodyToMono(ClustersResponse.class)
            .timeout(Duration.ofSeconds(60));
    }
    
    public Mono<ClusterPredictionResponse> predictBatch(List<String> techNames) {
        return clusteringWebClient.post()
            .uri("/clustering/predict/batch")
            .bodyValue(Map.of("tech_names", techNames))
            .retrieve()
            .bodyToMono(ClusterPredictionResponse.class)
            .timeout(Duration.ofSeconds(60));
    }
}
```

### 8.3 Resilience with Circuit Breaker

```java
@Configuration
public class ResilienceConfig {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .build();
        
        return CircuitBreakerRegistry.of(config);
    }
}

@Service
public class RagProxyService {
    
    private final WebClient ragWebClient;
    private final CircuitBreaker circuitBreaker;
    
    public RagProxyService(WebClient ragWebClient, CircuitBreakerRegistry registry) {
        this.ragWebClient = ragWebClient;
        this.circuitBreaker = registry.circuitBreaker("ai-rag-core");
    }
    
    public Mono<RagResponse> chat(String query, UUID userId, UUID sessionId) {
        return Mono.fromCallable(() -> circuitBreaker.executeSupplier(() -> 
            ragWebClient.post()
                .uri("/chat")
                .bodyValue(Map.of(
                    "query", query,
                    "user_id", userId != null ? userId.toString() : null,
                    "session_id", sessionId.toString()
                ))
                .retrieve()
                .bodyToMono(RagResponse.class)
                .block(Duration.ofSeconds(120))
        ))
        .subscribeOn(Schedulers.boundedElastic());
    }
}
```

---

## 9. Testing

### 9.1 Unit Testing

```java
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    
    @InjectMocks
    private LoginService loginService;
    
    @Test
    void login_success() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .passwordHash("$2a$10$encoded")
            .role(Role.USER)
            .build();
        
        when(userRepository.findByEmail(request.getEmail()))
            .thenReturn(Mono.just(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
            .thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user))
            .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(user))
            .thenReturn("refresh-token");
        
        // When
        Mono<LoginResponse> result = loginService.login(request);
        
        // Then
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.accessToken().equals("access-token") &&
                response.refreshToken().equals("refresh-token"))
            .verifyComplete();
        
        verify(userRepository).findByEmail(request.getEmail());
        verify(passwordEncoder).matches(request.getPassword(), user.getPasswordHash());
        verify(jwtTokenProvider).generateAccessToken(user);
        verify(jwtTokenProvider).generateRefreshToken(user);
    }
    
    @Test
    void login_userNotFound() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        
        when(userRepository.findByEmail(request.getEmail()))
            .thenReturn(Mono.empty());
        
        // When
        Mono<LoginResponse> result = loginService.login(request);
        
        // Then
        StepVerifier.create(result)
            .expectError(AuthenticationException.class)
            .verify();
    }
}
```

### 9.2 Integration Testing with Testcontainers

```java
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5")
            .withAdminPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> 
            String.format("r2dbc:postgresql://%s:%s/%s", 
                postgres.getHost(), postgres.getMappedPort(5432), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("neo4j.uri", neo4j::getBoltUrl);
        registry.add("neo4j.username", () -> "neo4j");
        registry.add("neo4j.password", () -> "test");
    }
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void saveAndFindUser() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .passwordHash("$2a$10$encoded")
            .role(Role.USER)
            .status(Status.ACTIVE)
            .build();
        
        StepVerifier.create(userRepository.save(user))
            .expectNextCount(1)
            .verifyComplete();
        
        StepVerifier.create(userRepository.findById(user.getId()))
            .expectNextMatches(saved -> saved.getEmail().equals("test@example.com"))
            .verifyComplete();
    }
}
```

### 9.3 WebFlux Controller Testing

```java
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    
    @Mock
    private LoginUseCase loginUseCase;
    
    @Mock
    private RegisterUseCase registerUseCase;
    
    @InjectMocks
    private AuthController authController;
    
    private WebTestClient webTestClient;
    
    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(authController)
            .configureClient()
            .build();
    }
    
    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        LoginResponse response = new LoginResponse(
            "access-token", "refresh-token", UUID.randomUUID(), 
            "test@example.com", "USER", 900000
        );
        
        when(loginUseCase.login(request)).thenReturn(Mono.just(response));
        
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(LoginResponse.class)
            .isEqualTo(response);
        
        verify(loginUseCase).login(request);
    }
}
```

---

## 10. Deployment

### 10.1 Docker Build

```dockerfile
# apps/backend/Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
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
- [Testcontainers Documentation](https://www.testcontainers.org/)
