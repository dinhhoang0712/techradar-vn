package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.CommentRepository;
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
 * Pins the SQL text, bind order (including the nullable {@code parent_comment_id} branch), and
 * row-mapping for {@link PostgresCommentRepository} — this hand-written SQL/row-mapping code
 * silently breaks on a typo'd column name or wrong bind order without a test like this.
 */
@ExtendWith(MockitoExtension.class)
class PostgresCommentRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<CommentRepository.CommentRow> commentRowsFetchSpec;
    @Mock
    private RowsFetchSpec<CommentRepository.ParentInfo> parentInfoRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresCommentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresCommentRepository(dbClient);
    }

    @Test
    void insert_withParentComment_bindsAllSixColumns() {
        UUID id = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        when(dbClient.sql(
                "INSERT INTO post_comment (id, post_id, user_id, content, parent_comment_id, created_at) " +
                        "VALUES (:id, :post_id, :user_id, :content, :parent_comment_id, :created_at)"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(id, postId, userId, "hello", parentId, createdAt))
                .verifyComplete();

        verify(executeSpec).bind("id", id);
        verify(executeSpec).bind("post_id", postId);
        verify(executeSpec).bind("user_id", userId);
        verify(executeSpec).bind("content", "hello");
        verify(executeSpec).bind("created_at", createdAt);
        verify(executeSpec).bind("parent_comment_id", parentId);
    }

    @Test
    void insert_withNullParentComment_bindsNullForParentCommentId() {
        UUID id = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull("parent_comment_id", UUID.class)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(id, postId, userId, "hello", null, createdAt))
                .verifyComplete();

        verify(executeSpec).bindNull("parent_comment_id", UUID.class);
    }

    @Test
    void findByPost_bindsPostIdLimitOffset_andMapsRows() {
        UUID postId = UUID.randomUUID();
        when(dbClient.sql(
                "SELECT c.id, c.user_id, u.full_name, up.avatar_url, c.content, c.parent_comment_id, c.created_at " +
                        "FROM post_comment c " +
                        "JOIN users u ON u.id = c.user_id " +
                        "LEFT JOIN user_profile up ON up.user_id = c.user_id " +
                        "WHERE c.post_id = :post_id AND c.deleted_at IS NULL " +
                        "ORDER BY c.created_at ASC LIMIT :limit OFFSET :offset"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("post_id", postId)).thenReturn(executeSpec);
        when(executeSpec.bind("limit", 10)).thenReturn(executeSpec);
        when(executeSpec.bind("offset", 0)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(commentRowsFetchSpec);
        CommentRepository.CommentRow row1 = new CommentRepository.CommentRow(
                UUID.randomUUID(), UUID.randomUUID(), "Alice", null, "hi", null, LocalDateTime.now());
        CommentRepository.CommentRow row2 = new CommentRepository.CommentRow(
                UUID.randomUUID(), UUID.randomUUID(), "Bob", null, "yo", null, LocalDateTime.now());
        when(commentRowsFetchSpec.all()).thenReturn(Flux.just(row1, row2));

        StepVerifier.create(repository.findByPost(postId, 10, 0))
                .expectNext(row1)
                .expectNext(row2)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByPost_rowMapper_mapsAllSevenColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(commentRowsFetchSpec);
        when(commentRowsFetchSpec.all()).thenReturn(Flux.empty());

        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("user_id", UUID.class)).thenReturn(userId);
        when(row.get("full_name", String.class)).thenReturn("Alice");
        when(row.get("avatar_url", String.class)).thenReturn("http://avatar");
        when(row.get("content", String.class)).thenReturn("hello world");
        when(row.get("parent_comment_id", UUID.class)).thenReturn(parentId);
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);

        repository.findByPost(UUID.randomUUID(), 10, 0).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, CommentRepository.CommentRow>> captor =
                ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        CommentRepository.CommentRow mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.id()).isEqualTo(id);
        assertThat(mapped.authorId()).isEqualTo(userId);
        assertThat(mapped.authorName()).isEqualTo("Alice");
        assertThat(mapped.authorAvatarUrl()).isEqualTo("http://avatar");
        assertThat(mapped.content()).isEqualTo("hello world");
        assertThat(mapped.parentCommentId()).isEqualTo(parentId);
        assertThat(mapped.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void findByUser_bindsUserId_assertsSqlAndMapsRows() {
        UUID userId = UUID.randomUUID();
        when(dbClient.sql(
                "SELECT c.id, c.user_id, u.full_name, up.avatar_url, c.content, c.parent_comment_id, c.created_at " +
                        "FROM post_comment c " +
                        "JOIN users u ON u.id = c.user_id " +
                        "LEFT JOIN user_profile up ON up.user_id = c.user_id " +
                        "WHERE c.user_id = :user_id AND c.deleted_at IS NULL " +
                        "ORDER BY c.created_at DESC"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("user_id", userId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(commentRowsFetchSpec);
        CommentRepository.CommentRow row1 = new CommentRepository.CommentRow(
                UUID.randomUUID(), userId, "Alice", null, "my comment", null, LocalDateTime.now());
        when(commentRowsFetchSpec.all()).thenReturn(Flux.just(row1));

        StepVerifier.create(repository.findByUser(userId)).expectNext(row1).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findParentInfo_bindsCommentId_mapsRow() {
        UUID commentId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(dbClient.sql("SELECT post_id, user_id, parent_comment_id FROM post_comment WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", commentId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(parentInfoRowsFetchSpec);
        when(parentInfoRowsFetchSpec.one())
                .thenReturn(Mono.just(new CommentRepository.ParentInfo(postId, authorId, null)));

        StepVerifier.create(repository.findParentInfo(commentId))
                .assertNext(info -> {
                    assertThat(info.postId()).isEqualTo(postId);
                    assertThat(info.authorId()).isEqualTo(authorId);
                    assertThat(info.parentCommentId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findParentInfo_returnsEmptyWhenNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(parentInfoRowsFetchSpec);
        when(parentInfoRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.findParentInfo(UUID.randomUUID())).verifyComplete();
    }

    @Test
    void deleteById_returnsTrueWhenExisted() {
        UUID commentId = UUID.randomUUID();
        when(dbClient.sql("UPDATE post_comment SET deleted_at = now() WHERE id = :id AND deleted_at IS NULL")).thenReturn(executeSpec);
        when(executeSpec.bind("id", commentId)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.deleteById(commentId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void deleteById_returnsFalseWhenNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.deleteById(UUID.randomUUID()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countAll_returnsMappedCount() {
        when(dbClient.sql("SELECT count(*) AS c FROM post_comment WHERE deleted_at IS NULL")).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.just(42L));

        StepVerifier.create(repository.countAll()).expectNext(42L).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countAll_defaultsToZeroWhenEmpty() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(longRowsFetchSpec);
        when(longRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.countAll()).expectNext(0L).verifyComplete();
    }
}
