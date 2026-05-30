package com.techpulse.techradar.features.radar.adapters.output;

import com.techpulse.techradar.features.radar.ports.RadarAnalyticsRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL adapter for analytics queries.
 * Implements RadarAnalyticsRepository using R2DBC.
 */
@Component
@RequiredArgsConstructor
public class PostgresRadarAnalyticsRepository implements RadarAnalyticsRepository {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Map<String, Object>> getAnalyticsByTechnology(String technology,
                                                               LocalDate startDate,
                                                               LocalDate endDate) {
        return dbClient.sql(
                "SELECT technology_name, " +
                "SUM(job_count) as total_jobs, " +
                "SUM(article_count) as total_articles, " +
                "AVG(growth_rate) as avg_growth " +
                "FROM tech_analytics " +
                "WHERE technology_name = :tech AND month BETWEEN :startDate AND :endDate " +
                "GROUP BY technology_name"
        )
                .bind("tech", technology)
                .bind("startDate", startDate)
                .bind("endDate", endDate)
                .map(row -> mapRowToAnalytics(row))
                .one();
    }

    @Override
    public Mono<List<Map<String, Object>>> getTopTechnologiesByJobCount(int limit, LocalDate month) {
        return dbClient.sql(
                "SELECT technology_name, job_count, article_count, growth_rate, ranking " +
                "FROM tech_analytics " +
                "WHERE month = :month " +
                "ORDER BY job_count DESC " +
                "LIMIT :limit"
        )
                .bind("month", month)
                .bind("limit", limit)
                .map(row -> mapRowToAnalytics(row))
                .all()
                .collectList();
    }

    @Override
    public Mono<List<Map<String, Object>>> getGrowthTrends(String technology) {
        return dbClient.sql(
                "SELECT month, job_count, article_count, growth_rate, yoy_growth, mom_growth " +
                "FROM tech_analytics " +
                "WHERE technology_name = :tech " +
                "ORDER BY month DESC LIMIT 24"
        )
                .bind("tech", technology)
                .map(row -> mapRowToTrend(row))
                .all()
                .collectList();
    }

    private Map<String, Object> mapRowToAnalytics(Row row) {
        Map<String, Object> map = new HashMap<>();
        map.put("technology_name", row.get("technology_name", String.class));
        map.put("total_jobs", row.get("total_jobs", Integer.class));
        map.put("total_articles", row.get("total_articles", Integer.class));
        map.put("avg_growth", row.get("avg_growth", Double.class));
        if (row.getMetadata().getColumnMetadata("ranking") != null) {
            map.put("ranking", row.get("ranking", Integer.class));
        }
        if (row.getMetadata().getColumnMetadata("job_count") != null) {
            map.put("job_count", row.get("job_count", Integer.class));
            map.put("article_count", row.get("article_count", Integer.class));
            map.put("growth_rate", row.get("growth_rate", Double.class));
        }
        return map;
    }

    private Map<String, Object> mapRowToTrend(Row row) {
        Map<String, Object> map = new HashMap<>();
        map.put("month", row.get("month", LocalDate.class));
        map.put("job_count", row.get("job_count", Integer.class));
        map.put("article_count", row.get("article_count", Integer.class));
        map.put("growth_rate", row.get("growth_rate", Double.class));
        map.put("yoy_growth", row.get("yoy_growth", Double.class));
        map.put("mom_growth", row.get("mom_growth", Double.class));
        return map;
    }
}
