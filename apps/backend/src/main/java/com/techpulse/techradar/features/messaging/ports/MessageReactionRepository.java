package com.techpulse.techradar.features.messaging.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface MessageReactionRepository {

    /** Sets (or replaces) {@code userId}'s reaction on a message — one reaction per user per message. */
    Mono<Void> upsert(UUID messageId, UUID userId, String emoji);

    Mono<Void> remove(UUID messageId, UUID userId);

    Flux<ReactionRow> findByMessageId(UUID messageId);

    /** Batch fetch for attaching reactions onto a page of message history in one query. */
    Flux<ReactionRow> findByMessageIds(Collection<UUID> messageIds);

    record ReactionRow(UUID messageId, UUID userId, String emoji) {
    }
}
