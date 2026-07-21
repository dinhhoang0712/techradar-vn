package com.techpulse.techradar.features.messaging.adapters.output;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessagingStatsRepository;
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
public class PostgresConversationRepository implements ConversationRepository, MessagingStatsRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<UUID> findOrCreate(UUID userX, UUID userY) {
        // Postgres compares uuid values byte-wise (unsigned), which disagrees with
        // java.util.UUID#compareTo (signed long on the MSBs) whenever the two UUIDs' most
        // significant bytes differ in sign bit — ordering by the canonical string form matches
        // Postgres's ordering exactly, satisfying the `CHECK (user_a_id < user_b_id)` constraint.
        boolean xFirst = userX.toString().compareTo(userY.toString()) < 0;
        UUID a = xFirst ? userX : userY;
        UUID b = xFirst ? userY : userX;

        return dbClient.sql(
                "INSERT INTO conversation (id, user_a_id, user_b_id) VALUES (:id, :a, :b) " +
                "ON CONFLICT (user_a_id, user_b_id) DO UPDATE SET user_a_id = conversation.user_a_id " +
                "RETURNING id")
                .bind("id", UUID.randomUUID())
                .bind("a", a)
                .bind("b", b)
                .map((row, meta) -> row.get("id", UUID.class))
                .one();
    }

    @Override
    public Mono<Boolean> isParticipant(UUID conversationId, UUID userId) {
        return dbClient.sql(
                "SELECT EXISTS(SELECT 1 FROM conversation WHERE id = :id AND (user_a_id = :user_id OR user_b_id = :user_id)) AS is_participant")
                .bind("id", conversationId)
                .bind("user_id", userId)
                .map((row, meta) -> Boolean.TRUE.equals(row.get("is_participant", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<UUID> otherParticipant(UUID conversationId, UUID userId) {
        return dbClient.sql(
                "SELECT CASE WHEN user_a_id = :user_id THEN user_b_id ELSE user_a_id END AS other_id " +
                "FROM conversation WHERE id = :id")
                .bind("id", conversationId)
                .bind("user_id", userId)
                .map((row, meta) -> row.get("other_id", UUID.class))
                .one();
    }

    @Override
    public Flux<ConversationRow> findAllForUser(UUID userId, int limit, int offset) {
        return dbClient.sql(
                "SELECT c.id, " +
                "       CASE WHEN c.user_a_id = :user_id THEN c.user_b_id ELSE c.user_a_id END AS other_id, " +
                "       u.full_name AS other_name, up.avatar_url AS other_avatar, " +
                "       lm.content AS last_content, lm.created_at AS last_at, lm.sender_id AS last_sender, " +
                "       (SELECT count(*) FROM direct_message dm2 " +
                "         WHERE dm2.conversation_id = c.id AND dm2.sender_id <> :user_id AND dm2.read_at IS NULL) AS unread_count " +
                "FROM conversation c " +
                "JOIN users u ON u.id = CASE WHEN c.user_a_id = :user_id THEN c.user_b_id ELSE c.user_a_id END " +
                "LEFT JOIN user_profile up ON up.user_id = u.id " +
                "LEFT JOIN LATERAL ( " +
                "    SELECT content, created_at, sender_id FROM direct_message dm " +
                "    WHERE dm.conversation_id = c.id ORDER BY dm.created_at DESC LIMIT 1 " +
                ") lm ON true " +
                "WHERE c.user_a_id = :user_id OR c.user_b_id = :user_id " +
                "ORDER BY lm.created_at DESC NULLS LAST " +
                "LIMIT :limit OFFSET :offset")
                .bind("user_id", userId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<Long> countConversations() {
        return dbClient.sql("SELECT count(*) AS c FROM conversation")
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Long> countMessages() {
        return dbClient.sql("SELECT count(*) AS c FROM direct_message")
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Long> countMessagesSince(LocalDateTime since) {
        return dbClient.sql("SELECT count(*) AS c FROM direct_message WHERE created_at >= :since")
                .bind("since", since)
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private static ConversationRow mapRow(Row row) {
        return new ConversationRow(
                row.get("id", UUID.class),
                row.get("other_id", UUID.class),
                row.get("other_name", String.class),
                row.get("other_avatar", String.class),
                row.get("last_content", String.class),
                row.get("last_at", LocalDateTime.class),
                row.get("last_sender", UUID.class),
                row.get("unread_count", Long.class)
        );
    }
}
