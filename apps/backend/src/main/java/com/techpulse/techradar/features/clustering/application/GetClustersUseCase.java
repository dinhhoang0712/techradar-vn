package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.domain.Cluster;
import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Get clusters use case.
 */
@Component
@RequiredArgsConstructor
public class GetClustersUseCase {

    private final ClusteringServicePort clusteringServicePort;

    public Flux<Cluster> execute() {
        return clusteringServicePort.getClusters();
    }
}
