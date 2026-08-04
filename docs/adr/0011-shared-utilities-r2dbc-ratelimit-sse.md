# ADR-0011: 3 tiện ích dùng chung mới (R2DBC bind, rate limiter, SSE heartbeat)

**Status**: Accepted

## Context

Sau khi gom `@OneOf` (ADR-0010) cho phần validation, rà soát tiếp toàn bộ backend (không giới
hạn ở validation) tìm thêm code lặp lại đáng gom. Phát hiện 3 nhóm, mỗi nhóm lặp lại y hệt ở
nhiều file, cộng 1 bug thật (thiếu heartbeat) lộ ra trong lúc rà soát.

## Decision

### 1. `shared/db/R2dbcBinders`

`.bind(name, null)` throw (R2DBC), nên mọi cột nullable cần `.bindNull(name, Class)` thay thế —
ternary 2-3 dòng này bị copy-paste thành **method private riêng** ở 5 repository
(`PostgresUserRepository`, `PostgresPostRepository`, `PostgresChatRepository`,
`PostgresUserProfileRepository`, `PostgresCmsRepository` — file này còn có thêm bản
`bindNullableDate` riêng cho `LocalDate`), và **inline trực tiếp** (không cả extract local) ở 7
file khác (`PostgresAuditLogRepository`, `PostgresReportRepository`,
`PostgresActivityLogRepository`, `PostgresOutboxEventRepository`,
`PostgresNotificationRepository`, `PostgresCommentRepository`,
`PostgresTechAnalyticsWriteAdapter`) — tổng **12 file**, cao nhất trong 3 nhóm. Gom thành 1 method
generic `bindNullable(spec, name, value, type)` + 1 overload cho `String` (loại phổ biến nhất,
khỏi phải viết `String.class` ở mọi nơi).

### 2. `shared/redis/FixedWindowRateLimiter`

`AuthRateLimiterService`/`ChatRateLimiterService`/`AiProxyRateLimiterService` mỗi cái tự viết lại
y hệt khối INCR + EXPIRE + so sánh (~15 dòng/file), chỉ khác key prefix và message log. Gom thành
1 method **static** `isAllowed(redisTemplate, key, maxRequests, windowSeconds)` — cố tình viết
static (không phải `@Component`) để 3 service vẫn tự inject `ReactiveStringRedisTemplate` như cũ,
3 file test hiện có (mock thẳng `ReactiveStringRedisTemplate`) không cần sửa gì cả.

### 3. `shared/sse/SseHeartbeat`

`RadarController`/`PostController`/`NotificationController` copy-paste y hệt khối
`Flux.interval(25s) + comment("ping") + Flux.merge` để giữ kết nối SSE sống qua proxy timeout
idle connection. **`ConversationController` lại thiếu hẳn khối này** — bug thật, không phải giả
định: stream tin nhắn realtime của nó có thể bị 1 reverse proxy timeout âm thầm ngắt kết nối mà
không ai biết, vì `MessagingContext` (FE) mở đúng 1 kết nối SSE bền vững cho toàn app (xem
`docs/ARCHITECTURE.md` §6.4). Gom thành `SseHeartbeat.merge(events)` (mặc định 25s) +
`merge(events, interval)` (cho phép chỉnh, cũng phục vụ test nhanh không cần chờ 25s thật) — áp
dụng cho cả 4 controller, vá luôn `ConversationController`.

## Consequences

- **Được**: 12 + 3 + 4 = 19 điểm gọi giờ dùng chung 3 nguồn logic thay vì tự viết lại; thêm 1
  repository/rate-limiter/SSE-endpoint mới không còn cơ hội copy sai 1 chi tiết nhỏ (quên
  `bindNull`, quên `EXPIRE`, quên heartbeat).
- Bug thiếu heartbeat ở `ConversationController` được vá như một tác dụng phụ của việc gom code —
  không phải mục tiêu ban đầu của đợt rà soát, nhưng lộ ra chính vì so sánh 4 endpoint cạnh nhau.
- **Đánh đổi**: `FixedWindowRateLimiter` static (không phải bean) là lựa chọn thực dụng để tránh
  phải viết lại 3 bộ test hiện có — nếu sau này cần rate limiter phức tạp hơn (token bucket,
  sliding-window log thay vì fixed-window) sẽ cần thiết kế lại thành bean có thể mock được, không
  thể tiếp tục dùng dạng static.
- `R2dbcBinders`/`SseHeartbeat` không có state, an toàn 100% dùng static; không có đánh đổi đáng kể.
