package com.techpulse.techradar.features.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import com.techpulse.techradar.features.kafka.model.ExtractedArticle;
import com.techpulse.techradar.features.kafka.model.ExtractedJob;
import com.techpulse.techradar.features.kafka.producer.KafkaProducerService;
import com.techpulse.techradar.features.notification.event.JobMatchEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class KafkaNeo4jWriterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaNeo4jWriterService.class);

    private final ObjectMapper objectMapper;
    private final Driver neo4jDriver;
    private final KafkaProducerService kafkaProducer;

    private final AtomicLong articlesProcessed = new AtomicLong();
    private final AtomicLong articlesFailed = new AtomicLong();
    private final AtomicLong jobsProcessed = new AtomicLong();
    private final AtomicLong jobsFailed = new AtomicLong();
    private final AtomicReference<Instant> lastArticleProcessedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastJobProcessedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailureMessage = new AtomicReference<>();

    public KafkaNeo4jWriterService(ObjectMapper objectMapper, Driver neo4jDriver, KafkaProducerService kafkaProducer) {
        this.objectMapper = objectMapper;
        this.neo4jDriver = neo4jDriver;
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
                    for (String orgName : article.getData().getEntities().getOrg()) {
                        if (orgName == null || orgName.isBlank()) {
                            continue;
                        }
                        String companyId = slugify(orgName);
                        tx.run(
                                "MERGE (c:Company {id: $company_id}) " +
                                        "SET c.name = $company_name " +
                                        "WITH c MATCH (a:Article {id: $article_id}) MERGE (a)-[:MENTIONS]->(c)",
                                org.neo4j.driver.Values.parameters(
                                        "company_id", companyId,
                                        "company_name", orgName,
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
            articlesProcessed.incrementAndGet();
            lastArticleProcessedAt.set(Instant.now());
        } catch (Exception e) {
            articlesFailed.incrementAndGet();
            recordFailure(e);
            LOGGER.error("Error writing article to Neo4j", e);
        }
    }

    private void writeJob(ExtractedJob job) {
        String jobId = generateId(job.getData().getJob().getSourceUrl());
        try (Session session = neo4jDriver.session()) {
            boolean isNewJob = !session.run(
                    "MATCH (j:Job {id: $id}) RETURN j",
                    Values.parameters("id", jobId)
            ).hasNext();

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
                    // Không phải crawler nào cũng scrape được industry/size (VD: TopCV có, ITviec thì
                    // không) — dùng CASE để giữ nguyên giá trị cũ khi tin tuyển dụng này không mang theo
                    // dữ liệu, thay vì ghi đè bằng rỗng và làm mất dữ liệu công ty đã có từ tin trước.
                    tx.run(
                            "MERGE (c:Company {id: $company_id}) " +
                                    "SET c.name = $company_name, c.location = $company_location, " +
                                    "c.industry = CASE WHEN $company_industry IS NULL OR $company_industry = '' THEN c.industry ELSE $company_industry END, " +
                                    "c.size = CASE WHEN $company_size IS NULL OR $company_size = '' THEN c.size ELSE $company_size END " +
                                    "WITH c MATCH (j:Job {id: $job_id}) MERGE (j)-[:POSTED_BY]->(c)",
                            org.neo4j.driver.Values.parameters(
                                    "company_id", companyId,
                                    "company_name", job.getData().getCompany().getName(),
                                    "company_location", job.getData().getCompany().getLocation(),
                                    "company_industry", job.getData().getCompany().getField(),
                                    "company_size", job.getData().getCompany().getSize(),
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
