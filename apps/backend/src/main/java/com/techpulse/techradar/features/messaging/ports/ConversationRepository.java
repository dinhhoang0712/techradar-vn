package com.techpulse.techradar.features.messaging.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConversationRepository {

    /** Gets the 1-1 conversation between the two users, creating it if it doesn't exist yet. */
    Mono<UUID> findOrCreate(UUID userX, UUID userY);

    Mono<Boolean> isParticipant(UUID conversationId, UUID userId);

    /** The other participant in a conversation (who to push a real-time event to). */
    Mono<UUID> otherParticipant(UUID conversationId, UUID userId);

    /** A user's conversations, each with its last message + unread count, most recent first. */
    Flux<ConversationRow> findAllForUser(UUID userId, int limit, int offset);

    record ConversationRow(
            UUID id,
            UUID otherUserId,
            String otherUserName,
            String otherUserAvatarUrl,
            String lastMessageContent,
            java.time.LocalDateTime lastMessageAt,
            UUID lastMessageSenderId,
            long unreadCount
    ) {
    }
}
