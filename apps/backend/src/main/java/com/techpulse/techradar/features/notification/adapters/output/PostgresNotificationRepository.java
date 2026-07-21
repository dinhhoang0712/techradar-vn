package com.techpulse.techradar.features.notification.adapters.output;

import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import com.techpulse.techradar.features.user.ports.UserProfileRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL adapter for the {@code notification} table. Subscriber lookups (trend/job-match/
 * roadmap alerts) delegate to {@link UserProfileRepository} instead of querying
 * {@code user_profile} directly — that table belongs to the {@code user} feature, so notification
 * should only see it through that feature's own port.
 */
@Repository
@RequiredArgsConstructor
public class PostgresNotificationRepository implements NotificationRepository {

    private final DatabaseClient dbClient;
    private final UserProfileRepository userProfileRepository;

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
    public Flux<Notification> findByUser(String userId, int limit, int offset) {
        return dbClient.sql(
                "SELECT id, user_id, type, title, body, link, is_read, created_at " +
                "FROM notification WHERE user_id = :user_id ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
        )
                .bind("user_id", UUID.fromString(userId))
                .bind("limit", limit)
                .bind("offset", offset)
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
        return userProfileRepository.findSubscribersByTechnology(technology)
                .map(r -> new TrendSubscriber(r.userId(), r.email(), r.notifyInapp(), r.notifyEmail()));
    }

    @Override
    public Flux<TrendSubscriber> findJobMatchSubscribers(List<String> technologies) {
        return userProfileRepository.findSubscribersByAnyTechnology(technologies)
                .map(r -> new TrendSubscriber(r.userId(), r.email(), r.notifyInapp(), r.notifyEmail()));
    }

    @Override
    public Flux<TrendSubscriber> findRoadmapCandidates() {
        return userProfileRepository.findSubscribersWithAnyTechnology()
                .map(r -> new TrendSubscriber(r.userId(), r.email(), r.notifyInapp(), r.notifyEmail()));
    }

    @Override
    public Flux<TypeCount> countGroupedByType() {
        return dbClient.sql("SELECT type, count(*) AS c FROM notification GROUP BY type ORDER BY c DESC")
                .map((row, meta) -> new TypeCount(row.get("type", String.class), row.get("c", Long.class)))
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
