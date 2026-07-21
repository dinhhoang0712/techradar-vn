package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.event.TrendAlertEvent;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Consumes {@code trend.alerts} domain events and fans them out to subscribed users across
 * channels (in-app + email). Producers (the radar ETL) stay decoupled from delivery — this is
 * the value of routing notifications through Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendAlertDispatcher {

    private final NotificationRepository repository;
    private final AlertDeliveryDispatcher alertDeliveryDispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConstants.TREND_ALERTS, groupId = "notification-dispatcher")
    public void onTrendAlert(ConsumerRecord<String, String> record) {
        try {
            TrendAlertEvent event = objectMapper.readValue(record.value(), TrendAlertEvent.class);
            String title = "Xu hướng tăng: " + event.getTechnology();
            String body = String.format(
                    "%s đang tăng %.0f%% so với tháng trước (nhu cầu tuyển dụng hiện tại: %d vị trí). " +
                    "Xem chi tiết trên trang Radar.",
                    event.getTechnology(), event.getMomRate(), event.getJobCount());

            long delivered = repository.findTrendSubscribers(event.getTechnology())
                    .flatMap(sub -> dispatch(sub, title, body).thenReturn(1))
                    .count()
                    .blockOptional()
                    .orElse(0L);
            log.info("Trend alert '{}' (+{}%) dispatched to {} subscriber(s)",
                    event.getTechnology(), Math.round(event.getMomRate()), delivered);
        } catch (Exception e) {
            log.error("Failed to dispatch trend alert: {}", record.value(), e);
        }
    }

    private Mono<Void> dispatch(TrendSubscriber sub, String title, String body) {
        return alertDeliveryDispatcher.dispatch(sub.userId(), "TREND_ALERT", title, body, "/radar",
                sub.notifyInapp(), sub.notifyEmail(), sub.email());
    }
}
