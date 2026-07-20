package com.techpulse.techradar.features.notification.application;

import com.techpulse.techradar.features.auth.ports.EmailSender;
import com.techpulse.techradar.features.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Shared in-app + email delivery for the alert dispatchers (trend/job-match/roadmap alerts):
 * saves an in-app {@link Notification} (if requested) and best-effort emails it (if requested and
 * an address is present), swallowing email failures so a broken mail provider never fails the
 * whole dispatch.
 * <p>
 * Extracted from {@link TrendAlertDispatcher}, which previously duplicated this exact two-branch
 * Mono inline (as do {@code JobMatchDispatcher} and {@code RoadmapAlertDispatcher} — migrating
 * those to call this helper too is a follow-up).
 */
@Component
@RequiredArgsConstructor
public class AlertDeliveryDispatcher {

    private final NotificationService notificationService;
    private final EmailSender emailSender;

    public Mono<Void> dispatch(UUID userId, String type, String title, String body, String link,
                                boolean notifyInApp, boolean notifyEmail, String email) {
        Mono<Void> inApp = notifyInApp
                ? notificationService.save(Notification.builder()
                        .userId(userId)
                        .type(type)
                        .title(title)
                        .body(body)
                        .link(link)
                        .read(false)
                        .build()).then()
                : Mono.empty();

        Mono<Void> emailDelivery = (notifyEmail && email != null && !email.isBlank())
                ? emailSender.sendNotification(email, title, body)
                        .onErrorResume(e -> Mono.empty())
                : Mono.empty();

        return inApp.then(emailDelivery);
    }
}
