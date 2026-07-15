package com.techpulse.techradar.features.messaging.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MessageRepository {

    Mono<MessageRow> insert(UUID messageId, UUID conversationId, UUID senderId, String content, LocalDateTime createdAt);

    /** Oldest first. */
    Flux<MessageRow> findByConversation(UUID conversationId, int limit, int offset);

    /** Marks every message in the conversation NOT sent by {@code readerId} as read. */
    Mono<Void> markRead(UUID conversationId, UUID readerId);

    record MessageRow(
            UUID id,
            UUID conversationId,
            UUID senderId,
            String content,
            LocalDateTime createdAt,
            LocalDateTime readAt
    ) {
    }
}
