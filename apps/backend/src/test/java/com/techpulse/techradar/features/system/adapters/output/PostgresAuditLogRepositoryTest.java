package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.domain.AuditLogEntry;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, conditional bind/bindNull branches for optional columns, and row-mapping for
 * {@link PostgresAuditLogRepository} — this kind of hand-written string-building/row-mapping code
 * silently breaks on a typo'd column name or wrong bind order without a test like this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresAuditLogRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<AuditLogEntry> rowsFetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresAuditLogRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresAuditLogRepository(dbClient);
    }

    @SuppressWarnings("unchecked")
    private BiFunction<Row, RowMetadata, AuditLogEntry> captureRowMapper() {
        ArgumentCaptor<BiFunction<Row, RowMetadata, AuditLogEntry>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        return captor.getValue();
    }

    // ---- insert ----

    @Test
    void insert_bindsAllColumns_whenOptionalFieldsPresent() {
        UUID actorId = UUID.randomUUID();
        AuditLogEntry entry = AuditLogEntry.builder()
                .actorId(actorId)
                .action("UPDATE_SETTING")
                .targetType("settings")
                .targetId("feature_chat")
                .details("changed value")
                .build();

        when(dbClient.sql(
                "INSERT INTO audit_log (actor_id, action, target_type, target_id, details) " +
                        "VALUES (:actor_id, :action, :target_type, :target_id, :details)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());

        StepVerifier.create(repository.insert(entry)).verifyComplete();

        verify(executeSpec).bind("actor_id", actorId);
        verify(executeSpec).bind("action", "UPDATE_SETTING");
        verify(executeSpec).bind("target_type", "settings");
        verify(executeSpec).bind("target_id", "feature_chat");
        verify(executeSpec).bind("details", "changed value");
    }

    @Test
    void insert_bindsNulls_whenOptionalFieldsAbsent() {
        UUID actorId = UUID.randomUUID();
        AuditLogEntry entry = AuditLogEntry.builder()
                .actorId(actorId)
                .action("LOGIN")
                .build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.then()).thenReturn(Mono.empty());

        StepVerifier.create(repository.insert(entry)).verifyComplete();

        verify(executeSpec).bind("actor_id", actorId);
        verify(executeSpec).bind("action", "LOGIN");
        verify(executeSpec).bindNull("target_type", String.class);
        verify(executeSpec).bindNull("target_id", String.class);
        verify(executeSpec).bindNull("details", String.class);
    }

    // ---- list ----

    @Test
    @SuppressWarnings("unchecked")
    void list_bindsLimitAndOffset_andReturnsMappedRows() {
        when(dbClient.sql(
                "SELECT id, actor_id, action, target_type, target_id, details, created_at " +
                        "FROM audit_log ORDER BY created_at DESC LIMIT :limit OFFSET :offset"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("limit", 20)).thenReturn(executeSpec);
        when(executeSpec.bind("offset", 40)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.just(AuditLogEntry.builder().action("LOGIN").build()));

        StepVerifier.create(repository.list(20, 40))
                .expectNextMatches(e -> e.getAction().equals("LOGIN"))
                .verifyComplete();

        verify(executeSpec).bind("limit", 20);
        verify(executeSpec).bind("offset", 40);
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_rowMapper_mapsAllSevenColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("actor_id", UUID.class)).thenReturn(actorId);
        when(row.get("action", String.class)).thenReturn("DELETE_CMS");
        when(row.get("target_type", String.class)).thenReturn("cms_content");
        when(row.get("target_id", String.class)).thenReturn("abc-123");
        when(row.get("details", String.class)).thenReturn("removed stale entry");
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);

        repository.list(10, 0).subscribe();
        AuditLogEntry mapped = captureRowMapper().apply(row, rowMetadata);

        assertThat(mapped.getId()).isEqualTo(id);
        assertThat(mapped.getActorId()).isEqualTo(actorId);
        assertThat(mapped.getAction()).isEqualTo("DELETE_CMS");
        assertThat(mapped.getTargetType()).isEqualTo("cms_content");
        assertThat(mapped.getTargetId()).isEqualTo("abc-123");
        assertThat(mapped.getDetails()).isEqualTo("removed stale entry");
        assertThat(mapped.getCreatedAt()).isEqualTo(createdAt);
    }
}
