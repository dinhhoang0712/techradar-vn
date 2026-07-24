package com.techpulse.techradar.features.graph.adapters.input;

import com.techpulse.techradar.features.graph.application.RebuildGraphAnalyticsUseCase;
import com.techpulse.techradar.features.graph.domain.GraphAnalyticsSummary;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Admin-triggered rebuild of graph analytics (PageRank/Louvain community/degree centrality) via
 * Neo4j GDS.
 */
@Tag(name = "Admin", description = "Graph analytics (GDS) rebuild")
@RestController
@RequestMapping("/admin/graph-analytics")
@RequiredArgsConstructor
public class GraphAnalyticsAdminController {

    private final RebuildGraphAnalyticsUseCase rebuildGraphAnalyticsUseCase;
    private final AuditLogService auditLogService;

    @Operation(summary = "Rebuild PageRank/Louvain/degree centrality for Technology nodes from the knowledge graph")
    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('graph:manage')")
    public Mono<ResponseEntity<ApiResponse<GraphAnalyticsSummary>>> rebuild() {
        return rebuildGraphAnalyticsUseCase.execute()
                .flatMap(summary -> auditLogService.record("GRAPH_ANALYTICS_REBUILD", "graph", null, summary.toString())
                        .thenReturn(summary))
                .map(summary -> ResponseEntity.ok(ApiResponse.success(summary, "Graph analytics rebuilt")))
                .onErrorResume(ex -> Mono.just(ResponseEntity.status(503).body(
                        ApiResponse.error("Graph analytics rebuild failed: " + ex.getMessage(), "GRAPH_ANALYTICS_ERROR"))));
    }
}
