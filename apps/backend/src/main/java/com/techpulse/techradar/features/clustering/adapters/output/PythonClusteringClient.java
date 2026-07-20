package com.techpulse.techradar.features.clustering.adapters.output;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.shared.client.PythonServiceWebClientFactory;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.exception.NotFoundException;
import com.techpulse.techradar.shared.http.AbstractPythonServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Transparent client for the Python ml-clustering service.
 * Returns the service JSON (snake_case) verbatim so the gateway never reshapes the contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonClusteringClient extends AbstractPythonServiceClient implements ClusteringServicePort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final String UNAVAILABLE = "Clustering service unavailable";

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.clustering.base-url:http://localhost:8001}")
    private String clusteringBaseUrl;

    @Value("${app.python.clustering.timeout:60000}")
    private long timeout;

    @Value("${app.python.internal-token:}")
    private String internalToken;

    private WebClient client() {
        return PythonServiceWebClientFactory.build(webClientBuilder, clusteringBaseUrl, internalToken);
    }

    @Override
    public Flux<Map<String, Object>> getClusters(Boolean isCoherent) {
        String uri = UriComponentsBuilder.fromHttpUrl(clusteringBaseUrl + "/clusters")
                .queryParamIfPresent("is_coherent", java.util.Optional.ofNullable(isCoherent))
                .build()
                .toUriString();
        Flux<Map<String, Object>> request = client()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToFlux(MAP_TYPE);
        return mapFlux(request, true, Duration.ofMillis(timeout),
                ex -> log.error("Failed to get clusters", ex), UNAVAILABLE);
    }

    @Override
    public Mono<Map<String, Object>> getCluster(String clusterId) {
        Mono<Map<String, Object>> request = client()
                .get()
                .uri(clusteringBaseUrl + "/clusters/" + clusterId)
                .retrieve()
                .bodyToMono(MAP_TYPE);
        return mapMono(request, true, Duration.ofMillis(timeout),
                ex -> log.error("Failed to get cluster {}", clusterId, ex), UNAVAILABLE);
    }

    @Override
    public Mono<Map<String, Object>> getTechCluster(String techName) {
        Mono<Map<String, Object>> request = client()
                .get()
                .uri(clusteringBaseUrl + "/tech/{name}/cluster", techName)
                .retrieve()
                .bodyToMono(MAP_TYPE);
        return mapMono(request, true, Duration.ofMillis(timeout),
                ex -> log.error("Failed to get cluster for technology {}", techName, ex), UNAVAILABLE);
    }

    @Override
    public Mono<Map<String, Object>> predictBatch(List<String> techNames) {
        Map<String, Object> body = Map.of("tech_names", techNames);
        Mono<Map<String, Object>> request = client()
                .post()
                .uri(clusteringBaseUrl + "/predict/batch")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE);
        return mapMono(request, true, Duration.ofMillis(timeout),
                ex -> log.error("Failed to batch predict clusters", ex), UNAVAILABLE);
    }

    @Override
    public Mono<Map<String, Object>> getPipelineStatus() {
        Mono<Map<String, Object>> request = client()
                .get()
                .uri(clusteringBaseUrl + "/pipeline/status")
                .retrieve()
                .bodyToMono(MAP_TYPE);
        return mapMono(request, true, Duration.ofMillis(timeout),
                ex -> log.error("Failed to get pipeline status", ex), UNAVAILABLE);
    }

    @Override
    public Mono<Map<String, Object>> triggerPipeline() {
        // No retry: POST /pipeline/trigger is not idempotent (starts a background retrain).
        Mono<Map<String, Object>> request = client()
                .post()
                .uri(clusteringBaseUrl + "/pipeline/trigger")
                .retrieve()
                .onStatus(status -> status.value() == 409, resp -> Mono.error(
                        new ConflictException(ErrorCode.PIPELINE_RUNNING, "Đang có một lượt huấn luyện lại đang chạy, vui lòng đợi")))
                .bodyToMono(MAP_TYPE);
        return mapMono(request, false, Duration.ofMillis(timeout),
                ex -> log.error("Failed to trigger pipeline retrain", ex), UNAVAILABLE);
    }

    @Override
    public Flux<Map<String, Object>> getPipelineRuns() {
        Flux<Map<String, Object>> request = client()
                .get()
                .uri(clusteringBaseUrl + "/pipeline/runs")
                .retrieve()
                .bodyToFlux(MAP_TYPE);
        return mapFlux(request, true, Duration.ofMillis(timeout),
                ex -> log.error("Failed to get pipeline run history", ex), UNAVAILABLE);
    }

    @Override
    public Mono<Map<String, Object>> updateClusterLabel(String clusterId, Map<String, Object> body, String actor) {
        // No retry: PUT is a deliberate write; a lost response + blind retry could double-log
        // overridden_at with a stale actor if a second admin edited it in between.
        WebClient.RequestBodySpec req = client()
                .put()
                .uri(clusteringBaseUrl + "/clusters/{id}/label", clusterId);
        if (actor != null && !actor.isBlank()) {
            req = req.header("X-Actor", actor);
        }
        Mono<Map<String, Object>> request = req.bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() == 404, resp -> Mono.error(
                        new NotFoundException("Cluster " + clusterId + " không tồn tại")))
                .bodyToMono(MAP_TYPE);
        return mapMono(request, false, Duration.ofMillis(timeout),
                ex -> log.error("Failed to update cluster label {}", clusterId, ex), UNAVAILABLE);
    }
}
