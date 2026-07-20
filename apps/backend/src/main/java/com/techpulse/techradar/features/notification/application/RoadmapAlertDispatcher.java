package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.auth.ports.EmailSender;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.notification.domain.Notification;
import com.techpulse.techradar.features.notification.event.RoadmapAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Consumes {@code roadmap.alerts} — one event per (user, hot recommended skill) pair, already
 * resolved by {@code RoadmapAlertService}. Mirrors {@link TrendAlertDispatcher}'s dispatch shape
 * (in-app + email), minus the subscriber lookup: the producer already knows exactly who to notify.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoadmapAlertDispatcher {

    private final NotificationService notificationService;
    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConstants.ROADMAP_ALERTS, groupId = "notification-dispatcher")
    public void onRoadmapAlert(ConsumerRecord<String, String> record) {
        try {
            RoadmapAlertEvent event = objectMapper.readValue(record.value(), RoadmapAlertEvent.class);
            String title = "Nên học tiếp: " + event.getTechnology();
            String body = String.format(
                    "%s đang tăng trưởng %.0f%% và phù hợp với lộ trình của bạn. Xem chi tiết trên trang Sự nghiệp.",
                    event.getTechnology(), event.getGrowthRate());

            dispatch(event, title, body).block();
            log.info("Roadmap alert '{}' dispatched to user {}", event.getTechnology(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to dispatch roadmap alert: {}", record.value(), e);
        }
    }

    private Mono<Void> dispatch(RoadmapAlertEvent event, String title, String body) {
        Mono<Void> inApp = event.isNotifyInapp()
                ? notificationService.save(Notification.builder()
                        .userId(UUID.fromString(event.getUserId()))
                        .type("CAREER_ALERT")
                        .title(title)
                        .body(body)
                        .link("/career")
                        .read(false)
                        .build()).then()
                : Mono.empty();

        Mono<Void> email = (event.isNotifyEmail() && event.getEmail() != null && !event.getEmail().isBlank())
                ? emailSender.sendNotification(event.getEmail(), title, body)
                        .onErrorResume(e -> Mono.empty())
                : Mono.empty();

        return inApp.then(email);
    }
}
