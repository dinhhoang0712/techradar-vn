package com.techpulse.techradar.features.system.adapters.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.techradar.features.system.application.DataPlatformJobStatusService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Admin-triggered on-demand runs for the 5 data-platform gold jobs that have no manual trigger
 * yet ({@code neo4j_article_sync}, {@code neo4j_job_sync}, {@code neo4j_enricher},
 * {@code tech_dedup}, {@code embed_trigger}). {@code gold_pg_etl} and {@code retrain_clustering}
 * are deliberately excluded — they already have their own trigger endpoints
 * ({@link com.techpulse.techradar.features.radar.adapters.input.AnalyticsAdminController},
 * {@link com.techpulse.techradar.features.clustering.adapters.input.AdminClusteringController}).
 * <p>
 * data-platform has no HTTP server of its own (only Kafka consumer threads + APScheduler), so —
 * same as {@link CrawlerAdminController} — this only publishes a {@code data-platform:trigger}
 * Redis Pub/Sub message; the Python side subscribes and calls
 * {@code scheduler.modify_job(job_id, next_run_time=now())}. Status is read directly from
 * {@code dp_pipeline_runs}, the Postgres audit table those jobs already write to on every run, so
 * (unlike the crawler) no Redis status key is needed here.
 */
@Slf4j
@Tag(name = "Admin", description = "Data platform gold job on-demand trigger")
@RestController
@RequestMapping("/admin/data-platform")
@RequiredArgsConstructor
public class AdminDataPlatformController {

    private static final String TRIGGER_CHANNEL = "data-platform:trigger";

    /** Must match ALLOWED_JOB_IDS in data-platform/common/job_trigger_listener.py. */
    private static final List<String> JOB_IDS = List.of(
            "neo4j_article_sync", "neo4j_job_sync", "neo4j_enricher", "tech_dedup", "embed_trigger");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DataPlatformJobStatusService jobStatusService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "List the 5 data-platform gold jobs with their latest run status")
    @GetMapping("/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<Map<String, Object>>>>> listJobs() {
        return jobStatusService.findLatestStatuses(JOB_IDS)
                .collectMap(row -> (String) row.get("job_name"))
                .map(byName -> JOB_IDS.stream()
                        .map(id -> byName.getOrDefault(id, Map.<String, Object>of("job_name", id, "status", "never_run")))
                        .toList())
                .map(list -> ResponseEntity.ok(ApiResponse.success(list)));
    }

    @Operation(summary = "Trigger an immediate run of a data-platform gold job instead of waiting for its cron schedule")
    @PostMapping("/jobs/{jobId}/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> trigger(@PathVariable String jobId) {
        if (!JOB_IDS.contains(jobId)) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponse.<Map<String, Object>>error("Job không tồn tại: " + jobId, "UNKNOWN_JOB")));
        }
        return jobStatusService.isRunning(jobId).flatMap(running -> {
            if (running) {
                return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(
                        ApiResponse.<Map<String, Object>>error(
                                "Job đang chạy, vui lòng đợi", "DATA_PLATFORM_JOB_RUNNING")));
            }
            return publishTrigger(jobId);
        });
    }

    private Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> publishTrigger(String jobId) {
        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("jobId", jobId));
        } catch (Exception e) {
            log.warn("Failed to serialize data-platform trigger event", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<Map<String, Object>>error("Không thể gửi yêu cầu kích hoạt", "SERIALIZATION_ERROR")));
        }
        return redisTemplate.convertAndSend(TRIGGER_CHANNEL, json)
                .map(subscribers -> {
                    boolean delivered = subscribers != null && subscribers > 0;
                    String message = delivered
                            ? "Đã gửi yêu cầu, job sẽ bắt đầu trong giây lát"
                            : "Đã gửi yêu cầu nhưng không có data-platform nào đang lắng nghe";
                    return ResponseEntity.ok(
                            ApiResponse.success(Map.<String, Object>of("delivered", delivered), message));
                });
    }
}
