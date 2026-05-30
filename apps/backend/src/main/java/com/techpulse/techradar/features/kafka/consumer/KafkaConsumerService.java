package com.techpulse.techradar.features.kafka.consumer;

import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumer service for reading messages from Kafka topics.
 *
 * The payload is currently handled as raw JSON text and can be
 * deserialized into domain models when the ingestion flow is fully migrated.
 */
@Service
public class KafkaConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerService.class);

    @KafkaListener(topics = KafkaTopicConstants.RAW_ARTICLES, groupId = "${spring.kafka.consumer.group-id}")
    public void consumeRawArticles(ConsumerRecord<String, String> record) {
        LOGGER.info("Received raw article from Kafka [{}]: {}", record.topic(), record.value());
        // TODO: wire this to an application service that persists or processes raw article payloads
    }

    @KafkaListener(topics = KafkaTopicConstants.RAW_JOBS, groupId = "${spring.kafka.consumer.group-id}")
    public void consumeRawJobs(ConsumerRecord<String, String> record) {
        LOGGER.info("Received raw job from Kafka [{}]: {}", record.topic(), record.value());
        // TODO: wire this to an application service that persists or processes raw job payloads
    }
}
