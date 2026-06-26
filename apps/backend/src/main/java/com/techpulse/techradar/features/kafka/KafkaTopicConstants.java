package com.techpulse.techradar.features.kafka;

/**
 * Constant Kafka topic names used by the TechRadar Spring Kafka integration.
 */
public final class KafkaTopicConstants {

    public static final String RAW_ARTICLES = "raw_articles";
    public static final String RAW_JOBS = "raw_jobs";
    public static final String EXTRACTED_ARTICLES = "extracted_articles";
    public static final String EXTRACTED_JOBS = "extracted_jobs";
    public static final String ARTICLE_VECTORS = "article_vectors";
    public static final String JOB_VECTORS = "job_vectors";

    /** Domain event: a technology crossed the trend-growth threshold (radar ETL → notifications). */
    public static final String TREND_ALERTS = "trend.alerts";

    private KafkaTopicConstants() {
        // Constants only
    }
}
