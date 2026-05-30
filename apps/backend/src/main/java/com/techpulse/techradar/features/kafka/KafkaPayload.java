package com.techpulse.techradar.features.kafka;

/**
 * Generic payload model for Kafka event messages.
 *
 * This record can be extended later with fields that match the existing Go ingestion schema.
 */
public record KafkaPayload(
        String id,
        String title,
        String content,
        String url,
        String source,
        String publishedAt
) {
}
