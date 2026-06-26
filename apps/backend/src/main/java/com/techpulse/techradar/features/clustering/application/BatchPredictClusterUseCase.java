package com.techpulse.techradar.features.clustering.application;

import com.techpulse.techradar.features.clustering.ports.ClusteringServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Predict clusters for a batch of technologies. Returns the service's BatchPredictResponse verbatim.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchPredictClusterUseCase {

    private final ClusteringServicePort clusteringServicePort;

    public Mono<Map<String, Object>> execute(List<String> technologies) {
        if (technologies == null || technologies.isEmpty()) {
            log.warn("Rejected batch cluster prediction: no technologies provided");
            return Mono.error(new IllegalArgumentException("At least one technology is required"));
        }
        List<String> cleaned = technologies.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .toList();
        if (cleaned.isEmpty()) {
            log.warn("Rejected batch cluster prediction: all technology names blank");
            return Mono.error(new IllegalArgumentException("At least one technology is required"));
        }
        log.info("Predicting clusters for batch of {} technologies", cleaned.size());
        return clusteringServicePort.predictBatch(cleaned)
                .doOnSuccess(r -> log.info("Completed batch cluster prediction for {} technologies", cleaned.size()));
    }
}
