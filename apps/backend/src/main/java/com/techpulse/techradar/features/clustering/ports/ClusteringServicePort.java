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
}
