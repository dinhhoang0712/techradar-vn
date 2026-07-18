package com.techpulse.techradar.features.clustering.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Output port for the Python ml-clustering service.
 * <p>
 * The backend is a transparent gateway: it forwards the service's JSON (snake_case) as-is so the
 * cluster contract stays owned by the Python service and never drifts.
 */
public interface ClusteringServicePort {

    Flux<Map<String, Object>> getClusters(Boolean isCoherent);

    Mono<Map<String, Object>> getCluster(String clusterId);

    Mono<Map<String, Object>> getTechCluster(String techName);

    Mono<Map<String, Object>> predictBatch(List<String> techNames);

    /** Current retrain pipeline state (idle|running|success|failed) — always live, never cached. */
    Mono<Map<String, Object>> getPipelineStatus();

    /**
     * Start a retrain run. Not idempotent — the Python side guards with 409 if one is already
     * running, so callers must NOT blindly retry this on transient failure.
     */
    Mono<Map<String, Object>> triggerPipeline();

    /** History of past "best" training runs (model quality metrics over time), newest first. */
    Flux<Map<String, Object>> getPipelineRuns();

    /**
     * Admin override of an AI-generated cluster label. {@code actor} (nullable) is forwarded as
     * the acting admin's user id, purely for display ("edited by"). Not idempotent in the sense
     * that repeated calls overwrite the same override record — callers must NOT retry blindly on
     * transient failure.
     */
    Mono<Map<String, Object>> updateClusterLabel(String clusterId, Map<String, Object> body, String actor);
}
