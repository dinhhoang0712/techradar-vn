package com.techpulse.techradar.features.graph.adapters.output;

import com.techpulse.techradar.features.graph.domain.GraphAnalyticsSummary;
import com.techpulse.techradar.features.graph.ports.GraphAnalyticsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs Neo4j GDS (PageRank + Louvain community + degree centrality) over the
 * Technology-RELATED_TO-Technology subgraph and writes the scores back onto {@code Technology}
 * nodes as plain properties — every existing graph read ({@code GET /graph/explore},
 * {@link Neo4jGraphRepository}) picks them up for free, no GDS call needed at read time.
 * <p>
 * Requires the GDS plugin (docker-compose {@code NEO4J_PLUGINS}); not wired through
 * {@code Neo4jReadTemplate} since that helper is read-only by contract and this does one
 * multi-step write.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jGraphAnalyticsAdapter implements GraphAnalyticsPort {

    private static final String GRAPH_NAME = "techGraph";
    /** Largest N Louvain communities keep their own palette slot; everything else buckets here. */
    private static final int MAX_DISPLAY_COMMUNITIES = 6;
    private static final int OTHER_COMMUNITY = 99;

    private final Driver driver;

    @Override
    public Mono<GraphAnalyticsSummary> rebuild() {
        return Mono.fromCallable(this::runGds).subscribeOn(Schedulers.boundedElastic());
    }

    private GraphAnalyticsSummary runGds() {
        try (Session session = driver.session()) {
            long techCount = session.run("MATCH (t:Technology) RETURN count(t) AS c")
                    .single().get("c").asLong();
            if (techCount == 0) {
                log.info("Graph analytics rebuild skipped: no Technology nodes yet");
                return new GraphAnalyticsSummary(0, 0);
            }

            session.run("CALL gds.graph.drop($name, false)", Map.of("name", GRAPH_NAME)).consume();
            session.run("""
                    CALL gds.graph.project($name, 'Technology',
                        {RELATED_TO: {orientation: 'UNDIRECTED', properties: 'co_mention_count'}})
                    """, Map.of("name", GRAPH_NAME)).consume();

            try {
                Map<Long, Double> pagerank = streamDoubleScores(session, """
                        CALL gds.pageRank.stream($name, {relationshipWeightProperty: 'co_mention_count'})
                        YIELD nodeId, score
                        WITH gds.util.asNode(nodeId) AS n, score
                        RETURN id(n) AS nodeId, score
                        """);
                Map<Long, Double> degree = streamDoubleScores(session, """
                        CALL gds.degree.stream($name)
                        YIELD nodeId, score
                        WITH gds.util.asNode(nodeId) AS n, score
                        RETURN id(n) AS nodeId, score
                        """);
                Map<Long, Long> rawCommunityByNode = streamCommunities(session);
                Map<Long, Integer> displayCommunityByNode = remapCommunities(rawCommunityByNode);

                List<Map<String, Object>> rows = new ArrayList<>();
                for (Long nodeId : pagerank.keySet()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("nodeId", nodeId);
                    row.put("pagerank", pagerank.getOrDefault(nodeId, 0.0));
                    row.put("degree", degree.getOrDefault(nodeId, 0.0));
                    row.put("community", displayCommunityByNode.getOrDefault(nodeId, OTHER_COMMUNITY));
                    rows.add(row);
                }

                int written = session.executeWrite(tx -> {
                    tx.run("""
                            UNWIND $rows AS row
                            MATCH (t) WHERE id(t) = row.nodeId
                            SET t.pagerank_score = row.pagerank,
                                t.degree_centrality = row.degree,
                                t.community_id = row.community
                            """, Values.parameters("rows", rows)).consume();
                    return rows.size();
                });

                long distinctCommunities = rawCommunityByNode.values().stream().distinct().count();
                log.info("Graph analytics rebuild: {} technologies scored, {} communities found",
                        written, distinctCommunities);
                return new GraphAnalyticsSummary(written, (int) distinctCommunities);
            } finally {
                session.run("CALL gds.graph.drop($name)", Map.of("name", GRAPH_NAME)).consume();
            }
        }
    }

    private Map<Long, Double> streamDoubleScores(Session session, String cypher) {
        Map<Long, Double> scores = new HashMap<>();
        for (Record record : session.run(cypher, Map.of("name", GRAPH_NAME)).list()) {
            scores.put(record.get("nodeId").asLong(), record.get("score").asDouble());
        }
        return scores;
    }

    private Map<Long, Long> streamCommunities(Session session) {
        Map<Long, Long> communities = new HashMap<>();
        String cypher = """
                CALL gds.louvain.stream($name, {relationshipWeightProperty: 'co_mention_count'})
                YIELD nodeId, communityId
                WITH gds.util.asNode(nodeId) AS n, communityId
                RETURN id(n) AS nodeId, communityId
                """;
        for (Record record : session.run(cypher, Map.of("name", GRAPH_NAME)).list()) {
            communities.put(record.get("nodeId").asLong(), record.get("communityId").asLong());
        }
        return communities;
    }

    /**
     * Louvain community ids are arbitrary large longs with no size ordering — remaps the
     * {@value #MAX_DISPLAY_COMMUNITIES} largest communities to a compact 0-based index (biggest
     * first) so the frontend can use a small fixed categorical palette; every smaller/singleton
     * community collapses into {@link #OTHER_COMMUNITY}.
     */
    private Map<Long, Integer> remapCommunities(Map<Long, Long> rawCommunityByNode) {
        Map<Long, Long> memberCountByRawCommunity = new HashMap<>();
        for (Long rawCommunity : rawCommunityByNode.values()) {
            memberCountByRawCommunity.merge(rawCommunity, 1L, Long::sum);
        }

        List<Long> largestCommunitiesFirst = memberCountByRawCommunity.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(MAX_DISPLAY_COMMUNITIES)
                .toList();

        Map<Long, Integer> rawToDisplayIndex = new LinkedHashMap<>();
        for (int i = 0; i < largestCommunitiesFirst.size(); i++) {
            rawToDisplayIndex.put(largestCommunitiesFirst.get(i), i);
        }

        Map<Long, Integer> displayCommunityByNode = new HashMap<>();
        rawCommunityByNode.forEach((nodeId, rawCommunity) ->
                displayCommunityByNode.put(nodeId, rawToDisplayIndex.getOrDefault(rawCommunity, OTHER_COMMUNITY)));
        return displayCommunityByNode;
    }
}
