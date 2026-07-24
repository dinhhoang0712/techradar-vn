package com.techpulse.techradar.features.clustering.adapters.input;

import com.techpulse.techradar.features.clustering.application.GetPipelineRunsUseCase;
import com.techpulse.techradar.features.clustering.application.GetPipelineStatusUseCase;
import com.techpulse.techradar.features.clustering.application.TriggerPipelineUseCase;
import com.techpulse.techradar.features.clustering.application.UpdateClusterLabelUseCase;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.redis.RedisLock;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Admin operations over the ml-clustering service: live retrain pipeline status/trigger, model
 * quality history (MLflow "best" runs), and manual override of AI-generated cluster labels.
 */
@Tag(name = "Admin", description = "Clustering pipeline ops + cluster label review")
@RestController
@RequestMapping("/admin/clustering")
@RequiredArgsConstructor
public class AdminClusteringController {

    private static final String TRIGGER_LOCK_KEY = "clustering:trigger:lock";

    private final GetPipelineStatusUseCase getPipelineStatusUseCase;
    private final TriggerPipelineUseCase triggerPipelineUseCase;
    private final GetPipelineRunsUseCase getPipelineRunsUseCase;
    private final UpdateClusterLabelUseCase updateClusterLabelUseCase;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final AuditLogService auditLogService;

    @Operation(summary = "Get the current clustering retrain pipeline status")
    @GetMapping("/pipeline/status")
    @PreAuthorize("hasAuthority('clustering:manage')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> pipelineStatus() {
        return getPipelineStatusUseCase.execute()
                .map(status -> ResponseEntity.ok(ApiResponse.success(status, "Pipeline status retrieved")));
    }

    @Operation(summary = "Trigger an immediate clustering retrain instead of waiting for the next schedule")
    @PostMapping("/pipeline/trigger")
    @PreAuthorize("hasAuthority('clustering:manage')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> triggerPipeline() {
        // Debounce lock only — an in-flight run is still rejected downstream with 409 by the
        // ml-clustering Python service itself; this just stops two rapid clicks from both
        // reaching that check at once.
        return RedisLock.tryAcquire(redisTemplate, TRIGGER_LOCK_KEY, Duration.ofSeconds(10))
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                                ApiResponse.<Map<String, Object>>error(
                                        "Vừa mới kích hoạt, vui lòng đợi vài giây rồi thử lại", "CLUSTERING_TRIGGER_DEBOUNCED")));
                    }
                    return triggerPipelineUseCase.execute()
                            .flatMap(result -> auditLogService.record("CLUSTERING_PIPELINE_TRIGGER", "pipeline", null, null)
                                    .thenReturn(result))
                            .map(result -> ResponseEntity.ok(ApiResponse.success(result, "Pipeline retrain started")));
                });
    }

    @Operation(summary = "List past training runs (model quality metrics over time)")
    @GetMapping("/pipeline/runs")
    @PreAuthorize("hasAuthority('clustering:manage')")
    public Mono<ResponseEntity<ApiResponse<List<Map<String, Object>>>>> pipelineRuns() {
        return getPipelineRunsUseCase.execute()
                .collectList()
                .map(runs -> ResponseEntity.ok(ApiResponse.success(runs, "Pipeline runs retrieved")));
    }

    @Operation(summary = "Override an AI-generated cluster label")
    @PutMapping("/clusters/{clusterId}/label")
    @PreAuthorize("hasAuthority('clustering:manage')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> updateClusterLabel(
            @PathVariable String clusterId,
            @RequestBody UpdateClusterLabelRequest request
    ) {
        return SecurityUtils.currentUserId()
                .defaultIfEmpty("")
                .flatMap(actor -> updateClusterLabelUseCase.execute(
                        clusterId,
                        request.getLabel(),
                        request.getLabelEn(),
                        request.getDescription(),
                        request.getDomain(),
                        actor.isBlank() ? null : actor))
                .flatMap(result -> auditLogService.record("CLUSTER_LABEL_OVERRIDE", "cluster", clusterId,
                                "label=" + request.getLabel())
                        .thenReturn(result))
                .map(result -> ResponseEntity.ok(ApiResponse.success(result, "Cluster label updated")))
                .onErrorResume(IllegalArgumentException.class, ex -> Mono.just(
                        ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), "INVALID_REQUEST"))));
    }

    @Data
    public static class UpdateClusterLabelRequest {
        private String label;
        private String labelEn;
        private String description;
        private String domain;
    }
}
