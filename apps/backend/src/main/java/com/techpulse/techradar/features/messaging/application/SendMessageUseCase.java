package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SendMessageUseCase {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageBroadcaster messageBroadcaster;

    public Mono<DirectMessage> execute(String conversationId, String senderId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return Mono.error(new AppException("Message content must not be empty", 400, "INVALID_CONTENT"));
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            return Mono.error(new AppException("Message too long (max " + MAX_CONTENT_LENGTH + " chars)", 400, "INVALID_CONTENT"));
        }

        UUID convId = UUID.fromString(conversationId);
        UUID senderUuid = UUID.fromString(senderId);

        return conversationRepository.isParticipant(convId, senderUuid)
                .flatMap(isParticipant -> {
                    if (!isParticipant) {
                        return Mono.error(new NotFoundException("Conversation not found: " + conversationId));
                    }
                    return messageRepository.insert(UUID.randomUUID(), convId, senderUuid, trimmed, LocalDateTime.now())
                            .map(MessageMapper::toDomain)
                            .flatMap(message -> conversationRepository.otherParticipant(convId, senderUuid)
                                    .doOnNext(otherId -> messageBroadcaster.publish(otherId.toString(), message))
                                    .thenReturn(message));
                });
    }
}
