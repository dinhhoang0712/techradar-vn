package com.techpulse.techradar.features.messaging.adapters.output;

import com.techpulse.techradar.features.messaging.ports.MessageReactionRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresMessageReactionRepository implements MessageReactionRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Void> upsert(UUID messageId, UUID userId, String emoji) {
        return dbClient.sql(
                "INSERT INTO message_reaction (message_id, user_id, emoji, created_at) " +
                "VALUES (:message_id, :user_id, :emoji, now()) " +
                "ON CONFLICT (message_id, user_id) DO UPDATE SET emoji = EXCLUDED.emoji, created_at = EXCLUDED.created_at")
                .bind("message_id", messageId)
                .bind("user_id", userId)
                .bind("emoji", emoji)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Void> remove(UUID messageId, UUID userId) {
        return dbClient.sql("DELETE FROM message_reaction WHERE message_id = :message_id AND user_id = :user_id")
                .bind("message_id", messageId)
                .bind("user_id", userId)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Flux<ReactionRow> findByMessageId(UUID messageId) {
        return dbClient.sql("SELECT message_id, user_id, emoji FROM message_reaction WHERE message_id = :message_id")
                .bind("message_id", messageId)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Flux<ReactionRow> findByMessageIds(Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Flux.empty();
        }
        return dbClient.sql("SELECT message_id, user_id, emoji FROM message_reaction WHERE message_id = ANY(:message_ids)")
                .bind("message_ids", messageIds.toArray(new UUID[0]))
                .map((row, meta) -> mapRow(row))
                .all();
    }

    private static ReactionRow mapRow(Row row) {
        return new ReactionRow(
                row.get("message_id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("emoji", String.class)
        );
    }
}
