package com.techpulse.techradar.features.notification.ports;

import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Persistence port for notifications and trend-alert subscriber lookups.
 */
public interface NotificationRepository {

    Mono<Notification> insert(Notification notification);

    Flux<Notification> findByUser(String userId, int limit);

    Mono<Long> markRead(String id, String userId);

    Mono<Long> markAllRead(String userId);

    Mono<Long> countUnread(String userId);

    /** Users whose profile lists {@code technology} and who want at least one channel. */
    Flux<TrendSubscriber> findTrendSubscribers(String technology);
}
