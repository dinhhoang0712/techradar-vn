package com.techpulse.techradar.features.notification.application;

import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.UUID;

/**
 * Application service for notifications: CRUD-ish reads + an in-process realtime stream.
 * <p>
 * Realtime delivery uses a multicast {@link Sinks.Many}: saved notifications are emitted to the
 * sink and the SSE endpoint filters per user. This reaches SSE clients connected to <em>this</em>
 * instance only — multi-instance fan-out would consume the Kafka topic per instance instead.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;

    private final Sinks.Many<Notification> sink = Sinks.many().multicast().onBackpressureBuffer();

    public Flux<Notification> list(String userId, int limit) {
        return repository.findByUser(userId, limit);
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

    /** Persist a notification and push it to any live SSE subscriber for that user. */
    public Mono<Notification> save(Notification notification) {
        return repository.insert(notification).doOnNext(sink::tryEmitNext);
    }

    public Flux<Notification> streamFor(String userId) {
        UUID uid = UUID.fromString(userId);
        return sink.asFlux().filter(n -> uid.equals(n.getUserId()));
    }
}
