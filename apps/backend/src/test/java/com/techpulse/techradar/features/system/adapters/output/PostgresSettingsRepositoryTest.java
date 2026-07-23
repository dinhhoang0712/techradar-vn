package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.domain.AppSettings;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresSettingsRepository} — this
 * kind of hand-written string-building/row-mapping code silently breaks on a typo'd column name
 * or wrong bind order without a test like this (the only other coverage is the full integration
 * suite, which needs a live Postgres to catch the same mistake).
 */
@ExtendWith(MockitoExtension.class)
class PostgresSettingsRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<AppSettings> rowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresSettingsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresSettingsRepository(dbClient);
    }

    @SuppressWarnings("unchecked")
    private BiFunction<Row, RowMetadata, AppSettings> captureRowMapper() {
        ArgumentCaptor<BiFunction<Row, RowMetadata, AppSettings>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        return captor.getValue();
    }

    private void stubRow(String key, String value, String description, LocalDateTime updatedAt) {
        when(row.get("key", String.class)).thenReturn(key);
        when(row.get("value", String.class)).thenReturn(value);
        when(row.get("description", String.class)).thenReturn(description);
        when(row.get("updated_at", LocalDateTime.class)).thenReturn(updatedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSetting_bindsKeyAndMapsRowToAppSettings() {
        when(dbClient.sql("SELECT key, value, description, updated_at FROM settings WHERE key = :key"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("key", "feature_chat")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.just(
                AppSettings.builder().key("feature_chat").value("true").build()));

        StepVerifier.create(repository.getSetting("feature_chat"))
                .assertNext(settings -> {
                    assertThat(settings.getKey()).isEqualTo("feature_chat");
                    assertThat(settings.getValue()).isEqualTo("true");
                })
                .verifyComplete();

        verify(executeSpec).bind("key", "feature_chat");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSetting_rowMapper_mapsAllFourColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.empty());
        LocalDateTime updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        stubRow("feature_chat", "true", "Enables chat", updatedAt);

        repository.getSetting("feature_chat").subscribe();
        AppSettings mapped = captureRowMapper().apply(row, rowMetadata);

        assertThat(mapped.getKey()).isEqualTo("feature_chat");
        assertThat(mapped.getValue()).isEqualTo("true");
        assertThat(mapped.getDescription()).isEqualTo("Enables chat");
        assertThat(mapped.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllSettings_ordersByKey_andMapsEveryRow() {
        when(dbClient.sql("SELECT key, value, description, updated_at FROM settings ORDER BY key"))
                .thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.just(
                AppSettings.builder().key("a").build(), AppSettings.builder().key("b").build()));

        StepVerifier.create(repository.getAllSettings())
                .expectNextMatches(s -> s.getKey().equals("a"))
                .expectNextMatches(s -> s.getKey().equals("b"))
                .verifyComplete();
    }

    @Test
    void saveSetting_upsertsAllFourColumnsAndReturnsTheSavedSettingsWithUpdatedTimestamp() {
        AppSettings settings = AppSettings.builder().key("feature_chat").value("false").description("desc").build();
        when(dbClient.sql(
                "INSERT INTO settings (key, value, description, updated_at) " +
                        "VALUES (:key, :value, :description, :updated_at) " +
                        "ON CONFLICT (key) DO UPDATE SET " +
                        "value = EXCLUDED.value, description = EXCLUDED.description, updated_at = EXCLUDED.updated_at"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(eq("key"), any())).thenReturn(executeSpec);
        when(executeSpec.bind(eq("value"), any())).thenReturn(executeSpec);
        when(executeSpec.bind(eq("description"), any())).thenReturn(executeSpec);
        when(executeSpec.bind(eq("updated_at"), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.saveSetting(settings))
                .assertNext(saved -> {
                    assertThat(saved.getKey()).isEqualTo("feature_chat");
                    assertThat(saved.getUpdatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(executeSpec).bind("key", "feature_chat");
        verify(executeSpec).bind("value", "false");
        verify(executeSpec).bind("description", "desc");
    }

    @Test
    void deleteSetting_bindsKeyAndCompletes() {
        when(dbClient.sql("DELETE FROM settings WHERE key = :key")).thenReturn(executeSpec);
        when(executeSpec.bind("key", "feature_chat")).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.deleteSetting("feature_chat")).verifyComplete();

        verify(executeSpec).bind("key", "feature_chat");
    }
}
