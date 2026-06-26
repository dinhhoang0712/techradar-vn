package com.techpulse.techradar.features.clustering.adapters.input;

import com.techpulse.techradar.features.clustering.application.BatchPredictClusterUseCase;
import com.techpulse.techradar.features.clustering.application.GetClusterUseCase;
import com.techpulse.techradar.features.clustering.application.GetClustersUseCase;
import com.techpulse.techradar.features.clustering.application.PredictClusterUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Clustering API controller. Transparent gateway over the Python ml-clustering service;
 * paths and payloads mirror that service (and what the web/mobile clients call).
 */
@Tag(name = "Clustering", description = "Technology clustering endpoints")
@RestController
@RequestMapping("/clustering")
@RequiredArgsConstructor
public class ClusteringController {

    private final GetClustersUseCase getClustersUseCase;
    private final GetClusterUseCase getClusterUseCase;
    private final PredictClusterUseCase predictClusterUseCase;
    private final BatchPredictClusterUseCase batchPredictClusterUseCase;

    @Operation(summary = "List technology clusters")
    @GetMapping("/clusters")
    public Mono<ResponseEntity<ApiResponse<List<Map<String, Object>>>>> getClusters(
            @RequestParam(value = "is_coherent", required = false) Boolean isCoherent
    ) {
        return getClustersUseCase.execute(isCoherent)
                .collectList()
                .map(clusters -> ResponseEntity.ok(ApiResponse.success(clusters, "Clusters retrieved")))
                .onErrorResume(ex -> unavailable());
    }

    @Operation(summary = "Get cluster detail by id")
    @GetMapping("/clusters/{clusterId}")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getCluster(
            @PathVariable String clusterId
    ) {
        return getClusterUseCase.execute(clusterId)
                .map(cluster -> ResponseEntity.ok(ApiResponse.success(cluster, "Cluster retrieved")))
                .onErrorResume(ex -> unavailable());
    }

    @Operation(summary = "Look up the cluster a technology belongs to")
    @GetMapping("/tech/{techName}/cluster")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> getTechCluster(
            @PathVariable String techName
    ) {
        return predictClusterUseCase.execute(techName)
                .map(cluster -> ResponseEntity.ok(ApiResponse.success(cluster, "Cluster predicted")))
                .onErrorResume(ex -> unavailable());
    }

    @Operation(summary = "Batch lookup clusters for multiple technologies")
    @PostMapping("/predict/batch")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> batchPredict(
            @RequestBody BatchPredictRequest request
    ) {
        return batchPredictClusterUseCase.execute(request != null ? request.getTechNames() : null)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result, "Clusters predicted")))
                .onErrorResume(IllegalArgumentException.class, ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "INVALID_REQUEST"))))
                .onErrorResume(ex -> unavailable());
    }

    private <T> Mono<ResponseEntity<ApiResponse<T>>> unavailable() {
        return Mono.just(ResponseEntity.status(503).body(
                ApiResponse.error("Clustering service unavailable", "SERVICE_UNAVAILABLE")));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class BatchPredictRequest {
        /** Serialized as {@code tech_names}. */
        private List<String> techNames;
    }
}
