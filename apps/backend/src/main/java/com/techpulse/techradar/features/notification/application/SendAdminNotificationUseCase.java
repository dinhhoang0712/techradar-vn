package com.techpulse.techradar.features.notification.application;

import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.user.application.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin-triggered notification: targeted at one user, or broadcast to every active user when no
 * user id is given. Reuses {@link NotificationService#save}, so persistence + realtime SSE
 * fan-out (Redis pub/sub) work exactly like the automated JobMatch/TrendAlert dispatchers — no
 * new delivery plumbing needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendAdminNotificationUseCase {

    private static final String TYPE = "ADMIN_ANNOUNCEMENT";

    private final NotificationService notificationService;
    private final AdminUserService userService;

    public Mono<Long> execute(String title, String body, String link, String targetUserId) {
        if (title == null || title.isBlank()) {
            return Mono.error(new IllegalArgumentException("title is required"));
        }
        if (body == null || body.isBlank()) {
            return Mono.error(new IllegalArgumentException("body is required"));
        }

        if (targetUserId != null && !targetUserId.isBlank()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(targetUserId);
            } catch (IllegalArgumentException ex) {
                return Mono.error(new IllegalArgumentException("userId is not a valid UUID"));
            }
            log.info("Admin sending targeted notification '{}' to user {}", title, uuid);
            return sendTo(uuid, title, body, link).thenReturn(1L);
        }

        return userService.listUsers()
                .filter(u -> "active".equalsIgnoreCase(u.getStatus()))
                .flatMap(u -> sendTo(u.getId(), title, body, link))
                .count()
                .doOnSuccess(count -> log.info("Admin broadcast '{}' sent to {} user(s)", title, count));
    }

    private Mono<Notification> sendTo(UUID userId, String title, String body, String link) {
        return notificationService.save(Notification.builder()
                .userId(userId)
                .type(TYPE)
                .title(title)
                .body(body)
                .link(link)
                .read(false)
                .build());
    }
}
