package com.techpulse.techradar.shared.outbox;

import com.techpulse.techradar.features.kafka.adapters.output.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 50;

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaProducerService kafkaProducer;

    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxRelayScheduler(outboxEventRepository, kafkaProducer);
        ReflectionTestUtils.setField(scheduler, "maxAttempts", MAX_ATTEMPTS);
        ReflectionTestUtils.setField(scheduler, "batchSize", BATCH_SIZE);
    }

    private static OutboxEvent event(UUID id, int attempts) {
        return event(id, attempts, "trend.alerts");
    }

    private static OutboxEvent event(UUID id, int attempts, String topic) {
        return OutboxEvent.builder()
                .id(id)
                .topic(topic)
                .payload("{\"technology\":\"Go\"}")
                .status(OutboxStatus.PENDING)
                .attempts(attempts)
                .build();
    }

    @Test
    void relay_publishesEachReadyRow_andMarksItPublished() {
        OutboxEvent event = event(UUID.randomUUID(), 0);
        when(outboxEventRepository.findReadyToPublish(MAX_ATTEMPTS, BATCH_SIZE)).thenReturn(Flux.just(event));
        when(kafkaProducer.sendRaw(event.getTopic(), event.getPayload())).thenReturn(Mono.empty());
        when(outboxEventRepository.markPublished(event.getId())).thenReturn(Mono.empty());

        scheduler.relay();

        verify(kafkaProducer).sendRaw(event.getTopic(), event.getPayload());
        verify(outboxEventRepository).markPublished(event.getId());
    }

    @Test
    void relay_marksFailed_withAttemptCountedAndErrorMessage_whenPublishFails() {
        OutboxEvent event = event(UUID.randomUUID(), 1);
        when(outboxEventRepository.findReadyToPublish(MAX_ATTEMPTS, BATCH_SIZE)).thenReturn(Flux.just(event));
        when(kafkaProducer.sendRaw(event.getTopic(), event.getPayload()))
                .thenReturn(Mono.error(new RuntimeException("broker unreachable")));
        when(outboxEventRepository.markFailed(eq(event.getId()), eq("broker unreachable"))).thenReturn(Mono.empty());

        scheduler.relay();

        verify(outboxEventRepository).markFailed(event.getId(), "broker unreachable");
    }

    @Test
    void relay_publishesEveryRowInBatch_evenWhenOneFails() {
        // Distinct topics so kafkaProducer.sendRaw(...)'s per-event stub can't collide — same
        // topic+payload for both would make Mockito's last-registered stub answer for both calls,
        // silently turning "one fails" into "both succeed" regardless of what's stubbed below.
        OutboxEvent ok = event(UUID.randomUUID(), 0, "trend.alerts.ok");
        OutboxEvent broken = event(UUID.randomUUID(), 0, "trend.alerts.broken");
        when(outboxEventRepository.findReadyToPublish(MAX_ATTEMPTS, BATCH_SIZE)).thenReturn(Flux.just(broken, ok));
        when(kafkaProducer.sendRaw(broken.getTopic(), broken.getPayload()))
                .thenReturn(Mono.error(new RuntimeException("boom")));
        when(kafkaProducer.sendRaw(ok.getTopic(), ok.getPayload())).thenReturn(Mono.empty());
        when(outboxEventRepository.markFailed(eq(broken.getId()), eq("boom"))).thenReturn(Mono.empty());
        when(outboxEventRepository.markPublished(ok.getId())).thenReturn(Mono.empty());

        scheduler.relay();

        verify(outboxEventRepository).markFailed(broken.getId(), "boom");
        verify(outboxEventRepository).markPublished(ok.getId());
    }
}
