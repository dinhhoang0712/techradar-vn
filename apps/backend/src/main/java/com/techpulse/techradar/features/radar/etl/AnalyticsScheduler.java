package com.techpulse.techradar.features.radar.etl;

import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional periodic rebuild of {@code tech_analytics}.
 * Disabled by default; enable with {@code app.analytics.etl.enabled=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.analytics.etl.enabled", havingValue = "true")
public class AnalyticsScheduler {

    private final RadarAnalyticsEtlService etlService;
    private final ReactiveRedisCache redisCache;

    @Scheduled(cron = "${app.analytics.etl.cron:0 0 3 * * *}")
    public void scheduledRebuild() {
        log.info("Scheduled tech_analytics ETL starting");
        etlService.rebuild()
                .doOnSuccess(count -> log.info("Scheduled ETL done: {} rows, evicting radar cache", count))
                .flatMap(count -> redisCache.evictByPattern("cache:radar:*").thenReturn(count))
                .subscribe(
                        count -> log.info("Radar cache evicted after ETL"),
                        err -> log.error("Scheduled ETL failed", err));
    }
}
