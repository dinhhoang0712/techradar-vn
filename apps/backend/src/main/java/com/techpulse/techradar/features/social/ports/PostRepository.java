package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PostRepository {

    Mono<Void> insert(NewPost post);

    /** @return true if a post with this id owned by this user was deleted. */
    Mono<Boolean> deleteOwnedBy(UUID postId, UUID userId);

    /** Posts by {@code viewerId} and everyone they follow, newest first. */
    Flux<FeedRow> findFeed(UUID viewerId, String hashtagFilter, int limit, int offset);

    /** Every post regardless of author/followers ("explore"), newest first. */
    Flux<FeedRow> findExplore(UUID viewerId, String hashtagFilter, int limit, int offset);

    /** A single user's own posts (their profile feed), as seen by {@code viewerId}. */
    Flux<FeedRow> findByUser(UUID targetUserId, UUID viewerId, int limit, int offset);

    Mono<Long> countByUser(UUID userId);

    /** For notifying the author on comment/like; empty if the post doesn't exist. */
    Mono<UUID> findAuthorId(UUID postId);

    /** @return true if this is a newly-recorded like (false if the user had already liked it). */
    Mono<Boolean> like(UUID postId, UUID userId);

    Mono<Void> unlike(UUID postId, UUID userId);

    /** Admin moderation: every post regardless of author/followers, newest first. */
    Flux<FeedRow> findAllForModeration(int limit, int offset);

    /** Admin moderation: delete any post by id, bypassing ownership. @return true if it existed. */
    Mono<Boolean> deleteById(UUID postId);

    Mono<Long> countAll();

    Mono<Long> countCreatedSince(LocalDateTime since);

    Mono<Long> countAllLikes();

    /** Users with the most posts, for an admin "most active" widget. */
    Flux<TopPosterRow> topPosters(int limit);

    record NewPost(
            UUID id,
            UUID userId,
            String content,
            List<String> hashtags,
            String taggedCompanyId,
            String taggedCompanyName,
            String taggedCompanyLocation,
            LocalDateTime createdAt
    ) {
    }

    record FeedRow(
            UUID id,
            UUID authorId,
            String authorName,
            String authorAvatarUrl,
            String content,
            LocalDateTime createdAt,
            long likeCount,
            long commentCount,
            boolean likedByMe,
            List<UUID> imageIds,
            List<String> hashtags,
            String taggedCompanyId,
            String taggedCompanyName,
            String taggedCompanyLocation
    ) {
    }

    record TopPosterRow(
            UUID userId,
            String fullName,
            long postCount
    ) {
    }
}
