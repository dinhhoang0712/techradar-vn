package com.techpulse.techradar.features.job.adapters.input;

import com.techpulse.techradar.features.job.domain.JobMatch;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

public class JobDtos {

    @Value
    @Builder
    public static class JobMatchResponse {
        String title;
        String company;
        String location;
        String salaryRaw;
        Double salaryMinMVnd;
        Double salaryMaxMVnd;
        String sourceUrl;
        LocalDate dueDate;
        List<String> matchedSkills;
        List<String> missingSkills;
        double score;

        public static JobMatchResponse from(JobMatch m) {
            return JobMatchResponse.builder()
                    .title(m.title())
                    .company(m.company())
                    .location(m.location())
                    .salaryRaw(m.salaryRaw())
                    .salaryMinMVnd(m.salaryMinVnd())
                    .salaryMaxMVnd(m.salaryMaxVnd())
                    .sourceUrl(m.sourceUrl())
                    .dueDate(m.dueDate())
                    .matchedSkills(m.matchedSkills())
                    .missingSkills(m.missingSkills())
                    .score(m.score())
                    .build();
        }
    }
}
