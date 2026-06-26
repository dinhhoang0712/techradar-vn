package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL adapter for {@code activity_log}.
 */
@Repository
@RequiredArgsConstructor
public class PostgresActivityLogRepository implements ActivityLogRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Void> recordVisit(String userId, String path) {
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO activity_log (type, user_id, path) VALUES ('visit', :user_id, :path)");
        spec = userId != null && isUuid(userId)
                ? spec.bind("user_id", UUID.fromString(userId))
                : spec.bindNull("user_id", UUID.class);
        spec = path != null ? spec.bind("path", path) : spec.bindNull("path", String.class);
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Void> recordSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Mono.empty();
        }
        return dbClient.sql("INSERT INTO activity_log (type, keyword) VALUES ('search', :keyword)")
                .bind("keyword", keyword.trim())
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Long> countToday(String type) {
        return dbClient.sql(
                "SELECT count(*) AS c FROM activity_log " +
                "WHERE type = :type AND created_at >= date_trunc('day', now())")
                .bind("type", type)
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Flux<Map<String, Object>> monthlyVisits() {
        return dbClient.sql(
                "SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month, count(*) AS count " +
                "FROM activity_log WHERE type = 'visit' " +
                "GROUP BY 1 ORDER BY 1 DESC LIMIT 12")
                .map((row, meta) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("month", row.get("month", String.class));
                    m.put("count", row.get("count", Long.class));
                    return m;
                })
                .all();
    }

    @Override
    public Flux<String> topKeywords(int limit) {
        return dbClient.sql(
                "SELECT keyword FROM activity_log WHERE type = 'search' AND keyword IS NOT NULL " +
                "GROUP BY keyword ORDER BY count(*) DESC LIMIT :limit")
                .bind("limit", limit)
                .map((row, meta) -> row.get("keyword", String.class))
                .all();
    }

    private static boolean isUuid(String v) {
        try {
            UUID.fromString(v);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
