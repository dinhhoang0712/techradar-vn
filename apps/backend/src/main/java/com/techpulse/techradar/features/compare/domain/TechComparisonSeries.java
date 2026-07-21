package com.techpulse.techradar.features.compare.domain;

import com.techpulse.techradar.features.radar.domain.MonthlyCount;

import java.util.List;

/**
 * One technology's grouped monthly comparison series, as returned by {@code /compare/search}:
 * the latest yoy/mom/growth rate plus the full monthly history that produced it.
 */
public record TechComparisonSeries(
        String name,
        double yoyRate,
        double momRate,
        double growthRate,
        List<MonthlyCount> monthly
) {
}
