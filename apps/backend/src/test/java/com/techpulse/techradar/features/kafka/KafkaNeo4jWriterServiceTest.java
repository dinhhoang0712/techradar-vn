package com.techpulse.techradar.features.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techpulse.techradar.features.kafka.model.ExtractedJob;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the KafkaNeo4jWriterService orchestration layer: delegating the actual Cypher write to
 * {@link Neo4jExtractionWriter} and firing (or skipping) the {@code job.match.alert} publish
 * based on the "is new" boolean {@link Neo4jExtractionWriter#writeJob} reports. That boolean is
 * computed atomically inside the writer's own MERGE transaction — see
 * {@code Neo4jExtractionWriterTest} for coverage of that logic itself.
 */
@ExtendWith(MockitoExtension.class)
class KafkaNeo4jWriterServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private KafkaProducerService kafkaProducer;

    @Mock
    private Neo4jExtractionWriter neo4jWriter;

    private KafkaNeo4jWriterService service;

    @BeforeEach
    void setUp() {
        service = new KafkaNeo4jWriterService(objectMapper, neo4jWriter, kafkaProducer);
    }

    private static String extractedJobJson(String companySizeField, String companyIndustryField) {
        return ("""
                {
                  "message_type": "extracted_job",
                  "source_platform": "TopCV",
                  "crawled_at": "2026-01-01T00:00:00Z",
                  "extracted_at": "2026-01-01T00:00:00Z",
                  "data": {
                    "job": {
                      "title": "Backend Developer",
                      "description": "desc",
                      "requirement": "req",
                      "benefit": "benefit",
                      "salary": "20-30 triệu",
                      "due_date": "",
                      "source_url": "https://example.com/job-1"
                    },
                    "company": {
                      "name": "Acme Corp",
                      "size": %s,
                      "field": %s,
                      "location": "Hà Nội"
                    },
                    "skills": [],
                    "technologies": ["Java"]
                  }
                }
                """).formatted(companySizeField, companyIndustryField);
    }

    @Test
    void writeJob_publishesJobMatchAlertWhenWriterReportsANewJob() throws Exception {
        when(neo4jWriter.writeJob(any(ExtractedJob.class))).thenReturn(true);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "extracted_jobs", 0, 0, "key", extractedJobJson("\"100-500\"", "\"Fintech\""));

        service.consumeExtractedJob(record);

        verify(kafkaProducer, times(1)).send(eqTopic(), any());
    }

    @Test
    void writeJob_doesNotPublishJobMatchAlertWhenWriterReportsAnExistingJob() {
        when(neo4jWriter.writeJob(any(ExtractedJob.class))).thenReturn(false);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "extracted_jobs", 0, 0, "key", extractedJobJson("\"100-500\"", "\"Fintech\""));

        service.consumeExtractedJob(record);

        verify(kafkaProducer, never()).send(any(), any());
    }

    @Test
    void writeJob_delegatesTheActualCypherWriteToNeo4jExtractionWriter() {
        when(neo4jWriter.writeJob(any(ExtractedJob.class))).thenReturn(false);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "extracted_jobs", 0, 0, "key", extractedJobJson("\"100-500\"", "\"Fintech\""));

        service.consumeExtractedJob(record);

        ArgumentCaptor<ExtractedJob> jobCaptor = ArgumentCaptor.forClass(ExtractedJob.class);
        verify(neo4jWriter).writeJob(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getData().getJob().getTitle()).isEqualTo("Backend Developer");
    }

    private static String eqTopic() {
        return KafkaTopicConstants.JOB_MATCH_ALERTS;
    }
}
