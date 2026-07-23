package com.techpulse.techradar.features.system.adapters.output;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, conditional bind/bindNull branches, and row-mapping for
 * {@link PostgresActivityLogRepository} — this kind of hand-written string-building/row-mapping
 * code silently breaks on a typo'd column name or wrong bind order without a test like this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresActivityLogRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Map<String, Object>> mapRowsFetchSpec;
    @Mock
    private RowsFetchSpec<String> stringRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresActivityLogRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresActivityLogRepository(dbClient);
    }

    @SuppressWarnings("unchecked")
    private <T> BiFunction<Row, RowMetadata, T> captureRowMapper() {
        ArgumentCaptor<BiFunction<Row, RowMetadata, T>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        return captor.getValue();
    }

    // ---- recordVisit ----

    @Test
    void recordVisit_bindsUserIdAndPath_whenBothPresent() {
        UUID userId = UUID.randomUUID();
        when(dbClient.sql("INSERT INTO activity_log (type, user_id, path) VALUES ('visit', :user_id, :path)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.bind("path", "/home")).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.recordVisit(userId.toString(), "/home")).verifyComplete();

        verify(executeSpec).bind("user_id", userId);
        verify(executeSpec).bind("path", "/home");
    }

    @Test
    void recordVisit_bindsNulls_whenUserIdInvalidAndPathNull() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bindNull("user_id", UUID.class)).thenReturn(executeSpec);
        when(executeSpec.bindNull("path", String.class)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.recordVisit("not-a-uuid", null)).verifyComplete();

        verify(executeSpec).bindNull("user_id", UUID.class);
        verify(executeSpec).bindNull("path", String.class);
    }

    // ---- recordSearch ----

    @Test
    void recordSearch_returnsEmptyWithoutDbCall_whenKeywordNull() {
        StepVerifier.create(repository.recordSearch(null)).verifyComplete();

        verifyNoInteractions(dbClient);
    }

    @Test
    void recordSearch_returnsEmptyWithoutDbCall_whenKeywordBlank() {
        StepVerifier.create(repository.recordSearch("   ")).verifyComplete();

        verifyNoInteractions(dbClient);
    }

    @Test
    void recordSearch_trimsAndInsertsKeyword() {
        when(dbClient.sql("INSERT INTO activity_log (type, keyword) VALUES ('search', :keyword)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("keyword", "java")).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.recordSearch("  java  ")).verifyComplete();

        verify(executeSpec).bind("keyword", "java");
    }

    // ---- recordAiRequest ----

    @Test
    void recordAiRequest_insertsWithFixedSqlAndNoBinds() {
        when(dbClient.sql("INSERT INTO activity_log (type) VALUES ('ai_request')")).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.recordAiRequest()).verifyComplete();

        verify(dbClient).sql("INSERT INTO activity_log (type) VALUES ('ai_request')");
        verify(executeSpec, never()).bind(anyString(), any());
    }

    // ---- countToday ----

    @Test
    @SuppressWarnings("unchecked")
    void countToday_bindsTypeParameter_andReturnsMappedCount() {
        when(dbClient.sql(
                "SELECT count(*) AS c FROM activity_log " +
                        "WHERE type = :type AND created_at >= date_trunc('day', now())"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("type", "visit")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(5L));

        StepVerifier.create(repository.countToday("visit"))
                .expectNext(5L)
                .verifyComplete();

        verify(executeSpec).bind("type", "visit");
    }

    @Test
    @SuppressWarnings("unchecked")
    void countToday_whenNoRows_defaultsToZero() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.countToday("visit"))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countToday_rowMapper_readsCountColumn() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());
        when(row.get("c", Long.class)).thenReturn(42L);

        repository.countToday("visit").subscribe();
        BiFunction<Row, RowMetadata, Long> mapper = captureRowMapper();

        assertThat(mapper.apply(row, rowMetadata)).isEqualTo(42L);
    }

    // ---- monthlyVisits ----

    @Test
    @SuppressWarnings("unchecked")
    void monthlyVisits_exactSql_returnsAllRows() {
        when(dbClient.sql(
                "SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month, count(*) AS count " +
                        "FROM activity_log WHERE type = 'visit' " +
                        "GROUP BY 1 ORDER BY 1 DESC LIMIT 12"))
                .thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(mapRowsFetchSpec);
        Map<String, Object> monthRow = Map.of("month", "2026-07", "count", 10L);
        when(mapRowsFetchSpec.all()).thenReturn(Flux.just(monthRow));

        StepVerifier.create(repository.monthlyVisits())
                .expectNextMatches(m -> m.get("month").equals("2026-07"))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void monthlyVisits_rowMapper_mapsMonthAndCountColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(mapRowsFetchSpec);
        when(mapRowsFetchSpec.all()).thenReturn(Flux.empty());
        when(row.get("month", String.class)).thenReturn("2026-01");
        when(row.get("count", Long.class)).thenReturn(7L);

        repository.monthlyVisits().subscribe();
        BiFunction<Row, RowMetadata, Map<String, Object>> mapper = captureRowMapper();
        Map<String, Object> mapped = mapper.apply(row, rowMetadata);

        assertThat(mapped.get("month")).isEqualTo("2026-01");
        assertThat(mapped.get("count")).isEqualTo(7L);
    }

    // ---- topKeywords ----

    @Test
    @SuppressWarnings("unchecked")
    void topKeywords_bindsLimit_andReturnsKeywords() {
        when(dbClient.sql(
                "SELECT keyword FROM activity_log WHERE type = 'search' AND keyword IS NOT NULL " +
                        "GROUP BY keyword ORDER BY count(*) DESC LIMIT :limit"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("limit", 5)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(stringRowsFetchSpec);
        when(stringRowsFetchSpec.all()).thenReturn(Flux.just("java", "python"));

        StepVerifier.create(repository.topKeywords(5))
                .expectNext("java", "python")
                .verifyComplete();

        verify(executeSpec).bind("limit", 5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void topKeywords_rowMapper_readsKeywordColumn() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(stringRowsFetchSpec);
        when(stringRowsFetchSpec.all()).thenReturn(Flux.empty());
        when(row.get("keyword", String.class)).thenReturn("kubernetes");

        repository.topKeywords(10).subscribe();
        BiFunction<Row, RowMetadata, String> mapper = captureRowMapper();

        assertThat(mapper.apply(row, rowMetadata)).isEqualTo("kubernetes");
    }
}
