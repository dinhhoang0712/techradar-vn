package com.techpulse.techradar.features.roadmap.domain;

import com.techpulse.techradar.features.salary.domain.SalaryInsight;

import java.util.Map;

/**
 * Result of "what if I learned this technology" — see {@code SimulateCareerMoveUseCase}.
 */
public record SimulationResult(
        String technology,
        long currentJobMatches,
        long simulatedJobMatches,
        SalaryInsight salary,
        Map<String, Object> forecast
) {
}
