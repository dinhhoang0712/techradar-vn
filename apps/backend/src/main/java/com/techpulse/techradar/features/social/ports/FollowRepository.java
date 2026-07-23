package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Follow-relationship mutation and counts. Profile lookup/search (basic info, suggestions,
 * @mention search) lives in {@link UserDirectoryRepository} instead — see its Javadoc for why.
 */
public interface FollowRepository {

    /** @return true if this is a newly-recorded follow (false if already following). */
    Mono<Boolean> follow(UUID followerId, UUID followeeId);

    Mono<Void> unfollow(UUID followerId, UUID followeeId);

    Mono<Boolean> isFollowing(UUID followerId, UUID followeeId);

    Mono<Long> followerCount(UUID userId);

    Mono<Long> followingCount(UUID userId);

    Mono<Long> countAll();
}
