package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.domain.Cluster;
import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Predict cluster for technology use case.
 */
@Component
@RequiredArgsConstructor
public class PredictClusterUseCase {

    private final ClusteringServicePort clusteringServicePort;

    public Mono<Cluster> execute(String technology) {
        if (technology == null || technology.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Technology name is required"));
        }
        return clusteringServicePort.predictCluster(technology);
    }
}
