package com.techpulse.techradar.features.kafka.adapters.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Producer service for sending JSON payloads to Kafka topics.
 */
@Service
public class KafkaProducerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(String topic, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, message);
            LOGGER.debug("Sent Kafka message to topic {}: {}", topic, message);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize Kafka payload for topic {}", topic, e);
            throw new IllegalStateException("Unable to serialize Kafka payload", e);
        }
    }

    /**
     * Same as {@link #send(String, Object)} but with an explicit message key (e.g. so
     * same-key messages land on the same partition), for producers that need one.
     */
    public void send(String topic, String key, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, message);
            LOGGER.debug("Sent Kafka message to topic {} with key {}: {}", topic, key, message);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize Kafka payload for topic {}", topic, e);
            throw new IllegalStateException("Unable to serialize Kafka payload", e);
        }
    }

    /**
     * Sends an already-serialized JSON payload verbatim (no re-serialization) and completes only
     * once the broker acknowledges the send — unlike {@link #send(String, Object)}, which is
     * fire-and-forget and never observes a broker-side failure. Used by
     * {@code shared.outbox.OutboxRelayScheduler} so a failed publish can be detected and retried
     * instead of silently dropped.
     */
    public Mono<Void> sendRaw(String topic, String rawJsonPayload) {
        return Mono.fromFuture(() -> kafkaTemplate.send(topic, rawJsonPayload)).then();
    }
}
