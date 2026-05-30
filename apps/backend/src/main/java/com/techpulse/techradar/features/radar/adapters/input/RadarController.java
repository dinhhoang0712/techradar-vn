package com.techpulse.techradar.features.radar.adapters.input;

import com.techpulse.techradar.features.radar.application.GetTop4TechnologiesUseCase;
import com.techpulse.techradar.features.radar.application.SearchTrendUseCase;
import com.techpulse.techradar.features.radar.domain.RadarTrend;
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
 * Radar API controller - input adapter for radar module.
 */
@Tag(name = "Radar", description = "Technology trend radar endpoints")
@RestController
@RequestMapping("/radar")
@RequiredArgsConstructor
public class RadarController {

    private final GetTop4TechnologiesUseCase getTop4UseCase;
    private final SearchTrendUseCase searchTrendUseCase;

    @Operation(summary = "Get top 4 growing technologies")
    @GetMapping("/top4")
    public Mono<ResponseEntity<ApiResponse<List<RadarTrend>>>> getTop4() {
        return getTop4UseCase.execute()
                .collectList()
                .map(trends -> ResponseEntity.ok(
                        ApiResponse.success(trends, "Top 4 technologies retrieved")
                ));
    }

    @Operation(summary = "Search technology trends")
    @GetMapping("/search")
    public Mono<ResponseEntity<ApiResponse<List<RadarTrend>>>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return searchTrendUseCase.execute(keyword, limit)
                .collectList()
                .map(trends -> ResponseEntity.ok(
                        ApiResponse.success(trends, "Search results")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "SEARCH_ERROR")
                        )
                ));
    }

    @Operation(summary = "Get top 10 technologies")
    @GetMapping("/top10")
    public Mono<ResponseEntity<ApiResponse<List<RadarTrend>>>> getTop10() {
        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(null, "Coming soon")
        ));
    }

    @Operation(summary = "Export radar as PNG")
    @GetMapping("/export-png")
    public Mono<ResponseEntity<ApiResponse<String>>> exportPng() {
        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(null, "Export feature coming soon")
        ));
    }

    @Operation(summary = "Export radar as CSV")
    @GetMapping("/export-csv")
    public Mono<ResponseEntity<ApiResponse<String>>> exportCsv() {
        return Mono.just(ResponseEntity.ok(
                ApiResponse.success(null, "Export feature coming soon")
        ));
    }
}
