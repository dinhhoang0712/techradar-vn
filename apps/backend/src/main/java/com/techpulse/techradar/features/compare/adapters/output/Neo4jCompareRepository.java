package com.techpulse.techradar.features.compare.adapters.output;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.ports.CompareRepository;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Neo4j adapter for technology comparison.
 */
@Component
@RequiredArgsConstructor
public class Neo4jCompareRepository implements CompareRepository {

    private final Driver driver;

    @Override
    public Mono<TechComparison> compareTechnologies(String tech1, String tech2) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                String query = "MATCH (t1:Technology {name: $tech1}), (t2:Technology {name: $tech2}) " +
                        "RETURN " +
                        "t1.name as tech1, t2.name as tech2, " +
                        "t1.growth_rate as growth1, t2.growth_rate as growth2, " +
                        "t1.job_count as jobs1, t2.job_count as jobs2, " +
                        "t1.article_count as articles1, t2.article_count as articles2";

                var result = session.run(query, java.util.Map.of("tech1", tech1, "tech2", tech2));

                if (result.list().isEmpty()) {
                    return null;
                }

                Record record = result.single();
                return TechComparison.builder()
                        .technology1(record.get("tech1").asString())
                        .technology2(record.get("tech2").asString())
                        .growthRate1(record.get("growth1").isNull() ? 0.0 : record.get("growth1").asDouble())
                        .growthRate2(record.get("growth2").isNull() ? 0.0 : record.get("growth2").asDouble())
                        .jobCount1(record.get("jobs1").isNull() ? 0 : record.get("jobs1").asInt())
                        .jobCount2(record.get("jobs2").isNull() ? 0 : record.get("jobs2").asInt())
                        .articleCount1(record.get("articles1").isNull() ? 0 : record.get("articles1").asInt())
                        .articleCount2(record.get("articles2").isNull() ? 0 : record.get("articles2").asInt())
                        .build();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
