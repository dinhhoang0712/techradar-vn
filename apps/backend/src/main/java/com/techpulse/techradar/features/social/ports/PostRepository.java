package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface PostRepository {

    Mono<Void> insert(UUID postId, UUID userId, String content, LocalDateTime createdAt);

    /** @return true if a post with this id owned by this user was deleted. */
    Mono<Boolean> deleteOwnedBy(UUID postId, UUID userId);

    /** Posts by {@code viewerId} and everyone they follow, newest first. */
    Flux<FeedRow> findFeed(UUID viewerId, int limit, int offset);

    /** A single user's own posts (their profile feed), as seen by {@code viewerId}. */
    Flux<FeedRow> findByUser(UUID targetUserId, UUID viewerId, int limit, int offset);

    Mono<Long> countByUser(UUID userId);

    Mono<Void> like(UUID postId, UUID userId);

    Mono<Void> unlike(UUID postId, UUID userId);

    record FeedRow(
            UUID id,
            UUID authorId,
            String authorName,
            String authorAvatarUrl,
            String content,
            LocalDateTime createdAt,
            long likeCount,
            long commentCount,
            boolean likedByMe
    ) {
    }
}
