# ADR-0002: WebFlux + R2DBC (reactive, non-blocking)

**Status**: Accepted

## Context

Gateway phải giữ nhiều kết nối đồng thời: SSE cho messaging/notification/feed/radar
(`live:messages`, `live:notifications`, `live:radar`, `live:feed` qua Redis Pub/Sub — xem
[ADR-0004](./0004-redis-pubsub-sse-fanout.md)), cộng với các call proxy sang `ai-rag-core`/
`ml-clustering` có độ trễ cao (LLM generation có thể mất vài giây). Một stack blocking
(Spring MVC + JDBC + servlet thread-per-request) sẽ giữ thread bị block trong lúc chờ AI service
trả lời hoặc trong lúc giữ SSE connection mở, dễ cạn thread pool khi tải tăng.

## Decision

Toàn bộ gateway dùng **Spring WebFlux** (`Mono`/`Flux`) cho tầng controller/use case, **R2DBC**
(không phải JDBC) cho Postgres, Neo4j Java Driver (async API) cho graph, và
`ReactiveRedisTemplate` cho Redis. Không trộn code blocking vào reactive chain — CPU-bound work
hiếm khi cần (ETL rebuild trong `RadarAnalyticsEtlService` là ngoại lệ có chủ đích, offload qua
`Schedulers.boundedElastic()`).

## Consequences

- **Được**: connection pool nhỏ vẫn phục vụ được nhiều request đồng thời đang chờ I/O (AI
  proxy call, SSE long-lived); scale ngang không cần nhiều instance chỉ vì thread bị giữ chờ.
- **Đánh đổi**: không có `@Transactional` cổ điển (blocking) — transaction reactive phải dùng
  `TransactionalOperator`/`ReactiveTransactionManager` tường minh (xem
  [ADR-0005](./0005-transactional-outbox-trend-alerts.md) cho ví dụ thực tế đầu tiên trong repo
  dùng pattern này). Debug stack trace reactive khó đọc hơn blocking — bù lại bằng
  `RequestContextMiddleware`/trace ID trong log (Logback JSON, xem `ARCHITECTURE.md` §12.1).
- R2DBC không cho `bind(name, null)` — phải dùng `.bindNull(name, Class)` tường minh (gotcha đã
  gặp và sửa ở `PostgresChatRepository`, xem [`DATABASE.md` §6](../DATABASE.md#6-quy-ước--gotchas-cross-service)).
- Test dùng Testcontainers thật (Postgres/Neo4j/Redis) thay vì H2/mock, vì hành vi reactive
  driver khác đáng kể so với JDBC blocking tương đương.
