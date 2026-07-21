package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
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

    private final AlertDeliveryDispatcher alertDeliveryDispatcher;
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
        return alertDeliveryDispatcher.dispatch(UUID.fromString(event.getUserId()), "CAREER_ALERT", title, body,
                "/career", event.isNotifyInapp(), event.isNotifyEmail(), event.getEmail());
    }
}
