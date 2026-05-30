package com.techpulse.techradar.features.auth.adapters.output;

import com.techpulse.techradar.features.auth.domain.User;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PostgreSQL adapter for user repository.
 * Implements the UserRepository port using R2DBC.
 */
@Repository
@RequiredArgsConstructor
public class PostgresUserRepository implements UserRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<User> findByEmail(String email) {
        return dbClient.sql(
                "SELECT id, email, password_hash, role, status, subscription_tier, created_at, updated_at " +
                "FROM users WHERE email = :email"
        )
                .bind("email", email)
                .map(row -> mapRowToUser((Row) row))
                .one();
    }

    @Override
    public Mono<User> findById(String userId) {
        return dbClient.sql(
                "SELECT id, email, password_hash, role, status, subscription_tier, created_at, updated_at " +
                "FROM users WHERE id = :id"
        )
                .bind("id", UUID.fromString(userId))
                .map(row -> mapRowToUser((Row) row))
                .one();
    }

    @Override
    public Mono<User> save(User user) {
        if (user.getId() == null) {
            return insert(user);
        } else {
            return update(user);
        }
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return dbClient.sql("SELECT 1 FROM users WHERE email = :email")
                .bind("email", email)
                .map(row -> true)
                .one()
                .defaultIfEmpty(false);
    }

    private Mono<User> insert(User user) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        user.setId(id);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return dbClient.sql(
                "INSERT INTO users (id, email, password_hash, role, status, subscription_tier, created_at, updated_at) " +
                "VALUES (:id, :email, :password_hash, :role, :status, :subscription_tier, :created_at, :updated_at)"
        )
                .bind("id", id)
                .bind("email", user.getEmail())
                .bind("password_hash", user.getPasswordHash())
                .bind("role", user.getRole())
                .bind("status", user.getStatus() != null ? user.getStatus() : "active")
                .bind("subscription_tier", user.getSubscriptionTier() != null ? user.getSubscriptionTier() : "free")
                .bind("created_at", now)
                .bind("updated_at", now)
                .fetch()
                .rowsUpdated()
                .map(rowsUpdated -> user);
    }

    private Mono<User> update(User user) {
        LocalDateTime now = LocalDateTime.now();
        user.setUpdatedAt(now);

        return dbClient.sql(
                "UPDATE users SET email = :email, password_hash = :password_hash, role = :role, " +
                "status = :status, subscription_tier = :subscription_tier, updated_at = :updated_at " +
                "WHERE id = :id"
        )
                .bind("id", user.getId())
                .bind("email", user.getEmail())
                .bind("password_hash", user.getPasswordHash())
                .bind("role", user.getRole())
                .bind("status", user.getStatus())
                .bind("subscription_tier", user.getSubscriptionTier())
                .bind("updated_at", now)
                .fetch()
                .rowsUpdated()
                .map(rowsUpdated -> user);
    }

    private User mapRowToUser(Row row) {
        return User.builder()
                .id((UUID) row.get("id"))
                .email(row.get("email", String.class))
                .passwordHash(row.get("password_hash", String.class))
                .role(row.get("role", String.class))
                .status(row.get("status", String.class))
                .subscriptionTier(row.get("subscription_tier", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .build();
    }
}
