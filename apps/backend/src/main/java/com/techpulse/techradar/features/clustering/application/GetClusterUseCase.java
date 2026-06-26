package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Get a single cluster's detail by id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetClusterUseCase {

    private final ClusteringServicePort clusteringServicePort;

    public Mono<Map<String, Object>> execute(String clusterId) {
        if (clusterId == null || clusterId.isBlank()) {
            log.warn("Rejected cluster lookup: blank cluster id");
            return Mono.error(new IllegalArgumentException("cluster id is required"));
        }
        log.info("Fetching cluster detail clusterId={}", clusterId);
        return clusteringServicePort.getCluster(clusterId)
                .doOnSuccess(c -> log.info("Fetched cluster detail clusterId={}", clusterId));
    }
}
