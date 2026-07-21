package com.techpulse.techradar.features.radar.adapters.output;

import com.techpulse.techradar.features.radar.domain.TechAnalyticsRow;
import com.techpulse.techradar.features.radar.ports.TechAnalyticsWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * PostgreSQL adapter for {@link TechAnalyticsWritePort}. SQL moved verbatim from the
 * formerly-inline {@code RadarAnalyticsEtlService.upsert()}.
 */
@Repository
@RequiredArgsConstructor
public class PostgresTechAnalyticsWriteAdapter implements TechAnalyticsWritePort {

    private final DatabaseClient dbClient;

    @Override
    public Mono<Long> upsert(TechAnalyticsRow row) {
        DatabaseClient.GenericExecuteSpec spec = dbClient.sql(
                "INSERT INTO tech_analytics " +
                "(technology_name, month, job_count, article_count, growth_rate, yoy_growth, mom_growth, ranking) " +
                "VALUES (:tech, :month, :job, :article, :growth, :yoy, :mom, :ranking) " +
                "ON CONFLICT (technology_name, month) DO UPDATE SET " +
                "job_count = EXCLUDED.job_count, article_count = EXCLUDED.article_count, " +
                "growth_rate = EXCLUDED.growth_rate, yoy_growth = EXCLUDED.yoy_growth, " +
                "mom_growth = EXCLUDED.mom_growth, ranking = EXCLUDED.ranking"
        )
                .bind("tech", row.tech())
                .bind("month", row.month())
                .bind("job", row.jobCount())
                .bind("article", row.articleCount())
                .bind("growth", row.growthRate())
                .bind("yoy", row.yoyGrowth())
                .bind("mom", row.momGrowth());
        spec = row.ranking() != null ? spec.bind("ranking", row.ranking()) : spec.bindNull("ranking", Integer.class);

        return spec.fetch().rowsUpdated();
    }
}
