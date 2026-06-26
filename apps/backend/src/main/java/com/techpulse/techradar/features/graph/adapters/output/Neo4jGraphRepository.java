package com.techpulse.techradar.features.graph.adapters.output;

import com.techpulse.techradar.features.graph.domain.GraphData;
import com.techpulse.techradar.features.graph.domain.GraphEdge;
import com.techpulse.techradar.features.graph.domain.GraphFilter;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Neo4j adapter for graph exploration.
 */
@Slf4j
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
        return Mono.fromCallable(() -> {
            List<GraphNode> nodes = new ArrayList<>();

            // Note: job salary is stored as free-text in the graph, so numeric salary
            // filtering is not reliable and is intentionally not applied here.
            Map<String, Object> params = new HashMap<>();
            params.put("nodeTypes", (filter.getNodeTypes() == null || filter.getNodeTypes().isEmpty())
                    ? null : filter.getNodeTypes());
            params.put("locations", (filter.getLocations() == null || filter.getLocations().isEmpty())
                    ? null : filter.getLocations());
            params.put("minSentiment", minSentimentFor(filter.getSentiment()));

            String query = "MATCH (n) " +
                    "WHERE ($nodeTypes IS NULL OR any(l IN labels(n) WHERE l IN $nodeTypes)) " +
                    "AND ($locations IS NULL OR n.location IN $locations) " +
                    "AND ($minSentiment IS NULL OR " +
                    "     (n.sentiment_score IS NOT NULL AND n.sentiment_score >= $minSentiment)) " +
                    "RETURN DISTINCT n LIMIT 100";

            try (Session session = driver.session()) {
                var result = session.run(query, params);
                for (Record record : result.list()) {
                    var node = record.get("n").asNode();
                    nodes.add(GraphNode.builder()
                            .id(String.valueOf(node.id()))
                            .type(String.join(",", node.labels()))
                            .name(node.get("name").isNull() ? null : node.get("name").asString())
                            .properties(new HashMap<>(node.asMap()))
                            .build());
                }
            }
            log.info("Neo4j filterNodes matched {} nodes for filter={}", nodes.size(), filter);
            return nodes;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Neo4j filterNodes failed for filter={}", filter, e))
                .flatMapIterable(nodes -> nodes);
    }

    /**
     * Maps a sentiment label to a minimum {@code sentiment_score} threshold.
     * {@code positive} requires a non-negative score; anything else applies no threshold.
     */
    private static Double minSentimentFor(String sentiment) {
        if (sentiment == null || sentiment.isBlank()) {
            return null;
        }
        return "positive".equalsIgnoreCase(sentiment.trim()) ? 0.0 : null;
    }

    @Override
    public Mono<GraphData> exploreByKeywords(List<String> keywords, int depth, String location, Long minSalary) {
        return Mono.fromCallable(() -> {
            int d = Math.max(1, Math.min(depth, 3));
            List<String> names = keywords == null ? List.of() : keywords.stream()
                    .filter(Objects::nonNull)
                    .map(k -> k.trim().toLowerCase())
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (names.isEmpty()) {
                log.warn("Neo4j exploreByKeywords skipped: keywords were all blank");
                return GraphData.builder().nodes(List.of()).edges(List.of()).found(false).build();
            }
            log.info("Neo4j exploreByKeywords names={} depth={} location={}", names, d, location);

            Map<String, GraphNode> nodes = new LinkedHashMap<>();
            Map<String, GraphEdge> edges = new LinkedHashMap<>();
            try (Session session = driver.session()) {
                // minSalary is not applied: salary is stored as free text in the graph.
                String query = "MATCH (n) WHERE toLower(n.name) IN $names " +
                        "MATCH p = (n)-[*1.." + d + "]-(m) " +
                        "WHERE ($location IS NULL OR ANY(x IN nodes(p) WHERE x.location = $location)) " +
                        "RETURN p LIMIT 150";
                Map<String, Object> params = new HashMap<>();
                params.put("names", names);
                params.put("location", (location == null || location.isBlank()) ? null : location);

                for (Record record : session.run(query, params).list()) {
                    var path = record.get("p").asPath();
                    path.nodes().forEach(node -> addNode(nodes, node));
                    path.relationships().forEach(rel -> addEdge(edges, rel));
                }

                if (nodes.isEmpty()) {
                    String seed = "MATCH (n) WHERE toLower(n.name) IN $names RETURN n LIMIT 50";
                    for (Record record : session.run(seed, Map.of("names", names)).list()) {
                        addNode(nodes, record.get("n").asNode());
                    }
                }
            }
            return GraphData.builder()
                    .nodes(new ArrayList<>(nodes.values()))
                    .edges(new ArrayList<>(edges.values()))
                    .found(!nodes.isEmpty())
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<GraphData> shortestPathByName(String from, String to) {
        return Mono.fromCallable(() -> {
            Map<String, GraphNode> nodes = new LinkedHashMap<>();
            Map<String, GraphEdge> edges = new LinkedHashMap<>();
            boolean found = false;
            try (Session session = driver.session()) {
                String query = "MATCH (a) WHERE toLower(a.name) = toLower($from) WITH a LIMIT 1 " +
                        "MATCH (b) WHERE toLower(b.name) = toLower($to) WITH a, b LIMIT 1 " +
                        "MATCH p = shortestPath((a)-[*..8]-(b)) RETURN p LIMIT 1";
                List<Record> records = session.run(query, Map.of("from", from, "to", to)).list();
                if (!records.isEmpty()) {
                    var path = records.get(0).get("p").asPath();
                    path.nodes().forEach(node -> addNode(nodes, node));
                    path.relationships().forEach(rel -> addEdge(edges, rel));
                    found = true;
                }
            }
            return GraphData.builder()
                    .nodes(new ArrayList<>(nodes.values()))
                    .edges(new ArrayList<>(edges.values()))
                    .found(found)
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void addNode(Map<String, GraphNode> acc, Node node) {
        String id = String.valueOf(node.id());
        acc.computeIfAbsent(id, k -> GraphNode.builder()
                .id(id)
                .type(String.join(",", node.labels()))
                .name(node.get("name").isNull() ? null : node.get("name").asString())
                .properties(new HashMap<>(node.asMap()))
                .build());
    }

    private void addEdge(Map<String, GraphEdge> acc, Relationship rel) {
        String id = String.valueOf(rel.id());
        acc.computeIfAbsent(id, k -> GraphEdge.builder()
                .id(id)
                .source(String.valueOf(rel.startNodeId()))
                .target(String.valueOf(rel.endNodeId()))
                .type(rel.type())
                .properties(new HashMap<>(rel.asMap()))
                .build());
    }
}
