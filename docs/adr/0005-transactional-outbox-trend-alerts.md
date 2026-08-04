# ADR-0005: Transactional outbox cho `trend.alerts`

**Status**: Accepted

## Context

`RadarAnalyticsEtlService.rebuild()` viết lại bảng `tech_analytics` (Postgres, qua
`TechAnalyticsWritePort`) rồi, nếu một công nghệ vượt ngưỡng tăng trưởng MoM, publish sự kiện
`trend.alerts` lên Kafka (`KafkaProducerService.send`) để `TrendAlertDispatcher` fan-out
in-app/email. Trước ADR này, việc publish nằm ngoài transaction, best-effort:

```java
Schedulers.boundedElastic().schedule(() -> {
    for (TrendAlertEvent alert : alerts) {
        try {
            kafkaProducer.send(KafkaTopicConstants.TREND_ALERTS, alert);
        } catch (Exception e) {
            log.warn("Could not publish trend alert ... (Kafka unavailable?)", e);
        }
    }
});
```

Rủi ro **dual-write** kinh điển: nếu Postgres commit thành công nhưng app crash (hoặc Kafka
broker down) đúng lúc giữa 2 bước, alert bị mất vĩnh viễn — không có gì để retry, vì trạng thái
"đã tính ra alert này" chỉ tồn tại trong bộ nhớ của lần chạy đó. Rebuild lần sau (theo lịch,
mặc định 3:00 AM) có thể tính lại đúng công nghệ đó nếu vẫn còn vượt ngưỡng, nhưng nếu ngưỡng chỉ
đạt đúng 1 lần trong tháng đó, alert đó biến mất không dấu vết — không log lỗi rõ ràng, không có
cách nào phát hiện ngoài việc nhận thấy "sao user không được báo".

## Decision

Áp dụng **transactional outbox pattern**, giới hạn phạm vi ở `trend.alerts` (nguồn dữ liệu gốc
là Postgres — điều kiện tiên quyết để outbox có ý nghĩa: business write và outbox write phải
cùng 1 datastore/transaction để atomic thật sự, xem "Vì sao không áp dụng cho job.match.alerts /
roadmap.alerts" bên dưới):

1. **Bảng `outbox_event`** (migration `V36__outbox_event.sql`): `id`, `topic`, `payload` (JSON
   text), `status` (`PENDING`/`PUBLISHED`/`FAILED`), `attempts`, `last_error`, `created_at`,
   `published_at`.
2. **Cùng 1 transaction R2DBC** (`TransactionalOperator`, lần đầu dùng transaction tường minh
   trong repo — xem [ADR-0002](./0002-webflux-reactive-stack.md)): `tech_analytics` upsert +
   `outbox_event` insert commit hoặc rollback cùng nhau. Không còn khoảng hở giữa "đã tính alert"
   và "đã ghi lại rằng cần gửi alert này".
3. **`OutboxRelayScheduler`** (poller riêng, `@Scheduled` interval ngắn, mặc định 10s): đọc
   `outbox_event` còn `PENDING` (hoặc `FAILED` với `attempts < max`), publish qua
   `KafkaProducerService`, đánh dấu `PUBLISHED`/tăng `attempts` + ghi `last_error` khi thất bại.
   Kafka down không còn nghĩa là mất alert — outbox row vẫn nằm đó chờ lần poll kế tiếp.

## Vì sao không áp dụng cho `job.match.alerts` / `roadmap.alerts`

`job.match.alerts` phát sinh từ ghi **Neo4j** (`KafkaNeo4jWriterService.writeJob`), không phải
Postgres — outbox table sống ở Postgres nên insert outbox row + Neo4j MERGE **không thể** cùng 1
transaction (2 datastore khác nhau, không có 2-phase-commit giữa chúng). Bọc outbox quanh 1 write
Neo4j chỉ dời dual-write risk từ "Postgres→Kafka" sang "Neo4j→Postgres", không giải quyết gốc rễ.
Publish này vẫn giữ nguyên fire-and-forget với try/catch — chấp nhận rủi ro mất alert cho *sự
kiện mới* (không mất dữ liệu nghiệp vụ: job vẫn nằm trong Neo4j, chỉ mất thông báo "có job mới
khớp kỹ năng"). Tương tự cho `roadmap.alerts` (`KafkaAlertPublisher`, publish trực tiếp).

## Consequences

- **Được**: `trend.alerts` giờ at-least-once — Kafka downtime không làm mất alert, chỉ trễ tới
  lần poll kế tiếp; có thể quan sát alert nào chưa gửi được qua `outbox_event.status='FAILED'`.
- **Đánh đổi**: alert có thể publish **trễ vài giây** (chu kỳ poll) thay vì ngay lập tức — chấp
  nhận được vì đây là thông báo xu hướng hàng tháng, không phải real-time UX (khác với SSE ở
  [ADR-0004](./0004-redis-pubsub-sse-fanout.md)).
- Thêm 1 bảng + 1 scheduler cần vận hành/giám sát (`outbox_event` không tự dọn — cần cân nhắc
  retention/archive nếu volume lớn; hiện tại volume rất thấp — vài chục row/tháng — nên chưa cần).
- Đây là pattern **mẫu** cho các luồng Postgres-sourced event tương lai cần đảm bảo delivery
  tương tự — không tự động áp dụng lại cho luồng có nguồn Neo4j/không transaction được.
