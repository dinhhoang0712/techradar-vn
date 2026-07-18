package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * History of past "best" training runs (silhouette/DBCV/... over time), for the model-quality
 * trend chart in admin. Not cached — this only changes after a full retrain, which is already
 * a rare, admin-visible event.
 */
@Component
@RequiredArgsConstructor
public class GetPipelineRunsUseCase {

    private final ClusteringServicePort clusteringServicePort;

    public Flux<Map<String, Object>> execute() {
        return clusteringServicePort.getPipelineRuns();
    }
}
