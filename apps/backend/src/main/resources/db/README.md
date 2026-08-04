# TechRadar — Thiết kế Database (Postgres)

Tài liệu này là **nguồn sự thật** về schema Postgres dùng chung và quy ước sở hữu
giữa Spring backend (`apps/backend`) và service `ai-rag-core`.

## 1. Nguyên tắc sở hữu (QUAN TRỌNG)

| Hạng mục | Chủ sở hữu | Ghi chú |
|---|---|---|
| **Schema (DDL)** | **Flyway của backend** (`db/migration/V*.sql`) | Nguồn DUY NHẤT. `ai-rag-core` **KHÔNG** `create_tables` (đã gỡ) để tránh schema drift. |
| `users`, `user_profile` | backend (ghi) | `ai-rag-core` chỉ **đọc** `user_profile` để cá nhân hoá RAG. |
| `chat_session` | backend (ghi/đọc/auth) | Backend quản vòng đời session: tạo, list, xoá, kiểm quyền sở hữu. |
| `chat_message` | **ai-rag-core (ghi)** | Service RAG sinh câu trả lời nên nó ghi cả `user` lẫn `assistant` message (cả non-stream lẫn stream). Backend chỉ **đọc** để trả lịch sử. |
| `settings`, `tech_analytics` | backend | Feature flags + ETL Neo4j→Postgres. |
| `notification` | backend (ghi/đọc) | Kafka `trend.alerts` → `NotificationDispatcher` (in-app + email fan-out). |
| `dp_bronze_catalog`, `dp_processed_articles`, `dp_processed_jobs`, `dp_pipeline_runs` | **data-platform** (Python, ghi) | Backend/Flyway chỉ tạo bảng; ghi/đọc thuộc `data-platform` (bronze/silver catalog, xem `data-platform/README.md`). |
| `post`, `follow`, `post_like`, `post_comment`, `post_image`, `content_report` | backend (ghi/đọc) | Social feed + moderation — xem [`docs/DATABASE.md`](../../../../../../docs/DATABASE.md). |
| `conversation`, `direct_message`, `message_reaction` | backend (ghi/đọc) | Direct messaging 1-1; realtime là SSE, fan-out qua Redis Pub/Sub (`MessageBroadcaster`, channel `live:messages`) nên chạy đúng với nhiều instance backend — xem [`docs/DATABASE.md`](../../../../../../docs/DATABASE.md) §5. |
| `roles`, `permissions`, `role_permissions`, `audit_log` | backend (ghi/đọc) | RBAC (V24) chồng lên `users.role` phẳng + audit trail thao tác admin (V20). |
| `dp_tech_alias_map`, `dp_tech_alias_review_queue` | backend (ghi) + **data-platform** (Python, ghi) | Chuẩn hoá tên công nghệ dùng chung giữa `EntityExtractionService.java` (Kafka realtime) và `silver/processor.py`. |
| `dp_tech_category` | **data-platform** (Python, ghi) | Backend/Flyway chỉ tạo bảng (V29); phân loại category ghi bởi `gold/tech_category_backfill.py`. |
| `llm_usage_log` | **ai-rag-core (ghi)** | Cost tracking mỗi lần `services/llm-gateway` gọi thành công 1 provider LLM. Giống `chat_message`: backend/Flyway chỉ tạo bảng (V33), không ghi/đọc. |

> **Vì sao tách `chat_session` (backend) và `chat_message` (ai-rag-core)?**
> Trước đây CẢ HAI cùng ghi message → mỗi lượt chat tạo 4 dòng thay vì 2
> (double-write). Đã sửa: backend ngừng ghi message; `ai-rag-core` là writer duy
> nhất của message (nó có sẵn full answer + xử lý stream accumulation). Backend
> vẫn là chủ session để có thể tạo session rỗng và kiểm quyền sở hữu trước khi
> gọi RAG. `session_id` luôn do client/path cung cấp nên 2 bên không lệch id.

## 2. Provisioning

Chạy backend → Flyway tự migrate khi khởi động (`jdbc`), app chạy reactive qua
`r2dbc`. `ai-rag-core` phụ thuộc DB đã được Flyway tạo (compose: `depends_on: postgres`,
backend chạy migration). KHÔNG còn `Base.metadata.create_all` ở phía Python.

DB mặc định (compose): `techradar` @ `postgres:5432`, user `postgres`.

