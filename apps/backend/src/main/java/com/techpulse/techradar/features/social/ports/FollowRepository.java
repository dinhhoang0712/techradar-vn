package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FollowRepository {

    /** @return true if this is a newly-recorded follow (false if already following). */
    Mono<Boolean> follow(UUID followerId, UUID followeeId);

    Mono<Void> unfollow(UUID followerId, UUID followeeId);

    Mono<Boolean> isFollowing(UUID followerId, UUID followeeId);

    Mono<Long> followerCount(UUID userId);

    Mono<Long> followingCount(UUID userId);

    Mono<Long> countAll();

    /** Basic public info (name/avatar/bio/job_role/location) for a profile page. */
    Mono<ProfileBasics> findProfileBasics(UUID userId);

    /** Users {@code viewerId} doesn't already follow, for a "who to follow" widget. */
    Flux<UserSummaryRow> suggested(UUID viewerId, int limit);

    record ProfileBasics(String fullName, String avatarUrl, String bio, String jobRole, String location) {
    }

    record UserSummaryRow(UUID id, String fullName, String avatarUrl) {
    }
}
