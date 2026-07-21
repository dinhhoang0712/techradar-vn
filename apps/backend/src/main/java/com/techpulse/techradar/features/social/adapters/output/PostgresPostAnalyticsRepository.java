package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.PostAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresPostAnalyticsRepository implements PostAnalyticsRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Long> countAll() {
        return dbClient.sql("SELECT count(*) AS c FROM post")
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Long> countCreatedSince(LocalDateTime since) {
        return dbClient.sql("SELECT count(*) AS c FROM post WHERE created_at >= :since")
                .bind("since", since)
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Long> countAllLikes() {
        return dbClient.sql("SELECT count(*) AS c FROM post_like")
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Flux<TopPosterRow> topPosters(int limit) {
        return dbClient.sql(
                "SELECT p.user_id, u.full_name, count(*) AS post_count " +
                "FROM post p JOIN users u ON u.id = p.user_id " +
                "GROUP BY p.user_id, u.full_name " +
                "ORDER BY post_count DESC LIMIT :limit")
                .bind("limit", limit)
                .map((row, meta) -> new TopPosterRow(
                        row.get("user_id", UUID.class),
                        row.get("full_name", String.class),
                        row.get("post_count", Long.class)))
                .all();
    }
}
