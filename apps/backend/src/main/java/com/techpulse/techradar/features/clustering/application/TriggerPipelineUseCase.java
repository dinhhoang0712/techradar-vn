package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin-triggered retrain of the clustering pipeline (5 DVC stages). Rejected with 409 by the
 * Python side if a run is already in progress.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerPipelineUseCase {

    private final ClusteringServicePort clusteringServicePort;

    public Mono<Map<String, Object>> execute() {
        log.info("Admin triggered clustering pipeline retrain");
        return clusteringServicePort.triggerPipeline();
    }
}
