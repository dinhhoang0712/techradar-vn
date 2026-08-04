package com.techpulse.techradar.shared.outbox;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence port for the transactional outbox. {@link #save} must be called inside the same
 * R2DBC transaction as the business write it accompanies (e.g. via {@code TransactionalOperator})
 * — that is the entire point of the pattern, see {@code docs/adr/0005-transactional-outbox-trend-alerts.md}.
 */
public interface OutboxEventRepository {

    /** Serializes {@code payload} to JSON and inserts a {@code PENDING} row for {@code topic}. */
    Mono<Void> save(String topic, Object payload);

    /** Rows ready to (re)try publishing: {@code PENDING}, or {@code FAILED} with attempts left. */
    Flux<OutboxEvent> findReadyToPublish(int maxAttempts, int limit);

    Mono<Void> markPublished(UUID id);

    Mono<Void> markFailed(UUID id, String error);
}
