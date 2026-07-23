package com.techpulse.techradar.features.graph.ports;

import com.techpulse.techradar.features.graph.domain.GraphAnalyticsSummary;
import reactor.core.publisher.Mono;

/**
 * Output port for the GDS-based graph analytics rebuild (PageRank/Louvain/degree centrality over
 * the Technology-RELATED_TO-Technology subgraph). Separate from {@link GraphRepository} because
 * this is a write/compute concern (mutates Technology nodes via GDS), not a plain graph read.
 */
public interface GraphAnalyticsPort {
    Mono<GraphAnalyticsSummary> rebuild();
}
