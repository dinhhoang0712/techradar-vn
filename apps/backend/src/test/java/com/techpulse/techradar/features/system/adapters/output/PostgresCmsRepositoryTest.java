package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.domain.CmsContent;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, conditional bind/bindNull branches for the nullable {@code type} and
 * {@code content_date} columns, the not-found branches, and row-mapping for
 * {@link PostgresCmsRepository} — this kind of hand-written string-building/row-mapping code
 * silently breaks on a typo'd column name or wrong bind order without a test like this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresCmsRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<CmsContent> rowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresCmsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresCmsRepository(dbClient);
    }

    @SuppressWarnings("unchecked")
    private BiFunction<Row, RowMetadata, CmsContent> captureRowMapper() {
        ArgumentCaptor<BiFunction<Row, RowMetadata, CmsContent>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        return captor.getValue();
    }

    private static final String COLUMNS = "id, title, type, content_date, status, created_at, updated_at";

    // ---- findAll ----

    @Test
    @SuppressWarnings("unchecked")
    void findAll_exactSql_returnsAllMappedRows() {
        when(dbClient.sql("SELECT " + COLUMNS + " FROM cms_content ORDER BY created_at DESC"))
                .thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.just(CmsContent.builder().title("Report A").build()));

        StepVerifier.create(repository.findAll())
                .expectNextMatches(c -> c.getTitle().equals("Report A"))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_rowMapper_mapsAllSevenColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        UUID id = UUID.randomUUID();
        LocalDate contentDate = LocalDate.of(2026, 7, 1);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 2, 11, 0);
        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("title", String.class)).thenReturn("Q3 Report");
        when(row.get("type", String.class)).thenReturn("Report");
        when(row.get("content_date", LocalDate.class)).thenReturn(contentDate);
        when(row.get("status", String.class)).thenReturn("Published");
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);
        when(row.get("updated_at", LocalDateTime.class)).thenReturn(updatedAt);

        repository.findAll().subscribe();
        CmsContent mapped = captureRowMapper().apply(row, rowMetadata);

        assertThat(mapped.getId()).isEqualTo(id);
        assertThat(mapped.getTitle()).isEqualTo("Q3 Report");
        assertThat(mapped.getType()).isEqualTo("Report");
        assertThat(mapped.getContentDate()).isEqualTo(contentDate);
        assertThat(mapped.getStatus()).isEqualTo("Published");
        assertThat(mapped.getCreatedAt()).isEqualTo(createdAt);
        assertThat(mapped.getUpdatedAt()).isEqualTo(updatedAt);
    }

    // ---- findById ----

    @Test
    @SuppressWarnings("unchecked")
    void findById_bindsIdAsUuid_andReturnsMappedContent() {
        UUID id = UUID.randomUUID();
        when(dbClient.sql("SELECT " + COLUMNS + " FROM cms_content WHERE id = :id")).thenReturn(executeSpec);
        when(executeSpec.bind("id", id)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.just(CmsContent.builder().id(id).title("Found").build()));

        StepVerifier.create(repository.findById(id.toString()))
                .assertNext(c -> assertThat(c.getTitle()).isEqualTo("Found"))
                .verifyComplete();

        verify(executeSpec).bind("id", id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_whenNotFound_returnsEmptyMono() {
        UUID id = UUID.randomUUID();
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.findById(id.toString())).verifyComplete();
    }

    // ---- insert ----

    @Test
    void insert_bindsAllColumns_whenTypeAndContentDatePresent() {
        CmsContent content = CmsContent.builder()
                .title("New Report")
                .type("Report")
                .contentDate(LocalDate.of(2026, 7, 20))
                .status("Published")
                .build();

        when(dbClient.sql(
                "INSERT INTO cms_content (id, title, type, content_date, status, created_at, updated_at) " +
                        "VALUES (:id, :title, :type, :content_date, :status, :created_at, :updated_at)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(content))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getUpdatedAt()).isNotNull();
                    assertThat(saved.getTitle()).isEqualTo("New Report");
                })
                .verifyComplete();

        verify(executeSpec).bind("title", "New Report");
        verify(executeSpec).bind("type", "Report");
        verify(executeSpec).bind("content_date", LocalDate.of(2026, 7, 20));
        verify(executeSpec).bind("status", "Published");
    }

    @Test
    void insert_bindsNullTypeAndContentDate_andDefaultsStatusToPending_whenAbsent() {
        CmsContent content = CmsContent.builder().title("Draft").build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(content))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo("Pending"))
                .verifyComplete();

        verify(executeSpec).bind("status", "Pending");
        verify(executeSpec).bindNull("type", String.class);
        verify(executeSpec).bindNull("content_date", LocalDate.class);
    }

    // ---- update ----

    @Test
    void update_bindsAllColumns_whenTypeAndContentDatePresent() {
        UUID id = UUID.randomUUID();
        CmsContent content = CmsContent.builder()
                .id(id)
                .title("Updated Report")
                .type("Job")
                .contentDate(LocalDate.of(2026, 7, 21))
                .status("Analyzed")
                .build();

        when(dbClient.sql(
                "UPDATE cms_content SET title = :title, type = :type, content_date = :content_date, " +
                        "status = :status, updated_at = :updated_at WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.update(content))
                .assertNext(saved -> {
                    assertThat(saved.getUpdatedAt()).isNotNull();
                    assertThat(saved.getTitle()).isEqualTo("Updated Report");
                })
                .verifyComplete();

        verify(executeSpec).bind("id", id);
        verify(executeSpec).bind("title", "Updated Report");
        verify(executeSpec).bind("type", "Job");
        verify(executeSpec).bind("content_date", LocalDate.of(2026, 7, 21));
        verify(executeSpec).bind("status", "Analyzed");
    }

    @Test
    void update_bindsNullTypeAndContentDate_andDefaultsStatusToPending_whenAbsent() {
        UUID id = UUID.randomUUID();
        CmsContent content = CmsContent.builder().id(id).title("Untyped").build();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.update(content))
                .assertNext(saved -> assertThat(saved.getStatus()).isEqualTo("Pending"))
                .verifyComplete();

        verify(executeSpec).bind("status", "Pending");
        verify(executeSpec).bindNull("type", String.class);
        verify(executeSpec).bindNull("content_date", LocalDate.class);
    }

    // ---- deleteById ----

    @Test
    void deleteById_bindsIdAsUuid_returnsRowsUpdatedCount() {
        UUID id = UUID.randomUUID();
        when(dbClient.sql("DELETE FROM cms_content WHERE id = :id")).thenReturn(executeSpec);
        when(executeSpec.bind("id", id)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.deleteById(id.toString()))
                .expectNext(1L)
                .verifyComplete();

        verify(executeSpec).bind("id", id);
    }

    @Test
    void deleteById_whenNotFound_returnsZero() {
        UUID id = UUID.randomUUID();
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.deleteById(id.toString()))
                .expectNext(0L)
                .verifyComplete();
    }
}
