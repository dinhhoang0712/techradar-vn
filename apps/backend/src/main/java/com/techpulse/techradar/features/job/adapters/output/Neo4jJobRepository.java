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
            // Job-[:HIRES_FOR]->Company was written by the old knowledge-graph/ batch importer
            // (removed from the repo — see docs/DATABASE.md §4.1); it's frozen historical data,
            // no longer written by anything. Job-[:POSTED_BY]->Company is the live edge, written
            // by the real-time Kafka pipeline (KafkaNeo4jWriterService) and by
            // data-platform/gold/neo4j_job_sync.py — a job seen by only one of the two has only
            // one edge.
            // A job seen by both could match a company via each edge; collect+head picks one
            // instead of returning one JobMatchRaw row per matched company.
            "OPTIONAL MATCH (j)-[:POSTED_BY|HIRES_FOR]->(c:Company) " +
            "WITH j, requiredNames, matchedNames, head(collect(DISTINCT c)) AS c, " +
            "     toFloat(size(matchedNames)) / size(requiredNames) AS score " +
            // Job.title/source_url come from the batch Python importer; the real-time Kafka
            // pipeline (KafkaNeo4jWriterService) writes the same data as Job.name/Job.url instead
            // — coalesce so matching works regardless of which pipeline wrote the node.
            "RETURN coalesce(j.name, j.title) AS title, c.name AS company, c.location AS location, " +
            "       j.salary AS salary, j.level AS level, coalesce(j.url, j.source_url) AS sourceUrl, toString(j.due_date) AS dueDate, " +
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
                            nullableString(r, "level"),
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

    private static final String COUNT_JOBS_QUERY = "MATCH (j:Job) RETURN count(j) AS c";

    private static final String TOP_TECHNOLOGIES_QUERY =
            "MATCH (j:Job)-[:REQUIRES]->(t) WHERE t:Technology OR t:Skill " +
            "RETURN t.name AS name, count(DISTINCT j) AS jobCount " +
            "ORDER BY jobCount DESC LIMIT $limit";

    private static final String JOBS_BY_LEVEL_QUERY =
            "MATCH (j:Job) WHERE j.level IS NOT NULL " +
            "RETURN j.level AS level, count(j) AS jobCount " +
            "ORDER BY jobCount DESC";

    @Override
    public Mono<Long> countJobs() {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                return session.run(COUNT_JOBS_QUERY).single().get("c").asLong();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<TechDemandRaw> topTechnologies(int limit) {
        return Mono.fromCallable(() -> {
            List<TechDemandRaw> result = new ArrayList<>();
            try (Session session = driver.session()) {
                var queryResult = session.run(TOP_TECHNOLOGIES_QUERY, Map.of("limit", limit));
                for (Record r : queryResult.list()) {
                    result.add(new TechDemandRaw(r.get("name").asString(), r.get("jobCount").asLong()));
                }
            }
            return result;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(list -> list);
    }

    @Override
    public Flux<LevelDemandRaw> jobsByLevel() {
        return Mono.fromCallable(() -> {
            List<LevelDemandRaw> result = new ArrayList<>();
            try (Session session = driver.session()) {
                var queryResult = session.run(JOBS_BY_LEVEL_QUERY);
                for (Record r : queryResult.list()) {
                    result.add(new LevelDemandRaw(r.get("level").asString(), r.get("jobCount").asLong()));
                }
            }
            return result;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(list -> list);
    }

    private static String nullableString(Record r, String key) {
        return r.get(key).isNull() ? null : r.get(key).asString();
    }
}
