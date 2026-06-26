package com.techpulse.techradar.features.salary.adapters.input;

import com.techpulse.techradar.features.salary.application.GetSalaryInsightsUseCase;
import com.techpulse.techradar.features.salary.application.GetTechSalaryDetailUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Salary Insights API — salary statistics aggregated by technology.
 * All salary values are in triệu VND (millions of Vietnamese dong).
 */
@Tag(name = "Salary Insights", description = "Technology salary statistics from job postings")
@RestController
@RequestMapping("/salary")
@RequiredArgsConstructor
public class SalaryController {

    private final GetSalaryInsightsUseCase getSalaryInsightsUseCase;
    private final GetTechSalaryDetailUseCase getTechSalaryDetailUseCase;

    @Operation(
            summary = "Top technologies by median salary",
            description = "Returns technologies ranked by median salary (triệu VND). " +
                          "Only techs with at least min_jobs job postings that have salary data are included."
    )
    @GetMapping("/top")
    public Mono<ResponseEntity<ApiResponse<List<SalaryDtos.SalaryInsightResponse>>>> getTop(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(name = "min_jobs", defaultValue = "3") int minJobs
    ) {
        return getSalaryInsightsUseCase.execute(limit, minJobs)
                .map(SalaryDtos.SalaryInsightResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Salary insights")));
    }

    @Operation(
            summary = "Salary detail for a specific technology",
            description = "Returns detailed salary stats (median, avg, percentiles) and " +
                          "the top co-required technologies for the given tech name."
    )
    @GetMapping("/tech/{techName}")
    public Mono<ResponseEntity<Object>> getTechSalary(@PathVariable String techName) {
        return getTechSalaryDetailUseCase.execute(techName)
                .map(SalaryDtos.SalaryInsightResponse::from)
                .map(r -> ResponseEntity.ok((Object) ApiResponse.success(r, "Salary detail")))
                .onErrorResume(NotFoundException.class, ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) ApiResponse.error(ex.getMessage(), "NOT_FOUND"))));
    }
}