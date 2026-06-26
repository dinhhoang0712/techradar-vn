package com.techpulse.techradar.features.radar.domain;

/**
 * Latest analytics snapshot for one technology (internal projection from {@code tech_analytics}).
 */
public record TechSnapshot(
        String name,
        int jobCount,
        double growthRate,
        double momRate,
        int jobsThisMonth
) {
}
