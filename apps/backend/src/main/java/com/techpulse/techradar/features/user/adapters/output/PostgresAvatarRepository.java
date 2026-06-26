package com.techpulse.techradar.features.user.adapters.output;

import com.techpulse.techradar.features.user.domain.Avatar;
import com.techpulse.techradar.features.user.ports.AvatarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PostgreSQL adapter for {@code user_avatar} (bytes stored as BYTEA).
 */
@Repository
@RequiredArgsConstructor
public class PostgresAvatarRepository implements AvatarRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Void> save(String userId, String contentType, byte[] data) {
        return dbClient.sql(
                "INSERT INTO user_avatar (user_id, content_type, data, updated_at) " +
                "VALUES (:user_id, :content_type, :data, :updated_at) " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "content_type = EXCLUDED.content_type, data = EXCLUDED.data, updated_at = EXCLUDED.updated_at")
                .bind("user_id", UUID.fromString(userId))
                .bind("content_type", contentType)
                .bind("data", data)
                .bind("updated_at", LocalDateTime.now())
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Avatar> find(String userId) {
        return dbClient.sql("SELECT content_type, data FROM user_avatar WHERE user_id = :user_id")
                .bind("user_id", UUID.fromString(userId))
                .map((row, meta) -> new Avatar(row.get("content_type", String.class), row.get("data", byte[].class)))
                .one();
    }
}
