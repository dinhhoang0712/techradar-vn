package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.ModerationPostRepository;
import com.techpulse.techradar.features.social.ports.PostRepository.FeedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresModerationPostRepository implements ModerationPostRepository {

    private final DatabaseClient dbClient;

    @Override
    public Flux<FeedRow> findAllForModeration(int limit, int offset) {
        return dbClient.sql(
                "SELECT p.id, p.user_id, u.full_name, up.avatar_url, p.content, p.created_at, " +
                "       (SELECT count(*) FROM post_like pl WHERE pl.post_id = p.id) AS like_count, " +
                "       (SELECT count(*) FROM post_comment pc WHERE pc.post_id = p.id) AS comment_count " +
                "FROM post p " +
                "JOIN users u ON u.id = p.user_id " +
                "LEFT JOIN user_profile up ON up.user_id = p.user_id " +
                "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset")
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> new FeedRow(
                        row.get("id", UUID.class),
                        row.get("user_id", UUID.class),
                        row.get("full_name", String.class),
                        row.get("avatar_url", String.class),
                        row.get("content", String.class),
                        row.get("created_at", LocalDateTime.class),
                        row.get("like_count", Long.class),
                        row.get("comment_count", Long.class),
                        false,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null))
                .all();
    }

    @Override
    public Mono<Boolean> deleteById(UUID postId) {
        return dbClient.sql("DELETE FROM post WHERE id = :id")
                .bind("id", postId)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }
}
