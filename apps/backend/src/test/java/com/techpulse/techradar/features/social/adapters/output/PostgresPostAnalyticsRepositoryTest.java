package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.PostAnalyticsRepository;
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
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresPostAnalyticsRepository}.
 */
@ExtendWith(MockitoExtension.class)
class PostgresPostAnalyticsRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private RowsFetchSpec<PostAnalyticsRepository.TopPosterRow> topPosterRowsFetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresPostAnalyticsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresPostAnalyticsRepository(dbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void countAll_returnsMappedCount() {
        when(dbClient.sql("SELECT count(*) AS c FROM post WHERE deleted_at IS NULL")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(123L));

        StepVerifier.create(repository.countAll()).expectNext(123L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countAll_defaultsToZeroWhenEmpty() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.countAll()).expectNext(0L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countCreatedSince_bindsSince() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        when(dbClient.sql("SELECT count(*) AS c FROM post WHERE created_at >= :since AND deleted_at IS NULL")).thenReturn(executeSpec);
        when(executeSpec.bind("since", since)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(9L));

        StepVerifier.create(repository.countCreatedSince(since)).expectNext(9L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countAllLikes_returnsMappedCount() {
        when(dbClient.sql("SELECT count(*) AS c FROM post_like")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(456L));

        StepVerifier.create(repository.countAllLikes()).expectNext(456L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void topPosters_bindsLimit_andMapsRows() {
        when(dbClient.sql(
                "SELECT p.user_id, u.full_name, count(*) AS post_count " +
                        "FROM post p JOIN users u ON u.id = p.user_id " +
                        "WHERE p.deleted_at IS NULL " +
                        "GROUP BY p.user_id, u.full_name " +
                        "ORDER BY post_count DESC LIMIT :limit"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("limit", 5)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(topPosterRowsFetchSpec);
        PostAnalyticsRepository.TopPosterRow topPoster =
                new PostAnalyticsRepository.TopPosterRow(UUID.randomUUID(), "Alice", 30L);
        when(topPosterRowsFetchSpec.all()).thenReturn(Flux.just(topPoster));

        StepVerifier.create(repository.topPosters(5)).expectNext(topPoster).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void topPosters_rowMapper_mapsAllThreeColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(topPosterRowsFetchSpec);
        when(topPosterRowsFetchSpec.all()).thenReturn(Flux.empty());

        UUID userId = UUID.randomUUID();
        when(row.get("user_id", UUID.class)).thenReturn(userId);
        when(row.get("full_name", String.class)).thenReturn("Bob");
        when(row.get("post_count", Long.class)).thenReturn(17L);

        repository.topPosters(5).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, PostAnalyticsRepository.TopPosterRow>> captor =
                ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        PostAnalyticsRepository.TopPosterRow mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.userId()).isEqualTo(userId);
        assertThat(mapped.fullName()).isEqualTo("Bob");
        assertThat(mapped.postCount()).isEqualTo(17L);
    }
}
