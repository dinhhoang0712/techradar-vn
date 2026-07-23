package com.techpulse.techradar.features.company.domain;

import java.util.List;

/**
 * Aggregate 0–100 score for how much a company's inferred tech stack is trending up (growing market
 * demand) vs. down, derived from {@code tech_analytics}. See {@link CompanyTechHealthScoreCalculator}.
 */
public record CompanyTechHealthScore(
        boolean available,
        int score,
        String label,
        int stackSize,
        int trackedCount,
        List<String> strengths,
        List<String> watchOuts
) {
}
