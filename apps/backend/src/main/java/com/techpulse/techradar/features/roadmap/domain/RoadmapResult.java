package com.techpulse.techradar.features.roadmap.domain;

import com.techpulse.techradar.features.job.domain.JobMatch;

import java.util.List;
import java.util.Map;

/**
 * Aggregated view combining /recommend (next skills), /career (role roadmap) and job matches for
 * one user. This is the type cached under {@code cache:roadmap:<userId>} — see
 * {@code GetCareerRoadmapUseCase}.
 */
public record RoadmapResult(
        boolean hasTechnologies,
        List<String> currentTechnologies,
        List<Map<String, Object>> nextSkills,
        Map<String, Object> careerPath,
        List<JobMatch> jobMatches
) {

    public static RoadmapResult empty(List<String> currentTechnologies) {
        return new RoadmapResult(false, currentTechnologies, List.of(), Map.of(), List.of());
    }
}
