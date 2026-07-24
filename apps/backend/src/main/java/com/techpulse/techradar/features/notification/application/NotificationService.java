package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import com.techpulse.techradar.shared.redis.RedisFanout;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Application service for notifications: CRUD-ish reads + an in-process realtime stream.
 * <p>
 * Realtime delivery: {@link #save} publishes the notification over Redis Pub/Sub (channel
 * {@value #CHANNEL}) instead of writing the local sink directly, so every backend instance — not
 * just the one that handled the save — feeds its own local {@link Sinks.Many} and can deliver to
 * whichever SSE clients ({@link #streamFor}) are connected to it. Fire-and-forget by design, same
 * as the in-memory sink this replaced: Postgres remains the source of truth, a missed live push
 * just means the client sees it on its next {@code GET /notifications} instead of instantly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String CHANNEL = "live:notifications";

    private final NotificationRepository repository;
    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    // autoCancel=false: if every connected client disconnects momentarily, the default
    // autoCancel=true would terminate this sink and silently refuse every subscriber for the
    // rest of the process's lifetime.
    private final Sinks.Many<Notification> sink = Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    private record NotificationEvent(
            UUID id, UUID userId, String type, String title, String body, String link, boolean read,
            LocalDateTime createdAt
    ) {
        static NotificationEvent from(Notification n) {
            return new NotificationEvent(n.getId(), n.getUserId(), n.getType(), n.getTitle(), n.getBody(),
                    n.getLink(), n.isRead(), n.getCreatedAt());
        }

        Notification toNotification() {
            return Notification.builder()
                    .id(id).userId(userId).type(type).title(title).body(body).link(link)
                    .read(read).createdAt(createdAt).build();
        }
    }

    @PostConstruct
    void subscribeToRedis() {
        RedisFanout.subscribe(redisListenerContainer, objectMapper, CHANNEL, NotificationEvent.class,
                event -> sink.tryEmitNext(event.toNotification()));
    }

    public Flux<Notification> list(String userId, int limit, int offset) {
        return repository.findByUser(userId, limit, offset);
    }

    public Mono<Long> unreadCount(String userId) {
        return repository.countUnread(userId);
    }

    /** {@code type == null} behaves exactly like {@link #unreadCount(String)} (no filter). */
    public Mono<Long> unreadCount(String userId, String type) {
        return type == null ? repository.countUnread(userId) : repository.countUnreadByType(userId, type);
    }

    public Mono<Void> markRead(String id, String userId) {
        return repository.markRead(id, userId).then();
    }

    public Mono<Void> markAllRead(String userId) {
        return repository.markAllRead(userId).then();
    }

    /** Persist a notification and broadcast it (via Redis) to any live SSE subscriber for that user. */
    public Mono<Notification> save(Notification notification) {
        return repository.insert(notification).doOnNext(this::publishLive);
    }

    /** Persists + broadcasts the same notification to every admin user (fire-and-forget). */
    public Mono<Void> notifyAllAdmins(String type, String title, String body, String link) {
        return userRepository.findAdmins()
                .flatMap(admin -> save(Notification.builder()
                        .userId(admin.getId())
                        .type(type)
                        .title(title)
                        .body(body)
                        .link(link)
                        .read(false)
                        .build()))
                .then();
    }

    public Flux<Notification> streamFor(String userId) {
        UUID uid = UUID.fromString(userId);
        return sink.asFlux().filter(n -> uid.equals(n.getUserId()));
    }

    private void publishLive(Notification notification) {
        RedisFanout.publish(redisTemplate, objectMapper, CHANNEL, NotificationEvent.from(notification));
    }
}
