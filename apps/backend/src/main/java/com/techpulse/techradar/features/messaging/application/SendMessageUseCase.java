package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.messaging.domain.DirectMessage;
import com.techpulse.techradar.features.messaging.ports.ConversationRepository;
import com.techpulse.techradar.features.messaging.ports.MessageRepository;
import com.techpulse.techradar.features.messaging.realtime.MessageBroadcaster;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
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
    private static final int NOTIFICATION_PREVIEW_LENGTH = 140;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageBroadcaster messageBroadcaster;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

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

        return conversationRepository.isParticipant(convId, senderUuid)
                .flatMap(isParticipant -> {
                    if (!isParticipant) {
                        return Mono.error(new NotFoundException("Conversation not found: " + conversationId));
                    }
                    return messageRepository.insert(UUID.randomUUID(), convId, senderUuid, trimmed, LocalDateTime.now())
                            .map(MessageMapper::toDomain)
                            .flatMap(message -> conversationRepository.otherParticipant(convId, senderUuid)
                                    .doOnNext(otherId -> messageBroadcaster.publish(otherId.toString(), message))
                                    .flatMap(otherId -> notifyNewMessage(conversationId, otherId, senderUuid, trimmed)
                                            .onErrorResume(e -> {
                                                log.warn("Could not create NEW_MESSAGE notification for conversation {}", conversationId, e);
                                                return Mono.empty();
                                            }))
                                    .thenReturn(message));
                });
    }

    private Mono<Void> notifyNewMessage(String conversationId, UUID recipientId, UUID senderId, String content) {
        return userRepository.findById(senderId.toString())
                .flatMap(sender -> notificationService.save(Notification.builder()
                        .userId(recipientId)
                        .type("NEW_MESSAGE")
                        .title("Tin nhắn mới từ " + sender.getFullName())
                        .body(preview(content))
                        .link("/messages?conversation=" + conversationId)
                        .read(false)
                        .build()))
                .then();
    }

    private static String preview(String content) {
        return content.length() > NOTIFICATION_PREVIEW_LENGTH
                ? content.substring(0, NOTIFICATION_PREVIEW_LENGTH) + "…"
                : content;
    }
}
