package com.techpulse.techradar.features.compare.adapters.input;

import com.techpulse.techradar.features.compare.application.CompareSearchUseCase;
import com.techpulse.techradar.features.compare.application.GenerateLlmSummaryUseCase;
import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.radar.domain.MonthlyCount;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                .map(rows -> ResponseEntity.ok(ApiResponse.success(toCompareItems(rows), "Comparison completed")))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "COMPARISON_ERROR")
                        )
                ));
    }

    /** Group monthly rows by technology, carrying the latest yoy/mom/growth rates. */
    private List<CompareDtos.CompareItem> toCompareItems(List<MonthlyCount> rows) {
        Map<String, CompareDtos.CompareItem> byTech = new LinkedHashMap<>();
        for (MonthlyCount row : rows) {
            CompareDtos.CompareItem item = byTech.computeIfAbsent(row.name(),
                    k -> new CompareDtos.CompareItem(row.name(), 0.0, 0.0, 0.0, new ArrayList<>()));
            item.getMonthly().add(new CompareDtos.MonthlyPoint(row.month(), row.year(), row.activity()));
            // rows are ordered ascending, so the last assignment reflects the most recent month.
            item.setYoyRate(row.yoyRate());
            item.setMomRate(row.momRate());
            item.setGrowthRate(row.growthRate());
        }
        return new ArrayList<>(byTech.values());
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
