package com.techpulse.techradar.features.compare.adapters.input;

import com.techpulse.techradar.features.compare.application.CompareTechnologiesUseCase;
import com.techpulse.techradar.features.compare.application.GenerateLlmSummaryUseCase;
import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Compare API controller.
 */
@Tag(name = "Compare", description = "Technology comparison endpoints")
@RestController
@RequestMapping("/compare")
@RequiredArgsConstructor
public class CompareController {

    private final CompareTechnologiesUseCase compareTechnologiesUseCase;
    private final GenerateLlmSummaryUseCase generateLlmSummaryUseCase;

    @Operation(summary = "Compare two technologies")
    @GetMapping("/search")
    public Mono<ResponseEntity<ApiResponse<TechComparison>>> compare(
            @RequestParam String tech1,
            @RequestParam String tech2
    ) {
        return compareTechnologiesUseCase.execute(tech1, tech2)
                .map(comparison -> ResponseEntity.ok(
                        ApiResponse.success(comparison, "Comparison completed")
                ))
                .onErrorResume(ex -> Mono.just(
                        ResponseEntity.badRequest().body(
                                ApiResponse.error(ex.getMessage(), "COMPARISON_ERROR")
                        )
                ));
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
