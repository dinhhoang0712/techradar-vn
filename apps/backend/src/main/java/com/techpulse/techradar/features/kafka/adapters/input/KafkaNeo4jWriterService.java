package com.techpulse.techradar.features.kafka.adapters.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.adapters.output.KafkaProducerService;
import com.techpulse.techradar.features.kafka.domain.KafkaSyncStatus;
import com.techpulse.techradar.features.kafka.event.ExtractedArticle;
import com.techpulse.techradar.features.kafka.event.ExtractedJob;
import com.techpulse.techradar.features.kafka.ports.ExtractionWriter;
import com.techpulse.techradar.features.notification.event.JobMatchEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class KafkaNeo4jWriterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaNeo4jWriterService.class);

    private final ObjectMapper objectMapper;
    private final ExtractionWriter neo4jWriter;
    private final KafkaProducerService kafkaProducer;

    private final AtomicLong articlesProcessed = new AtomicLong();
    private final AtomicLong articlesFailed = new AtomicLong();
    private final AtomicLong jobsProcessed = new AtomicLong();
    private final AtomicLong jobsFailed = new AtomicLong();
    private final AtomicReference<Instant> lastArticleProcessedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastJobProcessedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailureMessage = new AtomicReference<>();

    public KafkaNeo4jWriterService(ObjectMapper objectMapper, ExtractionWriter neo4jWriter, KafkaProducerService kafkaProducer) {
        this.objectMapper = objectMapper;
        this.neo4jWriter = neo4jWriter;
        this.kafkaProducer = kafkaProducer;
    }

    @KafkaListener(topics = KafkaTopicConstants.EXTRACTED_ARTICLES, groupId = "neo4j-writer-group")
    public void consumeExtractedArticle(ConsumerRecord<String, String> record) {
        try {
            ExtractedArticle article = objectMapper.readValue(record.value(), ExtractedArticle.class);
            writeArticle(article);
        } catch (Exception e) {
            articlesFailed.incrementAndGet();
            recordFailure(e);
            LOGGER.error("Failed to process extracted article for Neo4j", e);
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.EXTRACTED_JOBS, groupId = "neo4j-writer-group")
    public void consumeExtractedJob(ConsumerRecord<String, String> record) {
        try {
            ExtractedJob job = objectMapper.readValue(record.value(), ExtractedJob.class);
            writeJob(job);
        } catch (Exception e) {
            jobsFailed.incrementAndGet();
            recordFailure(e);
            LOGGER.error("Failed to process extracted job for Neo4j", e);
        }
    }

    private void recordFailure(Exception e) {
        lastFailureAt.set(Instant.now());
        lastFailureMessage.set(e.getMessage());
    }

    /** Snapshot of throughput/error counters since this instance started, for admin dashboards. */
    public KafkaSyncStatus syncStatus() {
        return new KafkaSyncStatus(
                articlesProcessed.get(),
                articlesFailed.get(),
                jobsProcessed.get(),
                jobsFailed.get(),
                lastArticleProcessedAt.get(),
                lastJobProcessedAt.get(),
                lastFailureAt.get(),
                lastFailureMessage.get()
        );
    }

    private void writeArticle(ExtractedArticle article) {
        try {
            neo4jWriter.writeArticle(article);
            LOGGER.info("Stored extracted article to Neo4j: {}", article.getData().getSourceUrl());
            articlesProcessed.incrementAndGet();
            lastArticleProcessedAt.set(Instant.now());
        } catch (Exception e) {
            articlesFailed.incrementAndGet();
            recordFailure(e);
            LOGGER.error("Error writing article to Neo4j", e);
        }
    }

    private void writeJob(ExtractedJob job) {
        try {
            boolean isNewJob = neo4jWriter.writeJob(job);
            LOGGER.info("Stored extracted job to Neo4j: {}", job.getData().getJob().getSourceUrl());
            jobsProcessed.incrementAndGet();
            lastJobProcessedAt.set(Instant.now());
            if (isNewJob) {
                publishJobMatchAlert(job);
            }
        } catch (Exception e) {
            jobsFailed.incrementAndGet();
            recordFailure(e);
            LOGGER.error("Error writing job to Neo4j", e);
        }
    }

    /**
     * Publish a {@code job.match.alerts} event for a brand-new job posting (skipped for MERGE
     * updates to an already-known job, so re-crawls of the same listing don't re-notify).
     */
    private void publishJobMatchAlert(ExtractedJob job) {
        var technologies = job.getData().getTechnologies();
        if (technologies == null || technologies.isEmpty()) {
            return;
        }
        try {
            JobMatchEvent event = new JobMatchEvent(
                    job.getData().getJob().getTitle(),
                    job.getData().getCompany() != null ? job.getData().getCompany().getName() : null,
                    technologies,
                    job.getData().getJob().getSourceUrl());
            kafkaProducer.send(KafkaTopicConstants.JOB_MATCH_ALERTS, event);
        } catch (Exception e) {
            LOGGER.warn("Could not publish job match alert for {} (Kafka unavailable?)",
                    job.getData().getJob().getSourceUrl(), e);
        }
    }
}
