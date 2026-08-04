package com.techpulse.techradar.shared.outbox;

import com.techpulse.techradar.features.kafka.adapters.output.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Polls {@code outbox_event} for rows a business write queued (in the same transaction) and
 * publishes them to Kafka, marking each {@code PUBLISHED} on success or {@code FAILED} (with the
 * attempt counted) on error. Enabled by default — unlike the heavier optional jobs
 * ({@code AnalyticsScheduler}, {@code RoadmapAlertScheduler}), an outbox with no relay running
 * would accumulate rows that are never delivered, defeating the entire pattern. See
 * {@code docs/adr/0005-transactional-outbox-trend-alerts.md}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducer;

    @Value("${app.outbox.relay.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.outbox.relay.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.relay.interval-ms:10000}")
    public void relay() {
        outboxEventRepository.findReadyToPublish(maxAttempts, batchSize)
                .concatMap(this::publishOne)
                .subscribe(
                        v -> { },
                        err -> log.error("Outbox relay poll failed", err));
    }

    private Mono<Void> publishOne(OutboxEvent event) {
        return kafkaProducer.sendRaw(event.getTopic(), event.getPayload())
                .then(Mono.defer(() -> outboxEventRepository.markPublished(event.getId())))
                .doOnSuccess(v -> log.debug("Outbox relay published event {} to topic {}", event.getId(), event.getTopic()))
                .onErrorResume(e -> {
                    log.warn("Outbox relay: failed to publish event {} to topic {} (attempt {})",
                            event.getId(), event.getTopic(), event.getAttempts() + 1, e);
                    return outboxEventRepository.markFailed(event.getId(), e.getMessage());
                });
    }
}
