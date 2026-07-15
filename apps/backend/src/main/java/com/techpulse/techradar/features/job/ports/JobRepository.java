package com.techpulse.techradar.features.job.ports;

import reactor.core.publisher.Flux;

import java.util.List;

public interface JobRepository {

    /**
     * Jobs that require at least one of {@code userSkillsLower} (already lower-cased), ranked by
     * skill-overlap score. {@code required}/{@code matched} keep the graph's original casing so
     * they can be displayed as-is.
     */
    Flux<JobMatchRaw> findMatchingJobs(List<String> userSkillsLower, int limit);

    record JobMatchRaw(
            String title,
            String company,
            String location,
            String salary,
            String sourceUrl,
            String dueDate,
            List<String> required,
            List<String> matched,
            double score
    ) {
    }
}
