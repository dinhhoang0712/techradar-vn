package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.List;
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
    private static final RedisSerializationContext.SerializationPair<String> STRING_PAIR =
            RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string());

    private final NotificationRepository repository;
    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final Sinks.Many<Notification> sink = Sinks.many().multicast().onBackpressureBuffer();

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
        redisListenerContainer.receive(List.of(ChannelTopic.of(CHANNEL)), STRING_PAIR, STRING_PAIR)
                .map(ReactiveSubscription.Message::getMessage)
                .flatMap(json -> Mono.fromCallable(() -> objectMapper.readValue(json, NotificationEvent.class))
                        .onErrorResume(e -> {
                            log.warn("Could not parse live notification event from Redis", e);
                            return Mono.empty();
                        }))
                .doOnNext(event -> sink.tryEmitNext(event.toNotification()))
                .subscribe();
    }

    public Flux<Notification> list(String userId, int limit, int offset) {
        return repository.findByUser(userId, limit, offset);
    }

    public Mono<Long> unreadCount(String userId) {
        return repository.countUnread(userId);
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

    public Flux<Notification> streamFor(String userId) {
        UUID uid = UUID.fromString(userId);
        return sink.asFlux().filter(n -> uid.equals(n.getUserId()));
    }

    private void publishLive(Notification notification) {
        try {
            String json = objectMapper.writeValueAsString(NotificationEvent.from(notification));
            redisTemplate.convertAndSend(CHANNEL, json)
                    .doOnError(e -> log.warn("Failed to publish live notification for user {}", notification.getUserId(), e))
                    .subscribe();
        } catch (Exception e) {
            log.warn("Failed to serialize live notification for user {}", notification.getUserId(), e);
        }
    }
}
