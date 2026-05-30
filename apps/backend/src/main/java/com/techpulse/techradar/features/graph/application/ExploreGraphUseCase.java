package com.techpulse.techradar.features.graph.application;

import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Explore graph neighbors use case.
 */
@Component
@RequiredArgsConstructor
public class ExploreGraphUseCase {

    private final GraphRepository graphRepository;

    public Flux<GraphNode> execute(String nodeId, int depth) {
        if (nodeId == null || nodeId.isEmpty()) {
            return Flux.error(new IllegalArgumentException("Node ID is required"));
        }
        if (depth < 1 || depth > 5) {
            depth = 2;
        }
        return graphRepository.exploreNeighbors(nodeId, depth);
    }
}
