package com.techpulse.techradar.features.graph.ports;

import com.techpulse.techradar.features.graph.domain.GraphEdge;
import com.techpulse.techradar.features.graph.domain.GraphFilter;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Output port for Neo4j graph queries.
 */
public interface GraphRepository {
    Mono<GraphNode> findNode(String nodeId);

    Flux<GraphNode> findNodesByType(String nodeType);

    Flux<GraphEdge> findEdges(String sourceId, String targetId);

    Flux<GraphNode> exploreNeighbors(String nodeId, int depth);

    Mono<List<GraphNode>> findPathBetween(String sourceId, String targetId);

    Flux<GraphNode> filterNodes(GraphFilter filter);
}
