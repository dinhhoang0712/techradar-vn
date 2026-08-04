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
| **PostgreSQL** | Nguồn sự thật quan hệ: user/auth, chat, social feed, messaging, analytics time-series, CMS, notification, LLM usage/billing, data-platform catalog | **Flyway** trong `apps/backend` (duy nhất) | `apps/backend` (hầu hết bảng) · `services/ai-rag-core` (`chat_message`, `llm_usage_log`) · `data-platform` (chỉ các bảng `dp_*`) | `apps/backend`, `services/ai-rag-core` (đọc `user_profile`, `chat_message`) |
| **Neo4j** | Đồ thị tri thức: Article/Technology/Skill/Company/Job + quan hệ suy luận (MENTIONS, REQUIRES, USES, RELATED_TO...) | `data-platform/common/neo4j_schema.py` (`ensure_constraints`, gọi idempotent mỗi lần `data-platform` khởi động — `CREATE CONSTRAINT IF NOT EXISTS`). Thay thế `knowledge-graph/utils/schema_define.py` cũ (đã xoá khỏi repo) sau khi phát hiện instance sống KHÔNG có constraint nào (`SHOW CONSTRAINTS` rỗng) dù docs từng giả định là có — hệ quả thực tế: `MERGE (t:Technology {name: ...})` race giữa Java realtime writer và Python batch writer sinh node trùng y hệt tên (xem §4.4) | `apps/backend` (`KafkaNeo4jWriterService`, real-time), `data-platform/gold/{neo4j_article_sync,neo4j_job_sync}.py` (batch) cho node gốc; `data-platform/gold/neo4j_enricher.py` (derived relationships/stats); `services/ml-clustering/pipelines/stage_05_writeback.py` (node `:Cluster` + cạnh `BELONGS_TO`/`NEAR_CLUSTER`, khi `writeback.enabled=true`) | `apps/backend` (graph/company/job features), `services/ai-rag-core` (RAG context, interview grounding), `services/ml-clustering` (đọc để train/serve cluster) |
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
| `settings`, `tech_analytics`, `activity_log`, `cms_content` | backend | Feature flags, ETL Neo4j→Postgres (radar/compare), traffic/search metrics, AdminCMS. `cms_content.body` (TEXT, V35) chỉ được populate cho row type "Report" do `MonthlyReportSchedulerService` (feature `aiproxy`) sinh hàng tháng và row type "Keyword" do `RadarAnalyticsEtlService` ghi sau mỗi lần rebuild (§4.2 BACKEND_GUIDE.md); NULL với các cms row khác (crawler/keyword-digest cũ). `activity_log.type` CHECK cho phép thêm `'ai_request'` từ V34 — xem gotcha ở §6. |
| `llm_usage_log` | **ai-rag-core** | Cùng pattern với `chat_message`: Flyway/backend chỉ tạo bảng (V33), writer thật là `services/ai-rag-core` (`app/core/llm_usage_sink.py`, callback `on_usage` của `services/llm-gateway`) — billing/cost tracking theo provider/model cho mỗi lượt gọi LLM. `services/ml-clustering`'s `llm_labeler.py` cũng dùng `llm-gateway` nhưng KHÔNG wire `on_usage` nên các lệnh gọi LLM để label cluster không được ghi vào bảng này. |
| `notification` | backend | 2 nguồn Kafka: `trend.alerts` → `TrendAlertDispatcher`, `job.match.alerts` → `JobMatchDispatcher` (in-app + email fan-out cho cả 2). Ngoài ra được ghi trực tiếp (không qua Kafka) bởi `ToggleLikeUseCase`/`AddCommentUseCase`/`ToggleFollowUseCase`/`SendMessageUseCase` khi có tương tác xã hội/tin nhắn (`POST_LIKE`/`POST_COMMENT`/`NEW_FOLLOWER`/`NEW_MESSAGE`) — xem [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.10. |
| `post`, `follow`, `post_like`, `post_comment` | backend | Social feed (V8) — theo dõi (`follow`) là bảng Postgres, KHÔNG phải cạnh Neo4j. |
| `content_report` | backend | Content moderation (V11/V12) — user report post/comment vi phạm; admin xem/dismiss qua `SocialModerationService`/`AdminSocialController`. |
| `conversation`, `direct_message` | backend | Direct messaging (V9); realtime là SSE, fan-out qua Redis Pub/Sub (`MessageBroadcaster`, xem §5) nên chạy đúng với nhiều instance backend. `direct_message.attachment_content_type/filename/size/data` (V31, BYTEA in-DB) và bảng `message_reaction` (V32, PK `(message_id, user_id)` — 1 reaction/user/message, upsert-replace) cũng thuộc nhóm này, ghi bởi backend qua `SetMessageReactionUseCase`/`RemoveMessageReactionUseCase`/`GetMessageAttachmentUseCase`. |
| `dp_bronze_catalog`, `dp_processed_articles`, `dp_processed_jobs`, `dp_pipeline_runs` | **data-platform** (Python) | Backend/Flyway chỉ tạo bảng (V7); ghi/đọc thuộc service `data-platform` (bronze/silver medallion catalog). `ai-rag-core` và `apps/backend` không đụng vào các bảng này. |
| `dp_tech_alias_map`, `dp_tech_alias_review_queue` | **CẢ 2** — `apps/backend` ghi qua 2 đường: `TechAliasCache.java` (chỉ đọc, canonicalize khi ingest) VÀ (từ V30) feature `kgreview` (`PostgresTechAliasReviewRepository`, gate bởi permission `kg:review`) ghi khi admin approve/reject qua `/admin/kg-review/tech-aliases/{id}/approve\|reject` — cập nhật `dp_tech_alias_review_queue.status` và upsert `dp_tech_alias_map` với `source='human_review'`; VÀ `data-platform` (`common/tech_alias_cache.py` đọc; `gold/tech_dedup.py` ghi `source='llm_auto'` khi phát hiện alias mới qua LLM) | Ngoại lệ có chủ đích duy nhất trong hệ thống nơi backend/data-platform cùng dùng chung 1 bảng — vì Docker build-context của 2 service tách biệt (`./apps/backend` vs `./data-platform`) nên không thể share 1 file cấu hình; Postgres là nơi trung lập cả 2 đều đã kết nối sẵn. Xem §4.3. |
| `dp_tech_category` | **data-platform** (`gold/tech_dedup.py` cho tên mới, `gold/tech_category_backfill.py` cho backfill 1 lần) | KHÔNG chia sẻ với backend (khác `dp_tech_alias_map`) — chỉ `data-platform/gold/neo4j_enricher.py` đọc bảng này để đẩy `category` lên `Technology` node trong Neo4j. Xem §4.1/§4.2. |
| Neo4j node/relationship gốc (Article, Technology, Skill, Company, Job, Person) | `apps/backend` (real-time) + `data-platform/gold` (batch) | Xem §4. |
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
  settings, cms_content   -- cms_content.body (V35) là TEXT, chỉ dùng cho report/keyword-digest

Notifications
  notification  (+ user_profile.notify_inapp / notify_email)

Social Feed (V8 — mới)
  post, follow, post_like, post_comment

Content Moderation (V11/V12 — mới)
  content_report   -- báo cáo vi phạm trên post/comment, hàng đợi kiểm duyệt cho admin

Direct Messaging (V9 — mới)
  conversation, direct_message
  direct_message += attachment_content_type/filename/size/data (V31)
  message_reaction (V32) -- PK (message_id, user_id), 1 reaction/user/message

LLM Usage / Billing (V33 — mới; bảng tạo bởi Flyway nhưng ghi bởi ai-rag-core, KHÔNG phải backend)
  llm_usage_log

Data Platform catalog (V7 — sở hữu bởi service `data-platform`, không phải backend/ai-rag-core)
  dp_bronze_catalog, dp_processed_articles, dp_processed_jobs, dp_pipeline_runs

Tech Alias / Canonicalization (V21 — dùng CHUNG bởi apps/backend VÀ data-platform, xem §4.3)
  dp_tech_alias_map, dp_tech_alias_review_queue

Tech Category Classification (V29 — nội bộ data-platform, không chia sẻ với backend)
  dp_tech_category

Transactional Outbox (V36 — mới; xem docs/adr/0005-transactional-outbox-trend-alerts.md)
  outbox_event   -- trend.alerts ghi trong CÙNG transaction với tech_analytics upsert;
                    OutboxRelayScheduler publish Kafka + đánh dấu PUBLISHED/FAILED
```

Quan hệ chính: `users 1—1 user_profile`; `users 1—N chat_session 1—N chat_message`;
`users 1—N post 1—N post_comment`; `users N—N users` qua `follow` và (trên mỗi `post`) qua `post_like`
(không có bảng "friendship" riêng — follow là một chiều, có CHECK chặn tự follow); `users 1—1 conversation`
với ràng buộc canonical `user_a_id < user_b_id` (tránh tạo 2 conversation cho cùng 1 cặp theo 2 thứ tự)
`1—N direct_message`.

### 3.1a ERD (logical — Postgres core domains)

Sơ đồ dưới đây là ERD **logic** (rút gọn cột, chỉ giữ khoá + cột định danh nghiệp vụ) cho các
domain lõi ở trên; DDL đầy đủ từng cột/index vẫn ở
[`apps/backend/.../db/README.md`](../apps/backend/src/main/resources/db/README.md).

```mermaid
erDiagram
    USERS ||--o| USER_PROFILE : "1-1"
    USERS ||--o| USER_AVATAR : "1-1"
    USERS ||--o{ PASSWORD_RESET : "1-N"
    USERS }o--|| ROLES : "role → code"
    ROLES ||--o{ ROLE_PERMISSIONS : "1-N"
    PERMISSIONS ||--o{ ROLE_PERMISSIONS : "1-N"

    USERS ||--o{ CHAT_SESSION : "1-N"
    CHAT_SESSION ||--o{ CHAT_MESSAGE : "1-N (ai-rag-core ghi)"

    USERS ||--o{ POST : "1-N"
    POST ||--o{ POST_COMMENT : "1-N"
    POST ||--o{ POST_LIKE : "1-N"
    POST ||--o{ POST_IMAGE : "1-N"
    USERS ||--o{ POST_COMMENT : "1-N"
    USERS ||--o{ POST_LIKE : "1-N"
    USERS ||--o{ FOLLOW : "follower_id"
    USERS ||--o{ FOLLOW : "followee_id"

    USERS ||--o{ CONTENT_REPORT : "reporter_id"
    POST ||--o{ CONTENT_REPORT : "post_id (nullable)"
    POST_COMMENT ||--o{ CONTENT_REPORT : "comment_id (nullable)"

    USERS ||--o{ CONVERSATION : "user_a_id"
    USERS ||--o{ CONVERSATION : "user_b_id"
    CONVERSATION ||--o{ DIRECT_MESSAGE : "1-N"
    DIRECT_MESSAGE ||--o{ MESSAGE_REACTION : "1-N"
    USERS ||--o{ MESSAGE_REACTION : "1-N"

    USERS ||--o{ NOTIFICATION : "1-N"
    USERS ||--o{ AUDIT_LOG : "actor_id (no FK — outlives deleted user)"

    USERS {
        uuid id PK
        string email
        string role FK
        string status
        string security_stamp
    }
    USER_PROFILE {
        uuid user_id PK_FK
        string_array technologies
        string_array target_skills
        bool notify_inapp
        bool notify_email
    }
    ROLES {
        string code PK
    }
    PERMISSIONS {
        string code PK
    }
    CHAT_SESSION {
        uuid id PK
        uuid user_id FK
        string title
    }
    CHAT_MESSAGE {
        uuid id PK
        uuid session_id FK
        string role
    }
    POST {
        uuid id PK
        uuid user_id FK
        text content
        timestamp deleted_at
    }
    FOLLOW {
        uuid follower_id PK_FK
        uuid followee_id PK_FK
    }
    CONVERSATION {
        uuid id PK
        uuid user_a_id FK
        uuid user_b_id FK
    }
    DIRECT_MESSAGE {
        uuid id PK
        uuid conversation_id FK
        uuid sender_id FK
        bytea attachment_data
    }
    MESSAGE_REACTION {
        uuid message_id PK_FK
        uuid user_id PK_FK
        string emoji
    }
    CONTENT_REPORT {
        uuid id PK
        uuid reporter_id FK
        string status
    }
    NOTIFICATION {
        uuid id PK
        uuid user_id FK
        string type
        bool is_read
    }
    AUDIT_LOG {
        uuid id PK
        uuid actor_id
        string action
    }
```

`tech_analytics`, `activity_log`, `settings`, `cms_content`, `llm_usage_log`, và các bảng
`dp_*` (sở hữu bởi `data-platform`) không có FK vào `users` — chúng là bảng thời gian/catalog
độc lập, xem mô tả từng bảng ở §2 phía trên.

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
| V13 | `post_image` (BYTEA, nhiều ảnh/post) |
| V14 | `post.hashtags` (TEXT[] + GIN index) cho feed hashtag filter |
| V15 | `post.tagged_company_*` — denormalized snapshot công ty (Company sống ở Neo4j, không FK được) |
| V16 | `post_comment.parent_comment_id` — comment threading |
| V17 | `user_profile.preferences_json` (JSONB) — cột `ai-rag-core` cần cho long-term memory/personalization, trước đó thiếu ở phía Java |
| V18 | `dp_processed_jobs.company_industry/company_size` — thêm nullable, không ảnh hưởng row cũ |
| V19 | `content_report.ai_suggested_*` — cache gợi ý kiểm duyệt AI, tính on-demand |
| V20 | `audit_log` — append-only audit trail cho hành động admin, không FK tới `users(id)` |
| V21 | `dp_tech_alias_map` + `dp_tech_alias_review_queue` — canonical hoá tên công nghệ dùng chung giữa `apps/backend` (`TechAliasCache.java`) và `data-platform` (`common/tech_alias_cache.py`, `gold/tech_dedup.py`); xem §4.3 |
| V22 | `user_profile.target_skills TEXT[]` + GIN index — để `JobMatchDispatcher` alert theo skill lộ trình sự nghiệp gợi ý, không chỉ theo `technologies` quan tâm |
| V23 | Composite index `(job_name, started_at DESC)` trên `dp_pipeline_runs` |
| V24 | RBAC theo permission — bảng `roles`/`permissions`/`role_permissions`, 12 permission code, `users.role` FK vào `roles(code)`, thêm `users.security_stamp` (revoke token tức thời khi đổi role/status) |
| V25 | Role `moderator` (chỉ có `social:moderate` + `audit:view`) — chứng minh RBAC ở V24 hoạt động đúng |
| V26 | Soft-delete cho `post`/`post_comment` (`deleted_at`) — giữ bằng chứng kiểm duyệt thay vì xoá cứng theo CASCADE |
| V27 | Permission `graph:manage` cho endpoint rebuild Graph Analytics (PageRank/Louvain qua Neo4j GDS) |
| V28 | Seed alias tự tham chiếu (`lower(canonical) -> canonical`) cho toàn bộ `TECH_KEYWORDS` còn thiếu trong `dp_tech_alias_map` — bịt lỗ hổng case-duplicate kiểu "SQL"/"Sql"/"sql" khi nhà tuyển dụng gõ case khác nhau và chưa có alias sẵn |
| V29 | `dp_tech_category` — phân loại category (language/framework/tool/cloud/database/...) cho Technology, key theo `canonical_name` (không phải `alias_normalized` như `dp_tech_alias_map`, để category không bị trôi/duplicate giữa các alias của cùng 1 tech); ghi bởi `gold/tech_dedup.py` (LLM, tên mới phát hiện) + `gold/tech_category_backfill.py` (one-time, backfill toàn bộ catalog cũ); đọc bởi `gold/neo4j_enricher.py` để ghi lên `Technology.category` — xem §4.1/§4.2 |
| V30 | Không có bảng mới — thêm permission `kg:review` (`permissions` + `role_permissions`, cấp cho `admin`), backing cho `/admin/kg-review/**` (`KgReviewAdminController`) — UI duyệt thủ công `dp_tech_alias_review_queue`, xem §4.3 |
| V31 | **Message attachments**: `ALTER TABLE direct_message ADD attachment_content_type, attachment_filename, attachment_size, attachment_data` (BYTEA in-DB, cùng pattern `post_image`/`user_avatar`), tất cả nullable |
| V32 | **Message reactions**: bảng mới `message_reaction(message_id FK→direct_message, user_id FK→users, emoji, created_at)`, `PK(message_id, user_id)` — 1 reaction/user/message (set lại = upsert-replace) |
| V33 | **LLM usage/billing**: bảng mới `llm_usage_log(id, service, provider, model, input_tokens, output_tokens, cost_usd, fallback_from, created_at)` — Flyway/backend chỉ tạo bảng, writer thật là `services/ai-rag-core` (xem §2) |
| V34 | Bugfix: nới CHECK `chk_activity_type` để cho phép `activity_log.type = 'ai_request'` — thiếu giá trị này từ lúc AI-proxy consolidation khiến mọi insert của `AiProxyRequestHandler.recordAiRequest()` bị CHECK chặn và nuốt lỗi âm thầm (`.onErrorResume`), tile admin "Request AI hôm nay" luôn đọc 0 cho tới khi có V34 — xem gotcha ở §6 |
| V35 | `cms_content.body TEXT` (nullable) — nội dung đầy đủ cho row do `MonthlyReportSchedulerService` (report tháng) và `RadarAnalyticsEtlService` (keyword digest) sinh; NULL với cms row khác |
| V36 | **Transactional outbox**: bảng mới `outbox_event(id, topic, payload, status, attempts, last_error, created_at, published_at)` — `RadarAnalyticsEtlService` ghi row `PENDING` cho mỗi `trend.alerts` trong CÙNG transaction R2DBC với `tech_analytics` upsert (`TransactionalOperator`, xem [ADR-0002](./adr/0002-webflux-reactive-stack.md)); `OutboxRelayScheduler` (poll định kỳ) publish qua Kafka rồi đánh dấu `PUBLISHED`, hoặc `FAILED` + tăng `attempts` khi lỗi. Xem [ADR-0005](./adr/0005-transactional-outbox-trend-alerts.md) cho lý do và phạm vi (chỉ áp dụng cho luồng nguồn Postgres, không áp dụng cho `job.match.alerts`/`roadmap.alerts` vốn nguồn từ Neo4j). |
| V37 | Không có bảng mới — thêm CHECK còn thiếu cho vocabulary cố định đã dùng thực tế nhưng chưa từng bị ràng buộc: `users.status`/`users.subscription_tier` (backfill UPPERCASE trước khi thêm CHECK, vì code cũ từng ghi lowercase), `cms_content.status`/`cms_content.type` (không cần backfill, đã Title-Case nhất quán từ trước) |
| V38 | `dp_processed_jobs.level` — backfill giá trị hiện có không khớp enum về NULL, rồi thêm CHECK `level IN ('Intern','Fresher','Junior','Middle','Senior','Lead')` (cho phép NULL). Cột đã tồn tại từ V7 (TEXT tự do, không CHECK) nhưng chưa nơi nào ghi giá trị vào — `silver/processor.py::normalize_level()` (data-platform) là nơi chuẩn hoá free-text (crawler scrape trực tiếp, hoặc `experienceRequirements` trong JSON-LD `JobPosting`) về enum này trước khi INSERT; `gold/neo4j_job_sync.py` đồng bộ tiếp lên `Job.level` (Neo4j, xem §4.1) |
| V39 | `user_profile.current_level` — cùng enum 6 mức với V38 (CHECK tương tự), cho user tự khai cấp độ kinh nghiệm bản thân; đọc/ghi qua `GET/PUT /user/profile` (backend) và tự tra bởi `career_service.py` (ai-rag-core) khi request `/career` không gửi `current_level` — dùng để cá nhân hoá skill-gap/estimated_months theo đúng cấp bậc, xem [`docs/API_DOCs_v1.md`](./API_DOCs_v1.md) §17 |
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
- `Technology`: name, **category** (derived, ghi bởi `neo4j_enricher.py` từ bảng Postgres `dp_tech_category` — V29, xem §3.2 — được LLM phân loại qua `gold/tech_dedup.py` cho tên mới + `gold/tech_category_backfill.py` cho backfill 1 lần toàn bộ catalog cũ; độ trễ tới ~24h giữa lúc `category` xuất hiện trong Postgres và lúc `neo4j_enricher.py` ghi lên node, vì `tech_dedup` 03:30 chạy SAU `neo4j_enricher` 03:00 mỗi đêm), subcategory, description, trend_score, demand_score, **article_count, job_count** (derived, ghi bởi `neo4j_enricher.py`), **pagerank_score, community_id, degree_centrality** (derived, ghi bởi `apps/backend` — `Neo4jGraphAnalyticsAdapter`, admin-triggered `POST /admin/graph-analytics/rebuild`, dùng Neo4j GDS trên cạnh `RELATED_TO`; `community_id` đã remap về 0-5 cho 6 cộng đồng lớn nhất + sentinel `99` = "khác"; xem §4.3 backend guide). `name` được canonical hoá **trước khi ghi** qua `dp_tech_alias_map` ("Golang" → "Go") — xem §4.3.
- `Skill`: name, category, demand_score
- `Company`: name, **industry** (không phải `field`), size, location. `rating` chỉ được ĐỌC
  (`coalesce(c.rating, 0.0)` ở `ai-rag-core`/`ml-clustering`) — không pipeline nào còn ghi property
  này, tương tự `HIRES_FOR`.
- `Job`: **name** + **url** (không phải `title`/`source_url` — đó là tên cột legacy từ pipeline cũ;
  `Neo4jJobRepository` phải `coalesce(j.name, j.title)`/`coalesce(j.url, j.source_url)` để tương
  thích ngược), description, requirement, benefit, salary, **level** (enum
  `Intern`/`Fresher`/`Junior`/`Middle`/`Senior`/`Lead`, có thể `null` — chuẩn hoá từ free-text bởi
  `normalize_level()`, cùng cơ chế ở cả 2 đường ghi: `Neo4jExtractionWriter.java` (real-time) và
  `gold/neo4j_job_sync.py` (batch); nguồn CHECK constraint tương ứng ở Postgres là V38, §3.2).
  `due_date` chỉ được đọc qua `coalesce`, không có writer hiện tại nào ghi property này.
  > **`services/ai-rag-core` từng thiếu cùng 1 fix này:** `app/db/graph_queries.py` (6 câu Cypher
  > tra Job) chỉ đọc/lọc `j.title` — vì writer thật (`neo4j_job_sync.py`, batch, ~901/907 Job node
  > thật) ghi `name` chứ không phải `title` (chỉ 6 node cũ/test dùng `title`), tiêu đề job trong
  > response `/chat` bị `NULL` với gần như toàn bộ dữ liệu thật. Đã sửa sang cùng pattern
  > `coalesce(j.title, j.name)` như `Neo4jJobRepository` phía Java — xác minh trực tiếp trên Neo4j
  > sống trước/sau fix.
- `Person`: name, role
- `Cluster`: cluster_id, name (nhãn mô tả LLM đặt, KHÔNG unique — nhiều cluster của các lần train khác nhau có thể trùng nhãn, vd "UNLABELED"), size, updated_at. Ghi bởi `services/ml-clustering/pipelines/stage_05_writeback.py`, MERGE theo `cluster_id` — **`cluster_id` chỉ là nhãn 0..N-1 thuật toán gán lại MỖI LẦN train, không phải định danh ổn định giữa các lần** (xem cảnh báo ở `BELONGS_TO` bên dưới).

**Relationship types:**
- `MENTIONS`: Article → Technology/Company/Person
- `REQUIRES`: Job → Technology/Skill (dùng bởi **Job Matching** — `Neo4jJobRepository`)
- `HIRES_FOR`: Job → Company — **không còn pipeline nào ghi cạnh này**: nó được tạo bởi batch importer của `knowledge-graph/` (đã xoá khỏi repo, xem §4.2). Các cạnh `HIRES_FOR` hiện có trong Neo4j là dữ liệu lịch sử (từ trước khi migrate về self-hosted), không tăng thêm nữa. `graph_queries.JOBS_BY_TITLE_AND_COMPANY` và `Neo4jJobRepository`/`Neo4jCompanyRepository` vẫn match cả `POSTED_BY|HIRES_FOR` để không bỏ sót company linkage của các Job cũ.
- `POSTED_BY`: Job → Company — **cùng ý nghĩa với `HIRES_FOR`**, ghi bởi pipeline real-time của `apps/backend` (`KafkaNeo4jWriterService`, consume topic `extracted.jobs`) và bởi `data-platform/gold/neo4j_job_sync.py` (batch/nightly) — đây là cạnh **duy nhất còn được ghi mới** cho Job → Company kể từ khi `knowledge-graph/` bị xoá.
- `USES`: Company → Technology — **derived**, ghi bởi `data-platform/gold/neo4j_enricher.py` (MERGE, tăng `evidence_count`/`first_seen`); theo snapshot của `ml-clustering` (06/05/2026, dataset khác — Aura trước khi migrate) có ~11.3k cạnh này. `apps/backend` cố tình **không** đọc `USES` trực tiếp cho Company Explorer (xem ghi chú dưới).

  > **Bug thật đã fix (KG Health Audit mở rộng phát hiện):** `_COMPANY_USES_TECH` trước đây CHỈ
  > suy ra từ Article co-mention (`(a:Article)-[:MENTIONS]->(c:Company)` + `(a)-[:MENTIONS]->
  > (t:Technology)`) — nhưng chỉ **6/425** Company từng được 1 Article nhắc tên, 419 công ty còn
  > lại CHỈ tồn tại qua Job posting nên không bao giờ có cạnh `USES` nào dù thực tế đang dùng rất
  > nhiều công nghệ. Kết quả trên dataset local: chỉ 46 cạnh `USES` (không phải ~11.3k), khiến cả
  > `ai-rag-core`'s `COMPANIES_USING_TECH` (câu hỏi "công ty nào dùng React?") lẫn
  > `ml-clustering`'s `neo4j_loader.py` (dùng `USES` làm feature huấn luyện cluster) đều đói dữ
  > liệu. Đã sửa: thêm `_COMPANY_USES_TECH_FROM_JOB` — cùng tín hiệu
  > `Company<-[:POSTED_BY|HIRES_FOR]-Job-[:REQUIRES]->Technology` mà
  > `Neo4jCompanyRepository`/`COMPANY_INSIGHT_CONTEXT` (Java) đã tin dùng — MERGE chung vào cùng
  > cạnh `USES` (không tạo relationship type mới). Xác minh trực tiếp trên Neo4j sống: **46 → 3018
  > cạnh**.
  >
  > **Root cause sâu hơn của con số 6/425 ở trên (fix riêng, xem `docs/BACKEND_GUIDE.md`):**
  > `EntityExtractionService.extractEntities()` (Java) từng LUÔN trả `ORG` rỗng — không phải vì
  > Article hiếm khi nhắc tên công ty, mà vì cơ chế trích xuất tên công ty từ văn bản chưa từng
  > được cài đặt xong. Đã bổ sung `extractOrg()` (dictionary tên Company đã biết qua
  > `CompanyNameCache`) — Article xử lý MỚI từ nay có thể tạo thêm `MENTIONS(Article→Company)`,
  > làm tín hiệu Article-based cho `USES` tăng dần theo thời gian (không hồi tố cho Article cũ
  > trừ khi chạy lại `neo4j_enricher.py` sau khi các Article đó được re-process).
- `RELATED_TO`: Technology → Technology — derived, cũng ghi bởi `neo4j_enricher.py` (co-mention count); `gold/tech_dedup.py` cũng dùng chính cạnh này (2 chiều) khi merge 2 node trùng, để không mất tín hiệu co-mention đã tích luỹ ở node bị xoá — xem §4.3.
- `BELONGS_TO`: Technology → Cluster — "primary cluster" (**đúng 1 cạnh/Technology**), ghi bởi `services/ml-clustering/pipelines/stage_05_writeback.py`. `writeback.clean_before_write` PHẢI `true` (đã đổi default sau khi phát hiện bug: để `false`, cạnh của lần train trước không bị xoá nên chồng thêm mỗi lần retrain — vì `cluster_id` không ổn định giữa các lần train, MERGE theo `cluster_id` không tự thay thế được cạnh cũ; đã thấy thực tế 135/156 Technology có 2-4 cạnh `BELONGS_TO` cùng lúc trước khi dọn thủ công).
- `NEAR_CLUSTER`: Technology → Cluster — "soft link" (nhiều cạnh/Technology, có `score`), cùng writer/cùng lưu ý `clean_before_write` như `BELONGS_TO`.
- `WORKS_AT`: Person → Company — **được document nhưng CHƯA TỪNG có writer nào ghi trong toàn repo** (grep xác nhận 0 kết quả); giữ lại mục này để không ai vô tình dùng nhầm làm nguồn dữ liệu, không phải quan hệ đang hoạt động, khác với `HIRES_FOR` (có dữ liệu lịch sử thật dù không ghi mới) — xem so sánh 2 loại "quan hệ chết" ở `data-platform/gold/kg_health_audit.py`.
- `WROTE`: Person → Article — cùng tình trạng với `WORKS_AT`: chưa từng có writer nào ghi.

> **Vì sao Company Explorer không đọc thẳng `USES`:** `Neo4jCompanyRepository`/
> `GetSimilarCompaniesUseCase` suy ra tech stack của công ty gián tiếp qua
> `Company<-[:POSTED_BY|HIRES_FOR]-Job-[:REQUIRES]->Technology` thay vì đọc `Company-[:USES]->
> Technology` trực tiếp — đây là lựa chọn có chủ đích (job đang tuyển phản ánh tech stack hiện
> tại chính xác hơn `USES`, vốn là tín hiệu derived chỉ refresh theo lịch chạy của
> `neo4j_enricher.py`), không phải vì `USES` không tồn tại. Một comment cũ trong code từng ghi
> sai rằng `USES` "không service nào ghi" — đã sửa lại cho đúng thực tế ở trên.

### 4.2 Ai ghi gì

- **`apps/backend`** (`features/kafka/adapters/input/KafkaNeo4jWriterService.java`, ghi thật qua port `ExtractionWriter` → `features/kafka/adapters/output/Neo4jExtractionWriter.java`): consume `extracted.articles`/`extracted.jobs` real-time, MERGE node gốc (Article/Technology/Skill/Company/Job/Location) + cạnh trực tiếp (`MENTIONS`, `REQUIRES`, `POSTED_BY`).
- **`data-platform/gold/neo4j_article_sync.py` + `neo4j_job_sync.py`**: batch/nightly, đọc `dp_processed_articles`/`dp_processed_jobs` (Postgres silver layer) và MERGE lại cùng loại node + cạnh trực tiếp — chạy song song với writer real-time ở trên (không phải nguồn duy nhất).
- **`data-platform/gold/neo4j_enricher.py`**: chạy sau, MERGE các cạnh **derived** (`USES`, `RELATED_TO`) và cập nhật thống kê trên `Technology` (`article_count`, `job_count`, `trend_score`, và từ khi có V29: `category`, đọc từ `dp_tech_category`); log mỗi lần chạy vào `dp_pipeline_runs` (Postgres, `job_name='neo4j_enricher'`).
- **`data-platform/gold/tech_dedup.py`** (LLM, tên Technology mới phát hiện) + **`gold/tech_category_backfill.py`** (script one-time, backfill toàn bộ catalog cũ): cùng ghi bảng Postgres `dp_tech_category` — không ghi trực tiếp vào Neo4j, chỉ `neo4j_enricher.py` mới đọc bảng này và đẩy lên `Technology.category`.
- **`services/ml-clustering`**: **đọc** (qua `neo4j_loader.py`, export ra parquet làm input huấn luyện cluster) VÀ **ghi** khi `writeback.enabled=true` trong `params.yaml` (mặc định `true`) — `pipelines/stage_05_writeback.py` MERGE node `:Cluster` + cạnh `BELONGS_TO`/`NEAR_CLUSTER` sau mỗi lần train/retrain (thủ công hoặc lịch tự động Chủ nhật, xem `DATA_PLATFORM.md` — Clustering Retrain). Đây là bản ghi PHỤ, KHÔNG phải nguồn phục vụ chính: kết quả cluster mà `apps/backend`/frontend (`ClusterDashboard`, `AdminClusters`) dùng vẫn đến từ API riêng của `ml-clustering` (đọc MinIO/MLflow), không đọc `:Cluster` qua Neo4j — writeback này hiện chưa có consumer nào khác ngoài `data-platform/gold/kg_health_audit.py`/`tech_dedup.py` (redirect khi merge Technology trùng).
- **`services/ai-rag-core`**: chỉ đọc (RAG context cho `/chat`, và grounding cho `/interview` qua `graph_queries.py`) — bao gồm cả các property derived ở trên (`category`, `pagerank_score`...) để làm graph-aware ranking cho multi-hop expansion, xem [`AI_PLATFORM.md`](./AI_PLATFORM.md) §2.3.
- **`apps/backend`**: cũng đọc nhiều hơn ghi (graph explore/road-analysis, company similarity, job matching, salary/sentiment filter trên `graph`/`company`/`job` features).

> Lưu ý: thư mục `knowledge-graph/` (crawl + entity resolution + import độc lập) từng là bản đầu tiên của pipeline này nhưng đã bị thay thế bởi `services/crawler/` (crawl) + hai nguồn ghi ở trên, và đã được xoá khỏi repo — không dùng làm tài liệu tham khảo runtime nữa.

### 4.3 Tech name canonicalization & dedup (`dp_tech_alias_map`)

Trước khi có cơ chế này, cùng 1 công nghệ có thể bị lưu thành nhiều `:Technology` node khác nhau tuỳ cách viết mà crawler/LLM extract ra (`Go`/`Golang`, `AWS`/`Aws`, `ML`/`Machine Learning`, `JavaScript`/`Javascript`...), làm loãng `mention_count`/`trend_score` và phá vỡ kết quả `RELATED_TO` co-mention. Fix theo 2 cơ chế bổ sung nhau:

**a) Write-time canonicalization (chặn từ gốc, cả 2 đường ghi Neo4j):**
- `apps/backend` — `TechAliasCache.java` cache `dp_tech_alias_map` in-memory (refresh mỗi 5 phút), `EntityExtractionService.extractTech()/extractEntities()` resolve qua cache này **trước khi** publish Kafka `extracted.articles`/`extracted.jobs` → `KafkaNeo4jWriterService` chỉ nhận tên đã canonical.
- `data-platform` — `common/tech_alias_cache.py` (cùng bảng, cache riêng phía Python), gọi từ `silver/processor.py` **trước khi** ghi `dp_processed_articles.entity_techs`/`dp_processed_jobs.technologies` → cả `neo4j_article_sync.py`/`neo4j_job_sync.py` (đọc từ Silver) đều nhận tên đã canonical.

**b) Periodic cleanup (dọn phần còn sót — node trùng tạo từ trước khi có (a), hoặc case mới):**
`data-platform/gold/tech_dedup.py` (5:30 AM daily, xem [`DATA_PLATFORM.md` §5e](./DATA_PLATFORM.md)) chạy 2 giai đoạn: Giai đoạn A áp `dp_tech_alias_map` đã biết trực tiếp lên các node hiện có trong Neo4j; Giai đoạn B gửi các tên chưa khớp alias nào cho LLM (Gemini/OpenAI) để phát hiện case mới (vd "K8s"/"Kubernetes") — case confidence cao thì merge luôn + lưu `dp_tech_alias_map` (`source='llm_auto'`), case không chắc thì ghi vào `dp_tech_alias_review_queue` chờ duyệt thủ công. Từ V30, hàng chờ duyệt thủ công này có UI admin (`/admin/kg-review`, permission `kg:review`) — approve/reject qua `ApproveTechAliasUseCase`/`PostgresTechAliasReviewRepository` cập nhật `status` trên `dp_tech_alias_review_queue` và, khi approve, upsert thêm 1 dòng vào `dp_tech_alias_map` với `source='human_review'` (giá trị `source` thứ 3, cạnh `llm_auto` và seed tự tham chiếu ở V28).

`_merge_duplicate_node()` dùng **Cypher thuần** (không phải `apoc.refactor.mergeNodes`) vì APOC plugin không có sẵn trên Neo4j Docker local của project: chuyển hướng các cạnh incoming đã biết loại (`MENTIONS`, `REQUIRES`, `USES`, `IS_TECHNOLOGY`) + outgoing (`BELONGS_TO`, `NEAR_CLUSTER`) + `RELATED_TO` 2 chiều từ node trùng sang node canonical, rồi `DETACH DELETE` node trùng. Hàm này match theo **tên** — dùng cho 2 tên KHÁC NHAU cùng 1 công nghệ (biến thể chính tả/hoa-thường). Với 2 node trùng Y HỆT cùng 1 chuỗi tên (2 node vật lý riêng biệt do race MERGE trước khi có constraint ở §4.4), `{name: $name}` khớp cả hai cùng lúc nên không dùng được — có `_merge_duplicate_node_by_id()`/`_dedup_exact_duplicates()` riêng, match theo `elementId`, chạy trước Giai đoạn A/B (Giai đoạn 0).

`dp_tech_alias_map`/`dp_tech_alias_review_queue` là bảng Postgres **duy nhất** trong hệ thống được cả `apps/backend` và `data-platform` cùng dùng chung (xem ngoại lệ ở §2) — lý do là 2 service có Docker build-context tách biệt nên không thể share 1 file cấu hình, Postgres là điểm trung lập cả 2 đã kết nối sẵn.

### 4.4 Uniqueness constraints

`data-platform/common/neo4j_schema.py` (`ensure_constraints`, gọi lúc `main.py` khởi động, `CREATE CONSTRAINT IF NOT EXISTS` nên an toàn gọi lại mỗi lần restart) tạo unique constraint theo ĐÚNG property mà mọi writer hiện tại dùng để MERGE:

| Label | Property | Vì sao |
|---|---|---|
| `Article` | `id` | hash xác định trước (`md5(sourceUrl)`), không phải tên hiển thị |
| `Company` | `id` | slug xác định trước (`slugify(companyName)`) |
| `Job` | `id` | hash xác định trước (`md5(sourceUrl)`) |
| `Technology` | `name` | chưa có id xác định trước — mọi writer đều MERGE theo tên |
| `Skill` | `name` | tương tự `Technology` |
| `Location` | `name` | tương tự `Technology` |

Trước khi thêm constraint này, `SHOW CONSTRAINTS` trên instance sống trả về RỖNG hoàn toàn (đúng như cảnh báo cũ ở §1 rằng `knowledge-graph/utils/schema_define.py` — script duy nhất từng tạo constraint — đã bị xoá khỏi repo mà không có gì thay thế). Hệ quả xác nhận được trên dữ liệu thật: `MERGE (t:Technology {name: ...})` race giữa Java realtime writer (`Neo4jExtractionWriter`) và Python batch writer (`neo4j_article_sync.py`/`neo4j_job_sync.py`) — 2 transaction cùng lúc đọc "chưa tồn tại" trước khi bên nào commit — sinh ra nhiều node `Technology` trùng Y HỆT tên (`TypeScript` ×2, `Laravel` ×3, `PHP` ×2, đã dedup thủ công qua `gold/tech_dedup.py` Giai đoạn 0, xem §4.3). `Article`/`Company`/`Job` (MERGE theo `id` xác định trước) không gặp race tương tự vì `id` được tính trước khi ghi, không phụ thuộc thứ tự transaction.

`CREATE CONSTRAINT` sẽ **fail** nếu dữ liệu hiện có đã vi phạm uniqueness — `ensure_constraints()` log warning và bỏ qua constraint đó thay vì crash cả service, nhưng vẫn cần dedup thủ công trước khi bật lại (không tự động dedup ngầm trong `ensure_constraints`, để lỗi dedup không bị che giấu bởi vẻ "đã tạo constraint thành công").

---

## 5. Redis

Toàn bộ Redis chỉ dùng trong `apps/backend` (`shared/redis/*`), qua `ReactiveRedisTemplate`
(cấu hình ở `config/RedisConfig.java`). Không phải nguồn sự thật — ngoại trừ token blacklist,
mọi key khác có thể bị xoá/miss mà không gây sai dữ liệu (chỉ mất cache, sẽ tự nạp lại).

| Dùng cho | Key pattern | Service class | TTL | Ghi chú |
|---|---|---|---|---|
| Auth — token blacklist (logout/refresh) | `blacklist:token:<hashCode>` | `TokenBlacklistService` | = thời gian còn hiệu lực của token | Đây là dữ liệu **có ý nghĩa nghiệp vụ thật** (không chỉ cache) — mất key này = token đã logout vẫn dùng được. |
| Chat rate limiting | `ratelimit:chat:<userId>` | `ChatRateLimiterService` | theo `windowSeconds` truyền vào (fixed window counter, `INCR` + `EXPIRE`) | Giới hạn số lượt chat/khoảng thời gian mỗi user. |
| Auth rate limiting (login/register/forgot-password) | `ratelimit:auth:<action>:<ip>` | `AuthRateLimiterService` | theo action (mặc định 60s login/register, 300s forgot-password) | Key theo IP (chưa có user id ở bước này); action riêng cho từng endpoint nên không dùng chung 1 bộ đếm. |
| AI proxy rate limiting (career/recommend/interview/agent/forecast/report/chat-summarize/company-insight) | `ratelimit:aiproxy:user:<userId>` (route auth-required, `forwardAsCurrentUser`) hoặc `ratelimit:aiproxy:ip:<ip>` (route public, `forward`) | `AiProxyRateLimiterService` | `app.redis.aiproxy-rate-limit.window-seconds`, mặc định 60s / 20 request | Chặn spam vào các route forward sang LLM tốn chi phí ở `ai-rag-core`; gắn tại `AiProxyRequestHandler` (điểm nghẽn chung của mọi controller `aiproxy`) — vi phạm ném `RateLimitExceededException` (429), KHÔNG bị nuốt thành 503 vì check nằm ngoài `onErrorResume` xử lý lỗi upstream. |
| Cache tra cứu (look-aside, JSON) | tuỳ theo use case, đặt key tại call-site | `ReactiveRedisCache` (generic `getOrLoad`/`getOrLoadMono`) | tuỳ use case | Dùng bởi: `radar` (`GetTopTechnologiesUseCase`, `SearchTrendUseCase`, `AnalyticsScheduler`, invalidate qua `AnalyticsAdminController` khi rebuild), `salary` (`GetSalaryInsightsUseCase`, `GetTechSalaryDetailUseCase`), `clustering` (`GetClustersUseCase`), **`company`** (`GetCompaniesUseCase`, key `cache:company:all`, TTL `app.redis.company-cache-ttl` mặc định 1800s — cache toàn bộ danh sách company đã tính tech stack; `GetSimilarCompaniesUseCase` tái dùng cache này thay vì query lại Neo4j), **`job`** (`GetJobMatchesUseCase`, key `cache:job:match:<skills sắp xếp, nối bằng \|>`, TTL `app.redis.job-cache-ttl` mặc định 1800s — cache theo tập kỹ năng, fetch ở `MAX_LIMIT*3` rồi serve mọi `limit` từ cùng 1 cache entry; location/min-salary lọc SAU cache nên không làm phân mảnh cache key), **`roadmap`** (`GetCareerRoadmapUseCase`, key `cache:roadmap:<userId>`; `SimulateCareerMoveUseCase`, key `cache:simulate:<userId>:<tech>` — evict tay qua `POST /admin/cache/roadmap/evict`), **public stats** (`GetPublicStatsUseCase`, key `cache:public-stats`). |
| SSE fan-out (Pub/Sub, không phải cache) | `live:messages`, `live:notifications`, `live:radar`, `live:feed` | `MessageBroadcaster`, `NotificationService`, `RadarBroadcaster`, `FeedBroadcaster` — cả 4 dùng chung `ReactiveRedisMessageListenerContainer` bean (`RedisConfig`) | n/a (fire-and-forget, không lưu) | `publish()`/`save()` broadcast qua Redis Pub/Sub thay vì ghi thẳng `Sinks.Many` cục bộ — mọi instance backend đều subscribe channel này và tự đẩy tới SSE client cục bộ của mình, nên hoạt động đúng dù sender/recipient rơi vào instance nào. `live:radar` đẩy snapshot mới ngay sau khi ETL rebuild xong (`GET /radar/stream`); `live:feed` đẩy bài đăng mới cho feed. Không bền (không phải Kafka): nguồn sự thật vẫn là Postgres (`direct_message`/`notification`/`post`), mất live push chỉ có nghĩa client thấy tin nhắn/thông báo/bài đăng khi tự fetch lại thay vì tức thời. |

`social`, `interview` không dùng Redis — đọc thẳng Postgres/Neo4j hoặc proxy sang `ai-rag-core` mỗi
request, không cache. `aiproxy` giờ CÓ dùng Redis (chỉ để rate limit, không cache response — xem
bảng trên) kể từ khi thêm `AiProxyRateLimiterService`.

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
- **Integration test dùng Testcontainers** (Postgres/Neo4j/Redis thật, singleton container pattern trong `IntegrationTestSupport`, tự start qua static initializer + `@DynamicPropertySource`) — chỉ cần Docker chạy được trên máy, không cần tự `docker run`/set env var nữa.
- **CHECK constraint hẹp hơn business logic thực tế thì lỗi bị nuốt âm thầm, không crash**: `activity_log.type` CHECK (V4) chỉ cho `'visit'`/`'search'` cho tới V34 — khi AI-proxy consolidation thêm `AiProxyRequestHandler.recordAiRequest()` ghi `type='ai_request'`, mọi insert bị CHECK reject nhưng code gọi `.onErrorResume(e -> Mono.empty())` nên không log lỗi, chỉ lặng lẽ không ghi được gì; hệ quả duy nhất quan sát được là tile admin "Request AI hôm nay" luôn = 0. V34 mới nới CHECK. Bài học: thêm `type`/`status`/enum value mới ở tầng application PHẢI kiểm tra CHECK constraint tương ứng đã cho phép giá trị đó chưa, đừng tin code compile chạy được là insert thành công.
- **Data Platform (`dp_*`) là vùng cấm với backend/ai-rag-core** — chỉ đọc gián tiếp qua kết quả cuối (Neo4j đã enrich, hoặc `tech_analytics` đã ETL), không bao giờ query thẳng `dp_processed_articles`/`dp_processed_jobs` từ Java hay ai-rag-core. **Ngoại lệ duy nhất:** `dp_tech_alias_map`/`dp_tech_alias_review_queue` (V21) — backend đọc trực tiếp qua `features/kafka/adapters/output/TechAliasCache.java` (implements port `features/kafka/ports/TechAliasResolver.java`) vì đây là bảng canonicalization dùng chung có chủ đích giữa 2 service, xem §4.3.
- **Test coverage cho messaging/social/notification**: integration test thật (WebTestClient trên server thật, không mock) sống dưới `apps/backend/src/test/java/.../integration/` (tách từ `ApiIntegrationTest` monolithic cũ thành các lớp `*IntegrationTest` theo domain từ 2026-07-17, dùng chung base `IntegrationTestSupport`) và vẫn nhắm chủ yếu vào hiệu ứng phụ "tạo notification" (message/comment/like/follow) + `findJobMatchSubscribers`; các luồng CRUD/list cơ bản của `/feed`, `/users/{id}/posts`, `/companies/**`, `/jobs/matches`, `/interview` và endpoint admin moderation/dashboard vẫn chỉ có unit test cấp use case, không phải integration test thật. Về unit test: sau một đợt rà soát coverage, `compare`/`graph`/`clustering`/`messaging`/`social`/`user`/`system`/`radar`/`company` giờ đều có unit test đầy đủ cho tầng `application` (trước đó nhiều class ở các feature này chưa có test nào); cross-instance Redis Pub/Sub có `MessageBroadcasterRedisCrossInstanceTest`, `NotificationServiceRedisCrossInstanceTest`, `RadarBroadcasterRedisCrossInstanceTest`, và `FeedBroadcasterRedisCrossInstanceTest` (verify hành vi pub/sub cross-instance, cần `REDIS_HOST` mới chạy).
