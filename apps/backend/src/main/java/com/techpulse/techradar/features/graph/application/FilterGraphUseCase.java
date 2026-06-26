package com.techpulse.techradar.features.graph.application;

import com.techpulse.techradar.features.graph.domain.GraphFilter;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Filter knowledge-graph nodes by location / node type / sentiment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilterGraphUseCase {

    private final GraphRepository graphRepository;

    public Flux<GraphNode> execute(GraphFilter filter) {
        if (filter == null) {
            log.warn("Graph filter rejected: filter is null");
            return Flux.error(new IllegalArgumentException("Filter is required"));
        }
        log.info("Filtering graph nodes with filter={}", filter);
        return graphRepository.filterNodes(filter)
                .doOnError(e -> log.error("Graph filter failed for filter={}", filter, e));
    }
}
