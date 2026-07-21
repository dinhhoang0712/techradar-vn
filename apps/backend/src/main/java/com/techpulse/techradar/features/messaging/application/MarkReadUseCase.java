package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MarkReadUseCase {

    private final ConversationAccessGuard conversationAccessGuard;
    private final MessageRepository messageRepository;

    public Mono<Void> execute(String conversationId, String readerId) {
        UUID convId = UUID.fromString(conversationId);
        UUID readerUuid = UUID.fromString(readerId);

        return conversationAccessGuard.requireParticipant(convId, readerUuid)
                .then(messageRepository.markRead(convId, readerUuid));
    }
}
