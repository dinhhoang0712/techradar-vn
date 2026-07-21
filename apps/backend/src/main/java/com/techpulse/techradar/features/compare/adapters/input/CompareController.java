package com.techpulse.techradar.features.compare.adapters.input;

import com.techpulse.techradar.features.compare.application.CompareSearchUseCase;
import com.techpulse.techradar.features.compare.application.GenerateLlmSummaryUseCase;
import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.domain.TechComparisonSeries;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Compare API controller. {@code /compare/search} returns per-technology monthly series
 * (keyword, yoy_rate, mom_rate, growth_rate, monthly[]) as the web/mobile clients expect.
 */
@Tag(name = "Compare", description = "Technology comparison endpoints")
@RestController
@RequestMapping("/compare")
@RequiredArgsConstructor
public class CompareController {

    private final CompareSearchUseCase compareSearchUseCase;
    private final GenerateLlmSummaryUseCase generateLlmSummaryUseCase;

    @Operation(summary = "Compare monthly trends across technologies")
    @GetMapping("/search")
    public Mono<ResponseEntity<ApiResponse<List<CompareDtos.CompareItem>>>> compare(
            @RequestParam List<String> keywords,
            @RequestParam(defaultValue = "12") int months
    ) {
        return compareSearchUseCase.execute(keywords, months)
                .map(series -> ResponseEntity.ok(ApiResponse.success(toCompareItems(series), "Comparison completed")))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "COMPARISON_ERROR")
                        )
                ));
    }

    /** Thin translation from the use case's grouped domain series to the client-facing DTO. */
    private List<CompareDtos.CompareItem> toCompareItems(List<TechComparisonSeries> series) {
        return series.stream()
                .map(s -> new CompareDtos.CompareItem(
                        s.name(), s.yoyRate(), s.momRate(), s.growthRate(),
                        s.monthly().stream()
                                .map(m -> new CompareDtos.MonthlyPoint(m.month(), m.year(), m.activity(), m.articleCount()))
                                .toList()
                ))
                .toList();
    }

    @Operation(summary = "Generate LLM summary for technology comparison")
    @PostMapping("/llm-summary")
    public Mono<ResponseEntity<ApiResponse<ComparisonSummaryResponse>>> generateSummary(
            @RequestBody TechComparison comparison
    ) {
        return generateLlmSummaryUseCase.execute(comparison)
                .map(summary -> ResponseEntity.ok(
                        ApiResponse.success(
                                new ComparisonSummaryResponse(summary),
                                "Summary generated"
                        )
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.status(503).body(
                                ApiResponse.error(
                                        "AI service unavailable: " + ex.getMessage(),
                                        "SERVICE_UNAVAILABLE"
                                )
                        )
                ));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ComparisonSummaryResponse {
        private String summary;
    }
}
