package com.techpulse.techradar.features.clustering.adapters.output;

import com.techpulse.techradar.features.clustering.domain.Cluster;
import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import com.techpulse.techradar.shared.exception.DatabaseUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Python clustering service client adapter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonClusteringClient implements ClusteringServicePort {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.python.clustering.base-url:http://localhost:8001}")
    private String clusteringBaseUrl;

    @Value("${app.python.clustering.timeout:60000}")
    private long timeout;

    @Override
    public Flux<Cluster> getClusters() {
        return webClientBuilder.build()
                .get()
                .uri(clusteringBaseUrl + "/clusters")
                .retrieve()
                .bodyToFlux(Map.class)
                .map(this::mapToCluster)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to get clusters", ex);
                    return Flux.error(
                            new DatabaseUnavailableException("Clustering service unavailable")
                    );
                });
    }

    @Override
    public Mono<Cluster> getCluster(String clusterId) {
        return webClientBuilder.build()
                .get()
                .uri(clusteringBaseUrl + "/clusters/" + clusterId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::mapToCluster)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to get cluster {}", clusterId, ex);
                    return Mono.error(
                            new DatabaseUnavailableException("Clustering service unavailable")
                    );
                });
    }

    @Override
    public Mono<Cluster> predictCluster(String technology) {
        return webClientBuilder.build()
                .get()
                .uri(clusteringBaseUrl + "/tech/" + technology + "/cluster")
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::mapToCluster)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to predict cluster for technology {}", technology, ex);
                    return Mono.error(
                            new DatabaseUnavailableException("Clustering service unavailable")
                    );
                });
    }

    @Override
    public Flux<Cluster> predictBatch(List<String> technologies) {
        Map<String, Object> request = Map.of("technologies", technologies);

        return webClientBuilder.build()
                .post()
                .uri(clusteringBaseUrl + "/predict/batch")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(Map.class)
                .map(this::mapToCluster)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMillis(timeout))
                .onErrorResume(ex -> {
                    log.error("Failed to batch predict clusters", ex);
                    return Flux.error(
                            new DatabaseUnavailableException("Clustering service unavailable")
                    );
                });
    }

    @SuppressWarnings("unchecked")
    private Cluster mapToCluster(Map<String, Object> response) {
        return Cluster.builder()
                .clusterId((String) response.get("cluster_id"))
                .name((String) response.getOrDefault("name", ""))
                .description((String) response.getOrDefault("description", ""))
                .technologies((List<String>) response.getOrDefault("technologies", List.of()))
                .size(((Number) response.getOrDefault("size", 0)).intValue())
                .score(((Number) response.getOrDefault("score", 0.0)).doubleValue())
                .build();
    }
}
