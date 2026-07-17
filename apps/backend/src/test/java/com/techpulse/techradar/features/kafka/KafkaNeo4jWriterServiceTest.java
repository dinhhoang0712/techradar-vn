package com.techpulse.techradar.features.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the CASE-based Cypher SET in writeJob that preserves an existing Company's
 * industry/size when the incoming job posting doesn't carry that data (e.g. crawlers other
 * than TopCV), instead of overwriting it with blank.
 */
@ExtendWith(MockitoExtension.class)
class KafkaNeo4jWriterServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private KafkaProducerService kafkaProducer;

    @Mock
    private Driver neo4jDriver;

    @Mock
    private Session session;

    @Mock
    private Transaction tx;

    @Mock
    private Result jobExistsResult;

    private KafkaNeo4jWriterService service;

    @BeforeEach
    void setUp() {
        service = new KafkaNeo4jWriterService(objectMapper, neo4jDriver, kafkaProducer);
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(any(String.class), any(Value.class))).thenReturn(jobExistsResult);
        when(jobExistsResult.hasNext()).thenReturn(true); // pretend the job already exists, skip the new-job alert path
        when(session.executeWrite(any())).thenAnswer(invocation -> {
            TransactionWork<Object> work = invocation.getArgument(0);
            return work.execute(tx);
        });
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
                    "technologies": []
                  }
                }
                """).formatted(companySizeField, companyIndustryField);
    }

    private String companyMergeCypher() {
        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(tx, org.mockito.Mockito.atLeastOnce())
                .run(cypherCaptor.capture(), any(Value.class));
        return cypherCaptor.getAllValues().stream()
                .filter(cypher -> cypher.contains("MERGE (c:Company"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Company MERGE Cypher was run"));
    }

    private Value companyMergeParams() {
        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Value> paramsCaptor = ArgumentCaptor.forClass(Value.class);
        org.mockito.Mockito.verify(tx, org.mockito.Mockito.atLeastOnce())
                .run(cypherCaptor.capture(), paramsCaptor.capture());
        List<String> cyphers = cypherCaptor.getAllValues();
        for (int i = 0; i < cyphers.size(); i++) {
            if (cyphers.get(i).contains("MERGE (c:Company")) {
                return paramsCaptor.getAllValues().get(i);
            }
        }
        throw new AssertionError("No Company MERGE Cypher was run");
    }

    @Test
    void writeJob_usesConditionalCaseInsteadOfUnconditionallyOverwritingIndustryAndSize() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "extracted_jobs", 0, 0, "key", extractedJobJson("\"100-500\"", "\"Fintech\""));

        service.consumeExtractedJob(record);

        String cypher = companyMergeCypher();
        assertThat(cypher).contains("CASE WHEN $company_industry IS NULL OR $company_industry = ''");
        assertThat(cypher).contains("CASE WHEN $company_size IS NULL OR $company_size = ''");
    }

    @Test
    void writeJob_passesTheIncomingIndustryAndSizeAsCypherParameters() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "extracted_jobs", 0, 0, "key", extractedJobJson("\"100-500\"", "\"Fintech\""));

        service.consumeExtractedJob(record);

        Value params = companyMergeParams();
        assertThat(params.get("company_size").asString()).isEqualTo("100-500");
        assertThat(params.get("company_industry").asString()).isEqualTo("Fintech");
    }

    @Test
    void writeJob_passesBlankIndustryAndSizeWhenTheJobPostingHasNone() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "extracted_jobs", 0, 0, "key", extractedJobJson("\"\"", "\"\""));

        service.consumeExtractedJob(record);

        Value params = companyMergeParams();
        assertThat(params.get("company_size").asString()).isEmpty();
        assertThat(params.get("company_industry").asString()).isEmpty();
    }
}
