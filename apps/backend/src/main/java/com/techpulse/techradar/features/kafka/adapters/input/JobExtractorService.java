package com.techpulse.techradar.features.kafka.adapters.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.adapters.output.KafkaProducerService;
import com.techpulse.techradar.features.kafka.domain.EntityExtractionService;
import com.techpulse.techradar.features.kafka.event.CompanyInfo;
import com.techpulse.techradar.features.kafka.event.Entities;
import com.techpulse.techradar.features.kafka.event.ExtractedJob;
import com.techpulse.techradar.features.kafka.event.ExtractedJobData;
import com.techpulse.techradar.features.kafka.event.JobData;
import com.techpulse.techradar.features.kafka.event.JobInfo;
import com.techpulse.techradar.features.kafka.event.RawJob;
import com.techpulse.techradar.shared.util.IdHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes raw crawled job postings off {@link KafkaTopicConstants#RAW_JOBS}, runs entity
 * extraction, and republishes the enriched result to {@link KafkaTopicConstants#EXTRACTED_JOBS}.
 * <p>
 * Split out of the former {@code KafkaExtractorService} (which also handled articles) — see
 * {@link ArticleExtractorService} for why the split rather than a shared template method.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExtractorService {

    private final ObjectMapper objectMapper;
    private final KafkaProducerService kafkaProducer;
    private final EntityExtractionService extractionService;

    @KafkaListener(topics = KafkaTopicConstants.RAW_JOBS, groupId = "${spring.kafka.consumer.group-id}")
    public void consumeRawJob(ConsumerRecord<String, String> record) {
        try {
            RawJob raw = objectMapper.readValue(record.value(), RawJob.class);
            log.info("Consuming raw job from platform {} url={}", raw.getSourcePlatform(),
                    raw.getData() != null ? raw.getData().getSourceUrl() : null);
            ExtractedJob extracted = buildExtractedJob(raw);
            sendExtractedJob(extracted);
        } catch (Exception e) {
            log.error("Failed to process raw job message", e);
        }
    }

    private ExtractedJob buildExtractedJob(RawJob raw) {
        JobData jobData = raw.getData();
        String text = jobData.getJobTitle() + " " + jobData.getDescription() + " " + jobData.getRequirement();
        Entities entities = extractionService.extractEntities(text, jobData.getSkills());

        JobInfo jobInfo = new JobInfo(
                jobData.getJobTitle(),
                jobData.getDescription(),
                jobData.getRequirement(),
                jobData.getBenefit(),
                jobData.getSalary(),
                "",
                jobData.getSourceUrl()
        );

        ExtractedJobData extractedData = new ExtractedJobData(
                jobInfo,
                new CompanyInfo(
                        jobData.getCompanyName(),
                        jobData.getSize() != null ? jobData.getSize() : "",
                        jobData.getField() != null ? jobData.getField() : "",
                        jobData.getLocation()
                ),
                jobData.getSkills() != null ? new ArrayList<>(jobData.getSkills()) : List.of(),
                new ArrayList<>(entities.getTech() != null ? entities.getTech() : List.of()),
                entities
        );

        return new ExtractedJob(
                "extracted_job",
                raw.getSourcePlatform(),
                raw.getCrawledAt(),
                OffsetDateTime.now(),
                extractedData
        );
    }

    private void sendExtractedJob(ExtractedJob extracted) {
        String key = IdHashUtils.md5(extracted.getData().getJob().getSourceUrl());
        kafkaProducer.send(KafkaTopicConstants.EXTRACTED_JOBS, key, extracted);
        log.info("Published extracted job to Kafka: {}", extracted.getData().getJob().getSourceUrl());
    }
}
