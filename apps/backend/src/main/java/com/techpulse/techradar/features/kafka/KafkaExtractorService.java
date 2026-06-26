package com.techpulse.techradar.features.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.model.ArticleData;
import com.techpulse.techradar.features.kafka.model.Entities;
import com.techpulse.techradar.features.kafka.model.ExtractedArticle;
import com.techpulse.techradar.features.kafka.model.ExtractedArticleData;
import com.techpulse.techradar.features.kafka.model.ExtractedJob;
import com.techpulse.techradar.features.kafka.model.ExtractedJobData;
import com.techpulse.techradar.features.kafka.model.JobInfo;
import com.techpulse.techradar.features.kafka.model.JobData;
import com.techpulse.techradar.features.kafka.model.RawArticle;
import com.techpulse.techradar.features.kafka.model.RawJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KafkaExtractorService {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EntityExtractionService extractionService;

    public KafkaExtractorService(ObjectMapper objectMapper,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 EntityExtractionService extractionService) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.extractionService = extractionService;
    }

    @KafkaListener(topics = KafkaTopicConstants.RAW_ARTICLES, groupId = "${spring.kafka.consumer.group-id}")
    public void consumeRawArticle(ConsumerRecord<String, String> record) {
        try {
            RawArticle raw = objectMapper.readValue(record.value(), RawArticle.class);
            log.info("Consuming raw article from platform {} url={}", raw.getSourcePlatform(),
                    raw.getData() != null ? raw.getData().getSourceUrl() : null);
            ExtractedArticle extracted = buildExtractedArticle(raw);
            sendExtractedArticle(extracted);
        } catch (Exception e) {
            log.error("Failed to process raw article message", e);
        }
    }

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

    private ExtractedArticle buildExtractedArticle(RawArticle raw) {
        String text = raw.getData().getTitle() + " " + raw.getData().getContent();
        Entities entities = extractionService.extractEntities(text, null);
        log.info("Extracted {} tech entities from article '{}'",
                entities.getTech() != null ? entities.getTech().size() : 0, raw.getData().getTitle());

        ArticleData data = raw.getData();
        ExtractedArticleData extractedData = new ExtractedArticleData(
                data.getTitle(),
                data.getPublishDate(),
                data.getContent(),
                data.getSourceUrl(),
                entities
        );

        return new ExtractedArticle(
                "extracted_article",
                raw.getSourcePlatform(),
                raw.getCrawledAt(),
                OffsetDateTime.now(),
                extractedData
        );
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
                new com.techpulse.techradar.features.kafka.model.CompanyInfo(
                        jobData.getCompanyName(),
                        "",
                        "",
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

    private void sendExtractedArticle(ExtractedArticle extracted) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(extracted);
        String key = md5(extracted.getData().getSourceUrl());
        kafkaTemplate.send(KafkaTopicConstants.EXTRACTED_ARTICLES, key, payload);
        log.info("Published extracted article to Kafka: {}", extracted.getData().getSourceUrl());
    }

    private void sendExtractedJob(ExtractedJob extracted) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(extracted);
        String key = md5(extracted.getData().getJob().getSourceUrl());
        kafkaTemplate.send(KafkaTopicConstants.EXTRACTED_JOBS, key, payload);
        log.info("Published extracted job to Kafka: {}", extracted.getData().getJob().getSourceUrl());
    }

    private String md5(String value) {
        if (value == null) {
            value = "";
        }
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
