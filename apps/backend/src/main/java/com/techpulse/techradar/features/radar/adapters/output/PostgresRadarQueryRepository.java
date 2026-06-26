package com.techpulse.techradar.features.radar.adapters.output;

import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * PostgreSQL adapter for radar/compare analytics over {@code tech_analytics}.
 */
@Repository
@RequiredArgsConstructor
public class PostgresRadarQueryRepository implements RadarQueryRepository {

    private final DatabaseClient dbClient;

    @Override
    public Flux<TechSnapshot> topTechnologies(int limit) {
        return dbClient.sql(
                "SELECT name, job_count, growth_rate, mom_growth FROM (" +
                "  SELECT DISTINCT ON (technology_name) technology_name AS name, " +
                "         job_count, growth_rate, COALESCE(mom_growth, 0) AS mom_growth " +
                "  FROM tech_analytics ORDER BY technology_name, month DESC" +
                ") latest ORDER BY job_count DESC LIMIT :limit"
        )
                .bind("limit", limit)
                .map((row, meta) -> new TechSnapshot(
                        row.get("name", String.class),
                        intval(row, "job_count"),
                        dblval(row, "growth_rate"),
                        dblval(row, "mom_growth"),
                        intval(row, "job_count")))
                .all();
    }

    @Override
    public Flux<MonthlyCount> monthlySeries(List<String> keywords, int months) {
        String[] names = keywords.stream().map(s -> s == null ? "" : s.toLowerCase()).toArray(String[]::new);
        return dbClient.sql(
                "SELECT technology_name AS name, " +
                "       EXTRACT(YEAR FROM month)::int AS yr, " +
                "       EXTRACT(MONTH FROM month)::int AS mon, " +
                "       job_count, article_count, COALESCE(yoy_growth,0) AS yoy, " +
                "       COALESCE(mom_growth,0) AS mom, growth_rate " +
                "FROM tech_analytics " +
                "WHERE lower(technology_name) = ANY(:names) " +
                "  AND month >= (CURRENT_DATE - make_interval(months => :months)) " +
                "ORDER BY month ASC"
        )
                .bind("names", names)
                .bind("months", months)
                .map((row, meta) -> new MonthlyCount(
                        row.get("name", String.class),
                        intval(row, "yr"),
                        intval(row, "mon"),
                        intval(row, "job_count"),
                        intval(row, "article_count"),
                        dblval(row, "yoy"),
                        dblval(row, "mom"),
                        dblval(row, "growth_rate")))
                .all();
    }

    private static int intval(Row row, String col) {
        Integer v = row.get(col, Integer.class);
        return v != null ? v : 0;
    }

    private static double dblval(Row row, String col) {
        Double v = row.get(col, Double.class);
        return v != null ? v : 0.0;
    }
}
