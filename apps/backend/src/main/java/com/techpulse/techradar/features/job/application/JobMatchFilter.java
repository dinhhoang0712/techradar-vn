package com.techpulse.techradar.features.job.application;

import com.techpulse.techradar.features.job.domain.JobMatch;

import java.util.Locale;

/**
 * A single independent predicate applied to a candidate {@link JobMatch} after Neo4j
 * scoring/mapping. Each filter dimension (location, salary range, ...) gets its own factory
 * method here instead of being inlined into {@link GetJobMatchesUseCase}, so adding a new
 * dimension is "add one factory method + one line building the active filter list" rather than
 * editing a monolithic filter chain.
 */
@FunctionalInterface
public interface JobMatchFilter {

    boolean matches(JobMatch job);

    /**
     * Case-insensitive substring match against the job's location. Caller is responsible for
     * only constructing this when {@code location} is non-null/non-blank.
     */
    static JobMatchFilter byLocation(String location) {
        String needle = location.toLowerCase(Locale.ROOT);
        return job -> job.location() != null
                && job.location().toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Passes jobs whose parsed max salary (triệu VND) is at least {@code minSalaryMVnd}. Jobs
     * with no parseable salary never pass.
     */
    static JobMatchFilter byMinSalary(double minSalaryMVnd) {
        return job -> job.salaryMaxVnd() != null && job.salaryMaxVnd() >= minSalaryMVnd;
    }
}
