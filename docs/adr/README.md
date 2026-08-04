# Architecture Decision Records — TechRadar VN

ADR ghi lại **quyết định kiến trúc đã có hiệu lực trong code**, không phải đề xuất — mục đích là
giải thích *vì sao* code trông như hiện tại, để người đọc sau (kể cả tương lai chính team) không
phải đoán hoặc vô tình đảo ngược một quyết định có chủ đích.

## Quy ước

- Mỗi ADR là 1 file `NNNN-tieu-de-ngan.md`, đánh số tăng dần, không tái sử dụng số đã xoá.
- Trạng thái: `Accepted` (đang áp dụng), `Superseded by ADR-000X` (đã bị thay), `Deprecated`.
- Không sửa nội dung một ADR đã `Accepted` để "cập nhật" — nếu quyết định đổi, viết ADR mới và
  đánh dấu ADR cũ là `Superseded`.

## Danh sách

| # | Tiêu đề | Trạng thái |
|---|---|---|
| [0001](./0001-hexagonal-feature-modules.md) | Hexagonal Architecture theo feature module | Accepted |
| [0002](./0002-webflux-reactive-stack.md) | WebFlux + R2DBC (reactive, non-blocking) | Accepted |
| [0003](./0003-api-envelope-with-auth-exception.md) | Envelope `ApiResponse` snake_case, ngoại lệ có chủ đích cho `/auth/*` và `/status` | Accepted |
| [0004](./0004-redis-pubsub-sse-fanout.md) | Redis Pub/Sub cho SSE fan-out đa instance (không dùng Kafka) | Accepted |
| [0005](./0005-transactional-outbox-trend-alerts.md) | Transactional outbox cho `trend.alerts` | Accepted |
| [0006](./0006-permission-based-rbac.md) | RBAC theo permission code (không hard-code role) | Accepted |
| [0007](./0007-circuit-breaker-for-python-service-calls.md) | Circuit breaker cho lệnh gọi Python service (ai-rag-core/ml-clustering) | Accepted |
| [0008](./0008-rich-user-entity-for-security-stamp-invariant.md) | Rich `User` entity cho invariant `securityStamp` | Accepted |
| [0009](./0009-conversation-canonical-pair-value-object.md) | `ConversationParticipants` — value object cho canonical pair | Accepted |
| [0010](./0010-oneof-validation-for-fixed-vocabulary-strings.md) | `@OneOf` validation cho string thuộc tập giá trị cố định | Accepted |
| [0011](./0011-shared-utilities-r2dbc-ratelimit-sse.md) | 3 tiện ích dùng chung mới (R2DBC bind, rate limiter, SSE heartbeat) | Accepted |
