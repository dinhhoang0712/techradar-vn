package com.techpulse.techradar.features.graph.adapters.input;

import com.techpulse.techradar.features.graph.application.ExploreGraphUseCase;
import com.techpulse.techradar.features.graph.application.FilterGraphUseCase;
import com.techpulse.techradar.features.graph.application.RoadAnalysisUseCase;
import com.techpulse.techradar.features.graph.domain.GraphData;
import com.techpulse.techradar.features.graph.domain.GraphFilter;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Graph API controller.
 */
@Tag(name = "Graph", description = "Knowledge graph exploration endpoints")
@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
public class GraphController {

    private final ExploreGraphUseCase exploreGraphUseCase;
    private final FilterGraphUseCase filterGraphUseCase;
    private final RoadAnalysisUseCase roadAnalysisUseCase;

    @Operation(summary = "Explore the subgraph around one or more keywords")
    @GetMapping("/explore")
    public Mono<ResponseEntity<ApiResponse<GraphData>>> explore(
            @RequestParam List<String> keywords,
            @RequestParam(defaultValue = "2") int depth,
            @RequestParam(required = false) String location,
            @RequestParam(value = "min_salary", required = false) Long minSalary
    ) {
        return exploreGraphUseCase.execute(keywords, depth, location, minSalary)
                .map(data -> ResponseEntity.ok(
                        ApiResponse.success(data, "Graph explored")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "EXPLORE_ERROR")
                        )
                ));
    }

    @Operation(summary = "Find the shortest path between two nodes")
    @GetMapping("/road_analysis")
    public Mono<ResponseEntity<ApiResponse<GraphData>>> roadAnalysis(
            @RequestParam String from,
            @RequestParam String to
    ) {
        return roadAnalysisUseCase.execute(from, to)
                .map(data -> ResponseEntity.ok(
                        ApiResponse.success(data, data.isFound() ? "Path found" : "No path found")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "ROAD_ANALYSIS_ERROR")
                        )
                ));
    }

    @Operation(summary = "Filter graph nodes")
    @PostMapping("/filter")
    public Mono<ResponseEntity<ApiResponse<List<GraphNode>>>> filter(
            @RequestBody FilterRequest request
    ) {
        GraphFilter filter = GraphFilter.builder()
                .locations(request != null ? request.getLocations() : null)
                .minSalary(request != null ? request.getMinSalary() : null)
                .maxSalary(request != null ? request.getMaxSalary() : null)
                .sentiment(request != null ? request.getSentiment() : null)
                .nodeTypes(request != null ? request.getNodeTypes() : null)
                .build();
        return filterGraphUseCase.execute(filter)
                .collectList()
                .map(nodes -> ResponseEntity.ok(
                        ApiResponse.success(nodes, "Graph nodes filtered")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "FILTER_ERROR")
                        )
                ));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class FilterRequest {
        private java.util.List<String> locations;
        private java.util.List<String> nodeTypes;
        private Integer minSalary;
        private Integer maxSalary;
        private String sentiment;
    }
}
