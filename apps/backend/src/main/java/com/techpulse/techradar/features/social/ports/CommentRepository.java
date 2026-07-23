package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface CommentRepository {

    Mono<Void> insert(UUID commentId, UUID postId, UUID userId, String content, UUID parentCommentId, LocalDateTime createdAt);

    Flux<CommentRow> findByPost(UUID postId, int limit, int offset);

    /** Every comment authored by this user across all posts, newest first — used by the GDPR data-export endpoint. */
    Flux<CommentRow> findByUser(UUID userId);

    /** For validating/threading a reply target. Empty if the parent comment doesn't exist. */
    Mono<ParentInfo> findParentInfo(UUID commentId);

    /** Admin moderation: delete any comment by id. @return true if it existed. */
    Mono<Boolean> deleteById(UUID commentId);

    Mono<Long> countAll();

    record CommentRow(
            UUID id,
            UUID authorId,
            String authorName,
            String authorAvatarUrl,
            String content,
            UUID parentCommentId,
            LocalDateTime createdAt
    ) {
    }

    record ParentInfo(UUID postId, UUID authorId, UUID parentCommentId) {
    }
}
