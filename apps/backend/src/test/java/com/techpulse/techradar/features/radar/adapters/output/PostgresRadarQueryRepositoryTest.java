package com.techpulse.techradar.features.radar.adapters.output;

import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
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

import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text and row-mapping for {@link PostgresRadarQueryRepository}, including the
 * lower-casing of technology names before binding them into the {@code = ANY(:names)} clauses,
 * and the short-circuit that avoids touching Postgres at all for an empty name list.
 */
@ExtendWith(MockitoExtension.class)
class PostgresRadarQueryRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<TechSnapshot> snapshotRowsFetchSpec;
    @Mock
    private RowsFetchSpec<MonthlyCount> monthlyRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresRadarQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresRadarQueryRepository(dbClient);
    }

    private void stubSnapshotRow(String name, Integer jobCount, Double growthRate, Double momGrowth) {
        when(row.get("name", String.class)).thenReturn(name);
        when(row.get("job_count", Integer.class)).thenReturn(jobCount);
        when(row.get("growth_rate", Double.class)).thenReturn(growthRate);
        when(row.get("mom_growth", Double.class)).thenReturn(momGrowth);
    }

    @Test
    @SuppressWarnings("unchecked")
    void topTechnologies_bindsLimit_andOrdersByJobCountDescending() {
        when(dbClient.sql(
                "SELECT name, job_count, growth_rate, mom_growth FROM (" +
                        "  SELECT DISTINCT ON (technology_name) technology_name AS name, " +
                        "         job_count, growth_rate, COALESCE(mom_growth, 0) AS mom_growth " +
                        "  FROM tech_analytics ORDER BY technology_name, month DESC" +
                        ") latest ORDER BY job_count DESC LIMIT :limit"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("limit", 10)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(snapshotRowsFetchSpec);
        when(snapshotRowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.topTechnologies(10)).verifyComplete();

        verify(executeSpec).bind("limit", 10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void topTechnologies_rowMapper_mapsAllFourColumns_andReusesJobCountAsJobsThisMonth() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(snapshotRowsFetchSpec);
        when(snapshotRowsFetchSpec.all()).thenReturn(Flux.empty());
        stubSnapshotRow("Kotlin", 42, 1.5, 0.3);

        repository.topTechnologies(10).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, TechSnapshot>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        TechSnapshot mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.name()).isEqualTo("Kotlin");
        assertThat(mapped.jobCount()).isEqualTo(42);
        assertThat(mapped.growthRate()).isEqualTo(1.5);
        assertThat(mapped.momRate()).isEqualTo(0.3);
        assertThat(mapped.jobsThisMonth()).isEqualTo(42);
    }

    @Test
    @SuppressWarnings("unchecked")
    void topTechnologies_rowMapper_defaultsNullNumericColumnsToZero() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(snapshotRowsFetchSpec);
        when(snapshotRowsFetchSpec.all()).thenReturn(Flux.empty());
        stubSnapshotRow("Rust", null, null, null);

        repository.topTechnologies(10).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, TechSnapshot>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        TechSnapshot mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.jobCount()).isZero();
        assertThat(mapped.growthRate()).isZero();
        assertThat(mapped.momRate()).isZero();
        assertThat(mapped.jobsThisMonth()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void monthlySeries_lowercasesKeywords_beforeBindingTheNamesArray() {
        when(dbClient.sql(
                "SELECT technology_name AS name, " +
                        "       EXTRACT(YEAR FROM month)::int AS yr, " +
                        "       EXTRACT(MONTH FROM month)::int AS mon, " +
                        "       job_count, article_count, COALESCE(yoy_growth,0) AS yoy, " +
                        "       COALESCE(mom_growth,0) AS mom, growth_rate " +
                        "FROM tech_analytics " +
                        "WHERE lower(technology_name) = ANY(:names) " +
                        "  AND month >= (CURRENT_DATE - make_interval(months => :months)) " +
                        "ORDER BY month ASC"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(monthlyRowsFetchSpec);
        when(monthlyRowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.monthlySeries(List.of("Java", "KOTLIN", "Rust"), 6)).verifyComplete();

        ArgumentCaptor<Object> namesCaptor = ArgumentCaptor.forClass(Object.class);
        verify(executeSpec).bind(org.mockito.ArgumentMatchers.eq("names"), namesCaptor.capture());
        assertThat((String[]) namesCaptor.getValue()).containsExactly("java", "kotlin", "rust");
        verify(executeSpec).bind("months", 6);
    }

    @Test
    @SuppressWarnings("unchecked")
    void monthlySeries_treatsNullKeywordEntriesAsEmptyString() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(monthlyRowsFetchSpec);
        when(monthlyRowsFetchSpec.all()).thenReturn(Flux.empty());

        List<String> keywords = new java.util.ArrayList<>();
        keywords.add("Go");
        keywords.add(null);

        repository.monthlySeries(keywords, 3).subscribe();

        ArgumentCaptor<Object> namesCaptor = ArgumentCaptor.forClass(Object.class);
        verify(executeSpec).bind(org.mockito.ArgumentMatchers.eq("names"), namesCaptor.capture());
        assertThat((String[]) namesCaptor.getValue()).containsExactly("go", "");
    }

    @Test
    @SuppressWarnings("unchecked")
    void monthlySeries_rowMapper_mapsAllEightColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(monthlyRowsFetchSpec);
        when(monthlyRowsFetchSpec.all()).thenReturn(Flux.empty());

        when(row.get("name", String.class)).thenReturn("Java");
        when(row.get("yr", Integer.class)).thenReturn(2026);
        when(row.get("mon", Integer.class)).thenReturn(7);
        when(row.get("job_count", Integer.class)).thenReturn(100);
        when(row.get("article_count", Integer.class)).thenReturn(20);
        when(row.get("yoy", Double.class)).thenReturn(0.1);
        when(row.get("mom", Double.class)).thenReturn(0.05);
        when(row.get("growth_rate", Double.class)).thenReturn(2.0);

        repository.monthlySeries(List.of("java"), 12).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, MonthlyCount>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        MonthlyCount mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.name()).isEqualTo("Java");
        assertThat(mapped.year()).isEqualTo(2026);
        assertThat(mapped.month()).isEqualTo(7);
        assertThat(mapped.jobCount()).isEqualTo(100);
        assertThat(mapped.articleCount()).isEqualTo(20);
        assertThat(mapped.yoyRate()).isEqualTo(0.1);
        assertThat(mapped.momRate()).isEqualTo(0.05);
        assertThat(mapped.growthRate()).isEqualTo(2.0);
    }

    @Test
    void findLatestSnapshotsForNames_returnsEmpty_withoutTouchingTheDatabase_whenNamesListIsEmpty() {
        StepVerifier.create(repository.findLatestSnapshotsForNames(List.of())).verifyComplete();

        verifyNoInteractions(dbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findLatestSnapshotsForNames_bindsNamesArray_andMapsSnapshots() {
        when(dbClient.sql(
                "SELECT name, job_count, growth_rate, mom_growth FROM (" +
                        "  SELECT DISTINCT ON (technology_name) technology_name AS name, " +
                        "         job_count, growth_rate, COALESCE(mom_growth, 0) AS mom_growth " +
                        "  FROM tech_analytics " +
                        "  WHERE lower(technology_name) = ANY(:names) " +
                        "  ORDER BY technology_name, month DESC" +
                        ") latest"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(snapshotRowsFetchSpec);
        when(snapshotRowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findLatestSnapshotsForNames(List.of("java", "kotlin"))).verifyComplete();

        ArgumentCaptor<Object> namesCaptor = ArgumentCaptor.forClass(Object.class);
        verify(executeSpec).bind(org.mockito.ArgumentMatchers.eq("names"), namesCaptor.capture());
        assertThat((String[]) namesCaptor.getValue()).containsExactly("java", "kotlin");
    }

    @Test
    @SuppressWarnings("unchecked")
    void countNewTechnologiesThisMonth_returnsMappedCount() {
        when(dbClient.sql(
                "SELECT count(*) AS c FROM (" +
                        "  SELECT technology_name FROM tech_analytics" +
                        "  GROUP BY technology_name" +
                        "  HAVING MIN(month) = date_trunc('month', CURRENT_DATE)::date" +
                        ") new_tech"))
                .thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(4L));

        StepVerifier.create(repository.countNewTechnologiesThisMonth()).expectNext(4L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countNewTechnologiesThisMonth_defaultsToZero_whenNoRow() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.countNewTechnologiesThisMonth()).expectNext(0L).verifyComplete();
    }
}
