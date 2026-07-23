package com.techpulse.techradar.features.radar.adapters.input;

import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Client-facing radar response DTOs (serialized snake_case to match the web/mobile clients).
 * Shared by {@link RadarController}'s REST endpoints and
 * {@link com.techpulse.techradar.features.radar.realtime.RadarBroadcaster}'s SSE snapshots, so
 * both shapes always stay in sync.
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

        public static Top4Item from(TechSnapshot t) {
            return new Top4Item(t.name(), t.growthRate(), t.jobCount(), t.momRate(), t.jobsThisMonth());
        }
    }

    /** {@code GET /radar/top10} item: keyword, job_count. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Top10Item {
        private String keyword;
        private int jobCount;

        public static Top10Item from(TechSnapshot t) {
            return new Top10Item(t.name(), t.jobCount());
        }
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
