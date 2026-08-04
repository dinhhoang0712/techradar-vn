# ADR-0004: Redis Pub/Sub cho SSE fan-out đa instance (không dùng Kafka)

**Status**: Accepted

## Context

4 tính năng cần đẩy sự kiện real-time tới client qua SSE: direct messaging (`live:messages`),
notification (`live:notifications`), radar snapshot (`live:radar`), social feed
(`live:feed`). Gateway chạy nhiều instance (horizontal scaling, xem `ARCHITECTURE.md` §11.1).
Nếu broadcast chỉ dùng `Sinks.Many` cục bộ trong 1 instance, sender ở instance A và recipient
đang giữ SSE connection ở instance B sẽ không bao giờ nhận được — tin nhắn/thông báo chỉ "sống"
khi cả 2 phía tình cờ rơi vào cùng 1 instance.

Kafka đã có sẵn trong hệ thống làm event bus cho data pipeline (crawler → Neo4j), nên lựa chọn
hiển nhiên đầu tiên là dùng luôn Kafka cho fan-out này.

## Decision

**Dùng Redis Pub/Sub, không dùng Kafka**, cho cross-instance SSE fan-out. Cả 4 broadcaster
(`MessageBroadcaster`, `NotificationService`, `RadarBroadcaster`, `FeedBroadcaster`) dùng chung 1
bean `ReactiveRedisMessageListenerContainer` (`RedisConfig`) — `publish()`/`save()` gửi qua Redis
channel, mọi instance subscribe và tự đẩy tới SSE client cục bộ của mình.

Lý do không dùng Kafka cho việc này:

- **Không cần durability/replay**: nguồn sự thật vẫn là Postgres (`direct_message`/
  `notification`/`post`) — mất 1 lần push chỉ có nghĩa client thấy dữ liệu khi tự fetch lại thay
  vì tức thời, không mất dữ liệu nghiệp vụ. Kafka's guarantee (durable log, consumer offset,
  replay) là overhead không cần thiết cho tín hiệu fire-and-forget này.
- **Độ trễ**: Redis Pub/Sub có latency thấp hơn Kafka cho fan-out tức thời tới UI; Kafka tối ưu
  cho throughput/durability của pipeline dữ liệu (crawler → extraction → Neo4j), không phải
  real-time push tới trình duyệt.
- **Redis đã là dependency bắt buộc** của gateway (cache, rate limit, token blacklist — xem
  [`DATABASE.md` §5](../DATABASE.md#5-redis)) — không cần thêm dependency mới cho việc này.

## Consequences

- **Được**: mọi instance backend đều nhận đúng sự kiện dù sender/recipient rơi vào instance nào;
  không cần Kafka consumer group/partition assignment cho một luồng vốn không cần durability.
- **Đánh đổi**: nếu Redis restart giữa lúc publish, message đó mất — chấp nhận được vì đây không
  phải nguồn sự thật (khác với `blacklist:token:*`, xem [`DATABASE.md` §5](../DATABASE.md#5-redis)
  — đó VẪN là dữ liệu nghiệp vụ thật, mất key đó nghĩa là token đã logout dùng lại được).
- Test cross-instance thật (không mock) có riêng: `MessageBroadcasterRedisCrossInstanceTest`,
  `NotificationServiceRedisCrossInstanceTest`, `RadarBroadcasterRedisCrossInstanceTest`,
  `FeedBroadcasterRedisCrossInstanceTest` — cần `REDIS_HOST` để chạy.
- **Không áp dụng ngược cho pipeline dữ liệu** — `trend.alerts`/`job.match.alerts`/
  `roadmap.alerts` vẫn dùng Kafka (cần durability + tách consumer riêng cho notification
  dispatcher), xem [ADR-0005](./0005-transactional-outbox-trend-alerts.md).
