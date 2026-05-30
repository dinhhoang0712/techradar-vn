package com.techpulse.techradar.features.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.model.ExtractedArticle;
import com.techpulse.techradar.features.kafka.model.ExtractedJob;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class KafkaNeo4jWriterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaNeo4jWriterService.class);

    private final ObjectMapper objectMapper;
    private final Driver neo4jDriver;

    public KafkaNeo4jWriterService(ObjectMapper objectMapper, Driver neo4jDriver) {
        this.objectMapper = objectMapper;
        this.neo4jDriver = neo4jDriver;
    }

    @KafkaListener(topics = KafkaTopicConstants.EXTRACTED_ARTICLES, groupId = "neo4j-writer-group")
    public void consumeExtractedArticle(ConsumerRecord<String, String> record) {
        try {
            ExtractedArticle article = objectMapper.readValue(record.value(), ExtractedArticle.class);
            writeArticle(article);
        } catch (Exception e) {
            LOGGER.error("Failed to process extracted article for Neo4j", e);
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.EXTRACTED_JOBS, groupId = "neo4j-writer-group")
    public void consumeExtractedJob(ConsumerRecord<String, String> record) {
        try {
            ExtractedJob job = objectMapper.readValue(record.value(), ExtractedJob.class);
            writeJob(job);
        } catch (Exception e) {
            LOGGER.error("Failed to process extracted job for Neo4j", e);
        }
    }

    private void writeArticle(ExtractedArticle article) {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run(
                        "MERGE (a:Article {id: $id}) " +
                                "SET a.title = $title, a.content = $content, a.url = $source_url, " +
                                "a.source_platform = $source_platform, a.published_date = $publish_date",
                        org.neo4j.driver.Values.parameters(
                                "id", generateId(article.getData().getSourceUrl()),
                                "title", article.getData().getTitle(),
                                "content", article.getData().getContent(),
                                "source_url", article.getData().getSourceUrl(),
                                "source_platform", article.getSourcePlatform(),
                                "publish_date", article.getData().getPublishDate()
                        )
                );

                if (article.getData().getEntities() != null && article.getData().getEntities().getTech() != null) {
                    for (String tech : article.getData().getEntities().getTech()) {
                        if (tech == null || tech.isBlank()) {
                            continue;
                        }
                        tx.run(
                                "MERGE (t:Technology {name: $tech}) " +
                                        "SET t.mention_count = COALESCE(t.mention_count, 0) + 1 " +
                                        "WITH t MATCH (a:Article {id: $article_id}) MERGE (a)-[:MENTIONS]->(t)",
                                org.neo4j.driver.Values.parameters(
                                        "tech", tech,
                                        "article_id", generateId(article.getData().getSourceUrl())
                                )
                        );
                    }
                }

                if (article.getData().getEntities() != null && article.getData().getEntities().getOrg() != null) {
                    for (String org : article.getData().getEntities().getOrg()) {
                        if (org == null || org.isBlank()) {
                            continue;
                        }
                        String companyId = slugify(org);
                        tx.run(
                                "MERGE (c:Company {id: $company_id}) " +
                                        "SET c.name = $company_name " +
                                        "WITH c MATCH (a:Article {id: $article_id}) MERGE (a)-[:MENTIONS]->(c)",
                                org.neo4j.driver.Values.parameters(
                                        "company_id", companyId,
                                        "company_name", org,
                                        "article_id", generateId(article.getData().getSourceUrl())
                                )
                        );
                    }
                }

                if (article.getData().getEntities() != null && article.getData().getEntities().getLoc() != null) {
                    for (String loc : article.getData().getEntities().getLoc()) {
                        if (loc == null || loc.isBlank()) {
                            continue;
                        }
                        tx.run(
                                "MERGE (l:Location {name: $location}) " +
                                        "WITH l MATCH (a:Article {id: $article_id}) MERGE (a)-[:MENTIONS]->(l)",
                                org.neo4j.driver.Values.parameters(
                                        "location", loc,
                                        "article_id", generateId(article.getData().getSourceUrl())
                                )
                        );
                    }
                }
                return null;
            });
            LOGGER.info("Stored extracted article to Neo4j: {}", article.getData().getSourceUrl());
        } catch (Exception e) {
            LOGGER.error("Error writing article to Neo4j", e);
        }
    }

    private void writeJob(ExtractedJob job) {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run(
                        "MERGE (j:Job {id: $id}) " +
                                "SET j.name = $title, j.description = $description, j.requirement = $requirement, " +
                                "j.benefit = $benefit, j.salary = $salary, j.url = $source_url, j.source_platform = $source_platform",
                        org.neo4j.driver.Values.parameters(
                                "id", generateId(job.getData().getJob().getSourceUrl()),
                                "title", job.getData().getJob().getTitle(),
                                "description", job.getData().getJob().getDescription(),
                                "requirement", job.getData().getJob().getRequirement(),
                                "benefit", job.getData().getJob().getBenefit(),
                                "salary", job.getData().getJob().getSalary(),
                                "source_url", job.getData().getJob().getSourceUrl(),
                                "source_platform", job.getSourcePlatform()
                        )
                );

                if (job.getData().getCompany() != null && job.getData().getCompany().getName() != null) {
                    String companyId = slugify(job.getData().getCompany().getName());
                    tx.run(
                            "MERGE (c:Company {id: $company_id}) " +
                                    "SET c.name = $company_name, c.location = $company_location " +
                                    "WITH c MATCH (j:Job {id: $job_id}) MERGE (j)-[:POSTED_BY]->(c)",
                            org.neo4j.driver.Values.parameters(
                                    "company_id", companyId,
                                    "company_name", job.getData().getCompany().getName(),
                                    "company_location", job.getData().getCompany().getLocation(),
                                    "job_id", generateId(job.getData().getJob().getSourceUrl())
                            )
                    );
                }

                if (job.getData().getTechnologies() != null) {
                    for (String tech : job.getData().getTechnologies()) {
                        if (tech == null || tech.isBlank()) {
                            continue;
                        }
                        tx.run(
                                "MERGE (t:Technology {name: $tech}) " +
                                        "SET t.mention_count = COALESCE(t.mention_count, 0) + 1 " +
                                        "WITH t MATCH (j:Job {id: $job_id}) MERGE (j)-[:REQUIRES]->(t)",
                                org.neo4j.driver.Values.parameters(
                                        "tech", tech,
                                        "job_id", generateId(job.getData().getJob().getSourceUrl())
                                )
                        );
                    }
                }

                if (job.getData().getSkills() != null) {
                    for (String skill : job.getData().getSkills()) {
                        if (skill == null || skill.isBlank()) {
                            continue;
                        }
                        tx.run(
                                "MERGE (s:Skill {name: $skill}) " +
                                        "SET s.mention_count = COALESCE(s.mention_count, 0) + 1 " +
                                        "WITH s MATCH (j:Job {id: $job_id}) MERGE (j)-[:REQUIRES]->(s)",
                                org.neo4j.driver.Values.parameters(
                                        "skill", skill,
                                        "job_id", generateId(job.getData().getJob().getSourceUrl())
                                )
                        );
                    }
                }
                return null;
            });
            LOGGER.info("Stored extracted job to Neo4j: {}", job.getData().getJob().getSourceUrl());
        } catch (Exception e) {
            LOGGER.error("Error writing job to Neo4j", e);
        }
    }

    private String generateId(String sourceUrl) {
        if (sourceUrl == null) {
            sourceUrl = "";
        }
        return DigestUtils.md5DigestAsHex(sourceUrl.getBytes(StandardCharsets.UTF_8));
    }

    private String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
