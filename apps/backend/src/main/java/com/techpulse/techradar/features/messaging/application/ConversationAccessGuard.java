package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Shared participant check used by every messaging use case before it reads or writes
 * conversation data — centralizes the "is this user actually in this conversation" gate so it
 * can't drift between call sites.
 */
@Component
@RequiredArgsConstructor
public class ConversationAccessGuard {

    private final ConversationRepository conversationRepository;

    public Mono<Void> requireParticipant(UUID conversationId, UUID userId) {
        return conversationRepository.isParticipant(conversationId, userId)
                .flatMap(isParticipant -> isParticipant
                        ? Mono.empty()
                        : Mono.error(new NotFoundException("Conversation not found: " + conversationId)));
    }
}