## 3. Schema

```
users
  id                UUID  PK
  email             VARCHAR(255) UNIQUE NOT NULL
  password_hash     VARCHAR(255) NOT NULL
  full_name         VARCHAR(255)                 -- (V2)
  role              VARCHAR(50)  NOT NULL = 'user'   -- FK -> roles(code) (V24, fk_users_role)
  status            VARCHAR(50)  NOT NULL = 'active'
  subscription_tier VARCHAR(50)  NOT NULL = 'free'
  created_at        TIMESTAMP NOT NULL = now()
  updated_at        TIMESTAMP NOT NULL = now()
  security_stamp    UUID NOT NULL = gen_random_uuid()  -- (V24) đổi giá trị này để revoke token
                                                         ngay khi admin đổi role/status của user

user_profile                                     -- (V2) 1-1 với users
  user_id          UUID PK  -> users(id) ON DELETE CASCADE
  job_role         VARCHAR(255)
  technologies     TEXT[]
  location         VARCHAR(255)
  bio              TEXT
  avatar_url       TEXT
  updated_at       TIMESTAMP NOT NULL = now()
  preferences_json JSONB                          -- (V17) đọc/ghi bởi ai-rag-core (long-term memory/
                                                       personalization); thiếu cột này ở Java trước V17
                                                       nên mọi read/write từ ai-rag-core silently fail
  target_skills    TEXT[]                         -- (V22) top skill roadmap đang học (career GPS),
                                                       dùng thêm bởi JobMatchDispatcher ngoài technologies
  INDEX idx_user_profile_technologies_gin(technologies) USING GIN  -- (V10)
  INDEX idx_user_profile_target_skills_gin(target_skills) USING GIN  -- (V22), mirror V10

chat_session                                     -- ghi bởi backend
  id            UUID PK
  user_id       UUID -> users(id) ON DELETE CASCADE
  title         VARCHAR(255)
  model_used    VARCHAR(100)
  system_prompt TEXT
  created_at    TIMESTAMP NOT NULL = now()
  updated_at    TIMESTAMP NOT NULL = now()
  INDEX idx_chat_session_user(user_id)

chat_message                                     -- ghi bởi ai-rag-core
  id                UUID PK
  session_id        UUID NOT NULL -> chat_session(id) ON DELETE CASCADE
  role              VARCHAR(20) NOT NULL          -- 'user' | 'assistant'
  content           TEXT NOT NULL
  prompt_tokens     INTEGER = 0
  completion_tokens INTEGER = 0
  finish_reason     VARCHAR(50)
  created_at        TIMESTAMP NOT NULL = now()
  INDEX idx_chat_message_session(session_id, created_at)

settings
  key         VARCHAR(100) PK                     -- maintenance_web/mobile, feature_graph/chat/rag
  value       TEXT
  description TEXT
  updated_at  TIMESTAMP NOT NULL = now()

tech_analytics                                    -- ETL Neo4j -> Postgres (radar/compare)
  id              BIGSERIAL PK
  technology_name VARCHAR(255) NOT NULL
  month           DATE NOT NULL
  job_count       INTEGER NOT NULL = 0
  article_count   INTEGER NOT NULL = 0
  growth_rate     DOUBLE PRECISION NOT NULL = 0
  yoy_growth      DOUBLE PRECISION
  mom_growth      DOUBLE PRECISION
  ranking         INTEGER
  UNIQUE(technology_name, month)
  INDEX idx_tech_analytics_month(month)
  INDEX idx_tech_analytics_name_lower(lower(technology_name))   -- (V4)

activity_log                                      -- (V3) traffic/search metrics cho admin dashboard
  id         BIGSERIAL PK
  type       VARCHAR(20) NOT NULL                 -- 'visit'|'search' (CHECK chk_activity_type, V4);
                                                      widened V34 to allow 'ai_request' — trước V34
                                                      mọi insert type='ai_request' của
                                                      AiProxyRequestHandler.recordAiRequest() bị
                                                      constraint chặn âm thầm (best-effort .onErrorResume())
  user_id    UUID
  path       TEXT
  keyword    TEXT
  created_at TIMESTAMP NOT NULL = now()
  INDEX idx_activity_type_time(type, created_at), idx_activity_keyword(keyword)

cms_content                                       -- (V3) AdminCMS content
  id           UUID PK
  title        VARCHAR(500) NOT NULL
  type         VARCHAR(50)                          -- Report | Job | Keyword
  content_date DATE
  status       VARCHAR(50) NOT NULL = 'Pending'
  created_at   TIMESTAMP NOT NULL = now()
  updated_at   TIMESTAMP NOT NULL = now()
  body         TEXT                                 -- (V35) nội dung report tháng do
                                                         MonthlyReportSchedulerService sinh; NULL cho
                                                         row crawler/keyword-digest (không có body riêng)

outbox_event                                      -- (V36) transactional outbox — xem
                                                      docs/adr/0005-transactional-outbox-trend-alerts.md
  id           UUID PK
  topic        VARCHAR(100) NOT NULL               -- vd. 'trend.alerts'
  payload      TEXT NOT NULL                        -- JSON đã serialize (snake_case, cùng ObjectMapper
                                                        dùng cho Kafka), publish verbatim, không serialize lại
  status       VARCHAR(20) NOT NULL = 'PENDING'     -- CHECK IN ('PENDING','PUBLISHED','FAILED')
  attempts     INT NOT NULL = 0
  last_error   TEXT
  created_at   TIMESTAMP NOT NULL = now()
  published_at TIMESTAMP
  INDEX idx_outbox_event_status_created(status, created_at)   -- relay poller: oldest-unpublished-first

notification                                      -- (V6) in-app/email, ghi bởi backend
  id         UUID PK
  user_id    UUID NOT NULL -> users(id) ON DELETE CASCADE
  type       VARCHAR(40) NOT NULL                  -- trend_alert | system | career | ...
  title      VARCHAR(200) NOT NULL
  body       TEXT
  link       VARCHAR(300)
  is_read    BOOLEAN NOT NULL = false
  created_at TIMESTAMP NOT NULL = now()
  INDEX idx_notification_user(user_id, is_read, created_at DESC)
  -- (V6) user_profile += notify_inapp, notify_email BOOLEAN = true (per-user channel prefs)

dp_bronze_catalog / dp_processed_articles /       -- (V7) sở hữu bởi `data-platform` (Python),
dp_processed_jobs / dp_pipeline_runs                 KHÔNG phải backend/ai-rag-core. Registry file
                                                      MinIO (bronze) + article/job đã dedupe (silver)
                                                      + log mỗi lần chạy Gold ETL/enricher.
                                                      Chi tiết cột: xem `data-platform/README.md`.
                                                      (V18) dp_processed_jobs += company_industry TEXT,
                                                      company_size TEXT (nullable; scraped từ
                                                      "Lĩnh vực"/"Quy mô", chỉ job (re-)process sau V18
                                                      mới có giá trị).
                                                      (V23) INDEX idx_dp_runs_job_started trên
                                                      dp_pipeline_runs(job_name, started_at DESC) — phục
                                                      vụ history query filter+sort không cần scan/sort rời.

post                                              -- (V8) social feed, ghi bởi backend
  id                      UUID PK
  user_id                 UUID NOT NULL -> users(id) ON DELETE CASCADE
  content                 TEXT NOT NULL
  created_at              TIMESTAMP NOT NULL = now()
  hashtags                TEXT[]                 -- (V14)
  tagged_company_id       TEXT                   -- (V15) snapshot từ Neo4j, KHÔNG FK (company không
  tagged_company_name     TEXT                   --       nằm trong Postgres)
  tagged_company_location TEXT                   -- (V15)
  deleted_at              TIMESTAMP               -- (V26) soft-delete; NULL = còn sống. Xoá cứng trước
                                                        V26 sẽ CASCADE xoá luôn content_report liên quan
                                                        (bằng chứng moderation) — nay chỉ tombstone.
  INDEX idx_post_created(created_at DESC), idx_post_user(user_id, created_at DESC)
  INDEX idx_post_hashtags_gin(hashtags) USING GIN                      -- (V14)
  INDEX idx_post_deleted_at(deleted_at) WHERE deleted_at IS NOT NULL   -- (V26)

follow                                            -- (V8) đồ thị theo dõi (không phải Neo4j)
  follower_id UUID NOT NULL -> users(id) ON DELETE CASCADE
  followee_id UUID NOT NULL -> users(id) ON DELETE CASCADE
  created_at  TIMESTAMP NOT NULL = now()
  PK(follower_id, followee_id), CHECK(follower_id <> followee_id)
  INDEX idx_follow_followee(followee_id)

post_like                                         -- (V8)
  post_id UUID NOT NULL -> post(id) ON DELETE CASCADE
  user_id UUID NOT NULL -> users(id) ON DELETE CASCADE
  created_at TIMESTAMP NOT NULL = now()
  PK(post_id, user_id)

post_comment                                      -- (V8)
  id                UUID PK
  post_id           UUID NOT NULL -> post(id) ON DELETE CASCADE
  user_id           UUID NOT NULL -> users(id) ON DELETE CASCADE
  content           TEXT NOT NULL
  created_at        TIMESTAMP NOT NULL = now()
  parent_comment_id UUID -> post_comment(id) ON DELETE CASCADE   -- (V16) self-reference, reply lồng nhau
  deleted_at        TIMESTAMP                                    -- (V26) soft-delete; NULL = còn sống
  INDEX idx_comment_post(post_id, created_at)
  INDEX idx_comment_parent(parent_comment_id)                              -- (V16)
  INDEX idx_post_comment_deleted_at(deleted_at) WHERE deleted_at IS NOT NULL  -- (V26)

conversation                                      -- (V9) direct messaging, ghi bởi backend
  id         UUID PK
  user_a_id  UUID NOT NULL -> users(id) ON DELETE CASCADE   -- luôn user_a_id < user_b_id
  user_b_id  UUID NOT NULL -> users(id) ON DELETE CASCADE      (canonical ordering, CHECK)
  created_at TIMESTAMP NOT NULL = now()
  CHECK(user_a_id < user_b_id), UNIQUE(user_a_id, user_b_id)
  INDEX idx_conversation_user_a(user_a_id), idx_conversation_user_b(user_b_id)

direct_message                                    -- (V9)
  id                       UUID PK
  conversation_id          UUID NOT NULL -> conversation(id) ON DELETE CASCADE
  sender_id                UUID NOT NULL -> users(id) ON DELETE CASCADE
  content                  TEXT NOT NULL
  created_at               TIMESTAMP NOT NULL = now()
  read_at                  TIMESTAMP NULL
  attachment_content_type  VARCHAR(150)           -- (V31) file/ảnh đính kèm 1-per-message, base64 in,
  attachment_filename      VARCHAR(255)           --       BYTEA storage (cùng dạng post_image/avatar)
  attachment_size          INTEGER                -- (V31)
  attachment_data          BYTEA                  -- (V31) chỉ SELECT bởi endpoint serve riêng, KHÔNG
                                                        bởi query list lịch sử hội thoại (giữ nhẹ)
  INDEX idx_dm_conversation(conversation_id, created_at)

content_report                                    -- (V11) report/flag trên post/comment, ghi bởi backend
  id                  UUID PK
  reporter_id         UUID NOT NULL -> users(id) ON DELETE CASCADE
  post_id             UUID -> post(id) ON DELETE CASCADE
  comment_id          UUID -> post_comment(id) ON DELETE CASCADE
  reason              TEXT NOT NULL
  status              VARCHAR(20) NOT NULL = 'PENDING'
  created_at          TIMESTAMP NOT NULL = now()
  resolved_at         TIMESTAMP
  resolved_by         UUID -> users(id) ON DELETE SET NULL
  ai_suggested_action VARCHAR(20)           -- (V19) 'REMOVE'|'DISMISS', CHECK chk_ai_suggested_action;
                                                cache gợi ý moderation LLM, tính khi admin bấm "Gợi ý AI"
  ai_suggested_reason TEXT                  -- (V19)
  ai_confidence       DOUBLE PRECISION      -- (V19)
  ai_suggested_at     TIMESTAMP             -- (V19)
  CHECK(status IN ('PENDING', 'DISMISSED'))
  CHECK((post_id IS NOT NULL AND comment_id IS NULL) OR (post_id IS NULL AND comment_id IS NOT NULL))
  INDEX idx_report_status(status, created_at)
  UNIQUE INDEX uq_report_reporter_post(reporter_id, post_id)
    WHERE post_id IS NOT NULL AND status = 'PENDING'       -- (V12, sửa từ V11: V11 chặn re-report vô
                                                                thời hạn; V12 chỉ chặn khi đang PENDING —
                                                                report đã bị dismiss thì báo lại được)
  UNIQUE INDEX uq_report_reporter_comment(reporter_id, comment_id)
    WHERE comment_id IS NOT NULL AND status = 'PENDING'    -- (V12)

post_image                                        -- (V13) ảnh đính kèm post, ghi bởi backend
  id           UUID PK
  post_id      UUID NOT NULL -> post(id) ON DELETE CASCADE
  ordinal      INT NOT NULL
  content_type VARCHAR(100) NOT NULL
  data         BYTEA NOT NULL
  created_at   TIMESTAMP NOT NULL = now()
  UNIQUE(post_id, ordinal)
  INDEX idx_post_image_post(post_id, ordinal)

audit_log                                         -- (V20) audit trail thao tác admin, ghi bởi backend
  id          UUID PK = gen_random_uuid()
  actor_id    UUID NOT NULL         -- KHÔNG FK -> users(id): trail phải sống sót qua việc xoá actor
  action      VARCHAR(50) NOT NULL
  target_type VARCHAR(50)
  target_id   VARCHAR(100)
  details     TEXT
  created_at  TIMESTAMP NOT NULL = now()
  INDEX idx_audit_log_created_at(created_at DESC), idx_audit_log_actor(actor_id)

dp_tech_alias_map                                 -- (V21) chuẩn hoá tên công nghệ, ghi bởi backend
  alias_normalized TEXT PK             -- casefold+trim, vd "golang"        (`EntityExtractionService
  canonical_name   TEXT NOT NULL       -- vd "Go"                           .java`, Kafka realtime) VÀ
  source           TEXT NOT NULL = 'seed'  -- 'seed'|'llm_auto'|'human_review'  data-platform (Python,
  created_at       TIMESTAMPTZ NOT NULL = now()                            `silver/processor.py`)
  INDEX idx_dp_tech_alias_canonical(canonical_name)
  -- Seed: alias viết tắt/đồng nghĩa tiếng Anh (V21) + alias tự tham chiếu casefold cho toàn bộ
  -- TECH_KEYWORDS còn thiếu (V28) — chặn node Neo4j trùng khác case (vd "SQL"/"Sql"/"sql").

dp_tech_alias_review_queue                        -- (V21) hàng chờ duyệt case LLM không tự tin
  id            BIGSERIAL PK
  name_a        TEXT NOT NULL
  name_b        TEXT NOT NULL
  llm_reasoning TEXT
  status        TEXT NOT NULL = 'pending'   -- 'pending' | 'approved' | 'rejected'
  created_at    TIMESTAMPTZ NOT NULL = now()
  decided_at    TIMESTAMPTZ
  INDEX idx_dp_tech_review_status(status)

roles                                             -- (V24) RBAC, ghi bởi backend
  id          UUID PK = gen_random_uuid()
  code        VARCHAR(50)  NOT NULL UNIQUE   -- 'user','admin' (V24 seed) + 'moderator' (V25 seed)
  name        VARCHAR(100) NOT NULL
  description TEXT

permissions                                       -- (V24)
  id          UUID PK = gen_random_uuid()
  code        VARCHAR(100) NOT NULL UNIQUE  -- 12 permission gốc (V24) + 'graph:manage' (V27) +
  description TEXT                             'kg:review' (V30)

role_permissions                                  -- (V24) N—N roles×permissions
  role_id       UUID NOT NULL -> roles(id) ON DELETE CASCADE
  permission_id UUID NOT NULL -> permissions(id) ON DELETE CASCADE
  PK(role_id, permission_id)
  -- V24 seed: 'admin' <- MỌI permission tồn tại lúc đó (grant-all snapshot, một lần — permission
  -- thêm sau (V27 graph:manage, V30 kg:review) phải tự INSERT role_permissions cho 'admin', vì
  -- INSERT của V24 không tự động phủ permission chưa tồn tại lúc nó chạy).
  -- V25 seed: 'moderator' <- 'social:moderate', 'audit:view'.

dp_tech_category                                  -- (V29) phân loại category công nghệ, sở hữu bởi
  canonical_name TEXT PK                              `data-platform` (Python); backend/Flyway chỉ tạo
  category       TEXT NOT NULL                        bảng, ghi bởi `gold/tech_category_backfill.py`
  source         TEXT NOT NULL = 'llm_auto'   -- 'llm_auto' | 'human_review'
  updated_at     TIMESTAMPTZ NOT NULL = now()

message_reaction                                  -- (V32) 1 emoji/user/message, ghi bởi backend
  message_id UUID NOT NULL -> direct_message(id) ON DELETE CASCADE
  user_id    UUID NOT NULL -> users(id) ON DELETE CASCADE
  emoji      VARCHAR(8) NOT NULL
  created_at TIMESTAMP NOT NULL = now()
  PK(message_id, user_id)              -- set reaction mới = replace reaction cũ (giống Messenger)
  INDEX idx_message_reaction_message(message_id)

llm_usage_log                                     -- (V33) ghi bởi ai-rag-core (KHÔNG phải backend)
  id            UUID PK               -- sinh ở tầng application, giống chat_session/chat_message
  service       VARCHAR(50)  NOT NULL  -- 'ai-rag-core' | 'ml-clustering' | 'data-platform'
  provider      VARCHAR(50)  NOT NULL  -- 'openai' | 'groq' | 'gemini' | 'claude'
  model         VARCHAR(100) NOT NULL
  input_tokens  INTEGER NOT NULL
  output_tokens INTEGER NOT NULL
  cost_usd      NUMERIC(12,6) NOT NULL
  fallback_from VARCHAR(50)            -- provider lỗi trước khi fallback xuống, NULL nếu gọi thẳng OK
  created_at    TIMESTAMP NOT NULL = now()
  INDEX idx_llm_usage_log_created_at(created_at), idx_llm_usage_log_provider_model(provider, model)
```

