package com.techpulse.techradar.features.graph.domain;

/**
 * Maps a sentiment label to a {@code [min, max]} band on {@code sentiment_score}.
 * The classifier's exact output range isn't documented anywhere in the codebase; a
 * +/-0.2 neutral dead-zone around 0 is a reasonable default and can be tuned later
 * if the real score distribution differs.
 */
public record SentimentBand(double min, double max) {

    public static SentimentBand forLabel(String sentiment) {
        if (sentiment == null || sentiment.isBlank()) {
            return null;
        }
        return switch (sentiment.trim().toLowerCase()) {
            case "positive" -> new SentimentBand(0.2, Double.MAX_VALUE);
            case "negative" -> new SentimentBand(-Double.MAX_VALUE, -0.2);
            case "neutral" -> new SentimentBand(-0.2, 0.2);
            default -> null;
        };
    }
}
