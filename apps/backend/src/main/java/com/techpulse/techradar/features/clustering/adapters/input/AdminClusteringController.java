package com.techpulse.techradar.features.clustering.adapters.input;

import com.techpulse.techradar.features.clustering.application.GetPipelineRunsUseCase;
import com.techpulse.techradar.features.clustering.application.GetPipelineStatusUseCase;
import com.techpulse.techradar.features.clustering.application.TriggerPipelineUseCase;
import com.techpulse.techradar.features.clustering.application.UpdateClusterLabelUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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

    private final GetPipelineStatusUseCase getPipelineStatusUseCase;
    private final TriggerPipelineUseCase triggerPipelineUseCase;
    private final GetPipelineRunsUseCase getPipelineRunsUseCase;
    private final UpdateClusterLabelUseCase updateClusterLabelUseCase;

    @Operation(summary = "Get the current clustering retrain pipeline status")
    @GetMapping("/pipeline/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> pipelineStatus() {
        return getPipelineStatusUseCase.execute()
                .map(status -> ResponseEntity.ok(ApiResponse.success(status, "Pipeline status retrieved")));
    }

    @Operation(summary = "Trigger an immediate clustering retrain instead of waiting for the next schedule")
    @PostMapping("/pipeline/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> triggerPipeline() {
        return triggerPipelineUseCase.execute()
                .map(result -> ResponseEntity.ok(ApiResponse.success(result, "Pipeline retrain started")));
    }

    @Operation(summary = "List past training runs (model quality metrics over time)")
    @GetMapping("/pipeline/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<ApiResponse<List<Map<String, Object>>>>> pipelineRuns() {
        return getPipelineRunsUseCase.execute()
                .collectList()
                .map(runs -> ResponseEntity.ok(ApiResponse.success(runs, "Pipeline runs retrieved")));
    }

    @Operation(summary = "Override an AI-generated cluster label")
    @PutMapping("/clusters/{clusterId}/label")
    @PreAuthorize("hasRole('ADMIN')")
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
