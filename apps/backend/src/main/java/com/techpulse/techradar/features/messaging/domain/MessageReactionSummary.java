package com.techpulse.techradar.features.messaging.domain;

/** Aggregated reaction count for one emoji on a message, from a specific viewer's perspective. */
public record MessageReactionSummary(
        String emoji,
        int count,
        boolean reactedByMe
) {
}
