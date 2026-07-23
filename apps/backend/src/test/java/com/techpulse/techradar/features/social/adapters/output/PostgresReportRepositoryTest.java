package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.ReportRepository;
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
 * Pins the SQL text, bind order (including the nullable {@code post_id}/{@code comment_id}
 * branches), and row-mapping for {@link PostgresReportRepository}.
 */
@ExtendWith(MockitoExtension.class)
class PostgresReportRepositoryTest {

    @Mock
    private DatabaseClient dbClient;
    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;
    @Mock
    private RowsFetchSpec<ReportRepository.ReportRow> reportRowsFetchSpec;
    @Mock
    private RowsFetchSpec<Long> longRowsFetchSpec;
    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;
    @Mock
    private Row row;
    @Mock
    private RowMetadata rowMetadata;

    private PostgresReportRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresReportRepository(dbClient);
    }

    @Test
    void insert_withPostId_bindsPostIdAndNullsCommentId() {
        UUID id = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(dbClient.sql(
                "INSERT INTO content_report (id, reporter_id, post_id, comment_id, reason) " +
                        "VALUES (:id, :reporter_id, :post_id, :comment_id, :reason) " +
                        "ON CONFLICT DO NOTHING"))
                .thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull("comment_id", UUID.class)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(id, reporterId, postId, null, "spam"))
                .expectNext(true)
                .verifyComplete();

        verify(executeSpec).bind("id", id);
        verify(executeSpec).bind("reporter_id", reporterId);
        verify(executeSpec).bind("reason", "spam");
        verify(executeSpec).bind("post_id", postId);
        verify(executeSpec).bindNull("comment_id", UUID.class);
    }

    @Test
    void insert_withCommentId_bindsCommentIdAndNullsPostId() {
        UUID id = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull("post_id", UUID.class)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.insert(id, reporterId, null, commentId, "abuse"))
                .expectNext(true)
                .verifyComplete();

        verify(executeSpec).bind("comment_id", commentId);
        verify(executeSpec).bindNull("post_id", UUID.class);
    }

    @Test
    void insert_returnsFalseWhenConflict() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.bindNull(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.insert(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "spam"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findPending_bindsLimitOffset_ordersOldestFirst_andMapsRows() {
        when(dbClient.sql(
                "SELECT r.id, r.reporter_id, ru.full_name AS reporter_name, r.post_id, r.comment_id, " +
                        "       COALESCE(p.content, c.content) AS target_content, " +
                        "       COALESCE(pu.full_name, cu.full_name) AS target_author_name, " +
                        "       r.reason, r.status, r.created_at, " +
                        "       r.ai_suggested_action, r.ai_suggested_reason, r.ai_confidence, r.ai_suggested_at " +
                        "FROM content_report r " +
                        "JOIN users ru ON ru.id = r.reporter_id " +
                        "LEFT JOIN post p ON p.id = r.post_id " +
                        "LEFT JOIN users pu ON pu.id = p.user_id " +
                        "LEFT JOIN post_comment c ON c.id = r.comment_id " +
                        "LEFT JOIN users cu ON cu.id = c.user_id " +
                        "WHERE r.status = 'PENDING' " +
                        "ORDER BY r.created_at ASC LIMIT :limit OFFSET :offset"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("limit", 20)).thenReturn(executeSpec);
        when(executeSpec.bind("offset", 0)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(reportRowsFetchSpec);
        ReportRepository.ReportRow reportRow = new ReportRepository.ReportRow(
                UUID.randomUUID(), UUID.randomUUID(), "Alice", UUID.randomUUID(), null,
                "bad content", "Bob", "spam", "PENDING", LocalDateTime.now(), null, null, null, null);
        when(reportRowsFetchSpec.all()).thenReturn(Flux.just(reportRow));

        StepVerifier.create(repository.findPending(20, 0)).expectNext(reportRow).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countPending_returnsMappedCount() {
        when(dbClient.sql("SELECT count(*) AS c FROM content_report WHERE status = 'PENDING'")).thenReturn(executeSpec);
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
    void findById_bindsReportId_mapsRow() {
        UUID reportId = UUID.randomUUID();
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind("id", reportId)).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(reportRowsFetchSpec);
        ReportRepository.ReportRow reportRow = new ReportRepository.ReportRow(
                reportId, UUID.randomUUID(), "Alice", null, UUID.randomUUID(),
                "bad comment", "Carol", "harassment", "PENDING", LocalDateTime.now(),
                "REMOVE", "toxic", 0.9, LocalDateTime.now());
        when(reportRowsFetchSpec.one()).thenReturn(Mono.just(reportRow));

        StepVerifier.create(repository.findById(reportId))
                .assertNext(r -> assertThat(r.id()).isEqualTo(reportId))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_returnsEmptyWhenNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(reportRowsFetchSpec);
        when(reportRowsFetchSpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.findById(UUID.randomUUID())).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_rowMapper_mapsAllFourteenColumns() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(reportRowsFetchSpec);
        when(reportRowsFetchSpec.one()).thenReturn(Mono.empty());

        UUID id = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime aiSuggestedAt = LocalDateTime.now().plusMinutes(1);
        when(row.get("id", UUID.class)).thenReturn(id);
        when(row.get("reporter_id", UUID.class)).thenReturn(reporterId);
        when(row.get("reporter_name", String.class)).thenReturn("Alice");
        when(row.get("post_id", UUID.class)).thenReturn(postId);
        when(row.get("comment_id", UUID.class)).thenReturn(commentId);
        when(row.get("target_content", String.class)).thenReturn("bad content");
        when(row.get("target_author_name", String.class)).thenReturn("Bob");
        when(row.get("reason", String.class)).thenReturn("spam");
        when(row.get("status", String.class)).thenReturn("PENDING");
        when(row.get("created_at", LocalDateTime.class)).thenReturn(createdAt);
        when(row.get("ai_suggested_action", String.class)).thenReturn("REMOVE");
        when(row.get("ai_suggested_reason", String.class)).thenReturn("toxic language");
        when(row.get("ai_confidence", Double.class)).thenReturn(0.87);
        when(row.get("ai_suggested_at", LocalDateTime.class)).thenReturn(aiSuggestedAt);

        repository.findById(id).subscribe();

        ArgumentCaptor<BiFunction<Row, RowMetadata, ReportRepository.ReportRow>> captor =
                ArgumentCaptor.forClass(BiFunction.class);
        verify(executeSpec).map(captor.capture());
        ReportRepository.ReportRow mapped = captor.getValue().apply(row, rowMetadata);

        assertThat(mapped.id()).isEqualTo(id);
        assertThat(mapped.reporterId()).isEqualTo(reporterId);
        assertThat(mapped.reporterName()).isEqualTo("Alice");
        assertThat(mapped.postId()).isEqualTo(postId);
        assertThat(mapped.commentId()).isEqualTo(commentId);
        assertThat(mapped.targetContent()).isEqualTo("bad content");
        assertThat(mapped.targetAuthorName()).isEqualTo("Bob");
        assertThat(mapped.reason()).isEqualTo("spam");
        assertThat(mapped.status()).isEqualTo("PENDING");
        assertThat(mapped.createdAt()).isEqualTo(createdAt);
        assertThat(mapped.aiSuggestedAction()).isEqualTo("REMOVE");
        assertThat(mapped.aiSuggestedReason()).isEqualTo("toxic language");
        assertThat(mapped.aiConfidence()).isEqualTo(0.87);
        assertThat(mapped.aiSuggestedAt()).isEqualTo(aiSuggestedAt);
    }

    @Test
    void dismiss_returnsTrueWhenPendingReportDismissed() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(dbClient.sql(
                "UPDATE content_report SET status = 'DISMISSED', resolved_at = now(), resolved_by = :admin_id " +
                        "WHERE id = :id AND status = 'PENDING'"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", reportId)).thenReturn(executeSpec);
        when(executeSpec.bind("admin_id", adminId)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.dismiss(reportId, adminId)).expectNext(true).verifyComplete();
    }

    @Test
    void dismiss_returnsFalseWhenNotPendingOrNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.dismiss(UUID.randomUUID(), UUID.randomUUID()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void saveAiSuggestion_bindsAllFourParams_returnsTrueWhenUpdated() {
        UUID reportId = UUID.randomUUID();
        when(dbClient.sql(
                "UPDATE content_report SET ai_suggested_action = :action, ai_suggested_reason = :reason, " +
                        "ai_confidence = :confidence, ai_suggested_at = now() WHERE id = :id"))
                .thenReturn(executeSpec);
        when(executeSpec.bind("id", reportId)).thenReturn(executeSpec);
        when(executeSpec.bind("action", "REMOVE")).thenReturn(executeSpec);
        when(executeSpec.bind("reason", "toxic")).thenReturn(executeSpec);
        when(executeSpec.bind("confidence", 0.95)).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(repository.saveAiSuggestion(reportId, "REMOVE", "toxic", 0.95))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void saveAiSuggestion_returnsFalseWhenReportNotFound() {
        when(dbClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        StepVerifier.create(repository.saveAiSuggestion(UUID.randomUUID(), "REMOVE", "toxic", 0.5))
                .expectNext(false)
                .verifyComplete();
    }
}
