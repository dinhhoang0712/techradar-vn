package com.techpulse.techradar.features.system.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Last known crawl run, read back from the {@code crawler:status} Redis key the Python crawler
 * writes (see {@code services/crawler/run_all.py}) for the admin live-metrics dashboard. Field
 * names are camelCase; the shared Jackson {@link com.fasterxml.jackson.databind.ObjectMapper}'s
 * snake_case naming strategy maps them onto the crawler's {@code started_at}/{@code success_count}
 * JSON keys.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerStatus {
    private String state; // "running" | "idle" | "unknown"
    private String startedAt;
    private String finishedAt;
    private Integer successCount;
    private Integer total;

    public static CrawlerStatus neverRun() {
        return new CrawlerStatus("idle", null, null, null, null);
    }
}
