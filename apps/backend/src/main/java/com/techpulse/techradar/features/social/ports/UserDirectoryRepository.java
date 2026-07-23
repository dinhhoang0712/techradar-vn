package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Profile lookup/search over users, split out of {@link FollowRepository} so consumers that only
 * need to look up or search profiles (the @mention picker, "who to follow" widget, a profile
 * page's basic info) don't have to depend on the follow-relationship mutation/count methods they
 * never call, and vice versa.
 */
public interface UserDirectoryRepository {

    /** Basic public info (name/avatar/bio/job_role/location) for a profile page. */
    Mono<ProfileBasics> findProfileBasics(UUID userId);

    /** Users {@code viewerId} doesn't already follow, for a "who to follow" widget. */
    Flux<UserSummaryRow> suggested(UUID viewerId, int limit);

    /** Users whose full name contains {@code pattern} (case-insensitive), for an @mention picker. */
    Flux<UserSummaryRow> searchByName(UUID viewerId, String pattern, int limit);

    record ProfileBasics(String fullName, String avatarUrl, String bio, String jobRole, String location) {
    }

    record UserSummaryRow(UUID id, String fullName, String avatarUrl) {
    }
}
