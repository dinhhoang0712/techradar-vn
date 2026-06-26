package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Look up the cluster a single technology belongs to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictClusterUseCase {

    private final ClusteringServicePort clusteringServicePort;

    public Mono<Map<String, Object>> execute(String technology) {
        if (technology == null || technology.isBlank()) {
            log.warn("Rejected cluster prediction: blank technology name");
            return Mono.error(new IllegalArgumentException("Technology name is required"));
        }
        log.info("Predicting cluster for tech={}", technology);
        return clusteringServicePort.getTechCluster(technology)
                .doOnSuccess(c -> log.info("Predicted cluster for tech={}", technology));
    }
}
