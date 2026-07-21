package com.techpulse.techradar.features.notification.application;

import com.techpulse.techradar.features.auth.ports.UserRepository;
import com.techpulse.techradar.features.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Shared "someone did something" in-app notification: look up the actor's name, build a
 * Vietnamese "{actor} {titleSuffix}" notification for the target user, and save it.
 * <p>
 * Extracted from {@code ToggleLikeUseCase#notifyLike}, {@code ToggleFollowUseCase#notifyFollow}
 * and {@code MentionNotifier#notifyOne}, which previously duplicated this exact
 * lookup-build-save shape. Deliberately does not swallow errors itself (unlike
 * {@link AlertDeliveryDispatcher}) — callers already wrap their own call site with an
 * {@code onErrorResume} carrying call-site-specific log wording, and this preserves that.
 */
@Component
@RequiredArgsConstructor
public class ActivityNotifier {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public Mono<Void> notify(UUID actorId, UUID targetUserId, String type, String titleSuffix, String link) {
        return userRepository.findById(actorId.toString())
                .flatMap(actor -> notificationService.save(Notification.builder()
                        .userId(targetUserId)
                        .type(type)
                        .title(actor.getFullName() + " " + titleSuffix)
                        .link(link)
                        .read(false)
                        .build()))
                .then();
    }
}
