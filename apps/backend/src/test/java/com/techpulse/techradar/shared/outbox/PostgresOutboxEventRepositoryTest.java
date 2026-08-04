package com.techpulse.techradar.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, JSON serialization, and row-mapping for
 * {@link PostgresOutboxEventRepository} — the same rationale as
 * {@code PostgresAuditLogRepositoryTest}: hand-written string SQL silently breaks on a typo'd
 * column or wrong bind order without a test like this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresOutboxEventRepositoryTest {

    @NoArgsConstructor
    @AllArgsConstructor
    static class Payload {
        public String technology;
    }

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<OutboxEvent> rowsFetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresOutboxEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresOutboxEventRepository(dbClient, new ObjectMapper());
    }

    // ---- save ----

    @Test
    void save_serializesPayloadAndInsertsPendingRow() {
        when(dbClient.sql("INSERT INTO outbox_event (topic, payload) VALUES (:topic, :payload)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());

        StepVerifier.create(repository.save("trend.alerts", new Payload("Go"))).verifyComplete();

        verify(executeSpec).bind("topic", "trend.alerts");
        // Must scope to the "payload" key specifically — topic's value ("trend.alerts") is also a
        // String, so a bare anyString() matcher here matches BOTH bind() calls (wanted 1, was 2).
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(executeSpec).bind(eq("payload"), payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues()).anyMatch(v -> v.contains("\"technology\":\"Go\""));
    }

    /** A bean whose getter throws — Jackson wraps this as a {@code JsonMappingException} (a
     *  {@code JsonProcessingException}), the exact failure {@code save} is meant to surface as an
     *  {@code IllegalStateException} instead of a raw Jackson exception. */
    static class ExplodingPayload {
        public String getValue() {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void save_errorsWithoutTouchingDb_whenPayloadNotSerializable() {
        StepVerifier.create(repository.save("trend.alerts", new ExplodingPayload()))
                .expectError(IllegalStateException.class)
                .verify();
    }

    // ---- findReadyToPublish ----

    @Test
    @SuppressWarnings("unchecked")
    void findReadyToPublish_bindsMaxAttemptsAndLimit() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        repository.findReadyToPublish(5, 50).blockLast();

        verify(executeSpec).bind("maxAttempts", 5);
        verify(executeSpec).bind("limit", 50);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findReadyToPublish_rowMapper_mapsAllColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("topic", String.class)).thenReturn("trend.alerts");
        when(row.get("payload", String.class)).thenReturn("{\"technology\":\"Go\"}");
        when(row.get("status", String.class)).thenReturn("PENDING");
        when(row.get("attempts", Integer.class)).thenReturn(2);
        when(row.get("last_error", String.class)).thenReturn(null);
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);
        when(row.get("published_at", LocalDateTime.class)).thenReturn(null);

        repository.findReadyToPublish(5, 50).subscribe();
        ArgumentCaptor<BiFunction<Row, RowMetadata, OutboxEvent>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        OutboxEvent mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.getId()).isEqualTo(id);
        assertThat(mapped.getTopic()).isEqualTo("trend.alerts");
        assertThat(mapped.getPayload()).contains("Go");
        assertThat(mapped.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(mapped.getAttempts()).isEqualTo(2);
        assertThat(mapped.getCreatedAt()).isEqualTo(createdAt);
        assertThat(mapped.getPublishedAt()).isNull();
    }

    // ---- markPublished / markFailed ----

    @Test
    void markPublished_updatesStatusAndPublishedAt() {
        UUID id = UUID.randomUUID();
        when(dbClient.sql("UPDATE outbox_event SET status = 'PUBLISHED', published_at = now() WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", id)).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());

        StepVerifier.create(repository.markPublished(id)).verifyComplete();

        verify(executeSpec).bind("id", id);
    }

    @Test
    void markFailed_bindsErrorAndIncrementsAttempts() {
        UUID id = UUID.randomUUID();
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());

        StepVerifier.create(repository.markFailed(id, "broker unreachable")).verifyComplete();

        verify(executeSpec).bind("id", id);
        verify(executeSpec).bind("error", "broker unreachable");
    }

    @Test
    void markFailed_bindsNullError_whenExceptionMessageIsNull() {
        UUID id = UUID.randomUUID();
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());

        StepVerifier.create(repository.markFailed(id, null)).verifyComplete();

        verify(executeSpec).bindNull("error", String.class);
    }
}
