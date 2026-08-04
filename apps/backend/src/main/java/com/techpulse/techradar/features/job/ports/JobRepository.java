package com.techpulse.techradar.features.job.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface JobRepository {

    /**
     * Jobs that require at least one of {@code userSkillsLower} (already lower-cased), ranked by
     * skill-overlap score. {@code required}/{@code matched} keep the graph's original casing so
     * they can be displayed as-is.
     */
    Flux<JobMatchRaw> findMatchingJobs(List<String> userSkillsLower, int limit);

    /** Total number of Job nodes indexed in the graph, for admin dashboards. */
    Mono<Long> countJobs();

    /** Most in-demand technologies/skills across all indexed jobs, most-requested first. */
    Flux<TechDemandRaw> topTechnologies(int limit);

    /**
     * Job count grouped by {@code level} (Intern/Fresher/Junior/Middle/Senior/Lead), for the
     * admin dashboard. Jobs with no classified level are excluded — see
     * {@code V38__job_level_enum.sql} and the crawler/silver pipeline normalizing free-text level
     * into this enum.
     */
    Flux<LevelDemandRaw> jobsByLevel();

    record TechDemandRaw(String name, long jobCount) {
    }

    record LevelDemandRaw(String level, long jobCount) {
    }

    record JobMatchRaw(
            String title,
            String company,
            String location,
            String salary,
            String level,
            String sourceUrl,
            String dueDate,
            List<String> required,
            List<String> matched,
            double score
    ) {
    }
}
