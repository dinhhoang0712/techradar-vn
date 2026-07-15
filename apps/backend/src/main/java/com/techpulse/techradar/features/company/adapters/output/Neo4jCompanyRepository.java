package com.techpulse.techradar.features.company.adapters.output;

import com.techpulse.techradar.features.company.ports.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    // Company.USES is declared in the schema but never written by any ingestion pipeline
    // (Kafka writer only sets Job-[:POSTED_BY]->Company); the tech stack signal that actually
    // exists comes from the technologies required by a company's own job postings.
    private static final String QUERY =
            "MATCH (c:Company)<-[:POSTED_BY]-(j:Job)-[:REQUIRES]->(t) " +
            "WHERE t:Technology OR t:Skill " +
            "WITH c, collect(DISTINCT t.name) AS techStack, count(DISTINCT j) AS jobCount " +
            "RETURN c.id AS id, c.name AS name, c.location AS location, techStack, jobCount " +
            "ORDER BY jobCount DESC " +
            "LIMIT 500";

    @Override
    public Flux<CompanyRaw> findAllWithTechStack() {
        return Mono.fromCallable(() -> {
            List<CompanyRaw> result = new ArrayList<>();
            try (Session session = driver.session()) {
                var queryResult = session.run(QUERY);
                for (Record r : queryResult.list()) {
                    result.add(new CompanyRaw(
                            r.get("id").asString(),
                            nullableString(r, "name"),
                            nullableString(r, "location"),
                            r.get("techStack").asList(v -> v.asString()),
                            r.get("jobCount").asInt()
                    ));
                }
            }
            log.info("Neo4jCompanyRepository found {} companies with a tech-stack signal", result.size());
            return result;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(list -> list);
    }

    private static String nullableString(Record r, String key) {
        return r.get(key).isNull() ? null : r.get(key).asString();
    }
}
