package com.techpulse.techradar.features.salary.domain;

import java.util.List;

/**
 * Aggregated salary statistics for one technology (all values in triệu VND).
 */
public record SalaryInsight(
        String techName,
        int totalJobs,
        int jobsWithSalary,
        double medianVnd,
        double avgVnd,
        double minVnd,
        double maxVnd,
        double p25Vnd,
        double p75Vnd,
        List<String> topCoTechs
) {
}