package com.techpulse.techradar.features.clustering.ports;

import com.techpulse.techradar.features.clustering.domain.Cluster;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Output port for clustering service.
 */
public interface ClusteringServicePort {
    Flux<Cluster> getClusters();

    Mono<Cluster> getCluster(String clusterId);

    Mono<Cluster> predictCluster(String technology);

    Flux<Cluster> predictBatch(java.util.List<String> technologies);
}
