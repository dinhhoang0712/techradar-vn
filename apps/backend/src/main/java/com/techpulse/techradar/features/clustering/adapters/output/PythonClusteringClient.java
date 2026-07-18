package com.techpulse.techradar.features.clustering.adapters.output;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.shared.exception.AppException;
import com.techpulse.techradar.shared.exception.ConflictException;
import com.techpulse.techradar.shared.exception.DatabaseUnavailableException;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

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
public class PythonClusteringClient implements ClusteringServicePort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.clustering.base-url:http://localhost:8001}")
    private String clusteringBaseUrl;

    @Value("${app.python.clustering.timeout:60000}")
    private long timeout;

    @Value("${app.python.internal-token:}")
    private String internalToken;

    @Override
    public Flux<Map<String, Object>> getClusters(Boolean isCoherent) {
        String uri = UriComponentsBuilder.fromHttpUrl(clusteringBaseUrl + "/clusters")
                .queryParamIfPresent("is_coherent", java.util.Optional.ofNullable(isCoherent))
                .build()
                .toUriString();
        return webClientBuilder.build()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToFlux(MAP_TYPE)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to get clusters", ex);
                    return Flux.error(new DatabaseUnavailableException("Clustering service unavailable"));
                });
    }

    @Override
    public Mono<Map<String, Object>> getCluster(String clusterId) {
        return webClientBuilder.build()
                .get()
                .uri(clusteringBaseUrl + "/clusters/" + clusterId)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to get cluster {}", clusterId, ex);
                    return Mono.error(new DatabaseUnavailableException("Clustering service unavailable"));
                });
    }

    @Override
    public Mono<Map<String, Object>> getTechCluster(String techName) {
        return webClientBuilder.build()
                .get()
                .uri(clusteringBaseUrl + "/tech/{name}/cluster", techName)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to get cluster for technology {}", techName, ex);
                    return Mono.error(new DatabaseUnavailableException("Clustering service unavailable"));
                });
    }

    @Override
    public Mono<Map<String, Object>> predictBatch(List<String> techNames) {
        Map<String, Object> request = Map.of("tech_names", techNames);
        return webClientBuilder.build()
                .post()
                .uri(clusteringBaseUrl + "/predict/batch")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to batch predict clusters", ex);
                    return Mono.error(new DatabaseUnavailableException("Clustering service unavailable"));
                });
    }

    @Override
    public Mono<Map<String, Object>> getPipelineStatus() {
        return webClientBuilder.build()
                .get()
                .uri(clusteringBaseUrl + "/pipeline/status")
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to get pipeline status", ex);
                    return Mono.error(new DatabaseUnavailableException("Clustering service unavailable"));
                });
    }

    @Override
    public Mono<Map<String, Object>> triggerPipeline() {
        // No retry: POST /pipeline/trigger is not idempotent (starts a background retrain).
        WebClient.RequestBodySpec req = webClientBuilder.build()
                .post()
                .uri(clusteringBaseUrl + "/pipeline/trigger");
        if (internalToken != null && !internalToken.isBlank()) {
            req = req.header("X-Internal-Auth", internalToken);
        }
        return req.retrieve()
                .onStatus(status -> status.value() == 409, resp -> Mono.error(
                        new ConflictException("Đang có một lượt huấn luyện lại đang chạy, vui lòng đợi", "PIPELINE_RUNNING")))
                .bodyToMono(MAP_TYPE)
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    if (ex instanceof AppException) {
                        return Mono.error(ex);
                    }
                    log.error("Failed to trigger pipeline retrain", ex);
                    return Mono.error(new DatabaseUnavailableException("Clustering service unavailable"));
                });
    }

    @Override
    public Flux<Map<String, Object>> getPipelineRuns() {
        return webClientBuilder.build()
                .get()
                .uri(clusteringBaseUrl + "/pipeline/runs")
                .retrieve()
                .bodyToFlux(MAP_TYPE)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to get pipeline run history", ex);
                    return Flux.error(new DatabaseUnavailableException("Clustering service unavailable"));
                });
    }

    @Override
    public Mono<Map<String, Object>> updateClusterLabel(String clusterId, Map<String, Object> body, String actor) {
        // No retry: PUT is a deliberate write; a lost response + blind retry could double-log
        // overridden_at with a stale actor if a second admin edited it in between.
        WebClient.RequestBodySpec req = webClientBuilder.build()
                .put()
                .uri(clusteringBaseUrl + "/clusters/{id}/label", clusterId);
        if (internalToken != null && !internalToken.isBlank()) {
            req = req.header("X-Internal-Auth", internalToken);
        }
        if (actor != null && !actor.isBlank()) {
            req = req.header("X-Actor", actor);
        }
        return req.bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() == 404, resp -> Mono.error(
                        new NotFoundException("Cluster " + clusterId + " không tồn tại")))
                .bodyToMono(MAP_TYPE)
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    if (ex instanceof AppException) {
                        return Mono.error(ex);
                    }
                    log.error("Failed to update cluster label {}", clusterId, ex);
                    return Mono.error(new DatabaseUnavailableException("Clustering service unavailable"));
                });
    }
}
