package com.techpulse.techradar.features.notification.adapters.output;

import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * PostgreSQL adapter for the {@code notification} table and trend-alert subscriber lookups.
 */
@Repository
@RequiredArgsConstructor
public class PostgresNotificationRepository implements NotificationRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Notification> insert(Notification n) {
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO notification (user_id, type, title, body, link) " +
                "VALUES (:user_id, :type, :title, :body, :link) " +
                "RETURNING id, created_at"
        )
                .bind("user_id", n.getUserId())
                .bind("type", n.getType())
                .bind("title", n.getTitle());
        spec = n.getBody() != null ? spec.bind("body", n.getBody()) : spec.bindNull("body", String.class);
        spec = n.getLink() != null ? spec.bind("link", n.getLink()) : spec.bindNull("link", String.class);
        return spec.map((row, meta) -> {
            n.setId(row.get("id", UUID.class));
            n.setCreatedAt(row.get("created_at", java.time.LocalDateTime.class));
            return n;
        }).one();
    }

    @Override
    public Flux<Notification> findByUser(String userId, int limit) {
        return dbClient.sql(
                "SELECT id, user_id, type, title, body, link, is_read, created_at " +
                "FROM notification WHERE user_id = :user_id ORDER BY created_at DESC LIMIT :limit"
        )
                .bind("user_id", UUID.fromString(userId))
                .bind("limit", limit)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<Long> markRead(String id, String userId) {
        return dbClient.sql("UPDATE notification SET is_read = true WHERE id = :id AND user_id = :user_id")
                .bind("id", UUID.fromString(id))
                .bind("user_id", UUID.fromString(userId))
                .fetch().rowsUpdated();
    }

    @Override
    public Mono<Long> markAllRead(String userId) {
        return dbClient.sql("UPDATE notification SET is_read = true WHERE user_id = :user_id AND is_read = false")
                .bind("user_id", UUID.fromString(userId))
                .fetch().rowsUpdated();
    }

    @Override
    public Mono<Long> countUnread(String userId) {
        return dbClient.sql("SELECT count(*) FROM notification WHERE user_id = :user_id AND is_read = false")
                .bind("user_id", UUID.fromString(userId))
                .map((row, meta) -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<TrendSubscriber> findTrendSubscribers(String technology) {
        return dbClient.sql(
                "SELECT u.id AS user_id, u.email AS email, p.notify_inapp, p.notify_email " +
                "FROM user_profile p JOIN users u ON u.id = p.user_id " +
                "WHERE :tech = ANY(p.technologies) AND (p.notify_inapp = true OR p.notify_email = true)"
        )
                .bind("tech", technology)
                .map((row, meta) -> new TrendSubscriber(
                        row.get("user_id", UUID.class),
                        row.get("email", String.class),
                        Boolean.TRUE.equals(row.get("notify_inapp", Boolean.class)),
                        Boolean.TRUE.equals(row.get("notify_email", Boolean.class))))
                .all();
    }

    private Notification mapRow(Row row) {
        return Notification.builder()
                .id(row.get("id", UUID.class))
                .userId(row.get("user_id", UUID.class))
                .type(row.get("type", String.class))
                .title(row.get("title", String.class))
                .body(row.get("body", String.class))
                .link(row.get("link", String.class))
                .read(Boolean.TRUE.equals(row.get("is_read", Boolean.class)))
                .createdAt(row.get("created_at", java.time.LocalDateTime.class))
                .build();
    }
}
