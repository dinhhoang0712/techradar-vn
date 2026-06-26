# API Documentation — v1

Tài liệu này phản ánh **API thực tế** do Spring Boot gateway (`apps/backend`) cung cấp.

## Quy ước chung

- **Base path**: mọi endpoint nằm dưới `/api/v1` (đặt bởi `spring.webflux.base-path`). Các path dưới đây đã gồm prefix.
- **Serialization**: Jackson `SNAKE_CASE` toàn cục → request/response dùng `snake_case` (vd `refresh_token`, `full_name`). Trường `null` được lược bỏ.
- **Envelope**: phần lớn response bọc trong `ApiResponse`:
  ```json
  { "success": true, "data": {}, "message": "string", "error_code": null, "timestamp": 0 }
  ```
  **Ngoại lệ (trả object thuần / bare):** `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/me`, và `/status`.
  Client nên đọc theo dạng `res?.data ?? res` để xử lý đồng nhất.
- **Auth**: gửi `Authorization: Bearer <access_token>`. Public/Authenticated/Admin xem mục [Phân quyền](#phân-quyền).
- **Lỗi**: response lỗi luôn `success:false` kèm `message` + `error_code`. Mã HTTP: `400/401/403/404/409/503`.

---

## 1. Auth — `/api/v1/auth` *(bare cho login/register/refresh/me)*

| Method | Path | Auth | Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | Public | `{full_name, email, password, subscription_tier?}` | `{access_token, refresh_token, user_id, email, role, expires_in}` — bare. 409 nếu email tồn tại |
| POST | `/auth/login` | Public | `{email, password}` | như trên — bare. 401 nếu sai |
| POST | `/auth/refresh` | Public | `{refresh_token}` | như trên — bare |
| POST | `/auth/logout` | JWT | — | `ApiResponse` (stateless, client xoá token) |
| GET | `/auth/me` | JWT | — | `{id, email, role, status, subscription_tier}` — bare |
| POST | `/auth/forgot-password` | Public | `{email}` | `ApiResponse`. Gửi mail fire-and-forget, luôn 200 (chống dò email) |
| POST | `/auth/reset-password` | Public | `{token, new_password}` | `ApiResponse` |

## 2. User — `/api/v1/user`

| Method | Path | Auth | Body | Response |
| --- | --- | --- | --- | --- |
| GET | `/user/profile` | JWT | — | `ApiResponse<UserProfile>`: `{id, full_name, email, role, status, subscription_tier, avatar_url, bio, job_role, location, technologies[], notify_inapp, notify_email}` |
| PUT | `/user/profile` | JWT | các field profile (đều optional, gồm `notify_inapp`/`notify_email` để bật/tắt thông báo) | `ApiResponse<UserProfile>` |
| POST | `/user/avatar` | JWT | `{content_type, data_base64}` | `ApiResponse<{avatar_url}>`. Ảnh lưu ở Postgres (`user_avatar` BYTEA), giới hạn 3 MB, chỉ png/jpeg/jpg/webp/gif |
| GET | `/user/avatar/{userId}` | **Public** | — | `byte[]` ảnh thô + `X-Content-Type-Options: nosniff`. 404 nếu chưa có |

## 3. Radar — `/api/v1/radar` *(đọc từ `tech_analytics` trong Postgres)*

| Method | Path | Auth | Query | Response |
| --- | --- | --- | --- | --- |
| GET | `/radar/top4` | JWT | — | `ApiResponse<[{industry, growth_rate, job_count, mom_rate, jobs_this_month}]>` |
| GET | `/radar/top10` | JWT | — | `ApiResponse<[{keyword, job_count}]>` |
| GET | `/radar/search` | JWT | `keywords[]`, `months=6` | `ApiResponse<[{month, year, keywords{tech: count}}]>` |
| GET | `/radar/export-png` | JWT | `limit=20` | `byte[]` PNG (attachment `radar.png`) |
| GET | `/radar/export-csv` | JWT | `limit=50` | `byte[]` CSV (attachment `radar.csv`) |

## 4. Compare — `/api/v1/compare`

| Method | Path | Auth | Body / Query | Response |
| --- | --- | --- | --- | --- |
| GET | `/compare/search` | JWT | `keywords[]`, `months=12` | `ApiResponse<[{keyword, yoy_rate, mom_rate, growth_rate, monthly[]}]>` |
| POST | `/compare/llm-summary` | JWT | `{technology1, technology2, growth_rate1, growth_rate2, job_count1, job_count2, article_count1, article_count2, comparison_score}` | `ApiResponse<{summary}>` — **proxy ai-rag-core**, 503 nếu service lỗi |

## 5. Graph — `/api/v1/graph` *(Neo4j)*

| Method | Path | Auth | Body / Query | Response |
| --- | --- | --- | --- | --- |
| GET | `/graph/explore` | JWT | `keywords[]` (bắt buộc), `depth=2`, `location?`, `min_salary?` | `ApiResponse<{nodes[], edges[], found}>` |
| GET | `/graph/road_analysis` | JWT | `from`, `to` | `ApiResponse<{nodes[], edges[], found}>` — đường đi ngắn nhất |
| POST | `/graph/filter` | JWT | `{locations[]?, node_types[]?, min_salary?, max_salary?, sentiment?}` | `ApiResponse<GraphNode[]>` |

## 6. Chat — `/api/v1/chat` *(proxy ai-rag-core; session ở Postgres)*

| Method | Path | Auth | Body | Response |
| --- | --- | --- | --- | --- |
| GET | `/chat` | JWT | — | `ApiResponse<{status, neo4j, version}>` — health của RAG |
| POST | `/chat/session` | JWT | — | `ApiResponse<{session_id, created_at}>` |
| GET | `/chat/sessions` | JWT | — | `ApiResponse<[{session_id, title, created_at}]>` |
| DELETE | `/chat/session/{sessionId}` | JWT | — | `ApiResponse` (kiểm tra ownership) |
| GET | `/chat/session/{sessionId}/messages` | JWT | — | `ApiResponse<[{id, role, content}]>` |
| POST | `/chat/session/{sessionId}/messages` | JWT | `{query}` | `ApiResponse<{answer, session_id, sources[], entities[], job_titles[], query}>` |
| POST | `/chat/session/{sessionId}/messages/stream` | JWT | `{query}` | **SSE** `text/event-stream` — stream câu trả lời theo token |

## 7. Clustering — `/api/v1/clustering` *(proxy ml-clustering, 503 nếu service lỗi)*

| Method | Path | Auth | Body / Query | Response |
| --- | --- | --- | --- | --- |
| GET | `/clustering/clusters` | JWT | `is_coherent?` | `ApiResponse<[...]>` |
| GET | `/clustering/clusters/{clusterId}` | JWT | — | `ApiResponse<{...}>` |
| GET | `/clustering/tech/{techName}/cluster` | JWT | — | `ApiResponse<{...}>` |
| POST | `/clustering/predict/batch` | JWT | `{tech_names[]}` | `ApiResponse<{...}>`. 400 nếu rỗng |

> Response của clustering được trả **verbatim** từ Python (gateway không reshape).

## 7b. Notifications — `/api/v1/notifications` *(in-app, JWT; scope theo user)*

| Method | Path | Auth | Response |
| --- | --- | --- | --- |
| GET | `/notifications` | JWT | `ApiResponse<[{id, type, title, body, link, read, created_at}]>` (50 mới nhất) |
| GET | `/notifications/unread-count` | JWT | `ApiResponse<Long>` |
| POST | `/notifications/{id}/read` | JWT | `ApiResponse` — đánh dấu đã đọc |
| POST | `/notifications/read-all` | JWT | `ApiResponse` |
| GET | `/notifications/stream` | JWT | **SSE** `text/event-stream` — đẩy notification realtime (event `notification`, heartbeat mỗi 25s) |

Nguồn sự kiện đầu tiên: **trend alert**. ETL radar phát event `trend.alerts` lên Kafka khi một công nghệ
tăng ≥ `app.notifications.trend-threshold`% MoM; `TrendAlertDispatcher` fan-out tới user có công nghệ đó
trong `user_profile.technologies` (kênh in-app + email theo `notify_inapp`/`notify_email`).

> SSE `/notifications/stream` yêu cầu JWT qua header → client nên dùng fetch-based SSE (như chat stream),
> không dùng `EventSource` thuần (không gắn được header `Authorization`).

## 8. Admin — `/api/v1/admin` *(yêu cầu role ADMIN)*

**Users**

| Method | Path | Body |
| --- | --- | --- |
| GET | `/admin/users` | — |
| POST | `/admin/users` | `{email, password, full_name?, role?, status?, subscription_tier?}` |
| PUT | `/admin/users/{id}` | các field (optional) |
| DELETE | `/admin/users/{id}` | — |

**Settings**

| Method | Path | Body |
| --- | --- | --- |
| GET | `/admin/settings` · `/admin/settings/{key}` | — |
| PUT | `/admin/settings/{key}` | `{value, description?}` |
| DELETE | `/admin/settings/{key}` | — |

**Dashboard** *(đọc `activity_log` / `users`)*

| Method | Path | Response data |
| --- | --- | --- |
| GET | `/admin/dashboard/user-count` | số user |
| GET | `/admin/dashboard/visits-today` | số lượt truy cập hôm nay |
| GET | `/admin/dashboard/searches-today` | số lượt tìm kiếm hôm nay |
| GET | `/admin/dashboard/monthly-visits` | `[{month, year, visit_count}]` 12 tháng |
| GET | `/admin/dashboard/top-keywords` | `[string]` top 10 |

**CMS** *(bảng `cms_content`)*

| Method | Path | Body |
| --- | --- | --- |
| GET | `/admin/cms` | — |
| POST | `/admin/cms` | `{title, type?, content_date?, status?}` |
| PUT | `/admin/cms/{id}` | như trên |
| DELETE | `/admin/cms/{id}` | — |

**Analytics ETL**

| Method | Path | Response |
| --- | --- | --- |
| POST | `/admin/analytics/rebuild` | `ApiResponse<{rows_upserted}>` — dựng lại `tech_analytics` từ Neo4j. 503 nếu Neo4j lỗi |

## 9. Health & Status — Public

| Method | Path | Response |
| --- | --- | --- |
| GET | `/health` | `ApiResponse<{status, version, timestamp}>` |
| GET | `/status` | **bare** `{maintenance_web, maintenance_mobile, feature_graph, feature_chat, feature_rag, ...}` — feature flags từ bảng `settings` |

---

## Phân quyền

- **Public** (không cần JWT): `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password`, `/health`, `/status`, `GET /user/avatar/{userId}`, `/actuator/**`, Swagger (`/swagger-ui/**`, `/v3/api-docs/**`).
- **Admin** (`hasRole('ADMIN')`): toàn bộ `/admin/**`.
- **Authenticated** (JWT hợp lệ): tất cả endpoint còn lại.

> Lưu ý security: `spring.webflux.base-path` bị strip **trước** security filter, nên matcher trong
> `SecurityConfig.PUBLIC_PATHS` được khai báo **không** kèm `/api/v1`.

## Proxy sang Python

| Nhóm | Service | Base URL (env) | Header bảo mật | Timeout |
| --- | --- | --- | --- | --- |
| `/chat/**`, `/compare/llm-summary` | ai-rag-core | `PYTHON_RAG_BASE_URL` (`:8000`) | `X-Internal-Auth: <PYTHON_INTERNAL_TOKEN>` | 120s |
| `/clustering/**` | ml-clustering | `PYTHON_ML_CLUSTERING_BASE_URL` (`:8001`) | — (service không yêu cầu) | 60s |
