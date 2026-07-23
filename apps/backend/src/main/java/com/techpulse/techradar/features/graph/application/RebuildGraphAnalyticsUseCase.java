package com.techpulse.techradar.features.graph.application;

import com.techpulse.techradar.features.graph.domain.GraphAnalyticsSummary;
import com.techpulse.techradar.features.graph.ports.GraphAnalyticsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Admin-triggered rebuild of graph analytics (PageRank/Louvain/degree centrality) from the
 * knowledge graph.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RebuildGraphAnalyticsUseCase {

    private final GraphAnalyticsPort graphAnalyticsPort;

    public Mono<GraphAnalyticsSummary> execute() {
        log.info("Admin triggered graph analytics rebuild");
        return graphAnalyticsPort.rebuild();
    }
}
