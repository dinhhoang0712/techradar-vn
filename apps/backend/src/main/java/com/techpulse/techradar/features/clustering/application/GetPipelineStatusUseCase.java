package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Current retrain pipeline state — always fetched live, never cached, so the admin UI can poll
 * it for progress.
 */
@Component
@RequiredArgsConstructor
public class GetPipelineStatusUseCase {

    private final ClusteringServicePort clusteringServicePort;

    public Mono<Map<String, Object>> execute() {
        return clusteringServicePort.getPipelineStatus();
    }
}
