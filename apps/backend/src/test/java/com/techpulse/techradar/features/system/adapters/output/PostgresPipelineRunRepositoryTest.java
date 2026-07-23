package com.techpulse.techradar.features.system.adapters.output;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text and bind order for {@link PostgresPipelineRunRepository} — this repository
 * has no row-mapping lambda of its own (it returns the raw {@code Map<String, Object>} rows via
 * {@code fetch().all()}), so coverage here is about the hand-written SQL and bind order, which is
 * exactly the kind of thing that silently breaks on a typo'd column/table name or wrong bind
 * order without a test like this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresPipelineRunRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;

    private PostgresPipelineRunRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresPipelineRunRepository(dbClient);
    }

    // ---- findLatestStatuses ----

    @Test
    void findLatestStatuses_bindsJobNamesArray_andReturnsRows() {
        ArgumentCaptor<String[]> namesCaptor = ArgumentCaptor.forClass(String[].class);
        when(dbClient.sql(
                "SELECT DISTINCT ON (job_name) job_name, status, rows_affected, error_msg, "
                        + "started_at, finished_at "
                        + "FROM dp_pipeline_runs "
                        + "WHERE job_name = ANY(:names) "
                        + "ORDER BY job_name, started_at DESC"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(eq("names"), namesCaptor.capture())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        Map<String, Object> statusRow = Map.of("job_name", "gold_daily_metrics", "status", "SUCCESS");
        when(fetchSpec.all()).thenReturn(Flux.just(statusRow));

        StepVerifier.create(repository.findLatestStatuses(List.of("gold_daily_metrics", "gold_top_skills")))
                .expectNextMatches(m -> m.get("job_name").equals("gold_daily_metrics"))
                .verifyComplete();

        assertThat(namesCaptor.getValue()).containsExactly("gold_daily_metrics", "gold_top_skills");
    }

    @Test
    void findLatestStatuses_withEmptyJobNames_bindsEmptyArray() {
        ArgumentCaptor<String[]> namesCaptor = ArgumentCaptor.forClass(String[].class);
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(eq("names"), namesCaptor.capture())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findLatestStatuses(List.of())).verifyComplete();

        assertThat(namesCaptor.getValue()).isEmpty();
    }

    // ---- findRunHistory ----

    @Test
    void findRunHistory_bindsJobNameLimitAndOffset_andReturnsRows() {
        when(dbClient.sql(
                "SELECT id, job_name, status, rows_affected, error_msg, started_at, finished_at, "
                        + "EXTRACT(EPOCH FROM (finished_at - started_at)) AS duration_s "
                        + "FROM dp_pipeline_runs WHERE job_name = :jobName "
                        + "ORDER BY started_at DESC LIMIT :limit OFFSET :offset"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("jobName", "gold_daily_metrics")).thenReturn(executeSpec);
        when(executeSpec.bind("limit", 20)).thenReturn(executeSpec);
        when(executeSpec.bind("offset", 0)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        Map<String, Object> historyRow = Map.of("job_name", "gold_daily_metrics", "duration_s", 12.5);
        when(fetchSpec.all()).thenReturn(Flux.just(historyRow));

        StepVerifier.create(repository.findRunHistory("gold_daily_metrics", 20, 0))
                .expectNextMatches(m -> m.get("duration_s").equals(12.5))
                .verifyComplete();

        verify(executeSpec).bind("jobName", "gold_daily_metrics");
        verify(executeSpec).bind("limit", 20);
        verify(executeSpec).bind("offset", 0);
    }

    @Test
    void findRunHistory_withPaginationOffset_bindsOffsetValue() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findRunHistory("gold_daily_metrics", 10, 50)).verifyComplete();

        verify(executeSpec).bind("limit", 10);
        verify(executeSpec).bind("offset", 50);
    }
}
