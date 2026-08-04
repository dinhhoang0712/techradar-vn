package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.shared.db.R2dbcBinders;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresReportRepository implements ReportRepository {

    private final DatabaseClient dbClient;

    private static final String SELECT_BASE =
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
            "LEFT JOIN users cu ON cu.id = c.user_id ";

    private static final String SELECT_PENDING = SELECT_BASE +
            "WHERE r.status = 'PENDING' " +
            "ORDER BY r.created_at ASC LIMIT :limit OFFSET :offset";

    private static final String SELECT_BY_ID = SELECT_BASE + "WHERE r.id = :id";

    @Override
    public Mono<Boolean> insert(UUID id, UUID reporterId, UUID postId, UUID commentId, String reason) {
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO content_report (id, reporter_id, post_id, comment_id, reason) " +
                "VALUES (:id, :reporter_id, :post_id, :comment_id, :reason) " +
                "ON CONFLICT DO NOTHING")
                .bind("id", id)
                .bind("reporter_id", reporterId)
                .bind("reason", reason);
        spec = R2dbcBinders.bindNullable(spec, "post_id", postId, UUID.class);
        spec = R2dbcBinders.bindNullable(spec, "comment_id", commentId, UUID.class);
        return spec.fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Flux<ReportRow> findPending(int limit, int offset) {
        return dbClient.sql(SELECT_PENDING)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<Long> countPending() {
        return dbClient.sql("SELECT count(*) AS c FROM content_report WHERE status = 'PENDING'")
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<ReportRow> findById(UUID reportId) {
        return dbClient.sql(SELECT_BY_ID)
                .bind("id", reportId)
                .map((row, meta) -> mapRow(row))
                .one();
    }

    @Override
    public Mono<Boolean> dismiss(UUID reportId, UUID adminId) {
        return dbClient.sql(
                "UPDATE content_report SET status = 'DISMISSED', resolved_at = now(), resolved_by = :admin_id " +
                "WHERE id = :id AND status = 'PENDING'")
                .bind("id", reportId)
                .bind("admin_id", adminId)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<Boolean> saveAiSuggestion(UUID reportId, String action, String reason, double confidence) {
        return dbClient.sql(
                "UPDATE content_report SET ai_suggested_action = :action, ai_suggested_reason = :reason, " +
                "ai_confidence = :confidence, ai_suggested_at = now() WHERE id = :id")
                .bind("id", reportId)
                .bind("action", action)
                .bind("reason", reason)
                .bind("confidence", confidence)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    private static ReportRow mapRow(Row row) {
        return new ReportRow(
                row.get("id", UUID.class),
                row.get("reporter_id", UUID.class),
                row.get("reporter_name", String.class),
                row.get("post_id", UUID.class),
                row.get("comment_id", UUID.class),
                row.get("target_content", String.class),
                row.get("target_author_name", String.class),
                row.get("reason", String.class),
                row.get("status", String.class),
                row.get("created_at", LocalDateTime.class),
                row.get("ai_suggested_action", String.class),
                row.get("ai_suggested_reason", String.class),
                row.get("ai_confidence", Double.class),
                row.get("ai_suggested_at", LocalDateTime.class)
        );
    }
}
