package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.shared.paging.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetMessagesUseCase {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 100;

    private final ConversationAccessGuard conversationAccessGuard;
    private final MessageRepository messageRepository;

    public Flux<DirectMessage> execute(String conversationId, String viewerId, int page, int size) {
        UUID convId = UUID.fromString(conversationId);
        UUID viewerUuid = UUID.fromString(viewerId);
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);

        return conversationAccessGuard.requireParticipant(convId, viewerUuid)
                .thenMany(messageRepository.findByConversation(convId, pageRequest.size(), pageRequest.offset())
                        .map(MessageMapper::toDomain));
    }
}
