package com.techpulse.techradar.features.radar.etl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of the {@code tech_analytics} ETL run state, written to the {@code radar:status} Redis
 * key by {@link RadarAnalyticsEtlService#rebuild()} and read back by the admin live-metrics
 * dashboard — the same pattern {@code crawler:status} uses for the crawler's own on/off state.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RadarStatus {
    private String state; // "running" | "idle"
    private String startedAt;
    private String finishedAt;
    private Long rowsUpserted;

    public static RadarStatus running(String startedAt) {
        return new RadarStatus("running", startedAt, null, null);
    }

    public static RadarStatus idle(String startedAt, String finishedAt, Long rowsUpserted) {
        return new RadarStatus("idle", startedAt, finishedAt, rowsUpserted);
    }

    public static RadarStatus neverRun() {
        return new RadarStatus("idle", null, null, null);
    }
}
