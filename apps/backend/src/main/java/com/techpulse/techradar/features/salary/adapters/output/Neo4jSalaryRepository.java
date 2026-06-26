package com.techpulse.techradar.features.salary.adapters.output;

import com.techpulse.techradar.features.salary.ports.SalaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jSalaryRepository implements SalaryRepository {

    private final Driver driver;

    @Override
    public Flux<TechSalaryRaw> findTechSalaries(int minJobs, int techLimit) {
        return Mono.fromCallable(() -> {
            List<TechSalaryRaw> result = new ArrayList<>();
            try (Session session = driver.session()) {
                String query =
                        "MATCH (j:Job)-[:REQUIRES]->(t:Technology) " +
                        "WHERE j.salary IS NOT NULL AND trim(j.salary) <> '' " +
                        "WITH t.name AS techName, collect(j.salary) AS salaries, count(j) AS totalJobs " +
                        "WHERE totalJobs >= $minJobs " +
                        "RETURN techName, salaries, totalJobs " +
                        "ORDER BY totalJobs DESC " +
                        "LIMIT $limit";

                for (Record r : session.run(query, Map.of("minJobs", minJobs, "limit", techLimit)).list()) {
                    result.add(new TechSalaryRaw(
                            r.get("techName").asString(),
                            r.get("totalJobs").asInt(),
                            r.get("salaries").asList(v -> v.asString())
                    ));
                }
            }
            log.info("Neo4jSalaryRepository found {} techs with salary data", result.size());
            return result;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapIterable(list -> list);
    }

    @Override
    public Mono<TechSalaryDetailRaw> findTechSalaryDetail(String techName) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                // Total jobs + salary strings for the target tech
                String salaryQuery =
                        "MATCH (j:Job)-[:REQUIRES]->(t:Technology) " +
                        "WHERE toLower(t.name) = toLower($techName) " +
                        "RETURN count(j) AS totalJobs, " +
                        "       [x IN collect(j.salary) WHERE x IS NOT NULL AND trim(x) <> ''] AS salaries";

                var salaryResult = session.run(salaryQuery, Map.of("techName", techName)).list();
                if (salaryResult.isEmpty()) {
                    return new TechSalaryDetailRaw(techName, 0, List.of(), List.of());
                }

                Record sr = salaryResult.get(0);
                int totalJobs = sr.get("totalJobs").asInt();
                List<String> salaries = sr.get("salaries").asList(v -> v.asString());

                // Co-required technologies (for "stack context")
                String coQuery =
                        "MATCH (j:Job)-[:REQUIRES]->(t:Technology) " +
                        "WHERE toLower(t.name) = toLower($techName) " +
                        "WITH j " +
                        "MATCH (j)-[:REQUIRES]->(co:Technology) " +
                        "WHERE toLower(co.name) <> toLower($techName) " +
                        "RETURN co.name AS coTech, count(j) AS cnt " +
                        "ORDER BY cnt DESC LIMIT 10";

                List<Map.Entry<String, Integer>> coTechs = new ArrayList<>();
                for (Record r : session.run(coQuery, Map.of("techName", techName)).list()) {
                    coTechs.add(new AbstractMap.SimpleEntry<>(
                            r.get("coTech").asString(),
                            r.get("cnt").asInt()
                    ));
                }

                return new TechSalaryDetailRaw(techName, totalJobs, salaries, coTechs);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}