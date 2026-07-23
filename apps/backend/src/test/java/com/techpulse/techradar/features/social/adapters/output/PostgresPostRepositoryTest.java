package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.PostRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the SQL text (including the conditional hashtag-filter clause and its bind), bind order
 * (including the nullable tagged-company fields), and row-mapping for
 * {@link PostgresPostRepository} — the feed-facing port with the biggest/most complex queries in
 * the social feature.
 */
@ExtendWith(MockitoExtension.class)
class PostgresPostRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<FeedRow> feedRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private RowsFetchSpec<UUID> uuidRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresPostRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresPostRepository(dbClient);
    }

    private static PostRepository.NewPost newPost(List<String> hashtags, String companyId, String companyName, String companyLocation) {
        return new PostRepository.NewPost(
                UUID.randomUUID(), UUID.randomUUID(), "hello world", hashtags,
                companyId, companyName, companyLocation, LocalDateTime.now());
    }

    @Test
    void insert_withTaggedCompanyAndHashtags_bindsAllColumns() {
        PostRepository.NewPost post = newPost(List.of("java", "spring"), "c1", "Acme", "Hanoi");
        when(dbClient.sql(
                "INSERT INTO post (id, user_id, content, hashtags, tagged_company_id, tagged_company_name, tagged_company_location, created_at) " +
                        "VALUES (:id, :user_id, :content, :hashtags, :tagged_company_id, :tagged_company_name, :tagged_company_location, :created_at)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(post)).verifyComplete();

        verify(executeSpec).bind("id", post.id());
        verify(executeSpec).bind("user_id", post.userId());
        verify(executeSpec).bind("content", post.content());
        verify(executeSpec).bind("hashtags", new String[]{"java", "spring"});
        verify(executeSpec).bind("created_at", post.createdAt());
        verify(executeSpec).bind("tagged_company_id", "c1");
        verify(executeSpec).bind("tagged_company_name", "Acme");
        verify(executeSpec).bind("tagged_company_location", "Hanoi");
    }

    @Test
    void insert_withoutTaggedCompany_bindsNullForAllThreeCompanyFields() {
        PostRepository.NewPost post = newPost(List.of("java"), null, null, null);
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull("tagged_company_id", String.class)).thenReturn(executeSpec);
        when(executeSpec.bindNull("tagged_company_name", String.class)).thenReturn(executeSpec);
        when(executeSpec.bindNull("tagged_company_location", String.class)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(post)).verifyComplete();

        verify(executeSpec).bindNull("tagged_company_id", String.class);
        verify(executeSpec).bindNull("tagged_company_name", String.class);
        verify(executeSpec).bindNull("tagged_company_location", String.class);
    }

    @Test
    void insert_withNullHashtags_bindsEmptyStringArray() {
        PostRepository.NewPost post = newPost(null, null, null, null);
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(post)).verifyComplete();

        verify(executeSpec).bind("hashtags", new String[0]);
    }

    @Test
    void deleteOwnedBy_returnsTrueWhenDeleted() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(dbClient.sql(
                "UPDATE post SET deleted_at = now() " +
                        "WHERE id = :id AND user_id = :user_id AND deleted_at IS NULL"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", postId)).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.deleteOwnedBy(postId, userId)).expectNext(true).verifyComplete();
    }

    @Test
    void deleteOwnedBy_returnsFalseWhenNotOwnedOrNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.deleteOwnedBy(UUID.randomUUID(), UUID.randomUUID()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findFeed_withoutHashtagFilter_assertsSqlAndBindsViewerLimitOffset() {
        UUID viewerId = UUID.randomUUID();
        String expectedSql = PostgresPostRepository.SELECT_FEED_ROW +
                "WHERE p.deleted_at IS NULL " +
                "AND (p.user_id = :viewer_id OR p.user_id IN (SELECT followee_id FROM follow WHERE follower_id = :viewer_id)) " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
        when(dbClient.sql(expectedSql)).thenReturn(executeSpec);
        when(executeSpec.bind("viewer_id", viewerId)).thenReturn(executeSpec);
        when(executeSpec.bind("limit", 10)).thenReturn(executeSpec);
        when(executeSpec.bind("offset", 0)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(feedRowsFetchSpec);
        when(feedRowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findFeed(viewerId, null, 10, 0)).verifyComplete();

        verify(executeSpec, never()).bind(eq("hashtag_arr"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findFeed_withHashtagFilter_assertsSqlIncludesFilterAndBindsHashtagArray() {
        UUID viewerId = UUID.randomUUID();
        String expectedSql = PostgresPostRepository.SELECT_FEED_ROW +
                "WHERE p.deleted_at IS NULL " +
                "AND (p.user_id = :viewer_id OR p.user_id IN (SELECT followee_id FROM follow WHERE follower_id = :viewer_id)) " +
                "AND p.hashtags @> :hashtag_arr " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
        when(dbClient.sql(expectedSql)).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(feedRowsFetchSpec);
        when(feedRowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findFeed(viewerId, "java", 10, 0)).verifyComplete();

        verify(executeSpec).bind("hashtag_arr", new String[]{"java"});
    }

    @Test
    @SuppressWarnings("unchecked")
    void findExplore_withoutHashtagFilter_assertsSql() {
        UUID viewerId = UUID.randomUUID();
        String expectedSql = PostgresPostRepository.SELECT_FEED_ROW +
                "WHERE p.deleted_at IS NULL " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
        when(dbClient.sql(expectedSql)).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(feedRowsFetchSpec);
        when(feedRowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findExplore(viewerId, null, 10, 0)).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findExplore_withHashtagFilter_assertsSqlAndBindsHashtagArray() {
        UUID viewerId = UUID.randomUUID();
        String expectedSql = PostgresPostRepository.SELECT_FEED_ROW +
                "WHERE p.deleted_at IS NULL " +
                "AND p.hashtags @> :hashtag_arr " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
        when(dbClient.sql(expectedSql)).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(feedRowsFetchSpec);
        when(feedRowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findExplore(viewerId, "kotlin", 10, 0)).verifyComplete();

        verify(executeSpec).bind("hashtag_arr", new String[]{"kotlin"});
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByUser_bindsViewerTargetLimitOffset_assertsSql() {
        UUID viewerId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        String expectedSql = PostgresPostRepository.SELECT_FEED_ROW +
                "WHERE p.deleted_at IS NULL AND p.user_id = :target_user_id " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
        when(dbClient.sql(expectedSql)).thenReturn(executeSpec);
        when(executeSpec.bind("viewer_id", viewerId)).thenReturn(executeSpec);
        when(executeSpec.bind("target_user_id", targetUserId)).thenReturn(executeSpec);
        when(executeSpec.bind("limit", 10)).thenReturn(executeSpec);
        when(executeSpec.bind("offset", 0)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(feedRowsFetchSpec);
        when(feedRowsFetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.findByUser(targetUserId, viewerId, 10, 0)).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_bindsViewerAndPostId_assertsSql() {
        UUID postId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        String expectedSql = PostgresPostRepository.SELECT_FEED_ROW + "WHERE p.deleted_at IS NULL AND p.id = :post_id";
        when(dbClient.sql(expectedSql)).thenReturn(executeSpec);
        when(executeSpec.bind("viewer_id", viewerId)).thenReturn(executeSpec);
        when(executeSpec.bind("post_id", postId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(feedRowsFetchSpec);
        when(feedRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.findById(postId, viewerId)).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countByUser_bindsUserId_returnsMappedCount() {
        UUID userId = UUID.randomUUID();
        when(dbClient.sql("SELECT count(*) AS c FROM post WHERE user_id = :user_id AND deleted_at IS NULL")).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(6L));

        StepVerifier.create(repository.countByUser(userId)).expectNext(6L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countByUser_defaultsToZeroWhenEmpty() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.countByUser(UUID.randomUUID())).expectNext(0L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAuthorId_bindsPostId_mapsUserId() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(dbClient.sql("SELECT user_id FROM post WHERE id = :id AND deleted_at IS NULL")).thenReturn(executeSpec);
        when(executeSpec.bind("id", postId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(uuidRowsFetchSpec);
        when(uuidRowsFetchSpec.one()).thenReturn(Mono.just(authorId));

        StepVerifier.create(repository.findAuthorId(postId)).expectNext(authorId).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAuthorId_returnsEmptyWhenPostNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(uuidRowsFetchSpec);
        when(uuidRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.findAuthorId(UUID.randomUUID())).verifyComplete();
    }

    @Test
    void like_returnsTrueWhenNewlyLiked() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(dbClient.sql(
                "INSERT INTO post_like (post_id, user_id) VALUES (:post_id, :user_id) " +
                        "ON CONFLICT (post_id, user_id) DO NOTHING"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("post_id", postId)).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.like(postId, userId)).expectNext(true).verifyComplete();
    }

    @Test
    void like_returnsFalseWhenAlreadyLiked() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.like(UUID.randomUUID(), UUID.randomUUID()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void unlike_bindsBothIds_completes() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(dbClient.sql("DELETE FROM post_like WHERE post_id = :post_id AND user_id = :user_id")).thenReturn(executeSpec);
        when(executeSpec.bind("post_id", postId)).thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.unlike(postId, userId)).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rowMapper_mapsAllFieldsWhenImagesAndHashtagsPresent() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(feedRowsFetchSpec);
        when(feedRowsFetchSpec.one()).thenReturn(Mono.empty());

        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        UUID image1 = UUID.randomUUID();
        UUID image2 = UUID.randomUUID();
        when(row.get("id", UUID.class)).thenReturn(postId);
        when(row.get("user_id", UUID.class)).thenReturn(authorId);
        when(row.get("full_name", String.class)).thenReturn("Alice");
        when(row.get("avatar_url", String.class)).thenReturn("avatar.png");
        when(row.get("content", String.class)).thenReturn("hello world");
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);
        when(row.get("like_count", Long.class)).thenReturn(4L);
        when(row.get("comment_count", Long.class)).thenReturn(2L);
        when(row.get("liked_by_me", Boolean.class)).thenReturn(true);
        when(row.get("image_ids", UUID[].class)).thenReturn(new UUID[]{image1, image2});
        when(row.get("hashtags", String[].class)).thenReturn(new String[]{"java", "spring"});
        when(row.get("tagged_company_id", String.class)).thenReturn("c1");
        when(row.get("tagged_company_name", String.class)).thenReturn("Acme");
        when(row.get("tagged_company_location", String.class)).thenReturn("Hanoi");

        repository.findById(postId, UUID.randomUUID()).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, FeedRow>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        FeedRow mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.id()).isEqualTo(postId);
        assertThat(mapped.authorId()).isEqualTo(authorId);
        assertThat(mapped.authorName()).isEqualTo("Alice");
        assertThat(mapped.authorAvatarUrl()).isEqualTo("avatar.png");
        assertThat(mapped.content()).isEqualTo("hello world");
        assertThat(mapped.createdAt()).isEqualTo(createdAt);
        assertThat(mapped.likeCount()).isEqualTo(4L);
        assertThat(mapped.commentCount()).isEqualTo(2L);
        assertThat(mapped.likedByMe()).isTrue();
        assertThat(mapped.imageIds()).containsExactly(image1, image2);
        assertThat(mapped.hashtags()).containsExactly("java", "spring");
        assertThat(mapped.taggedCompanyId()).isEqualTo("c1");
        assertThat(mapped.taggedCompanyName()).isEqualTo("Acme");
        assertThat(mapped.taggedCompanyLocation()).isEqualTo("Hanoi");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rowMapper_treatsNullImageIdsAndHashtagsAsEmptyLists() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(feedRowsFetchSpec);
        when(feedRowsFetchSpec.one()).thenReturn(Mono.empty());

        when(row.get("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(row.get("user_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(row.get("full_name", String.class)).thenReturn("Bob");
        when(row.get("avatar_url", String.class)).thenReturn(null);
        when(row.get("content", String.class)).thenReturn("no media");
        when(row.get("created_at", LocalDateTime.class)).thenReturn(LocalDateTime.now());
        when(row.get("like_count", Long.class)).thenReturn(0L);
        when(row.get("comment_count", Long.class)).thenReturn(0L);
        when(row.get("liked_by_me", Boolean.class)).thenReturn(null);
        when(row.get("image_ids", UUID[].class)).thenReturn(null);
        when(row.get("hashtags", String[].class)).thenReturn(null);
        when(row.get("tagged_company_id", String.class)).thenReturn(null);
        when(row.get("tagged_company_name", String.class)).thenReturn(null);
        when(row.get("tagged_company_location", String.class)).thenReturn(null);

        repository.findById(UUID.randomUUID(), UUID.randomUUID()).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, FeedRow>> captor = ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        FeedRow mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.likedByMe()).isFalse();
        assertThat(mapped.imageIds()).isEmpty();
        assertThat(mapped.hashtags()).isEmpty();
    }
}
