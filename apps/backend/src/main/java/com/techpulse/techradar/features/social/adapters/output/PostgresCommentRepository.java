package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.CommentRepository;
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
public class PostgresCommentRepository implements CommentRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Void> insert(UUID commentId, UUID postId, UUID userId, String content, LocalDateTime createdAt) {
        return dbClient.sql(
                "INSERT INTO post_comment (id, post_id, user_id, content, created_at) " +
                "VALUES (:id, :post_id, :user_id, :content, :created_at)")
                .bind("id", commentId)
                .bind("post_id", postId)
                .bind("user_id", userId)
                .bind("content", content)
                .bind("created_at", createdAt)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Flux<CommentRow> findByPost(UUID postId, int limit, int offset) {
        return dbClient.sql(
                "SELECT c.id, c.user_id, u.full_name, up.avatar_url, c.content, c.created_at " +
                "FROM post_comment c " +
                "JOIN users u ON u.id = c.user_id " +
                "LEFT JOIN user_profile up ON up.user_id = c.user_id " +
                "WHERE c.post_id = :post_id " +
                "ORDER BY c.created_at ASC LIMIT :limit OFFSET :offset")
                .bind("post_id", postId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    private static CommentRow mapRow(Row row) {
        return new CommentRow(
                row.get("id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("full_name", String.class),
                row.get("avatar_url", String.class),
                row.get("content", String.class),
                row.get("created_at", LocalDateTime.class)
        );
    }
}
