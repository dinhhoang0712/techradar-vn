package com.techpulse.techradar.features.kafka.adapters.output;

import com.techpulse.techradar.features.kafka.event.ExtractedArticle;
import com.techpulse.techradar.features.kafka.event.ExtractedJob;
import com.techpulse.techradar.features.kafka.ports.ExtractionWriter;
import com.techpulse.techradar.shared.util.IdHashUtils;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Raw Neo4j Cypher writes for Kafka-extracted articles and jobs. Extracted out of
 * {@code KafkaNeo4jWriterService} so that class only has to deal with Kafka consumption,
 * metrics/status tracking and alert-publishing orchestration (SRP) — this class owns the
 * session/transaction mechanics and the actual write Cypher.
 */
@Component
public class Neo4jExtractionWriter implements ExtractionWriter {

    private final Driver neo4jDriver;

    public Neo4jExtractionWriter(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public void writeArticle(ExtractedArticle article) {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run(
                        "MERGE (a:Article {id: $id}) " +
                                "SET a.title = $title, a.content = $content, a.url = $source_url, " +
                                "a.source_platform = $source_platform, a.published_date = $publish_date",
                        Values.parameters(
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
                                Values.parameters(
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
                        // slugify() có thể trả rỗng cho tên chỉ gồm ký tự không phải a-z0-9 (vd "---",
                        // "?") dù orgName đã qua check isBlank() ở trên — bỏ qua để không tạo
                        // Company{id:"", name: orgName} (cùng lớp bug với writeJob(), xem ghi chú ở đó).
                        if (companyId.isEmpty()) {
                            continue;
                        }
                        tx.run(
                                "MERGE (c:Company {id: $company_id}) " +
                                        "SET c.name = $company_name " +
                                        "WITH c MATCH (a:Article {id: $article_id}) MERGE (a)-[:MENTIONS]->(c)",
                                Values.parameters(
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
                                Values.parameters(
                                        "location", loc,
                                        "article_id", generateId(article.getData().getSourceUrl())
                                )
                        );
                    }
                }
                return null;
            });
        }
    }

    /**
     * Writes/updates a {@code Job} node and its relationships in a single transaction and
     * reports whether this call is the one that created the node.
     * <p>
     * The "is this new?" determination is made INSIDE the same {@code MERGE} statement that
     * performs the write (via a one-time random {@code creationToken} that only the
     * {@code ON CREATE} branch sets, compared back in the same query's {@code RETURN}), rather
     * than by a separate read query executed before the write. That earlier check-then-act
     * pattern left a window where two Kafka messages for the same job arriving close together
     * could both observe "not existing yet" in their own read, both then {@code MERGE}, and
     * both report "new" — causing a duplicate {@code job.match.alert} downstream. Folding the
     * check into the write's own transaction closes that window: whichever call actually
     * creates the node is the only one whose token matches afterward.
     */
    @Override
    public boolean writeJob(ExtractedJob job) {
        String jobId = generateId(job.getData().getJob().getSourceUrl());
        String creationToken = UUID.randomUUID().toString();
        try (Session session = neo4jDriver.session()) {
            return session.executeWrite(tx -> {
                Record jobRecord = tx.run(
                        "MERGE (j:Job {id: $id}) " +
                                "ON CREATE SET j.createdAt = timestamp(), j.creationToken = $creation_token " +
                                "SET j.name = $title, j.description = $description, j.requirement = $requirement, " +
                                "j.benefit = $benefit, j.salary = $salary, j.level = $level, j.url = $source_url, j.source_platform = $source_platform " +
                                "WITH j, coalesce(j.creationToken = $creation_token, false) AS isNew " +
                                "REMOVE j.creationToken " +
                                "RETURN isNew",
                        Values.parameters(
                                "id", jobId,
                                "creation_token", creationToken,
                                "title", job.getData().getJob().getTitle(),
                                "description", job.getData().getJob().getDescription(),
                                "requirement", job.getData().getJob().getRequirement(),
                                "benefit", job.getData().getJob().getBenefit(),
                                "salary", job.getData().getJob().getSalary(),
                                "level", job.getData().getJob().getLevel(),
                                "source_url", job.getData().getJob().getSourceUrl(),
                                "source_platform", job.getSourcePlatform()
                        )
                ).single();
                boolean isNewJob = jobRecord.get("isNew").asBoolean();

                if (job.getData().getCompany() != null && job.getData().getCompany().getName() != null
                        && !job.getData().getCompany().getName().isBlank()) {
                    String companyId = slugify(job.getData().getCompany().getName());
                    // slugify() có thể trả rỗng cho tên chỉ gồm ký tự không phải a-z0-9 (vd "---", "?",
                    // "N/A" viết kiểu khác) dù đã qua check isBlank() ở trên (isBlank() chỉ bắt chuỗi
                    // rỗng/toàn khoảng trắng) — bỏ qua để không tạo Company{id:"", name:""} (đã xác nhận
                    // sống: 1 node như vậy tồn tại, mồ côi, không rel nào, xem docs/DATABASE.md §4).
                    if (!companyId.isEmpty()) {
                        // Không phải crawler nào cũng scrape được industry/size (VD: TopCV có, ITviec
                        // thì không) — dùng CASE để giữ nguyên giá trị cũ khi tin tuyển dụng này không
                        // mang theo dữ liệu, thay vì ghi đè bằng rỗng và làm mất dữ liệu công ty đã có
                        // từ tin trước.
                        tx.run(
                                "MERGE (c:Company {id: $company_id}) " +
                                        "SET c.name = $company_name, c.location = $company_location, " +
                                        "c.industry = CASE WHEN $company_industry IS NULL OR $company_industry = '' THEN c.industry ELSE $company_industry END, " +
                                        "c.size = CASE WHEN $company_size IS NULL OR $company_size = '' THEN c.size ELSE $company_size END " +
                                        "WITH c MATCH (j:Job {id: $job_id}) MERGE (j)-[:POSTED_BY]->(c)",
                                Values.parameters(
                                        "company_id", companyId,
                                        "company_name", job.getData().getCompany().getName(),
                                        "company_location", job.getData().getCompany().getLocation(),
                                        "company_industry", job.getData().getCompany().getField(),
                                        "company_size", job.getData().getCompany().getSize(),
                                        "job_id", jobId
                                )
                        );
                    }
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
                                Values.parameters(
                                        "tech", tech,
                                        "job_id", jobId
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
                                Values.parameters(
                                        "skill", skill,
                                        "job_id", jobId
                                )
                        );
                    }
                }
                return isNewJob;
            });
        }
    }

    private String generateId(String sourceUrl) {
        return IdHashUtils.md5(sourceUrl);
    }

    private String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
