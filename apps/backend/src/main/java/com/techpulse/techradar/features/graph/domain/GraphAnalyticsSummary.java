package com.techpulse.techradar.features.graph.domain;

/**
 * Result of a {@code Neo4jGraphAnalyticsAdapter} rebuild: how many {@code Technology} nodes got
 * fresh {@code pagerank_score}/{@code degree_centrality}/{@code community_id} properties, and how
 * many distinct Louvain communities GDS found (before the top-10 + "other" remap applied for the
 * frontend palette).
 */
public record GraphAnalyticsSummary(int technologiesScored, int communitiesFound) {
}
