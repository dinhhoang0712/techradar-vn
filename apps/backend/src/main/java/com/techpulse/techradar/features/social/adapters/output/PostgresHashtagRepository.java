package com.techpulse.techradar.features.social.adapters.output;

import com.techpulse.techradar.features.social.ports.HashtagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class PostgresHashtagRepository implements HashtagRepository {

    private final DatabaseClient dbClient;

    @Override
    public Flux<TrendingRow> trending(LocalDateTime since, int limit) {
        // Sequential scan by design over unnest() — the V14 GIN index speeds up the feed's
        // `hashtags @> ARRAY[:tag]` filter, not this aggregation.
        return dbClient.sql(
                "SELECT unnest(hashtags) AS tag, count(*) AS post_count " +
                "FROM post WHERE created_at > :since AND hashtags IS NOT NULL AND deleted_at IS NULL " +
                "GROUP BY tag ORDER BY post_count DESC LIMIT :limit")
                .bind("since", since)
                .bind("limit", limit)
                .map((row, meta) -> new TrendingRow(
                        row.get("tag", String.class),
                        row.get("post_count", Long.class)))
                .all();
    }
}
