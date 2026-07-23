package com.techpulse.techradar.features.user.ports;

import com.techpulse.techradar.features.user.domain.JobMatchSubscriber;
import com.techpulse.techradar.features.user.domain.NotificationRecipient;
import com.techpulse.techradar.features.user.domain.UserProfile;
import com.techpulse.techradar.features.user.domain.UserProfiles;
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
     * Convenience shared by the roadmap feature: the current user's profile technologies,
     * or an empty list if the profile doesn't exist yet.
     */
    default Mono<List<String>> technologiesOf(String userId) {
        return findByUserId(userId)
                .map(UserProfiles::technologiesOrEmpty)
                .defaultIfEmpty(List.of());
    }

    /**
     * Users whose profile lists {@code technology} and who want at least one notification
     * channel. Used by the {@code notification} feature to resolve trend-alert subscribers
     * without querying {@code user_profile} directly.
     */
    Flux<NotificationRecipient> findSubscribersByTechnology(String technology);

    /**
     * Users whose profile {@code technologies} or {@code target_skills} overlap any of
     * {@code technologies}, wanting at least one channel — {@code matchesCurrentSkills}
     * distinguishes which. Used by the {@code notification} feature to resolve job-match
     * subscribers.
     */
    Flux<JobMatchSubscriber> findJobMatchSubscribers(List<String> technologies);

    /**
     * Users with at least one profile technology, wanting at least one channel. Used by the
     * {@code notification} feature as the candidate pool for the weekly roadmap-alert scan.
     */
    Flux<NotificationRecipient> findSubscribersWithAnyTechnology();

    /**
     * Persists the user's current top roadmap recommendations ("skills they're actively learning
     * next", computed by {@code GetCareerRoadmapUseCase}), so {@link #findJobMatchSubscribers}
     * can match new job postings against them too.
     */
    Mono<Long> updateTargetSkills(String userId, List<String> skills);
}
