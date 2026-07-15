package com.techpulse.techradar.features.job.adapters.output;

import com.techpulse.techradar.features.job.ports.JobRepository;
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
import java.util.Map;

/**
 * Neo4j adapter for job/skill-overlap matching.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jJobRepository implements JobRepository {

    private final Driver driver;

    private static final String QUERY =
            "MATCH (j:Job)-[:REQUIRES]->(t) " +
            "WHERE t:Technology OR t:Skill " +
            // DISTINCT: Technology and Skill nodes can both hold the exact same name (e.g. "AI"
            // exists as both a Technology and a Skill node) — without it the same name is
            // double-counted, skewing the score and duplicating chips in the UI.
            "WITH j, collect(DISTINCT t.name) AS requiredNames " +
            "WHERE size(requiredNames) > 0 " +
            "WITH j, requiredNames, " +
            "     [n IN requiredNames WHERE toLower(n) IN $userSkillsLower] AS matchedNames " +
            "WHERE size(matchedNames) > 0 " +
            "OPTIONAL MATCH (j)-[:POSTED_BY]->(c:Company) " +
            "WITH j, c, requiredNames, matchedNames, " +
            "     toFloat(size(matchedNames)) / size(requiredNames) AS score " +
            // Job.title/source_url come from the batch Python importer; the real-time Kafka
            // pipeline (KafkaNeo4jWriterService) writes the same data as Job.name/Job.url instead
            // — coalesce so matching works regardless of which pipeline wrote the node.
            "RETURN coalesce(j.name, j.title) AS title, c.name AS company, c.location AS location, " +
            "       j.salary AS salary, coalesce(j.url, j.source_url) AS sourceUrl, toString(j.due_date) AS dueDate, " +
            "       requiredNames AS required, matchedNames AS matched, score " +
            "ORDER BY score DESC " +
            "LIMIT $limit";

    @Override
    public Flux<JobMatchRaw> findMatchingJobs(List<String> userSkillsLower, int limit) {
        return Mono.fromCallable(() -> {
            List<JobMatchRaw> result = new ArrayList<>();
            if (userSkillsLower == null || userSkillsLower.isEmpty()) {
                return result;
            }

            try (Session session = driver.session()) {
                var queryResult = session.run(QUERY, Map.of(
                        "userSkillsLower", userSkillsLower,
                        "limit", limit
                ));
                for (Record r : queryResult.list()) {
                    result.add(new JobMatchRaw(
                            nullableString(r, "title"),
                            nullableString(r, "company"),
                            nullableString(r, "location"),
                            nullableString(r, "salary"),
                            nullableString(r, "sourceUrl"),
                            nullableString(r, "dueDate"),
                            r.get("required").asList(v -> v.asString()),
                            r.get("matched").asList(v -> v.asString()),
                            r.get("score").asDouble()
                    ));
                }
            }
            log.info("Neo4jJobRepository found {} matching jobs for {} skills", result.size(), userSkillsLower.size());
            return result;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(list -> list);
    }

    private static String nullableString(Record r, String key) {
        return r.get(key).isNull() ? null : r.get(key).asString();
    }
}
