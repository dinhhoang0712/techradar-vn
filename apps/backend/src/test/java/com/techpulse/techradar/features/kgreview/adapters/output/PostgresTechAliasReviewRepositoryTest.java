package com.techpulse.techradar.features.kgreview.adapters.output;

import com.techpulse.techradar.features.kgreview.domain.TechAliasReviewItem;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresTechAliasReviewRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<TechAliasReviewItem> itemRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresTechAliasReviewRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresTechAliasReviewRepository(dbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findPending_bindsLimitOffset_ordersOldestFirst_andMapsRows() {
        when(dbClient.sql(
                "SELECT id, name_a, name_b, llm_reasoning, status, created_at " +
                        "FROM dp_tech_alias_review_queue WHERE status = 'pending' " +
                        "ORDER BY created_at ASC LIMIT :limit OFFSET :offset"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("limit", 20)).thenReturn(executeSpec);
        when(executeSpec.bind("offset", 0)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(itemRowsFetchSpec);
        TechAliasReviewItem item = new TechAliasReviewItem(1L, "Golang", "Go", "same language", "pending", LocalDateTime.now());
        when(itemRowsFetchSpec.all()).thenReturn(Flux.just(item));

        StepVerifier.create(repository.findPending(20, 0)).expectNext(item).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countPending_returnsMappedCount() {
        when(dbClient.sql("SELECT count(*) AS c FROM dp_tech_alias_review_queue WHERE status = 'pending'"))
                .thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(3L));

        StepVerifier.create(repository.countPending()).expectNext(3L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countPending_defaultsToZeroWhenEmpty() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.countPending()).expectNext(0L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_bindsId_mapsRow() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind("id", 1L)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(itemRowsFetchSpec);
        TechAliasReviewItem item = new TechAliasReviewItem(1L, "Golang", "Go", "same language", "pending", LocalDateTime.now());
        when(itemRowsFetchSpec.one()).thenReturn(Mono.just(item));

        StepVerifier.create(repository.findById(1L))
                .assertNext(r -> assertThat(r.id()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void markApproved_updatesStatusAndReturnsTrueWhenRowAffected() {
        when(dbClient.sql(
                "UPDATE dp_tech_alias_review_queue SET status = :status, decided_at = now() " +
                        "WHERE id = :id AND status = 'pending'"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", 1L)).thenReturn(executeSpec);
        when(executeSpec.bind("status", "approved")).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.markApproved(1L)).expectNext(true).verifyComplete();
    }

    @Test
    void markRejected_updatesStatusAndReturnsFalseWhenNoRowAffected() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.markRejected(999L)).expectNext(false).verifyComplete();

        verify(executeSpec).bind("status", "rejected");
    }

    @Test
    void saveAlias_upsertsIntoAliasMapWithHumanReviewSource() {
        when(dbClient.sql(
                "INSERT INTO dp_tech_alias_map (alias_normalized, canonical_name, source) " +
                        "VALUES (:alias_normalized, :canonical_name, 'human_review') " +
                        "ON CONFLICT (alias_normalized) DO UPDATE SET canonical_name = EXCLUDED.canonical_name, " +
                        "source = 'human_review'"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("alias_normalized", "golang")).thenReturn(executeSpec);
        when(executeSpec.bind("canonical_name", "Go")).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.saveAlias("golang", "Go")).verifyComplete();
    }
}
