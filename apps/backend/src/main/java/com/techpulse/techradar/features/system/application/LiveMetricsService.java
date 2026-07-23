package com.techpulse.techradar.features.system.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.domain.KafkaSyncStatus;
import com.techpulse.techradar.features.radar.etl.RadarStatus;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.features.system.domain.CrawlerStatus;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import com.techpulse.techradar.shared.redis.RedisJsonStatus;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The headline numbers for the admin live-metrics panel: articles crawled (last run),
 * technologies first tracked this month, whether a radar rebuild is currently running, how
 * many AI-proxy requests have been made today, Kafka-to-Neo4j sync health, and pending
 * moderation reports. Polled every few seconds by {@code AdminDashboardController}'s SSE
 * stream — each tick re-reads live state (Redis + Postgres) rather than pushing on a specific
 * event, since most of these sources (crawler, radar ETL, AI proxy, Kafka sync) have no event
 * bus of their own to hook into.
 */
@Component
@RequiredArgsConstructor
public class LiveMetricsService {

    private static final String CRAWLER_STATUS_KEY = "crawler:status";
    private static final String RADAR_STATUS_KEY = "radar:status";

    private final RadarQueryRepository radarQueryRepository;
    private final ActivityLogRepository activityLog;
    private final ReportRepository reportRepository;
    private final PipelineHealthService pipelineHealthService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<LiveMetrics> liveMetrics() {
        return Mono.zip(
                RedisJsonStatus.read(redisTemplate, objectMapper, CRAWLER_STATUS_KEY, CrawlerStatus.class, CrawlerStatus.neverRun()),
                RedisJsonStatus.read(redisTemplate, objectMapper, RADAR_STATUS_KEY, RadarStatus.class, RadarStatus.neverRun()),
                radarQueryRepository.countNewTechnologiesThisMonth(),
                activityLog.countToday("ai_request"),
                reportRepository.countPending()
        ).map(t -> LiveMetrics.builder()
                .crawler(t.getT1())
                .radar(t.getT2())
                .newTechnologiesThisMonth(t.getT3())
                .aiRequestsToday(t.getT4())
                .pendingReports(t.getT5())
                .pipelineHealth(pipelineHealthService.pipelineHealth())
                .build());
    }

    @Value
    @Builder
    public static class LiveMetrics {
        CrawlerStatus crawler;
        RadarStatus radar;
        long newTechnologiesThisMonth;
        long aiRequestsToday;
        long pendingReports;
        KafkaSyncStatus pipelineHealth;
    }
}
