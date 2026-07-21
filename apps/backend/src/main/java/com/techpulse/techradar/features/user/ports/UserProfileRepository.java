package com.techpulse.techradar.features.user.ports;

import com.techpulse.techradar.features.user.domain.NotificationRecipient;
import com.techpulse.techradar.features.user.domain.UserProfile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Output port for the {@code user_profile} table.
 */
public interface UserProfileRepository {

    Mono<UserProfile> findByUserId(String userId);

    Mono<UserProfile> upsert(UserProfile profile);

    /**
     * Users whose profile lists {@code technology} and who want at least one notification
     * channel. Used by the {@code notification} feature to resolve trend-alert subscribers
     * without querying {@code user_profile} directly.
     */
    Flux<NotificationRecipient> findSubscribersByTechnology(String technology);

    /**
     * Users whose profile technologies overlap any of {@code technologies}, wanting at least
     * one channel. Used by the {@code notification} feature to resolve job-match subscribers.
     */
    Flux<NotificationRecipient> findSubscribersByAnyTechnology(List<String> technologies);

    /**
     * Users with at least one profile technology, wanting at least one channel. Used by the
     * {@code notification} feature as the candidate pool for the weekly roadmap-alert scan.
     */
    Flux<NotificationRecipient> findSubscribersWithAnyTechnology();
}
