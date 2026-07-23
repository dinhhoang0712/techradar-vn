package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.PostRepository.FeedRow;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text, bind order, and row-mapping for {@link PostgresModerationPostRepository} —
 * in particular that {@code findAllForModeration} deliberately hardcodes the viewer-specific
 * fields ({@code likedByMe=false}, empty image/hashtag lists, null tagged-company fields) since
 * moderation is not viewer-scoped.
 */
@ExtendWith(MockitoExtension.class)
class PostgresModerationPostRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<FeedRow> rowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresModerationPostRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresModerationPostRepository(dbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllForModeration_bindsLimitAndOffset_andMapsRows() {
        when(dbClient.sql(
                "SELECT p.id, p.user_id, u.full_name, up.avatar_url, p.content, p.created_at, " +
                        "       (SELECT count(*) FROM post_like pl WHERE pl.post_id = p.id) AS like_count, " +
                        "       (SELECT count(*) FROM post_comment pc WHERE pc.post_id = p.id) AS comment_count " +
                        "FROM post p " +
                        "JOIN users u ON u.id = p.user_id " +
                        "LEFT JOIN user_profile up ON up.user_id = p.user_id " +
                        "WHERE p.deleted_at IS NULL " +
                        "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("limit", 20)).thenReturn(executeSpec);
        when(executeSpec.bind("offset", 0)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        FeedRow feedRow = new FeedRow(UUID.randomUUID(), UUID.randomUUID(), "Alice", null,
                "content", LocalDateTime.now(), 1L, 2L, false, java.util.List.of(), java.util.List.of(), null, null, null);
        when(rowsFetchSpec.all()).thenReturn(Flux.just(feedRow));

        StepVerifier.create(repository.findAllForModeration(20, 0))
                .expectNext(feedRow)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllForModeration_rowMapper_hardcodesViewerAgnosticFields() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.empty());

        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        when(row.get("id", UUID.class)).thenReturn(postId);
        when(row.get("user_id", UUID.class)).thenReturn(userId);
        when(row.get("full_name", String.class)).thenReturn("Alice");
        when(row.get("avatar_url", String.class)).thenReturn("avatar.png");
        when(row.get("content", String.class)).thenReturn("bad content");
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);
        when(row.get("like_count", Long.class)).thenReturn(5L);
        when(row.get("comment_count", Long.class)).thenReturn(3L);

        repository.findAllForModeration(20, 0).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, FeedRow>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        FeedRow mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.id()).isEqualTo(postId);
        assertThat(mapped.authorId()).isEqualTo(userId);
        assertThat(mapped.authorName()).isEqualTo("Alice");
        assertThat(mapped.authorAvatarUrl()).isEqualTo("avatar.png");
        assertThat(mapped.content()).isEqualTo("bad content");
        assertThat(mapped.createdAt()).isEqualTo(createdAt);
        assertThat(mapped.likeCount()).isEqualTo(5L);
        assertThat(mapped.commentCount()).isEqualTo(3L);
        assertThat(mapped.likedByMe()).isFalse();
        assertThat(mapped.imageIds()).isEmpty();
        assertThat(mapped.hashtags()).isEmpty();
        assertThat(mapped.taggedCompanyId()).isNull();
        assertThat(mapped.taggedCompanyName()).isNull();
        assertThat(mapped.taggedCompanyLocation()).isNull();
    }

    @Test
    void deleteById_returnsTrueWhenExisted() {
        UUID postId = UUID.randomUUID();
        when(dbClient.sql("UPDATE post SET deleted_at = now() WHERE id = :id AND deleted_at IS NULL")).thenReturn(executeSpec);
        when(executeSpec.bind("id", postId)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.deleteById(postId)).expectNext(true).verifyComplete();
    }

    @Test
    void deleteById_returnsFalseWhenNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.deleteById(UUID.randomUUID())).expectNext(false).verifyComplete();
    }
}
