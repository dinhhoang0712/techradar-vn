package com.techpulse.techradar.features.messaging.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MessageRepository {

    Mono<MessageRow> insert(UUID messageId, UUID conversationId, UUID senderId, String content, LocalDateTime createdAt,
                             AttachmentInput attachment);

    /** Oldest first. Never selects the attachment bytes — only its metadata columns. */
    Flux<MessageRow> findByConversation(UUID conversationId, int limit, int offset);

    /** A single message's metadata (no attachment bytes) — used to verify it belongs to a conversation. */
    Mono<MessageRow> findById(UUID messageId);

    /** The attachment bytes for a message, or empty if it has none. */
    Mono<AttachmentRow> findAttachmentData(UUID messageId);

    /** Marks every message in the conversation NOT sent by {@code readerId} as read. */
    Mono<Void> markRead(UUID conversationId, UUID readerId);

    record MessageRow(
            UUID id,
            UUID conversationId,
            UUID senderId,
            String content,
            LocalDateTime createdAt,
            LocalDateTime readAt,
            String attachmentContentType,
            String attachmentFilename,
            Integer attachmentSize
    ) {
    }

    /** Attachment payload to persist alongside a new message; {@code null} if the message has none. */
    record AttachmentInput(String contentType, String filename, int size, byte[] data) {
    }

    /** Attachment bytes fetched back out for the serve endpoint. */
    record AttachmentRow(String contentType, String filename, byte[] data) {
    }
}
