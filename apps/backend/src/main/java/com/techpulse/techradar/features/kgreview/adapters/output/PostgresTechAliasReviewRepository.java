package com.techpulse.techradar.features.kgreview.adapters.output;

import com.techpulse.techradar.features.kgreview.domain.TechAliasReviewItem;
import com.techpulse.techradar.features.kgreview.ports.TechAliasReviewRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class PostgresTechAliasReviewRepository implements TechAliasReviewRepository {

    private final DatabaseClient dbClient;

    private static final String SELECT_PENDING =
            "SELECT id, name_a, name_b, llm_reasoning, status, created_at " +
            "FROM dp_tech_alias_review_queue WHERE status = 'pending' " +
            "ORDER BY created_at ASC LIMIT :limit OFFSET :offset";

    private static final String SELECT_BY_ID =
            "SELECT id, name_a, name_b, llm_reasoning, status, created_at " +
            "FROM dp_tech_alias_review_queue WHERE id = :id";

    @Override
    public Flux<TechAliasReviewItem> findPending(int limit, int offset) {
        return dbClient.sql(SELECT_PENDING)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<Long> countPending() {
        return dbClient.sql("SELECT count(*) AS c FROM dp_tech_alias_review_queue WHERE status = 'pending'")
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<TechAliasReviewItem> findById(long id) {
        return dbClient.sql(SELECT_BY_ID)
                .bind("id", id)
                .map((row, meta) -> mapRow(row))
                .one();
    }

    @Override
    public Mono<Boolean> markApproved(long id) {
        return updateStatus(id, "approved");
    }

    @Override
    public Mono<Boolean> markRejected(long id) {
        return updateStatus(id, "rejected");
    }

    private Mono<Boolean> updateStatus(long id, String status) {
        return dbClient.sql(
                "UPDATE dp_tech_alias_review_queue SET status = :status, decided_at = now() " +
                "WHERE id = :id AND status = 'pending'")
                .bind("id", id)
                .bind("status", status)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<Void> saveAlias(String aliasNormalized, String canonicalName) {
        return dbClient.sql(
                "INSERT INTO dp_tech_alias_map (alias_normalized, canonical_name, source) " +
                "VALUES (:alias_normalized, :canonical_name, 'human_review') " +
                "ON CONFLICT (alias_normalized) DO UPDATE SET canonical_name = EXCLUDED.canonical_name, " +
                "source = 'human_review'")
                .bind("alias_normalized", aliasNormalized)
                .bind("canonical_name", canonicalName)
                .fetch().rowsUpdated()
                .then();
    }

    private static TechAliasReviewItem mapRow(Row row) {
        return new TechAliasReviewItem(
                row.get("id", Long.class),
                row.get("name_a", String.class),
                row.get("name_b", String.class),
                row.get("llm_reasoning", String.class),
                row.get("status", String.class),
                row.get("created_at", LocalDateTime.class)
        );
    }
}
