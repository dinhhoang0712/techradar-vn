package com.techpulse.techradar.shared.outbox;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the transactional outbox: a Kafka event whose delivery must survive a crash between
 * the business write that produced it and the moment it actually reaches the broker. See
 * {@code docs/adr/0005-transactional-outbox-trend-alerts.md}.
 */
@Data
@Builder
public class OutboxEvent {
    private UUID id;
    private String topic;
    /** Already-serialized JSON (snake_case, same {@code ObjectMapper} used for direct Kafka sends). */
    private String payload;
    private OutboxStatus status;
    private int attempts;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
