# TechRadar — Database Architecture

Tài liệu này mô tả **toàn bộ tầng dữ liệu** của hệ thống (PostgreSQL, Neo4j, Redis):
ai sở hữu bảng/dữ liệu nào, service nào đọc/ghi, và các quy ước cross-service. Đây là
tài liệu Ở CẤP HỆ THỐNG (nhìn từ `docs/`); với DDL Postgres chi tiết từng cột/migration,
xem [`apps/backend/src/main/resources/db/README.md`](../apps/backend/src/main/resources/db/README.md)
— file đó vẫn là nguồn sự thật (source of truth) cho schema Postgres vì Flyway (backend)
là nơi DUY NHẤT tạo/sửa DDL.

## Mục lục

1. [Tổng quan 3 datastore](#1-tổng-quan-3-datastore)
2. [Nguyên tắc sở hữu dữ liệu](#2-nguyên-tắc-sở-hữu-dữ-liệu)
3. [PostgreSQL](#3-postgresql)
4. [Neo4j](#4-neo4j)
5. [Redis](#5-redis)
6. [Quy ước & gotchas cross-service](#6-quy-ước--gotchas-cross-service)

---

## 1. Tổng quan 3 datastore

| Datastore | Vai trò | Ai tạo schema | Ai ghi | Ai đọc |
|---|---|---|---|---|
| **PostgreSQL** | Nguồn sự thật quan hệ: user/auth, chat, social feed, messaging, analytics time-series, CMS, notification, data-platform catalog | **Flyway** trong `apps/backend` (duy nhất) | `apps/backend` (hầu hết bảng) · `services/ai-rag-core` (chỉ `chat_message`) · `data-platform` (chỉ các bảng `dp_*`) | `apps/backend`, `services/ai-rag-core` (đọc `user_profile`, `chat_message`) |
| **Neo4j** | Đồ thị tri thức: Article/Technology/Skill/Company/Job/Person + quan hệ suy luận (MENTIONS, REQUIRES, USES, RELATED_TO...) | `knowledge-graph/utils/schema_define.py` (constraints/indexes) | `knowledge-graph` (crawl + entity resolution + import), `data-platform/gold/neo4j_enricher.py` (derived relationships/stats) | `apps/backend` (graph/company/job features), `services/ai-rag-core` (RAG context, interview grounding), `services/ml-clustering` (đọc để train/serve cluster) |
| **Redis** | Cache tra cứu nhanh + state tạm thời, KHÔNG phải nguồn sự thật, có thể mất dữ liệu mà không hỏng nghiệp vụ (trừ token blacklist) | `apps/backend` (mọi key) | `apps/backend` | `apps/backend` |

Không service nào khác ghi trực tiếp vào Postgres của backend hay Redis; `services/ai-rag-core`
và `data-platform` chỉ được cấp quyền ghi đúng phạm vi bảng của mình (xem §2).

---

## 2. Nguyên tắc sở hữu dữ liệu

| Bảng / vùng dữ liệu | Chủ ghi | Ghi chú |
|---|---|---|
| `users`, `user_profile`, `user_avatar`, `password_reset` | backend | `ai-rag-core` chỉ **đọc** `user_profile` (cá nhân hoá RAG/interview). |
| `chat_session` | backend | Vòng đời session (tạo/list/xoá/kiểm quyền sở hữu). |
| `chat_message` | **ai-rag-core** | Service RAG là writer duy nhất (có sẵn câu trả lời + xử lý stream). Backend chỉ đọc để trả lịch sử. Trước đây cả 2 bên cùng ghi → double-write (4 dòng/lượt) — đã sửa. |
| `settings`, `tech_analytics`, `activity_log`, `cms_content` | backend | Feature flags, ETL Neo4j→Postgres (radar/compare), traffic/search metrics, AdminCMS. |
| `notification` | backend | 2 nguồn Kafka: `trend.alerts` → `TrendAlertDispatcher`, `job.match.alerts` → `JobMatchDispatcher` (in-app + email fan-out cho cả 2). Ngoài ra được ghi trực tiếp (không qua Kafka) bởi `ToggleLikeUseCase`/`AddCommentUseCase`/`ToggleFollowUseCase`/`SendMessageUseCase` khi có tương tác xã hội/tin nhắn (`POST_LIKE`/`POST_COMMENT`/`NEW_FOLLOWER`/`NEW_MESSAGE`) — xem [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.10. |
| `post`, `follow`, `post_like`, `post_comment` | backend | Social feed (V8) — theo dõi (`follow`) là bảng Postgres, KHÔNG phải cạnh Neo4j. |
| `content_report` | backend | Content moderation (V11/V12) — user report post/comment vi phạm; admin xem/dismiss qua `SocialModerationService`/`AdminSocialController`. |
| `conversation`, `direct_message` | backend | Direct messaging (V9); realtime là SSE, fan-out qua Redis Pub/Sub (`MessageBroadcaster`, xem §5) nên chạy đúng với nhiều instance backend. |
| `dp_bronze_catalog`, `dp_processed_articles`, `dp_processed_jobs`, `dp_pipeline_runs` | **data-platform** (Python) | Backend/Flyway chỉ tạo bảng (V7); ghi/đọc thuộc service `data-platform` (bronze/silver medallion catalog). `ai-rag-core` và `apps/backend` không đụng vào các bảng này. |
| Neo4j node/relationship gốc (Article, Technology, Skill, Company, Job, Person) | `knowledge-graph` (crawl + entity resolution + import pipeline) | Xem §4. |
| Neo4j derived relationships (`USES`, `RELATED_TO`) + stats (`trend_score`, `article_count`, `job_count` trên `Technology`) | `data-platform/gold/neo4j_enricher.py` | Chạy như một Gold-layer job (ghi log vào `dp_pipeline_runs`, `job_name='neo4j_enricher'`). |
| Redis keys | backend | Không có service nào khác dùng chung Redis instance cho dữ liệu nghiệp vụ. |

---

## 3. PostgreSQL

### 3.1 Nhóm bảng theo domain

```
Auth & Profile
  users, user_profile, user_avatar, password_reset

Chat / RAG
  chat_session (backend ghi) — chat_message (ai-rag-core ghi)

Radar / Analytics (ETL Neo4j → Postgres)
  tech_analytics, activity_log

Admin / CMS
  settings, cms_content

Notifications
  notification  (+ user_profile.notify_inapp / notify_email)

Social Feed (V8 — mới)
  post, follow, post_like, post_comment

Content Moderation (V11/V12 — mới)
  content_report   -- báo cáo vi phạm trên post/comment, hàng đợi kiểm duyệt cho admin

Direct Messaging (V9 — mới)
  conversation, direct_message

Data Platform catalog (V7 — sở hữu bởi service `data-platform`, không phải backend/ai-rag-core)
  dp_bronze_catalog, dp_processed_articles, dp_processed_jobs, dp_pipeline_runs
```

Quan hệ chính: `users 1—1 user_profile`; `users 1—N chat_session 1—N chat_message`;
`users 1—N post 1—N post_comment`; `users N—N users` qua `follow` và (trên mỗi `post`) qua `post_like`
(không có bảng "friendship" riêng — follow là một chiều, có CHECK chặn tự follow); `users 1—1 conversation`
với ràng buộc canonical `user_a_id < user_b_id` (tránh tạo 2 conversation cho cùng 1 cặp theo 2 thứ tự)
`1—N direct_message`.

### 3.2 Bảng migration (Flyway ledger)

| Version | Nội dung |
|---|---|
| V1 | Base schema: `users`, `settings`, `tech_analytics` |
| V2 | `users.full_name` + `user_profile` |
| V3 | `activity_log` + `cms_content` |
| V4 | CHECK constraints (`users.role`, `chat_message.role`, `activity_log.type`) + index + trigger `set_updated_at()` |
| V5 | `user_avatar` (BYTEA, avatar lưu trong DB) + `password_reset` |
| V6 | `notification` + `user_profile.notify_inapp/notify_email` |
| V7 | `dp_bronze_catalog`, `dp_processed_articles`, `dp_processed_jobs`, `dp_pipeline_runs` (data-platform catalog) |
| V8 | **Social feed**: `post`, `follow`, `post_like`, `post_comment` |
| V9 | **Direct messaging**: `conversation`, `direct_message` |
| V10 | GIN index `idx_user_profile_technologies_gin` trên `user_profile.technologies` — tăng tốc `findTrendSubscribers` (chạy trên mỗi Kafka `trend.alerts`); đổi query từ `:tech = ANY(technologies)` sang `technologies @> :tech` để dùng được index |
| V11 | **Content moderation**: `content_report` (báo cáo post/comment vi phạm, `status IN ('PENDING','DISMISSED')`, unique index chặn 1 user report trùng cùng 1 target) |
| V12 | Sửa 2 unique index của V11: chỉ tính `status='PENDING'` là "đã report" — nếu report cũ đã bị dismiss, user vẫn report lại được nếu vi phạm tái diễn |
| V900–V905 (dev only) | Seed: admin/demo user, sample data, jobs/articles, activity_log, tech_analytics mở rộng, thêm users + cms cho môi trường dev |

DDL đầy đủ từng cột/index: [`apps/backend/.../db/README.md`](../apps/backend/src/main/resources/db/README.md) §3.

### 3.3 Điểm cần lưu ý khi thêm bảng mới

- Tất cả foreign key trỏ về `users(id)` đều `ON DELETE CASCADE` — xoá user sẽ xoá sạch post/comment/message/conversation liên quan (không có soft-delete cho user hiện tại).
- Composite PK được dùng cho quan hệ N—N thuần (`follow`, `post_like`) thay vì bảng có `id` riêng + unique index — giữ nguyên convention này cho tính năng N—N mới.
- `conversation.CHECK(user_a_id < user_b_id)` là pattern canonical-ordering để tránh trùng lặp cặp theo 2 chiều; nếu thêm bảng quan hệ 2 user mới (vd. block/report), nên áp dụng cùng convention.

---

## 4. Neo4j

### 4.1 Node & Relationship types

**Node types:**
- `Article`: title, content, source, published_date, sentiment_score, embedding (768d)
- `Technology`: name, category, subcategory, description, trend_score, demand_score, **article_count, job_count** (derived, ghi bởi `neo4j_enricher.py`)
- `Skill`: name, category, demand_score
- `Company`: name, field, size, location, rating
- `Job`: title, description, requirement, benefit, salary, due_date, source_url
- `Person`: name, role

**Relationship types:**
- `MENTIONS`: Article → Technology/Company/Person
- `REQUIRES`: Job → Technology/Skill (dùng bởi **Job Matching** — `Neo4jJobRepository`)
- `HIRES_FOR`: Job → Company — ghi bởi batch importer `knowledge-graph` (dùng bởi **AI Interview** grounding — `graph_queries.JOBS_BY_TITLE_AND_COMPANY`, và bởi `ml-clustering`/`ai-rag-core` nói chung).
- `POSTED_BY`: Job → Company — **cùng ý nghĩa với `HIRES_FOR`** nhưng ghi bởi pipeline real-time riêng của `apps/backend` (`KafkaNeo4jWriterService`, consume topic `extracted.jobs`); một Job chỉ đi qua một trong hai pipeline sẽ chỉ có một trong hai cạnh này. `Neo4jJobRepository`/`Neo4jCompanyRepository` match cả `POSTED_BY|HIRES_FOR` để không bỏ sót company linkage của job chỉ được batch-import.
- `USES`: Company → Technology — **derived**, ghi bởi `data-platform/gold/neo4j_enricher.py` (MERGE, tăng `evidence_count`/`first_seen`); theo snapshot của `ml-clustering` (06/05/2026) có ~11.3k cạnh này trong AuraDB. `apps/backend` cố tình **không** đọc `USES` trực tiếp cho Company Explorer (xem ghi chú dưới).
- `RELATED_TO`: Technology → Technology — derived, cũng ghi bởi `neo4j_enricher.py` (co-mention count)
- `WORKS_AT`: Person → Company (derived)
- `WROTE`: Person → Article (derived)

> **Vì sao Company Explorer không đọc thẳng `USES`:** `Neo4jCompanyRepository`/
> `GetSimilarCompaniesUseCase` suy ra tech stack của công ty gián tiếp qua
> `Company<-[:POSTED_BY|HIRES_FOR]-Job-[:REQUIRES]->Technology` thay vì đọc `Company-[:USES]->
> Technology` trực tiếp — đây là lựa chọn có chủ đích (job đang tuyển phản ánh tech stack hiện
> tại chính xác hơn `USES`, vốn là tín hiệu derived chỉ refresh theo lịch chạy của
> `neo4j_enricher.py`), không phải vì `USES` không tồn tại. Một comment cũ trong code từng ghi
> sai rằng `USES` "không service nào ghi" — đã sửa lại cho đúng thực tế ở trên.

### 4.2 Ai ghi gì

- **`knowledge-graph`** (crawl → entity resolution → import): tạo node gốc (Article/Technology/Skill/Company/Job/Person) + cạnh trực tiếp (`MENTIONS`, `REQUIRES`, `HIRES_FOR`).
- **`data-platform/gold/neo4j_enricher.py`**: chạy sau, MERGE các cạnh **derived** (`USES`, `RELATED_TO`) và cập nhật thống kê trên `Technology` (`article_count`, `job_count`, `trend_score`); log mỗi lần chạy vào `dp_pipeline_runs` (Postgres, `job_name='neo4j_enricher'`).
- **`services/ml-clustering`**: chỉ **đọc** (qua `neo4j_loader.py`, export ra parquet làm input huấn luyện cluster) — không ghi ngược kết quả cluster vào Neo4j; kết quả cluster được serve qua API riêng của `ml-clustering` (không lưu trong graph).
- **`services/ai-rag-core`**: chỉ đọc (RAG context cho `/chat`, và grounding cho `/interview` qua `graph_queries.py`).
- **`apps/backend`**: chủ yếu đọc (graph explore/road-analysis, company similarity, job matching, salary/sentiment filter trên `graph`/`company`/`job` features) — nhưng **cũng ghi** qua `KafkaNeo4jWriterService` (`features/kafka`): consume `extracted.articles`/`extracted.jobs`, MERGE Article/Technology/Skill/Company/Job + cạnh `MENTIONS`/`REQUIRES`/`POSTED_BY` real-time, song song với batch import của `knowledge-graph`.

### 4.3 Modules ghi/xử lý graph (knowledge-graph)

```
knowledge-graph/
├── entity_resolution/     # Alias normalization (tech_resolver, company_resolver)
├── ontology/              # Taxonomy classification
├── cypher_repo/           # Cypher query constants
├── analytics/             # trend_scorer, demand_scorer
├── crawl/                 # VNExpress, GenK, DanTri, ICTNews, TopCV, ITviec, Viblo, GitHub...
└── utils/                 # schema_define (constraints/index), import_multi_source, run_complete_pipeline
```

---

## 5. Redis

Toàn bộ Redis chỉ dùng trong `apps/backend` (`shared/redis/*`), qua `ReactiveRedisTemplate`
(cấu hình ở `config/RedisConfig.java`). Không phải nguồn sự thật — ngoại trừ token blacklist,
mọi key khác có thể bị xoá/miss mà không gây sai dữ liệu (chỉ mất cache, sẽ tự nạp lại).

| Dùng cho | Key pattern | Service class | TTL | Ghi chú |
|---|---|---|---|---|
| Auth — token blacklist (logout/refresh) | `blacklist:token:<hashCode>` | `TokenBlacklistService` | = thời gian còn hiệu lực của token | Đây là dữ liệu **có ý nghĩa nghiệp vụ thật** (không chỉ cache) — mất key này = token đã logout vẫn dùng được. |
| Chat rate limiting | `ratelimit:chat:<userId>` | `ChatRateLimiterService` | theo `windowSeconds` truyền vào (fixed window counter, `INCR` + `EXPIRE`) | Giới hạn số lượt chat/khoảng thời gian mỗi user. |
| Cache tra cứu (look-aside, JSON) | tuỳ theo use case, đặt key tại call-site | `ReactiveRedisCache` (generic `getOrLoad`/`getOrLoadMono`) | tuỳ use case | Dùng bởi: `radar` (`GetTopTechnologiesUseCase`, `SearchTrendUseCase`, `AnalyticsScheduler`, invalidate qua `AnalyticsAdminController` khi rebuild), `salary` (`GetSalaryInsightsUseCase`, `GetTechSalaryDetailUseCase`), `clustering` (`GetClustersUseCase`), **`company`** (`GetCompaniesUseCase`, key `cache:company:all`, TTL `app.redis.company-cache-ttl` mặc định 1800s — cache toàn bộ danh sách company đã tính tech stack; `GetSimilarCompaniesUseCase` tái dùng cache này thay vì query lại Neo4j), **`job`** (`GetJobMatchesUseCase`, key `cache:job:match:<skills sắp xếp, nối bằng \|>`, TTL `app.redis.job-cache-ttl` mặc định 1800s — cache theo tập kỹ năng, fetch ở `MAX_LIMIT*3` rồi serve mọi `limit` từ cùng 1 cache entry; location/min-salary lọc SAU cache nên không làm phân mảnh cache key). |
| SSE fan-out (Pub/Sub, không phải cache) | `live:messages`, `live:notifications` | `MessageBroadcaster`, `NotificationService` — cả 2 dùng chung `ReactiveRedisMessageListenerContainer` bean (`RedisConfig`) | n/a (fire-and-forget, không lưu) | `publish()`/`save()` broadcast qua Redis Pub/Sub thay vì ghi thẳng `Sinks.Many` cục bộ — mọi instance backend đều subscribe channel này và tự đẩy tới SSE client cục bộ của mình, nên hoạt động đúng dù sender/recipient rơi vào instance nào. Không bền (không phải Kafka): nguồn sự thật vẫn là Postgres (`direct_message`/`notification`), mất live push chỉ có nghĩa client thấy tin nhắn/thông báo khi tự fetch lại thay vì tức thời. |

`social`, `interview`, `aiproxy` không dùng Redis — đọc thẳng Postgres/Neo4j hoặc proxy sang
`ai-rag-core` mỗi request, không cache.

> **Cảnh báo cache staleness (company/job):** vì `company`/`job` cache toàn bộ kết quả Neo4j
> trong 30 phút, dữ liệu công ty/job mới ingest sẽ KHÔNG xuất hiện ngay lập tức trên
> `/companies`, `/companies/{id}/similar`, `/jobs/matches` cho tới khi TTL hết hạn **hoặc** admin
> gọi tay `POST /admin/cache/companies/evict` / `POST /admin/cache/jobs/evict`
> (`CacheAdminController`, ADMIN only) — không có ETL/rebuild nào để tự động trigger evict như
> `AnalyticsAdminController` làm cho `radar`, vì company/job không qua bước rebuild, dữ liệu Neo4j
> vốn đã mới nhất, chỉ có cache là cũ.

---

## 6. Quy ước & gotchas cross-service

- **snake_case toàn cục**: Jackson (`spring.jackson.property-naming-strategy: SNAKE_CASE`) áp dụng cho MỌI DTO — không cần annotate từng field trừ khi muốn override tên (vd. `SocialDtos.ProfileSummaryResponse.following` → ép `@JsonProperty("is_following")` vì tên field không có "hump" để Jackson tự tách).
- **`session_id`/`conversation_id` luôn do client (qua path) cung cấp**, không sinh phía nào tự động lệch nhau giữa 2 service ghi khác nhau (áp dụng cho cả `chat_session`↔`chat_message` và `conversation`↔`direct_message`).
- **R2DBC `.bind(name, null)` luôn throw** — cột string có thể null phải dùng `.bindNull(name, String.class)` (bug đã gặp và sửa ở `PostgresChatRepository`; áp dụng khi viết repository Postgres mới).
- **Không có Testcontainers** cho integration test (Docker API version mismatch) — dùng `docker run` thủ công + env wiring; test cần cả 3 datastore (Postgres, Neo4j, Redis) chạy thật.
- **Data Platform (`dp_*`) là vùng cấm với backend/ai-rag-core** — chỉ đọc gián tiếp qua kết quả cuối (Neo4j đã enrich, hoặc `tech_analytics` đã ETL), không bao giờ query thẳng `dp_processed_articles`/`dp_processed_jobs` từ Java hay ai-rag-core.
- **Test coverage cho messaging/social/notification** trong `ApiIntegrationTest` hiện chỉ nhắm vào hiệu ứng phụ "tạo notification" của 4 use case (`message_notifiesRecipient_withConversationLink`, `comment_notifiesPostAuthor_butNotOnSelfComment`, `like_notifiesPostAuthor_onlyOnceAndNotOnSelfLike`, `follow_notifiesFollowee_onlyOnce`) + 1 test cho `findJobMatchSubscribers` (kỹ năng trùng lặp) — đây LÀ integration test thật (WebTestClient trên server thật, không mock). Vẫn CHƯA có integration test cho các luồng CRUD/list cơ bản của `/feed`, `/users/{id}/posts`, `/companies/**`, `/jobs/matches`, `/interview`, hay các endpoint admin moderation/dashboard mới (`/admin/posts/**`, `/admin/reports/**`, `/admin/dashboard/social|jobs|pipeline|messaging`, `/admin/cache/**`) — những chỗ đó mới chỉ có unit test cấp use case (`apps/backend/src/test/java/.../features/**`), ví dụ `MessageBroadcasterRedisCrossInstanceTest`/`NotificationServiceRedisCrossInstanceTest` (verify hành vi pub/sub cross-instance) và `CacheAdminControllerTest`.
