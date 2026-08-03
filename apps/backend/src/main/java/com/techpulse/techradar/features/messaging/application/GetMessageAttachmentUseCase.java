package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetMessageAttachmentUseCase {

    private final ConversationAccessGuard conversationAccessGuard;
    private final MessageRepository messageRepository;

    public Mono<MessageRepository.AttachmentRow> execute(String conversationId, String messageId, String viewerId) {
        UUID convId = UUID.fromString(conversationId);
        UUID msgId = UUID.fromString(messageId);
        UUID viewerUuid = UUID.fromString(viewerId);

        return conversationAccessGuard.requireParticipant(convId, viewerUuid)
                .then(messageRepository.findById(msgId))
                .filter(row -> row.conversationId().equals(convId))
                .switchIfEmpty(Mono.error(new NotFoundException("Message not found: " + messageId)))
                .then(messageRepository.findAttachmentData(msgId))
                .switchIfEmpty(Mono.error(new NotFoundException("No attachment on message: " + messageId)));
    }
}
