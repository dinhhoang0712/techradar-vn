package com.techpulse.techradar.features.graph.application;

import com.techpulse.techradar.features.graph.domain.GraphData;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Find the shortest path ("career road") between two named graph nodes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoadAnalysisUseCase {

    private final GraphRepository graphRepository;

    public Mono<GraphData> execute(String from, String to) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            log.warn("Road analysis rejected: from={} to={}", from, to);
            return Mono.error(new IllegalArgumentException("Both 'from' and 'to' are required"));
        }
        log.info("Analysing career road from={} to={}", from, to);
        return graphRepository.shortestPathByName(from, to)
                .doOnSuccess(data -> log.info("Road analysis from={} to={} found={} nodes={}",
                        from, to, data.isFound(), data.getNodes().size()))
                .doOnError(e -> log.error("Road analysis failed from={} to={}", from, to, e));
    }
}
