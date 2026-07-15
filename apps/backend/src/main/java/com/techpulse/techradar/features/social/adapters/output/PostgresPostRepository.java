package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.PostRepository;
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
public class PostgresPostRepository implements PostRepository {

    private final DatabaseClient dbClient;

    private static final String SELECT_FEED_ROW =
            "SELECT p.id, p.user_id, u.full_name, up.avatar_url, p.content, p.created_at, " +
            "       (SELECT count(*) FROM post_like pl WHERE pl.post_id = p.id) AS like_count, " +
            "       (SELECT count(*) FROM post_comment pc WHERE pc.post_id = p.id) AS comment_count, " +
            "       EXISTS(SELECT 1 FROM post_like pl2 WHERE pl2.post_id = p.id AND pl2.user_id = :viewer_id) AS liked_by_me " +
            "FROM post p " +
            "JOIN users u ON u.id = p.user_id " +
            "LEFT JOIN user_profile up ON up.user_id = p.user_id ";

    @Override
    public Mono<Void> insert(UUID postId, UUID userId, String content, LocalDateTime createdAt) {
        return dbClient.sql("INSERT INTO post (id, user_id, content, created_at) VALUES (:id, :user_id, :content, :created_at)")
                .bind("id", postId)
                .bind("user_id", userId)
                .bind("content", content)
                .bind("created_at", createdAt)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Boolean> deleteOwnedBy(UUID postId, UUID userId) {
        return dbClient.sql("DELETE FROM post WHERE id = :id AND user_id = :user_id")
                .bind("id", postId)
                .bind("user_id", userId)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Flux<FeedRow> findFeed(UUID viewerId, int limit, int offset) {
        return dbClient.sql(
                SELECT_FEED_ROW +
                "WHERE p.user_id = :viewer_id " +
                "   OR p.user_id IN (SELECT followee_id FROM follow WHERE follower_id = :viewer_id) " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset")
                .bind("viewer_id", viewerId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Flux<FeedRow> findByUser(UUID targetUserId, UUID viewerId, int limit, int offset) {
        return dbClient.sql(
                SELECT_FEED_ROW +
                "WHERE p.user_id = :target_user_id " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset")
                .bind("viewer_id", viewerId)
                .bind("target_user_id", targetUserId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<Long> countByUser(UUID userId) {
        return dbClient.sql("SELECT count(*) AS c FROM post WHERE user_id = :user_id")
                .bind("user_id", userId)
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Void> like(UUID postId, UUID userId) {
        return dbClient.sql(
                "INSERT INTO post_like (post_id, user_id) VALUES (:post_id, :user_id) " +
                "ON CONFLICT (post_id, user_id) DO NOTHING")
                .bind("post_id", postId)
                .bind("user_id", userId)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Void> unlike(UUID postId, UUID userId) {
        return dbClient.sql("DELETE FROM post_like WHERE post_id = :post_id AND user_id = :user_id")
                .bind("post_id", postId)
                .bind("user_id", userId)
                .fetch().rowsUpdated().then();
    }

    private static FeedRow mapRow(Row row) {
        return new FeedRow(
                row.get("id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("full_name", String.class),
                row.get("avatar_url", String.class),
                row.get("content", String.class),
                row.get("created_at", LocalDateTime.class),
                row.get("like_count", Long.class),
                row.get("comment_count", Long.class),
                Boolean.TRUE.equals(row.get("liked_by_me", Boolean.class))
        );
    }
}
