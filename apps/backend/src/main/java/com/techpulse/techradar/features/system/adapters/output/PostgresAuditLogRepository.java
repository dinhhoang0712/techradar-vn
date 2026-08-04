package com.techpulse.techradar.features.system.adapters.output;

import com.techpulse.techradar.features.system.domain.AuditLogEntry;
import com.techpulse.techradar.features.system.ports.AuditLogRepository;
import com.techpulse.techradar.shared.db.R2dbcBinders;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PostgreSQL adapter for the {@code audit_log} table.
 */
@Repository
@RequiredArgsConstructor
public class PostgresAuditLogRepository implements AuditLogRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Void> insert(AuditLogEntry entry) {
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO audit_log (actor_id, action, target_type, target_id, details) " +
                "VALUES (:actor_id, :action, :target_type, :target_id, :details)"
        )
                .bind("actor_id", entry.getActorId())
                .bind("action", entry.getAction());
        spec = R2dbcBinders.bindNullable(spec, "target_type", entry.getTargetType());
        spec = R2dbcBinders.bindNullable(spec, "target_id", entry.getTargetId());
        spec = R2dbcBinders.bindNullable(spec, "details", entry.getDetails());
        return spec.then();
    }

    @Override
    public Flux<AuditLogEntry> list(int limit, int offset) {
        return dbClient.sql(
                "SELECT id, actor_id, action, target_type, target_id, details, created_at " +
                "FROM audit_log ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
        )
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> AuditLogEntry.builder()
                        .id(row.get("id", UUID.class))
                        .actorId(row.get("actor_id", UUID.class))
                        .action(row.get("action", String.class))
                        .targetType(row.get("target_type", String.class))
                        .targetId(row.get("target_id", String.class))
                        .details(row.get("details", String.class))
                        .createdAt(row.get("created_at", LocalDateTime.class))
                        .build())
                .all();
    }
}
