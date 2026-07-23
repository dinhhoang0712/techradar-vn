package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aggregate post/like counts for the admin dashboard — split out of {@link PostRepository} so the
 * feed-facing port isn't also the analytics port; {@code SocialEngagementMetricsService} is the
 * only caller and has no business reading/writing individual posts.
 */
public interface PostAnalyticsRepository {

    Mono<Long> countAll();

    Mono<Long> countCreatedSince(LocalDateTime since);

    Mono<Long> countAllLikes();

    /** Users with the most posts, for an admin "most active" widget. */
    Flux<TopPosterRow> topPosters(int limit);

    record TopPosterRow(
            UUID userId,
            String fullName,
            long postCount
    ) {
    }
}
