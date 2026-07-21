package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.application.NotificationService;
import com.techpulse.techradar.features.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Builds and persists the "new message" notification for the recipient of a direct message —
 * looks up the sender's display name and assembles the Vietnamese title/preview, kept separate
 * from {@link SendMessageUseCase} so that use case only orchestrates the send itself.
 */
@Component
@RequiredArgsConstructor
public class NewMessageNotifier {

    private static final int NOTIFICATION_PREVIEW_LENGTH = 140;

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public Mono<Void> notify(UUID conversationId, UUID recipientId, UUID senderId, String content) {
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
