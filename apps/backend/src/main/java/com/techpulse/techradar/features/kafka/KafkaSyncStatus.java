package com.techpulse.techradar.features.kafka;

import java.time.Instant;

/**
 * Snapshot of {@link KafkaNeo4jWriterService} throughput/error counters, for admin dashboards.
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
