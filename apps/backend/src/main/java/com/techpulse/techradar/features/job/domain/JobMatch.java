package com.techpulse.techradar.features.job.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * A job posting scored against a user's profile skills.
 * Salary fields are normalized to triệu VND when {@code raw.salary} parses (see SalaryParser); null otherwise.
 */
public record JobMatch(
        String title,
        String company,
        String location,
        String salaryRaw,
        Double salaryMinVnd,
        Double salaryMaxVnd,
        String level,
        String sourceUrl,
        LocalDate dueDate,
        List<String> matchedSkills,
        List<String> missingSkills,
        double score
) {
}
