package com.techpulse.techradar.features.roadmap.adapters.input;

import com.techpulse.techradar.features.job.adapters.input.JobDtos;
import com.techpulse.techradar.features.roadmap.domain.RoadmapResult;

import java.util.List;
import java.util.Map;

public class RoadmapDtos {

    public record RoadmapResponse(
            boolean hasTechnologies,
            List<String> currentTechnologies,
            List<Map<String, Object>> nextSkills,
            Map<String, Object> careerPath,
            List<JobDtos.JobMatchResponse> jobMatches
    ) {
        public static RoadmapResponse from(RoadmapResult r) {
            return new RoadmapResponse(
                    r.hasTechnologies(),
                    r.currentTechnologies(),
                    r.nextSkills(),
                    r.careerPath(),
                    r.jobMatches().stream().map(JobDtos.JobMatchResponse::from).toList());
        }
    }
}
