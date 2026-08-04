package com.techpulse.techradar.features.radar.adapters.input;

import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(example = "Go")
        private String industry;
        @Schema(description = "Year-over-year growth (%).", example = "42.5")
        private double growthRate;
        @Schema(description = "Total distinct jobs currently requiring this technology.", example = "128")
        private int jobCount;
        @Schema(description = "Month-over-month growth (%) — the figure trend alerts threshold on.", example = "31.0")
        private double momRate;
        @Schema(description = "Jobs posted this calendar month.", example = "14")
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
        @Schema(example = "Kubernetes")
        private String keyword;
        @Schema(example = "96")
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
        @Schema(example = "6")
        private int month;
        @Schema(example = "2026")
        private int year;
        @Schema(description = "Activity count for that month, keyed by requested technology name.",
                example = "{\"Python\": 42, \"Go\": 17}")
        private Map<String, Integer> keywords;
    }
}
