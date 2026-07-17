package com.techpulse.techradar.features.company.adapters.input;

import com.techpulse.techradar.features.company.application.GetCompaniesUseCase;
import com.techpulse.techradar.features.company.application.GetCompanyMentionsUseCase;
import com.techpulse.techradar.features.company.application.GetSimilarCompaniesUseCase;
import com.techpulse.techradar.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Company Tech-Stack API — company profiles and similarity, inferred from job postings.
 */
@Tag(name = "Company", description = "Company tech-stack fingerprinting and similarity")
@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final GetCompaniesUseCase getCompaniesUseCase;
    private final GetSimilarCompaniesUseCase getSimilarCompaniesUseCase;
    private final GetCompanyMentionsUseCase getCompanyMentionsUseCase;

    @Operation(summary = "Companies with an inferred tech stack, ranked by job count")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<CompanyDtos.CompanyProfileResponse>>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return getCompaniesUseCase.execute(q, page, size)
                .map(CompanyDtos.CompanyProfileResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Companies")));
    }

    @Operation(
            summary = "Companies with the most similar tech stack",
            description = "Ranks other companies by Jaccard similarity of required-skill overlap " +
                          "against the given company's inferred tech stack."
    )
    @GetMapping("/{id}/similar")
    public Mono<ResponseEntity<ApiResponse<List<CompanyDtos.SimilarCompanyResponse>>>> similar(
            @PathVariable String id,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return getSimilarCompaniesUseCase.execute(id, limit)
                .map(list -> list.stream().map(CompanyDtos.SimilarCompanyResponse::from).toList())
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Similar companies")));
    }

    @Operation(
            summary = "Recent articles mentioning this company",
            description = "Backed by the Article-[:MENTIONS]->Company relationship written by the ingestion pipeline."
    )
    @GetMapping("/{id}/mentions")
    public Mono<ResponseEntity<ApiResponse<List<CompanyDtos.CompanyMentionResponse>>>> mentions(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return getCompanyMentionsUseCase.execute(id, limit)
                .map(CompanyDtos.CompanyMentionResponse::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Company mentions")));
    }
}
