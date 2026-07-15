package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetMessagesUseCase {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 100;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public Flux<DirectMessage> execute(String conversationId, String viewerId, int page, int size) {
        UUID convId = UUID.fromString(conversationId);
        UUID viewerUuid = UUID.fromString(viewerId);
        int effectiveSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = Math.max(page, 0) * effectiveSize;

        return conversationRepository.isParticipant(convId, viewerUuid)
                .flatMapMany(isParticipant -> isParticipant
                        ? messageRepository.findByConversation(convId, effectiveSize, offset).map(MessageMapper::toDomain)
                        : Flux.error(new NotFoundException("Conversation not found: " + conversationId)));
    }
}
