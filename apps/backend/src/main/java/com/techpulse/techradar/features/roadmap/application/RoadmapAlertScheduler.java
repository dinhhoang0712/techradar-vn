package com.techpulse.techradar.features.roadmap.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional weekly scan for proactive "your top recommended skill is trending" alerts.
 * Disabled by default; enable with {@code app.roadmap.alert.enabled=true}. Runs after the daily
 * {@code tech_analytics} ETL (see {@code AnalyticsScheduler}, default 3 AM) so growth data is fresh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.roadmap.alert.enabled", havingValue = "true")
public class RoadmapAlertScheduler {

    private final RoadmapAlertService roadmapAlertService;

    @Scheduled(cron = "${app.roadmap.alert.cron:0 0 4 * * MON}")
    public void scheduledScan() {
        log.info("Scheduled roadmap alert scan starting");
        roadmapAlertService.runOnce()
                .subscribe(
                        count -> log.info("Roadmap alert scan published {} alert(s)", count),
                        err -> log.error("Scheduled roadmap alert scan failed", err));
    }
}
