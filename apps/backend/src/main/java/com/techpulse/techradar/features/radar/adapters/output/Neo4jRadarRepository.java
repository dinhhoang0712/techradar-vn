package com.techpulse.techradar.features.radar.adapters.output;

import com.techpulse.techradar.features.radar.domain.RadarTrend;
import com.techpulse.techradar.features.radar.ports.RadarRepository;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j adapter for radar queries.
 * Implements RadarRepository using native Neo4j driver.
 */
@Component
@RequiredArgsConstructor
public class Neo4jRadarRepository implements RadarRepository {

    private final Driver driver;

    @Override
    public Flux<RadarTrend> findTopTechnologies(int limit) {
        return Mono.fromCallable(() -> {
            List<RadarTrend> trends = new ArrayList<>();
            try (Session session = driver.session()) {
                String query = "MATCH (t:Technology) " +
                        "RETURN t.name as name, t.job_count as jobCount, " +
                        "t.article_count as articleCount, t.growth_rate as growthRate " +
                        "ORDER BY t.growth_rate DESC LIMIT $limit";

                var result = session.run(query, java.util.Map.of("limit", limit));
                for (Record record : result.list()) {
                    RadarTrend trend = RadarTrend.builder()
                            .technologyName(record.get("name").asString())
                            .jobCount(record.get("jobCount").isNull() ? 0 : record.get("jobCount").asInt())
                            .articleCount(record.get("articleCount").isNull() ? 0 : record.get("articleCount").asInt())
                            .growthRate(record.get("growthRate").isNull() ? 0.0 : record.get("growthRate").asDouble())
                            .build();
                    trends.add(trend);
                }
            }
            return trends;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(trends -> trends);
    }

    @Override
    public Flux<RadarTrend> findTrendsByTechnology(String technology) {
        return Mono.fromCallable(() -> {
            List<RadarTrend> trends = new ArrayList<>();
            try (Session session = driver.session()) {
                String query = "MATCH (t:Technology {name: $name})-[r:HAS_TREND]->(tm:TrendMonth) " +
                        "RETURN t.name as name, tm.date as month, tm.job_count as jobCount, " +
                        "tm.article_count as articleCount, tm.growth_rate as growthRate " +
                        "ORDER BY tm.date DESC";

                var result = session.run(query, java.util.Map.of("name", technology));
                for (Record record : result.list()) {
                    RadarTrend trend = RadarTrend.builder()
                            .technologyName(record.get("name").asString())
                            .month(LocalDate.parse(record.get("month").asString()))
                            .jobCount(record.get("jobCount").isNull() ? 0 : record.get("jobCount").asInt())
                            .articleCount(record.get("articleCount").isNull() ? 0 : record.get("articleCount").asInt())
                            .growthRate(record.get("growthRate").isNull() ? 0.0 : record.get("growthRate").asDouble())
                            .build();
                    trends.add(trend);
                }
            }
            return trends;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(trends -> trends);
    }

    @Override
    public Flux<RadarTrend> searchTrends(String keyword, int limit) {
        return Mono.fromCallable(() -> {
            List<RadarTrend> trends = new ArrayList<>();
            try (Session session = driver.session()) {
                String query = "MATCH (t:Technology) " +
                        "WHERE t.name CONTAINS $keyword " +
                        "RETURN t.name as name, t.job_count as jobCount, " +
                        "t.article_count as articleCount, t.growth_rate as growthRate " +
                        "ORDER BY t.growth_rate DESC LIMIT $limit";

                var result = session.run(query,
                        java.util.Map.of("keyword", keyword, "limit", limit));
                for (Record record : result.list()) {
                    RadarTrend trend = RadarTrend.builder()
                            .technologyName(record.get("name").asString())
                            .jobCount(record.get("jobCount").isNull() ? 0 : record.get("jobCount").asInt())
                            .articleCount(record.get("articleCount").isNull() ? 0 : record.get("articleCount").asInt())
                            .growthRate(record.get("growthRate").isNull() ? 0.0 : record.get("growthRate").asDouble())
                            .build();
                    trends.add(trend);
                }
            }
            return trends;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(trends -> trends);
    }

    @Override
    public Mono<RadarTrend> findByTechnologyAndMonth(String technology, LocalDate month) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                String query = "MATCH (t:Technology {name: $name})-[:HAS_TREND]->(tm:TrendMonth {date: $date}) " +
                        "RETURN t.name as name, tm.date as month, tm.job_count as jobCount, " +
                        "tm.article_count as articleCount, tm.growth_rate as growthRate";

                var result = session.run(query,
                        java.util.Map.of("name", technology, "date", month.toString()));

                if (result.list().isEmpty()) {
                    return null;
                }

                Record record = result.single();
                return RadarTrend.builder()
                        .technologyName(record.get("name").asString())
                        .month(LocalDate.parse(record.get("month").asString()))
                        .jobCount(record.get("jobCount").isNull() ? 0 : record.get("jobCount").asInt())
                        .articleCount(record.get("articleCount").isNull() ? 0 : record.get("articleCount").asInt())
                        .growthRate(record.get("growthRate").isNull() ? 0.0 : record.get("growthRate").asDouble())
                        .build();
            }
        })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
