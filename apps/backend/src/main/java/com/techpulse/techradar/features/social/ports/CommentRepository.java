package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface CommentRepository {

    Mono<Void> insert(UUID commentId, UUID postId, UUID userId, String content, LocalDateTime createdAt);

    Flux<CommentRow> findByPost(UUID postId, int limit, int offset);

    record CommentRow(
            UUID id,
            UUID authorId,
            String authorName,
            String authorAvatarUrl,
            String content,
            LocalDateTime createdAt
    ) {
    }
}
