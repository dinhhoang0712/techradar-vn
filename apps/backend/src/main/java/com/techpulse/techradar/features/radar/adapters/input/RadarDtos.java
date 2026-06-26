package com.techpulse.techradar.features.radar.adapters.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Client-facing radar response DTOs (serialized snake_case to match the web/mobile clients).
 */
public final class RadarDtos {

    private RadarDtos() {
    }

    /** {@code GET /radar/top4} item: industry, growth_rate, job_count, mom_rate, jobs_this_month. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Top4Item {
        private String industry;
        private double growthRate;
        private int jobCount;
        private double momRate;
        private int jobsThisMonth;
    }

    /** {@code GET /radar/top10} item: keyword, job_count. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Top10Item {
        private String keyword;
        private int jobCount;
    }

    /** {@code GET /radar/search} point: month, year, keywords{tech: count}. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendPoint {
        private int month;
        private int year;
        private Map<String, Integer> keywords;
    }
}
