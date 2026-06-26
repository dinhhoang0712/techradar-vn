package com.techpulse.techradar.features.radar.domain;

/**
 * One month of analytics for one technology (internal projection from {@code tech_analytics}).
 */
public record MonthlyCount(
        String name,
        int year,
        int month,
        int jobCount,
        int articleCount,
        double yoyRate,
        double momRate,
        double growthRate
) {
    /**
     * Display activity for charts: job postings when available, otherwise article-mention activity
     * (job postings often lack reliable per-month dates in the graph).
     */
    public int activity() {
        return jobCount > 0 ? jobCount : articleCount;
    }
}
