package com.techpulse.techradar.features.graph.ports;

import com.techpulse.techradar.features.graph.domain.GraphData;
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

    /**
     * Explore the subgraph around one or more nodes matched by name.
     *
     * @param keywords  node names to seed from (case-insensitive)
     * @param depth     traversal depth (clamped 1..3)
     * @param location  optional location filter (kept if any node on the path matches)
     * @param minSalary optional; currently ignored (salary is free-text in the graph)
     */
    Mono<GraphData> exploreByKeywords(List<String> keywords, int depth, String location, Long minSalary);

    /**
     * Shortest path between two nodes matched by name. {@code found=false} when no path exists.
     */
    Mono<GraphData> shortestPathByName(String from, String to);
}
