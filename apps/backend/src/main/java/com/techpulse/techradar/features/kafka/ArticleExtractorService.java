package com.techpulse.techradar.features.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.model.ArticleData;
import com.techpulse.techradar.features.kafka.model.Entities;
import com.techpulse.techradar.features.kafka.model.ExtractedArticle;
import com.techpulse.techradar.features.kafka.model.ExtractedArticleData;
import com.techpulse.techradar.features.kafka.model.RawArticle;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import com.techpulse.techradar.shared.util.IdHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Consumes raw crawled articles off {@link KafkaTopicConstants#RAW_ARTICLES}, runs entity
 * extraction, and republishes the enriched result to {@link KafkaTopicConstants#EXTRACTED_ARTICLES}.
 * <p>
 * Split out of the former {@code KafkaExtractorService} (which also handled jobs) once the
 * duplicated serialize/send and MD5-hashing logic moved out to {@link KafkaProducerService} and
 * {@link IdHashUtils} — at that point the article and job pipelines shared nothing but those two
 * (already-external) dependencies, so each got its own single-responsibility listener instead of
 * one class doing two unrelated jobs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleExtractorService {

    private final ObjectMapper objectMapper;
    private final KafkaProducerService kafkaProducer;
    private final EntityExtractionService extractionService;

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

    private void sendExtractedArticle(ExtractedArticle extracted) {
        String key = IdHashUtils.md5(extracted.getData().getSourceUrl());
        kafkaProducer.send(KafkaTopicConstants.EXTRACTED_ARTICLES, key, extracted);
        log.info("Published extracted article to Kafka: {}", extracted.getData().getSourceUrl());
    }
}
