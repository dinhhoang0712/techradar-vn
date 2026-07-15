package com.techpulse.techradar.features.notification.ports;

import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Persistence port for notifications and trend-alert subscriber lookups.
 */
public interface NotificationRepository {

    Mono<Notification> insert(Notification notification);

    Flux<Notification> findByUser(String userId, int limit, int offset);

    Mono<Long> markRead(String id, String userId);

    Mono<Long> markAllRead(String userId);

    Mono<Long> countUnread(String userId);

    /** Users whose profile lists {@code technology} and who want at least one channel. */
    Flux<TrendSubscriber> findTrendSubscribers(String technology);

    /** Users whose profile technologies overlap any of {@code technologies}, wanting at least one channel. */
    Flux<TrendSubscriber> findJobMatchSubscribers(List<String> technologies);

    /** Notification counts grouped by {@code type} (e.g. JOB_MATCH, TREND_ALERT), for admin dashboards. */
    Flux<TypeCount> countGroupedByType();

    record TypeCount(String type, long count) {
    }
}