Quan hệ: `users 1—1 user_profile`, `users 1—N chat_session 1—N chat_message`,
`users 1—N post 1—N post_comment` (`1—N post_image`, `1—N content_report` qua `post_id`/`comment_id`),
`post_comment 1—N post_comment` qua `parent_comment_id` (reply lồng nhau, V16),
`users N—N users` qua `follow`/`post_like` (composite PK, không có bảng riêng),
`users 1—1 conversation` (canonical `user_a_id<user_b_id`) `1—N direct_message 1—N message_reaction`,
`roles N—N permissions` qua `role_permissions` (composite PK).

**Migrations:** V1 base + flags + tech_analytics · V2 full_name + user_profile · V3 activity_log + cms_content ·
V4 CHECK (`users.role`, `chat_message.role`, `activity_log.type`) + functional/role index +
trigger `set_updated_at()` (BEFORE UPDATE: users/user_profile/chat_session/settings/cms_content) ·
V5 `user_avatar` (BYTEA, in-DB avatar) + `password_reset` (token, expires_at, used) ·
V6 `notification` + `user_profile` notify_inapp/notify_email ·
V7 `dp_bronze_catalog`/`dp_processed_articles`/`dp_processed_jobs`/`dp_pipeline_runs` (data-platform catalog, sở hữu bởi service Python `data-platform`, KHÔNG phải ai-rag-core) ·
V8 `post`/`follow`/`post_like`/`post_comment` (social feed) ·
V9 `conversation`/`direct_message` (direct messaging) ·
V10 GIN index `idx_user_profile_technologies_gin` trên `user_profile.technologies` (dùng bởi
`PostgresNotificationRepository.findTrendSubscribers`, chạy trên mỗi Kafka `trend.alerts` — trước
đó là sequential scan; query đổi từ `:tech = ANY(technologies)` sang `technologies @> :tech` để
tận dụng index) ·
V11 `content_report` (báo cáo post/comment) + unique index chống report trùng ·
V12 sửa unique index của V11 — chỉ chặn re-report khi report cũ đang PENDING (đã dismiss thì report lại được) ·
V13 `post_image` (ảnh đính kèm post, BYTEA) ·
V14 `post.hashtags` (TEXT[]) + GIN index cho filter feed theo hashtag ·
V15 `post.tagged_company_id/name/location` (snapshot tên/địa điểm công ty từ Neo4j, không FK) ·
V16 `post_comment.parent_comment_id` (self-reference) — reply lồng nhau ·
V17 `user_profile.preferences_json` (JSONB) — cột này thiếu ở Java trước V17 nên mọi read/write từ
ai-rag-core (long-term memory/personalization) silently fail ·
V18 `dp_processed_jobs` += `company_industry`, `company_size` (nullable, backfill dần) ·
V19 `content_report` += `ai_suggested_action/ai_suggested_reason/ai_confidence/ai_suggested_at`
(gợi ý moderation LLM, tính khi admin bấm "Gợi ý AI", không tự động) ·
V20 `audit_log` (audit trail thao tác admin; không FK tới `users` để trail sống sót qua xoá actor) ·
V21 `dp_tech_alias_map` + `dp_tech_alias_review_queue` (chuẩn hoá tên công nghệ dùng chung
`EntityExtractionService.java` và `silver/processor.py`) + seed alias đồng nghĩa ·
V22 `user_profile.target_skills` (TEXT[]) + GIN index — mirror `technologies`/V10 cho career roadmap ·
V23 composite index `idx_dp_runs_job_started` trên `dp_pipeline_runs(job_name, started_at DESC)` ·
V24 RBAC: `roles`/`permissions`/`role_permissions` + FK `users.role -> roles(code)`
(`fk_users_role`) + `users.security_stamp` (revoke token khi đổi role/status) ·
V25 seed role `moderator` (`social:moderate`, `audit:view`) ·
V26 soft-delete: `post.deleted_at`, `post_comment.deleted_at` — xoá post/comment không còn CASCADE
xoá cứng `content_report` liên quan (giữ bằng chứng moderation) ·
V27 permission `graph:manage` (GDS graph-analytics rebuild) — grant thẳng cho `admin` vì grant-all
của V24 không tự phủ permission thêm sau ·
V28 seed alias tự tham chiếu (casefold) cho toàn bộ TECH_KEYWORDS còn thiếu, chặn trùng node khác
case (kiểu lỗi tương tự "SQL"/"Sql"/"sql") ·
V29 `dp_tech_category` (phân loại category công nghệ theo `canonical_name`, sở hữu data-platform) ·
V30 permission `kg:review` (Knowledge Graph review queue) — cùng lý do grant riêng như V27 ·
V31 `direct_message` += cột đính kèm file/ảnh (`attachment_content_type/filename/size/data`, BYTEA) ·
V32 `message_reaction` (1 emoji/user/message trên `direct_message`, cùng dạng `post_like`) ·
V33 `llm_usage_log` (cost tracking mỗi lần gọi LLM qua `services/llm-gateway`, ghi bởi
**ai-rag-core** — backend/Flyway chỉ tạo bảng, giống `chat_message`) ·
V34 nới `chk_activity_type` cho phép `'ai_request'` — trước đó `AiProxyRequestHandler.recordAiRequest()`
insert bị constraint chặn âm thầm (best-effort `.onErrorResume()`), khiến tile admin live-metrics
"Request AI hôm nay" luôn đọc ra 0 ·
V35 `cms_content.body` (TEXT) — lưu nội dung report tháng do `MonthlyReportSchedulerService` sinh ·
V36 `outbox_event` — transactional outbox cho `trend.alerts` (xem
[ADR-0005](../../../../../../docs/adr/0005-transactional-outbox-trend-alerts.md)); `RadarAnalyticsEtlService`
ghi row PENDING trong CÙNG transaction với `tech_analytics` upsert, `OutboxRelayScheduler` publish
qua Kafka và đánh dấu PUBLISHED/FAILED.
Dev-only: V900 admin seed · V901 sample data (demo user, tech_analytics, cms) · V902-V905 (jobs/articles/activity_log/tech_analytics/users+cms mở rộng cho dev).

Xem thêm [`docs/DATABASE.md`](../../../../../../docs/DATABASE.md) cho bức tranh toàn hệ CSDL
(Postgres + Neo4j + Redis) và các quy ước cross-service; file này (`db/README.md`) vẫn là
nguồn sự thật cho **DDL chi tiết** của Postgres (do Flyway quản lý, cập nhật đến V36).

## 4. Quy ước migration

- Mỗi thay đổi schema = một file Flyway mới `V{n}__mô_tả.sql` (không sửa file đã apply).
- Model SQLAlchemy trong `ai-rag-core` (`app/models/*.py`) chỉ là **mirror để đọc/ghi**,
  phải khớp cột của Flyway; khi đổi schema thì cập nhật cả hai. Không dùng chúng để tạo bảng.
- Lưu ý drift đã biết: model Python `ChatSession` không có `model_used/system_prompt/updated_at`,
  `UserProfile` (Python) không có `avatar_url` — không sao vì Python chỉ ghi `chat_message`
  và chỉ đọc các cột nó cần; backend Flyway mới là schema đầy đủ.
