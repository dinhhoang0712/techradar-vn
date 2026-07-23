package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.HashtagRepository;
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
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresHashtagRepository}.
 */
@ExtendWith(MockitoExtension.class)
class PostgresHashtagRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<HashtagRepository.TrendingRow> rowsFetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresHashtagRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresHashtagRepository(dbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void trending_bindsSinceAndLimit_andMapsRows() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        when(dbClient.sql(
                "SELECT unnest(hashtags) AS tag, count(*) AS post_count " +
                        "FROM post WHERE created_at > :since AND hashtags IS NOT NULL AND deleted_at IS NULL " +
                        "GROUP BY tag ORDER BY post_count DESC LIMIT :limit"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("since", since)).thenReturn(executeSpec);
        when(executeSpec.bind("limit", 10)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        HashtagRepository.TrendingRow trendingRow = new HashtagRepository.TrendingRow("java", 42L);
        when(rowsFetchSpec.all()).thenReturn(Flux.just(trendingRow));

        StepVerifier.create(repository.trending(since, 10))
                .expectNext(trendingRow)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void trending_rowMapper_mapsTagAndPostCount() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());
        when(row.get("tag", String.class)).thenReturn("kubernetes");
        when(row.get("post_count", Long.class)).thenReturn(99L);

        repository.trending(LocalDateTime.now(), 10).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, HashtagRepository.TrendingRow>> captor =
                ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        HashtagRepository.TrendingRow mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.tag()).isEqualTo("kubernetes");
        assertThat(mapped.postCount()).isEqualTo(99L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void trending_returnsEmptyFluxWhenNoHashtagsFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.trending(LocalDateTime.now(), 10)).verifyComplete();
    }
}
