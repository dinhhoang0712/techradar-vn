package com.techpulse.techradar.features.graph.adapters.input;

import com.techpulse.techradar.features.graph.application.ExploreGraphUseCase;
import com.techpulse.techradar.features.graph.domain.GraphNode;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
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

    @Operation(summary = "Explore graph neighbors")
    @GetMapping("/explore")
    public Mono<ResponseEntity<ApiResponse<List<GraphNode>>>> explore(
            @RequestParam String nodeId,
            @RequestParam(defaultValue = "2") int depth
    ) {
        return exploreGraphUseCase.execute(nodeId, depth)
                .collectList()
                .map(nodes -> ResponseEntity.ok(
                        ApiResponse.success(nodes, "Graph neighbors explored")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "EXPLORE_ERROR")
                        )
                ));
    }

    @Operation(summary = "Filter graph nodes")
    @PostMapping("/filter")
    public Mono<ResponseEntity<ApiResponse<List<GraphNode>>>> filter(
            @RequestBody FilterRequest request
    ) {
        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(null, "Filter feature coming soon")
        ));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class FilterRequest {
        private java.util.List<String> locations;
        private Integer minSalary;
        private Integer maxSalary;
        private String sentiment;
    }
}
