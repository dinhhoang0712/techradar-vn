package com.techpulse.techradar.features.kafka.domain;

import java.time.Instant;

/**
 * Snapshot of the Kafka-to-Neo4j writer's throughput/error counters, for admin dashboards.
 */
public record KafkaSyncStatus(
        long articlesProcessed,
        long articlesFailed,
        long jobsProcessed,
        long jobsFailed,
        Instant lastArticleProcessedAt,
        Instant lastJobProcessedAt,
        Instant lastFailureAt,
        String lastFailureMessage
) {
}
