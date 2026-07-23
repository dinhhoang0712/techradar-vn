package com.techpulse.techradar.features.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.aiproxy.ports.AiProxyPort;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.notification.domain.TrendSubscriber;
import com.techpulse.techradar.features.notification.event.TrendAlertEvent;
import com.techpulse.techradar.features.notification.ports.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Consumes {@code trend.alerts} domain events and fans them out to subscribed users across
 * channels (in-app + email). Producers (the radar ETL) stay decoupled from delivery — this is
 * the value of routing notifications through Kafka. Enriches the notification body with an
 * AI-written narrative (via ai-rag-core's {@code /summarize}, grounded in the actual ingested
 * articles for the technology/period) explaining why the trend is happening, not just the bare
 * stat — falling back to the stat-only body if no articles are found or the call fails/times out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendAlertDispatcher {

    private final NotificationRepository repository;
    private final AlertDeliveryDispatcher alertDeliveryDispatcher;
    private final ObjectMapper objectMapper;
    private final AiProxyPort aiProxyPort;

    /** Kept below AiProxyPort.DEFAULT_TIMEOUT (60s) so a slow/hung call can't block the Kafka
     *  consumer thread for a full minute per alert. */
    @Value("${app.notifications.trend-summarize-timeout-ms:30000}")
    private long summarizeTimeoutMs;

    @KafkaListener(topics = KafkaTopicConstants.TREND_ALERTS, groupId = "notification-dispatcher")
    public void onTrendAlert(ConsumerRecord<String, String> record) {
        try {
            TrendAlertEvent event = objectMapper.readValue(record.value(), TrendAlertEvent.class);
            String title = "Xu hướng tăng: " + event.getTechnology();
            String baseBody = String.format(
                    "%s đang tăng %.0f%% so với tháng trước (nhu cầu tuyển dụng hiện tại: %d vị trí). " +
                    "Xem chi tiết trên trang Radar.",
                    event.getTechnology(), event.getMomRate(), event.getJobCount());
            String body = withNarrative(event, baseBody);

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

    /**
     * Called once per alert (not once per subscriber — {@code /summarize} takes only
     * technology/period, no user context, so the narrative is identical for every subscriber).
     */
    private String withNarrative(TrendAlertEvent event, String baseBody) {
        Map<String, Object> req = Map.of(
                "tech_name", event.getTechnology(), "period", event.getMonth(), "format", "paragraph");
        String narrative = aiProxyPort.forward("/summarize", req, Duration.ofMillis(summarizeTimeoutMs))
                .map(TrendAlertDispatcher::extractNarrative)
                .onErrorResume(e -> {
                    log.warn("TrendAlert: /summarize call failed for {}", event.getTechnology(), e);
                    return Mono.just("");
                })
                .blockOptional()
                .orElse("");
        return narrative.isBlank() ? baseBody : baseBody + " " + narrative;
    }

    /** {@code sources_used == 0} is ai-rag-core's canned "no articles found" response (no LLM call
     *  happened in that path) — treated as "nothing grounded to add". */
    private static String extractNarrative(Map<String, Object> response) {
        Object sourcesUsedObj = response.get("sources_used");
        int sourcesUsed = sourcesUsedObj instanceof Number n ? n.intValue() : 0;
        Object summary = response.get("summary");
        return sourcesUsed > 0 && summary != null ? String.valueOf(summary) : "";
    }

    private Mono<Void> dispatch(TrendSubscriber sub, String title, String body) {
        return alertDeliveryDispatcher.dispatch(sub.userId(), "TREND_ALERT", title, body, "/radar",
                sub.notifyInapp(), sub.notifyEmail(), sub.email());
    }
}
