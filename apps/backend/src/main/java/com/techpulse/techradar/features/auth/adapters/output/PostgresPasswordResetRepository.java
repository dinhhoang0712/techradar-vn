package com.techpulse.techradar.features.auth.adapters.output;

import com.techpulse.techradar.features.auth.ports.PasswordResetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PostgreSQL adapter for {@code password_reset}.
 */
@Repository
@RequiredArgsConstructor
public class PostgresPasswordResetRepository implements PasswordResetRepository {

    private static final long TTL_MINUTES = 60;

    private final DatabaseClient dbClient;

    @Override
    public Mono<UUID> createToken(String userId) {
        UUID token = UUID.randomUUID();
        return dbClient.sql(
                "INSERT INTO password_reset (token, user_id, expires_at) VALUES (:token, :user_id, :expires_at)")
                .bind("token", token)
                .bind("user_id", UUID.fromString(userId))
                .bind("expires_at", LocalDateTime.now().plusMinutes(TTL_MINUTES))
                .fetch().rowsUpdated()
                .thenReturn(token);
    }

    @Override
    public Mono<String> findValidUserId(String token) {
        return dbClient.sql(
                "SELECT user_id FROM password_reset " +
                "WHERE token = :token AND used = false AND expires_at > now()")
                .bind("token", UUID.fromString(token))
                .map((row, meta) -> row.get("user_id", UUID.class).toString())
                .one();
    }

    @Override
    public Mono<Void> markUsed(String token) {
        return dbClient.sql("UPDATE password_reset SET used = true WHERE token = :token")
                .bind("token", UUID.fromString(token))
                .fetch().rowsUpdated().then();
    }
}
