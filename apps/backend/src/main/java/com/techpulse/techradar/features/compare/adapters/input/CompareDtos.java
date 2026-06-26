package com.techpulse.techradar.features.compare.adapters.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Client-facing compare response DTOs (snake_case).
 */
public final class CompareDtos {

    private CompareDtos() {
    }

    /** One technology's comparison series: keyword, yoy_rate, mom_rate, growth_rate, monthly[]. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CompareItem {
        private String keyword;
        private double yoyRate;
        private double momRate;
        private double growthRate;
        private List<MonthlyPoint> monthly;
    }

    /** One month: month, year, job_count. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MonthlyPoint {
        private int month;
        private int year;
        private int jobCount;
    }
}
