package com.techpulse.techradar.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PostgreSQL adapter for the {@code outbox_event} table.
 */
@Repository
@RequiredArgsConstructor
public class PostgresOutboxEventRepository implements OutboxEventRepository {

    private final DatabaseClient dbClient;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> save(String topic, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Unable to serialize outbox payload for topic " + topic, e));
        }
        return dbClient.sql("INSERT INTO outbox_event (topic, payload) VALUES (:topic, :payload)")
                .bind("topic", topic)
                .bind("payload", json)
                .then();
    }

    @Override
    public Flux<OutboxEvent> findReadyToPublish(int maxAttempts, int limit) {
        return dbClient.sql(
                "SELECT id, topic, payload, status, attempts, last_error, created_at, published_at " +
                "FROM outbox_event " +
                "WHERE status = 'PENDING' OR (status = 'FAILED' AND attempts < :maxAttempts) " +
                "ORDER BY created_at ASC LIMIT :limit")
                .bind("maxAttempts", maxAttempts)
                .bind("limit", limit)
                .map((row, meta) -> OutboxEvent.builder()
                        .id(row.get("id", UUID.class))
                        .topic(row.get("topic", String.class))
                        .payload(row.get("payload", String.class))
                        .status(OutboxStatus.valueOf(row.get("status", String.class)))
                        .attempts(row.get("attempts", Integer.class))
                        .lastError(row.get("last_error", String.class))
                        .createdAt(row.get("created_at", LocalDateTime.class))
                        .publishedAt(row.get("published_at", LocalDateTime.class))
                        .build())
                .all();
    }

    @Override
    public Mono<Void> markPublished(UUID id) {
        return dbClient.sql("UPDATE outbox_event SET status = 'PUBLISHED', published_at = now() WHERE id = :id")
                .bind("id", id)
                .then();
    }

    @Override
    public Mono<Void> markFailed(UUID id, String error) {
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "UPDATE outbox_event SET status = 'FAILED', attempts = attempts + 1, last_error = :error WHERE id = :id")
                .bind("id", id);
        spec = error != null ? spec.bind("error", error) : spec.bindNull("error", String.class);
        return spec.then();
    }
}
