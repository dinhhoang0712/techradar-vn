package com.techpulse.techradar.features.graph.adapters.output;

import com.techpulse.techradar.features.graph.domain.GraphData;
import com.techpulse.techradar.features.graph.domain.GraphEdge;
import com.techpulse.techradar.features.graph.domain.GraphFilter;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.features.graph.domain.SalaryOverlap;
import com.techpulse.techradar.features.graph.domain.SentimentBand;
import com.techpulse.techradar.features.graph.ports.GraphRepository;
import com.techpulse.techradar.shared.exception.BadRequestException;
import com.techpulse.techradar.shared.exception.ErrorCode;
import com.techpulse.techradar.shared.neo4j.Neo4jReadTemplate;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Neo4j adapter for graph exploration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jGraphRepository implements GraphRepository {

    /**
     * Node labels actually written by the ingestion pipeline (see KafkaNeo4jWriterService /
     * Neo4jExtractionWriter and the other Neo4j*Repository adapters). {@link #findNodesByType}
     * splices its {@code nodeType} argument directly into a Cypher label position, so it's
     * validated against this allowlist first instead of trusting caller input.
     */
    private static final Set<String> ALLOWED_NODE_TYPES =
            Set.of("Article", "Company", "Job", "Location", "Skill", "Technology");

    /** Same traversal-depth bound {@link #exploreByKeywords} already clamps to. */
    private static final int MIN_EXPLORE_DEPTH = 1;
    private static final int MAX_EXPLORE_DEPTH = 3;

    private final Driver driver;

    @Override
    public Mono<GraphNode> findNode(String nodeId) {
        return runRead(session -> {
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
        });
    }

    @Override
    public Flux<GraphNode> findNodesByType(String nodeType) {
        return runRead(session -> {
            if (!ALLOWED_NODE_TYPES.contains(nodeType)) {
                throw new BadRequestException(ErrorCode.INVALID_NODE_TYPE,
                        "Unsupported node type: " + nodeType);
            }

            List<GraphNode> nodes = new ArrayList<>();
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
            return nodes;
        }).flatMapIterable(nodes -> nodes);
    }

    @Override
    public Flux<GraphEdge> findEdges(String sourceId, String targetId) {
        return runRead(session -> {
            List<GraphEdge> edges = new ArrayList<>();
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
            return edges;
        }).flatMapIterable(edges -> edges);
    }

    @Override
    public Flux<GraphNode> exploreNeighbors(String nodeId, int depth) {
        return runRead(session -> {
            int d = Math.max(MIN_EXPLORE_DEPTH, Math.min(depth, MAX_EXPLORE_DEPTH));
            List<GraphNode> nodes = new ArrayList<>();
            String query = "MATCH (n)-[*1.." + d + "]-(neighbor) WHERE id(n) = $nodeId " +
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
            return nodes;
        }).flatMapIterable(nodes -> nodes);
    }

    @Override
    public Mono<List<GraphNode>> findPathBetween(String sourceId, String targetId) {
        return runRead(session -> {
            List<GraphNode> path = new ArrayList<>();
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
            return path;
        });
    }

    @Override
    public Flux<GraphNode> filterNodes(GraphFilter filter) {
        return runRead(session -> {
            List<GraphNode> nodes = new ArrayList<>();

            SentimentBand sentimentBand = SentimentBand.forLabel(filter.getSentiment());
            Map<String, Object> params = new HashMap<>();
            params.put("nodeTypes", (filter.getNodeTypes() == null || filter.getNodeTypes().isEmpty())
                    ? null : filter.getNodeTypes());
            params.put("locations", (filter.getLocations() == null || filter.getLocations().isEmpty())
                    ? null : filter.getLocations());
            params.put("sentimentMin", sentimentBand == null ? null : sentimentBand.min());
            params.put("sentimentMax", sentimentBand == null ? null : sentimentBand.max());

            // Salary is free-text in the graph (e.g. "15-25 triệu"), so it can't be filtered
            // reliably in Cypher — matched here, then narrowed by salaryOverlaps() in Java below.
            String query = "MATCH (n) " +
                    "WHERE ($nodeTypes IS NULL OR any(l IN labels(n) WHERE l IN $nodeTypes)) " +
                    "AND ($locations IS NULL OR n.location IN $locations) " +
                    "AND ($sentimentMin IS NULL OR " +
                    "     (n.sentiment_score IS NOT NULL AND n.sentiment_score >= $sentimentMin AND n.sentiment_score <= $sentimentMax)) " +
                    "RETURN DISTINCT n LIMIT 100";

            var result = session.run(query, params);
            for (Record record : result.list()) {
                var node = record.get("n").asNode();
                Map<String, Object> properties = new HashMap<>(node.asMap());
                if (!SalaryOverlap.matches(properties.get("salary"), filter.getMinSalary(), filter.getMaxSalary())) {
                    continue;
                }
                nodes.add(GraphNode.builder()
                        .id(String.valueOf(node.id()))
                        .type(String.join(",", node.labels()))
                        .name(node.get("name").isNull() ? null : node.get("name").asString())
                        .properties(properties)
                        .build());
            }
            log.info("Neo4j filterNodes matched {} nodes for filter={}", nodes.size(), filter);
            return nodes;
        })
                .doOnError(e -> log.error("Neo4j filterNodes failed for filter={}", filter, e))
                .flatMapIterable(nodes -> nodes);
    }

    @Override
    public Mono<GraphData> exploreByKeywords(List<String> keywords, int depth, String location, Long minSalary) {
        return runRead(session -> {
            int d = Math.max(MIN_EXPLORE_DEPTH, Math.min(depth, MAX_EXPLORE_DEPTH));
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
            return GraphData.builder()
                    .nodes(new ArrayList<>(nodes.values()))
                    .edges(new ArrayList<>(edges.values()))
                    .found(!nodes.isEmpty())
                    .build();
        });
    }

    @Override
    public Mono<GraphData> shortestPathByName(String from, String to) {
        return runRead(session -> {
            Map<String, GraphNode> nodes = new LinkedHashMap<>();
            Map<String, GraphEdge> edges = new LinkedHashMap<>();
            boolean found = false;
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
            return GraphData.builder()
                    .nodes(new ArrayList<>(nodes.values()))
                    .edges(new ArrayList<>(edges.values()))
                    .found(found)
                    .build();
        });
    }

    /**
     * Runs {@code work} against a fresh Neo4j session on the bounded-elastic scheduler,
     * deferred until subscription (so any exception {@code work} throws — including
     * validation errors like {@link BadRequestException} — surfaces as a reactive error rather
     * than a synchronous throw), and closes the session afterward. Delegates to the shared
     * {@link Neo4jReadTemplate} so this session/scheduler boilerplate has exactly one
     * implementation repo-wide.
     */
    private <T> Mono<T> runRead(Function<Session, T> work) {
        return Neo4jReadTemplate.read(driver, work);
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
