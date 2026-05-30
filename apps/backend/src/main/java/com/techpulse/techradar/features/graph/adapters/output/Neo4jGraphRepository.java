package com.techpulse.techradar.features.graph.adapters.output;

import com.techpulse.techradar.features.graph.domain.GraphEdge;
import com.techpulse.techradar.features.graph.domain.GraphFilter;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j adapter for graph exploration.
 */
@Component
@RequiredArgsConstructor
public class Neo4jGraphRepository implements GraphRepository {

    private final Driver driver;

    @Override
    public Mono<GraphNode> findNode(String nodeId) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                String query = "MATCH (n) WHERE id(n) = $nodeId RETURN n, labels(n) as labels";
                var result = session.run(query, Map.of("nodeId", Long.parseLong(nodeId)));

                if (result.list().isEmpty()) {
                    return null;
                }

                Record record = result.single();
                var node = record.get("n").asNode();
                return GraphNode.builder()
                        .id(String.valueOf(node.id()))
                        .name(node.get("name").asString())
                        .type(String.join(",", node.labels()))
                        .properties(new HashMap<>(node.asMap()))
                        .build();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<GraphNode> findNodesByType(String nodeType) {
        return Mono.fromCallable(() -> {
            List<GraphNode> nodes = new ArrayList<>();
            try (Session session = driver.session()) {
                String query = "MATCH (n:" + nodeType + ") RETURN n LIMIT 100";
                var result = session.run(query);

                for (Record record : result.list()) {
                    var node = record.get("n").asNode();
                    GraphNode graphNode = GraphNode.builder()
                            .id(String.valueOf(node.id()))
                            .type(nodeType)
                            .name(node.get("name").asString())
                            .properties(new HashMap<>(node.asMap()))
                            .build();
                    nodes.add(graphNode);
                }
            }
            return nodes;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(nodes -> nodes);
    }

    @Override
    public Flux<GraphEdge> findEdges(String sourceId, String targetId) {
        return Mono.fromCallable(() -> {
            List<GraphEdge> edges = new ArrayList<>();
            try (Session session = driver.session()) {
                String query = "MATCH (s)-[r]->(t) WHERE id(s) = $sourceId AND id(t) = $targetId " +
                        "RETURN r, type(r) as relType, id(s) as sourceId, id(t) as targetId";

                var result = session.run(query,
                        Map.of("sourceId", Long.parseLong(sourceId), "targetId", Long.parseLong(targetId)));

                for (Record record : result.list()) {
                    var rel = record.get("r").asRelationship();
                    GraphEdge edge = GraphEdge.builder()
                            .id(String.valueOf(rel.id()))
                            .source(sourceId)
                            .target(targetId)
                            .type(record.get("relType").asString())
                            .properties(new HashMap<>(rel.asMap()))
                            .build();
                    edges.add(edge);
                }
            }
            return edges;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(edges -> edges);
    }

    @Override
    public Flux<GraphNode> exploreNeighbors(String nodeId, int depth) {
        return Mono.fromCallable(() -> {
            List<GraphNode> nodes = new ArrayList<>();
            try (Session session = driver.session()) {
                String query = "MATCH (n)-[*1.." + depth + "]-(neighbor) WHERE id(n) = $nodeId " +
                        "RETURN DISTINCT neighbor LIMIT 50";

                var result = session.run(query, Map.of("nodeId", Long.parseLong(nodeId)));

                for (Record record : result.list()) {
                    var node = record.get("neighbor").asNode();
                    GraphNode graphNode = GraphNode.builder()
                            .id(String.valueOf(node.id()))
                            .type(String.join(",", node.labels()))
                            .name(node.get("name").asString())
                            .properties(new HashMap<>(node.asMap()))
                            .build();
                    nodes.add(graphNode);
                }
            }
            return nodes;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(nodes -> nodes);
    }

    @Override
    public Mono<List<GraphNode>> findPathBetween(String sourceId, String targetId) {
        return Mono.fromCallable(() -> {
            List<GraphNode> path = new ArrayList<>();
            try (Session session = driver.session()) {
                String query = "MATCH p = shortestPath((s)-[*]-(t)) WHERE id(s) = $sourceId AND id(t) = $targetId " +
                        "RETURN nodes(p) as nodes";

                var result = session.run(query,
                        Map.of("sourceId", Long.parseLong(sourceId), "targetId", Long.parseLong(targetId)));

                if (!result.list().isEmpty()) {
                    var nodes = result.single().get("nodes").asList(v -> {
                        var node = v.asNode();
                        return GraphNode.builder()
                                .id(String.valueOf(node.id()))
                                .type(String.join(",", node.labels()))
                                .name(node.get("name").asString())
                                .build();
                    });
                    path.addAll(nodes);
                }
            }
            return path;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<GraphNode> filterNodes(GraphFilter filter) {
        return Flux.empty();
    }
}
