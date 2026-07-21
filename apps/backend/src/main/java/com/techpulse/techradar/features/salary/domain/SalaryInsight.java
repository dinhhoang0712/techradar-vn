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

    /**
     * Builds a {@link SalaryInsight} from parsed salary midpoints (triệu VND), computing
     * {@link SalaryStats} and rounding every stat to one decimal place. {@code midpoints} may be
     * empty — {@link SalaryStats#compute} already returns all-zero stats for that case, so callers
     * don't need a special-cased branch.
     */
    public static SalaryInsight fromMidpoints(String techName, int totalJobs, List<Double> midpoints,
                                                List<String> topCoTechs) {
        SalaryStats.Stats stats = SalaryStats.compute(midpoints);
        return new SalaryInsight(
                techName,
                totalJobs,
                midpoints.size(),
                round(stats.median()),
                round(stats.avg()),
                round(stats.min()),
                round(stats.max()),
                round(stats.p25()),
                round(stats.p75()),
                topCoTechs
        );
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /**
     * Human-readable "typical range" label from this insight's 25th/75th percentile, e.g.
     * {@code "15 - 25 triệu VND"}, or {@code "N/A"} when there's no salary data at all.
     */
    public String salaryRangeLabel() {
        if (p25Vnd <= 0 && p75Vnd <= 0) {
            return "N/A";
        }
        return String.format("%.0f - %.0f triệu VND", p25Vnd, p75Vnd);
    }
}