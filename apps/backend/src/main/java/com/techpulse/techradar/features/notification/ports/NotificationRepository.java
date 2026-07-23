package com.techpulse.techradar.features.notification.ports;

import com.techpulse.techradar.features.notification.domain.JobMatchSubscriber;
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

    /** Unread count filtered to one notification {@code type} (e.g. ADMIN_JOB_REPEATED_FAILURE). */
    Mono<Long> countUnreadByType(String userId, String type);

    /** Users whose profile lists {@code technology} and who want at least one channel. */
    Flux<TrendSubscriber> findTrendSubscribers(String technology);

    /**
     * Users whose profile technologies or target skills overlap any of {@code technologies},
     * wanting at least one channel — {@code matchesCurrentSkills} distinguishes which.
     */
    Flux<JobMatchSubscriber> findJobMatchSubscribers(List<String> technologies);

    /** Users with at least one profile technology, wanting at least one channel — weekly roadmap-alert scan candidates. */
    Flux<TrendSubscriber> findRoadmapCandidates();

    /** Notification counts grouped by {@code type} (e.g. JOB_MATCH, TREND_ALERT), for admin dashboards. */
    Flux<TypeCount> countGroupedByType();

    record TypeCount(String type, long count) {
    }
}
