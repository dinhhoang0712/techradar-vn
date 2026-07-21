package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendMessageUseCase {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final ConversationAccessGuard conversationAccessGuard;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageBroadcaster messageBroadcaster;
    private final NewMessageNotifier newMessageNotifier;

    public Mono<DirectMessage> execute(String conversationId, String senderId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return Mono.error(new BadRequestException(ErrorCode.INVALID_CONTENT, "Message content must not be empty"));
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            return Mono.error(new BadRequestException(ErrorCode.INVALID_CONTENT, "Message too long (max " + MAX_CONTENT_LENGTH + " chars)"));
        }

        UUID convId = UUID.fromString(conversationId);
        UUID senderUuid = UUID.fromString(senderId);

        return conversationAccessGuard.requireParticipant(convId, senderUuid)
                .then(messageRepository.insert(UUID.randomUUID(), convId, senderUuid, trimmed, LocalDateTime.now())
                        .map(MessageMapper::toDomain)
                        .flatMap(message -> conversationRepository.otherParticipant(convId, senderUuid)
                                .doOnNext(otherId -> messageBroadcaster.publish(otherId.toString(), message))
                                .flatMap(otherId -> newMessageNotifier.notify(convId, otherId, senderUuid, trimmed)
                                        .onErrorResume(e -> {
                                            log.warn("Could not create NEW_MESSAGE notification for conversation {}", conversationId, e);
                                            return Mono.empty();
                                        }))
                                .thenReturn(message)));
    }
}
