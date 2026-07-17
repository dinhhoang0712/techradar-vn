package com.techpulse.techradar.features.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techpulse.techradar.features.kafka.model.CompanyInfo;
import com.techpulse.techradar.features.kafka.model.Entities;
import com.techpulse.techradar.features.kafka.model.ExtractedJob;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the fix where CompanyInfo.size/field used to be hardcoded to "" regardless of what the
 * crawler sent — see JobData.size/field and KafkaExtractorService.buildExtractedJob.
 */
@ExtendWith(MockitoExtension.class)
class KafkaExtractorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private EntityExtractionService extractionService;

    private KafkaExtractorService service;

    @BeforeEach
    void setUp() {
        service = new KafkaExtractorService(objectMapper, kafkaTemplate, extractionService);
        when(extractionService.extractEntities(any(), any()))
                .thenReturn(new Entities(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    private static String rawJobJson(String extraCompanyFields) {
        return ("""
                {
                  "message_type": "job",
                  "source_platform": "TopCV",
                  "crawled_at": "2026-01-01T00:00:00Z",
                  "data": {
                    "job_title": "Backend Developer",
                    "company_name": "Acme Corp",
                    "location": "Hà Nội",
                    "salary": "20-30 triệu",
                    "level": "Senior",
                    "description": "desc",
                    "requirement": "req",
                    "benefit": "benefit",
                    "skills": [],
                    "source_url": "https://example.com/job-1",
                    "posted_date": ""
                    %s
                  }
                }
                """).formatted(extraCompanyFields);
    }

    private ExtractedJob consumeAndCapturePublishedJob(String json) throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw_jobs", 0, 0, "key", json);
        service.consumeRawJob(record);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopicConstants.EXTRACTED_JOBS), any(), payloadCaptor.capture());
        return objectMapper.readValue(payloadCaptor.getValue(), ExtractedJob.class);
    }

    @Test
    void consumeRawJob_populatesCompanyInfoSizeAndFieldWhenTheCrawlerSentThem() throws Exception {
        ExtractedJob extracted = consumeAndCapturePublishedJob(
                rawJobJson(", \"size\": \"100-500\", \"field\": \"Fintech\""));

        CompanyInfo company = extracted.getData().getCompany();
        assertThat(company.getSize()).isEqualTo("100-500");
        assertThat(company.getField()).isEqualTo("Fintech");
    }

    @Test
    void consumeRawJob_defaultsToEmptyStringsWhenTheCrawlerDidNotSendSizeOrField() throws Exception {
        ExtractedJob extracted = consumeAndCapturePublishedJob(rawJobJson(""));

        CompanyInfo company = extracted.getData().getCompany();
        assertThat(company.getSize()).isEmpty();
        assertThat(company.getField()).isEmpty();
    }
}
