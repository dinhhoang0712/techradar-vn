package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MarkReadUseCase {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public Mono<Void> execute(String conversationId, String readerId) {
        UUID convId = UUID.fromString(conversationId);
        UUID readerUuid = UUID.fromString(readerId);

        return conversationRepository.isParticipant(convId, readerUuid)
                .flatMap(isParticipant -> isParticipant
                        ? messageRepository.markRead(convId, readerUuid)
                        : Mono.error(new NotFoundException("Conversation not found: " + conversationId)));
    }
}
