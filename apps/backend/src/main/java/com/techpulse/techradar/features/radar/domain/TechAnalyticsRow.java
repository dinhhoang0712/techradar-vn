package com.techpulse.techradar.features.radar.domain;

import java.time.LocalDate;

/**
 * One (technology, month) row of the {@code tech_analytics} time series, as produced by
 * {@link TechAnalyticsTransformer} and persisted by the ETL rebuild.
 */
public record TechAnalyticsRow(
        String tech,
        LocalDate month,
        int jobCount,
        int articleCount,
        double growthRate,
        double yoyGrowth,
        double momGrowth,
        Integer ranking
) {
}
