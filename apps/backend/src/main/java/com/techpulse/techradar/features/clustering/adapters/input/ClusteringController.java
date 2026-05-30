package com.techpulse.techradar.features.clustering.adapters.input;

import com.techpulse.techradar.features.clustering.application.GetClustersUseCase;
import com.techpulse.techradar.features.clustering.application.PredictClusterUseCase;
import com.techpulse.techradar.features.clustering.domain.Cluster;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Clustering API controller.
 */
@Tag(name = "Clustering", description = "Technology clustering endpoints")
@RestController
@RequestMapping("/clustering")
@RequiredArgsConstructor
public class ClusteringController {

    private final GetClustersUseCase getClustersUseCase;
    private final PredictClusterUseCase predictClusterUseCase;

    @Operation(summary = "Get all technology clusters")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<Cluster>>>> getClusters() {
        return getClustersUseCase.execute()
                .collectList()
                .map(clusters -> ResponseEntity.ok(
                        ApiResponse.success(clusters, "Clusters retrieved")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error(
                                        "Clustering service unavailable: " + ex.getMessage(),
                                        "SERVICE_UNAVAILABLE"
                                )
                        )
                ));
    }

    @Operation(summary = "Predict cluster for technology")
    @GetMapping("/predict")
    public Mono<ResponseEntity<ApiResponse<Cluster>>> predictCluster(
            @RequestParam String technology
    ) {
        return predictClusterUseCase.execute(technology)
                .map(cluster -> ResponseEntity.ok(
                        ApiResponse.success(cluster, "Cluster predicted")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error(
                                        "Clustering service unavailable: " + ex.getMessage(),
                                        "SERVICE_UNAVAILABLE"
                                )
                        )
                ));
    }

    @Operation(summary = "Batch predict clusters for multiple technologies")
    @PostMapping("/predict/batch")
    public Mono<ResponseEntity<ApiResponse<List<Cluster>>>> batchPredict(
            @RequestBody BatchPredictRequest request
    ) {
        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(null, "Batch prediction feature coming soon")
        ));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class BatchPredictRequest {
        private java.util.List<String> technologies;
    }
}
