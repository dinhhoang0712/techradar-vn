package com.techpulse.techradar.features.graph.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A subgraph result (nodes + edges) returned by explore / road-analysis queries.
 * {@code found} indicates whether a path/seed was located.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphData {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private boolean found;
}
