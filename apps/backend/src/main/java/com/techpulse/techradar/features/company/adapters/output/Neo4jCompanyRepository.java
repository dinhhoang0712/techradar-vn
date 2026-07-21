package com.techpulse.techradar.features.company.adapters.output;

import com.techpulse.techradar.features.company.domain.CompanyMention;
import com.techpulse.techradar.features.company.ports.CompanyRepository;
import com.techpulse.techradar.shared.neo4j.Neo4jReadTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j adapter for company tech-stack fingerprinting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jCompanyRepository implements CompanyRepository {

    private final Driver driver;

    // Company-[:USES]->Technology does exist (written by data-platform/gold/neo4j_enricher.py),
    // but we deliberately infer the tech stack from job requirements instead: it reflects jobs the
    // company is hiring for right now, whereas USES is a derived, batch-enriched signal that only
    // refreshes on the enricher's schedule.
    //
    // Job-[:POSTED_BY]->Company is the live edge, written by the real-time Kafka pipeline
    // (KafkaNeo4jWriterService) and by data-platform/gold/neo4j_job_sync.py.
    // Job-[:HIRES_FOR]->Company was written by the old knowledge-graph/ batch importer
    // (removed from the repo — see docs/DATABASE.md §4.1); it's frozen historical data, no
    // longer written by anything. Older jobs may only have HIRES_FOR, so match both or company
    // linkage silently goes missing for them.
    private static final String QUERY =
            "MATCH (c:Company)<-[:POSTED_BY|HIRES_FOR]-(j:Job)-[:REQUIRES]->(t) " +
            "WHERE t:Technology OR t:Skill " +
            "WITH c, collect(DISTINCT t.name) AS techStack, count(DISTINCT j) AS jobCount " +
            "RETURN c.id AS id, c.name AS name, c.location AS location, techStack, jobCount, " +
            "c.industry AS industry, c.size AS size " +
            "ORDER BY jobCount DESC " +
            "LIMIT 500";

    private static final String MENTIONS_QUERY =
            "MATCH (a:Article)-[:MENTIONS]->(c:Company {id: $company_id}) " +
            "RETURN a.id AS id, a.title AS title, a.source_url AS url, " +
            "a.publish_date AS publishDate, a.source_platform AS sourcePlatform " +
            "ORDER BY a.publish_date DESC " +
            "LIMIT $limit";

    @Override
    public Flux<CompanyRaw> findAllWithTechStack() {
        return Neo4jReadTemplate.read(driver, session -> {
            List<CompanyRaw> result = new ArrayList<>();
            var queryResult = session.run(QUERY);
            for (Record r : queryResult.list()) {
                result.add(new CompanyRaw(
                        r.get("id").asString(),
                        nullableString(r, "name"),
                        nullableString(r, "location"),
                        r.get("techStack").asList(v -> v.asString()),
                        r.get("jobCount").asInt(),
                        nullableString(r, "industry"),
                        nullableString(r, "size")
                ));
            }
            log.info("Neo4jCompanyRepository found {} companies with a tech-stack signal", result.size());
            return result;
        }).flatMapIterable(list -> list);
    }

    @Override
    public Flux<CompanyMention> findMentions(String companyId, int limit) {
        return Neo4jReadTemplate.read(driver, session -> {
            List<CompanyMention> result = new ArrayList<>();
            var queryResult = session.run(MENTIONS_QUERY,
                    Values.parameters("company_id", companyId, "limit", limit));
            for (Record r : queryResult.list()) {
                result.add(new CompanyMention(
                        r.get("id").asString(),
                        nullableString(r, "title"),
                        nullableString(r, "url"),
                        nullableString(r, "publishDate"),
                        nullableString(r, "sourcePlatform")
                ));
            }
            return result;
        }).flatMapIterable(list -> list);
    }

    private static String nullableString(Record r, String key) {
        return r.get(key).isNull() ? null : r.get(key).asString();
    }
}
