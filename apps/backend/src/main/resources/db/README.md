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
```

Quan hệ: `users 1—1 user_profile`, `users 1—N chat_session 1—N chat_message`.

**Migrations:** V1 base + flags + tech_analytics · V2 full_name + user_profile · V3 activity_log + cms_content ·
V4 CHECK (`users.role`, `chat_message.role`, `activity_log.type`) + functional/role index +
trigger `set_updated_at()` (BEFORE UPDATE: users/user_profile/chat_session/settings/cms_content) ·
V5 `user_avatar` (BYTEA, in-DB avatar) + `password_reset` (token, expires_at, used).
Dev-only: V900 admin seed · V901 sample data (demo user, tech_analytics, cms).

## 4. Quy ước migration

- Mỗi thay đổi schema = một file Flyway mới `V{n}__mô_tả.sql` (không sửa file đã apply).
- Model SQLAlchemy trong `ai-rag-core` (`app/models/*.py`) chỉ là **mirror để đọc/ghi**,
  phải khớp cột của Flyway; khi đổi schema thì cập nhật cả hai. Không dùng chúng để tạo bảng.
- Lưu ý drift đã biết: model Python `ChatSession` không có `model_used/system_prompt/updated_at`,
  `UserProfile` (Python) không có `avatar_url` — không sao vì Python chỉ ghi `chat_message`
  và chỉ đọc các cột nó cần; backend Flyway mới là schema đầy đủ.
