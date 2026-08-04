package com.techpulse.techradar.features.roadmap.domain;

import com.techpulse.techradar.features.salary.domain.SalaryInsight;

/**
 * Result of "what if I moved to this experience level" — see {@code SimulateLevelMoveUseCase}.
 * No forecast field (unlike {@link SimulationResult}): trend forecasting is tech-specific
 * (ai-rag-core's {@code /forecast}), there is no equivalent "trend" concept for a level.
 */
public record LevelMoveResult(
        String currentLevel,
        String targetLevel,
        long currentJobMatches,
        long simulatedJobMatches,
        SalaryInsight salary
) {
}
