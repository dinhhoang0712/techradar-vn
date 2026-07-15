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
| `post`, `follow`, `post_like`, `post_comment` | backend (ghi/đọc) | Social feed — xem [`docs/DATABASE.md`](../../../../../../docs/DATABASE.md). |
| `conversation`, `direct_message` | backend (ghi/đọc) | Direct messaging 1-1; realtime là SSE in-memory (`MessageBroadcaster`), KHÔNG dùng Postgres LISTEN/NOTIFY hay Redis pub/sub — single-instance only. |

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
  role              VARCHAR(50)  NOT NULL = 'user'
  status            VARCHAR(50)  NOT NULL = 'active'
  subscription_tier VARCHAR(50)  NOT NULL = 'free'
  created_at        TIMESTAMP NOT NULL = now()
  updated_at        TIMESTAMP NOT NULL = now()

user_profile                                     -- (V2) 1-1 với users
  user_id      UUID PK  -> users(id) ON DELETE CASCADE
  job_role     VARCHAR(255)
  technologies TEXT[]
  location     VARCHAR(255)
  bio          TEXT
  avatar_url   TEXT
  updated_at   TIMESTAMP NOT NULL = now()

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
  type       VARCHAR(20) NOT NULL                 -- 'visit' | 'search' (CHECK, V4)
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

post                                              -- (V8) social feed, ghi bởi backend
  id         UUID PK
  user_id    UUID NOT NULL -> users(id) ON DELETE CASCADE
  content    TEXT NOT NULL
  created_at TIMESTAMP NOT NULL = now()
  INDEX idx_post_created(created_at DESC), idx_post_user(user_id, created_at DESC)

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
  id         UUID PK
  post_id    UUID NOT NULL -> post(id) ON DELETE CASCADE
  user_id    UUID NOT NULL -> users(id) ON DELETE CASCADE
  content    TEXT NOT NULL
  created_at TIMESTAMP NOT NULL = now()
  INDEX idx_comment_post(post_id, created_at)

conversation                                      -- (V9) direct messaging, ghi bởi backend
  id         UUID PK
  user_a_id  UUID NOT NULL -> users(id) ON DELETE CASCADE   -- luôn user_a_id < user_b_id
  user_b_id  UUID NOT NULL -> users(id) ON DELETE CASCADE      (canonical ordering, CHECK)
  created_at TIMESTAMP NOT NULL = now()
  CHECK(user_a_id < user_b_id), UNIQUE(user_a_id, user_b_id)
  INDEX idx_conversation_user_a(user_a_id), idx_conversation_user_b(user_b_id)

direct_message                                    -- (V9)
  id              UUID PK
  conversation_id UUID NOT NULL -> conversation(id) ON DELETE CASCADE
  sender_id       UUID NOT NULL -> users(id) ON DELETE CASCADE
  content         TEXT NOT NULL
  created_at      TIMESTAMP NOT NULL = now()
  read_at         TIMESTAMP NULL
  INDEX idx_dm_conversation(conversation_id, created_at)
```

Quan hệ: `users 1—1 user_profile`, `users 1—N chat_session 1—N chat_message`,
`users 1—N post 1—N post_comment`, `users N—N users` qua `follow`/`post_like` (composite PK, không có bảng riêng),
`users 1—1 conversation` (canonical `user_a_id<user_b_id`) `1—N direct_message`.

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
tận dụng index).
Dev-only: V900 admin seed · V901 sample data (demo user, tech_analytics, cms) · V902-V905 (jobs/articles/activity_log/tech_analytics/users+cms mở rộng cho dev).

Xem thêm [`docs/DATABASE.md`](../../../../../../docs/DATABASE.md) cho bức tranh toàn hệ CSDL
(Postgres + Neo4j + Redis) và các quy ước cross-service; file này (`db/README.md`) vẫn là
nguồn sự thật cho **DDL chi tiết** của Postgres (do Flyway quản lý).

## 4. Quy ước migration

- Mỗi thay đổi schema = một file Flyway mới `V{n}__mô_tả.sql` (không sửa file đã apply).
- Model SQLAlchemy trong `ai-rag-core` (`app/models/*.py`) chỉ là **mirror để đọc/ghi**,
  phải khớp cột của Flyway; khi đổi schema thì cập nhật cả hai. Không dùng chúng để tạo bảng.
- Lưu ý drift đã biết: model Python `ChatSession` không có `model_used/system_prompt/updated_at`,
  `UserProfile` (Python) không có `avatar_url` — không sao vì Python chỉ ghi `chat_message`
  và chỉ đọc các cột nó cần; backend Flyway mới là schema đầy đủ.
