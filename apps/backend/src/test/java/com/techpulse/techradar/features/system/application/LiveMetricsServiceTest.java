package com.techpulse.techradar.features.system.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.kafka.domain.KafkaSyncStatus;
import com.techpulse.techradar.features.radar.ports.RadarQueryRepository;
import com.techpulse.techradar.features.social.ports.ReportRepository;
import com.techpulse.techradar.features.system.ports.ActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveMetricsServiceTest {

    @Mock
    private RadarQueryRepository radarQueryRepository;
    @Mock
    private ActivityLogRepository activityLog;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PipelineHealthService pipelineHealthService;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private LiveMetricsService service;

    @BeforeEach
    void setUp() {
        service = new LiveMetricsService(radarQueryRepository, activityLog, reportRepository,
                pipelineHealthService, redisTemplate, new ObjectMapper());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void liveMetrics_combinesCrawlerRadarAiRequestPipelineAndReportState() {
        when(valueOperations.get("crawler:status")).thenReturn(Mono.just(
                "{\"state\":\"idle\",\"startedAt\":\"2026-07-21T00:00:00Z\",\"finishedAt\":\"2026-07-21T00:05:00Z\","
                        + "\"successCount\":12,\"total\":12}"));
        when(valueOperations.get("radar:status")).thenReturn(Mono.just(
                "{\"state\":\"running\",\"startedAt\":\"2026-07-21T01:00:00Z\",\"finishedAt\":null,\"rowsUpserted\":null}"));
        when(radarQueryRepository.countNewTechnologiesThisMonth()).thenReturn(Mono.just(3L));
        when(activityLog.countToday("ai_request")).thenReturn(Mono.just(57L));
        when(reportRepository.countPending()).thenReturn(Mono.just(4L));
        KafkaSyncStatus syncStatus = new KafkaSyncStatus(100L, 2L, 80L, 1L, Instant.now(), Instant.now(), null, null);
        when(pipelineHealthService.pipelineHealth()).thenReturn(syncStatus);

        StepVerifier.create(service.liveMetrics())
                .assertNext(metrics -> {
                    assertThat(metrics.getCrawler().getState()).isEqualTo("idle");
                    assertThat(metrics.getCrawler().getSuccessCount()).isEqualTo(12);
                    assertThat(metrics.getRadar().getState()).isEqualTo("running");
                    assertThat(metrics.getNewTechnologiesThisMonth()).isEqualTo(3L);
                    assertThat(metrics.getAiRequestsToday()).isEqualTo(57L);
                    assertThat(metrics.getPendingReports()).isEqualTo(4L);
                    assertThat(metrics.getPipelineHealth()).isEqualTo(syncStatus);
                })
                .verifyComplete();
    }

    @Test
    void liveMetrics_defaultsToNeverRun_whenNeitherStatusKeyExistsYet() {
        when(valueOperations.get("crawler:status")).thenReturn(Mono.empty());
        when(valueOperations.get("radar:status")).thenReturn(Mono.empty());
        when(radarQueryRepository.countNewTechnologiesThisMonth()).thenReturn(Mono.just(0L));
        when(activityLog.countToday("ai_request")).thenReturn(Mono.just(0L));
        when(reportRepository.countPending()).thenReturn(Mono.just(0L));
        when(pipelineHealthService.pipelineHealth()).thenReturn(
                new KafkaSyncStatus(0L, 0L, 0L, 0L, null, null, null, null));

        StepVerifier.create(service.liveMetrics())
                .assertNext(metrics -> {
                    assertThat(metrics.getCrawler().getState()).isEqualTo("idle");
                    assertThat(metrics.getCrawler().getSuccessCount()).isNull();
                    assertThat(metrics.getRadar().getState()).isEqualTo("idle");
                    assertThat(metrics.getRadar().getStartedAt()).isNull();
                    assertThat(metrics.getPendingReports()).isEqualTo(0L);
                })
                .verifyComplete();
    }
}
