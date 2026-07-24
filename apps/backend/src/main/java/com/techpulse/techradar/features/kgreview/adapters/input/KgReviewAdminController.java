package com.techpulse.techradar.features.kgreview.adapters.input;

import com.techpulse.techradar.features.kgreview.adapters.input.KgReviewDtos.CompanyDuplicateGroupView;
import com.techpulse.techradar.features.kgreview.adapters.input.KgReviewDtos.TechAliasReviewView;
import com.techpulse.techradar.features.kgreview.application.ApproveTechAliasUseCase;
import com.techpulse.techradar.features.kgreview.application.ListCompanyNearDuplicatesUseCase;
import com.techpulse.techradar.features.kgreview.application.ListPendingTechAliasesUseCase;
import com.techpulse.techradar.features.kgreview.application.MergeCompanyDuplicateUseCase;
import com.techpulse.techradar.features.kgreview.application.RejectTechAliasUseCase;
import com.techpulse.techradar.features.system.application.AuditLogService;
import com.techpulse.techradar.shared.dto.ApiResponse;
import com.techpulse.techradar.shared.paging.PageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Admin review queue for Knowledge Graph dedup candidates the automated pipelines weren't
 * confident enough to resolve on their own:
 * <ul>
 *   <li>Technology alias pairs {@code data-platform/gold/tech_dedup.py} sent to
 *       {@code dp_tech_alias_review_queue} instead of auto-merging;</li>
 *   <li>Company near-duplicate groups (legal-entity name variants) detected live from Neo4j —
 *       never auto-merged, since a wrong Company merge corrupts {@code job_count}/
 *       {@code company_size} aggregates.</li>
 * </ul>
 */
@Tag(name = "Admin", description = "Knowledge Graph dedup review queue")
@RestController
@RequestMapping("/admin/kg-review")
@RequiredArgsConstructor
public class KgReviewAdminController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ListPendingTechAliasesUseCase listPendingTechAliasesUseCase;
    private final ApproveTechAliasUseCase approveTechAliasUseCase;
    private final RejectTechAliasUseCase rejectTechAliasUseCase;
    private final ListCompanyNearDuplicatesUseCase listCompanyNearDuplicatesUseCase;
    private final MergeCompanyDuplicateUseCase mergeCompanyDuplicateUseCase;
    private final AuditLogService auditLogService;

    @Operation(summary = "List pending Technology alias review items")
    @GetMapping("/tech-aliases")
    @PreAuthorize("hasAuthority('kg:review')")
    public Mono<ResponseEntity<ApiResponse<List<TechAliasReviewView>>>> listTechAliases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SIZE, MAX_SIZE);
        return listPendingTechAliasesUseCase.execute(pageRequest.size(), pageRequest.offset())
                .map(TechAliasReviewView::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Pending Technology aliases")));
    }

    @Operation(summary = "Count of pending Technology alias review items (for the admin sidebar badge)")
    @GetMapping("/tech-aliases/count")
    @PreAuthorize("hasAuthority('kg:review')")
    public Mono<ResponseEntity<ApiResponse<Map<String, Long>>>> countTechAliases() {
        return listPendingTechAliasesUseCase.count()
                .map(count -> ResponseEntity.ok(ApiResponse.success(Map.of("pending", count))));
    }

    @Operation(summary = "Approve a Technology alias pair — merges the duplicate node in Neo4j immediately")
    @PostMapping("/tech-aliases/{id}/approve")
    @PreAuthorize("hasAuthority('kg:review')")
    public Mono<ResponseEntity<ApiResponse<Void>>> approveTechAlias(
            @PathVariable long id,
            @RequestBody(required = false) ApproveTechAliasRequest request
    ) {
        String canonicalOverride = request != null ? request.getCanonicalName() : null;
        return approveTechAliasUseCase.execute(id, canonicalOverride)
                .then(auditLogService.record("TECH_ALIAS_APPROVE", "tech_alias_review", String.valueOf(id), canonicalOverride))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Technology alias merged")))
                .onErrorResume(IllegalArgumentException.class, ex -> Mono.just(
                        ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), "INVALID_REQUEST"))));
    }

    @Operation(summary = "Reject a Technology alias pair — no merge, keeps both nodes separate")
    @PostMapping("/tech-aliases/{id}/reject")
    @PreAuthorize("hasAuthority('kg:review')")
    public Mono<ResponseEntity<ApiResponse<Void>>> rejectTechAlias(@PathVariable long id) {
        return rejectTechAliasUseCase.execute(id)
                .then(auditLogService.record("TECH_ALIAS_REJECT", "tech_alias_review", String.valueOf(id), null))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Technology alias rejected")));
    }

    @Operation(summary = "Detect Company near-duplicate groups live from Neo4j (never persisted, computed on demand)")
    @GetMapping("/company-duplicates")
    @PreAuthorize("hasAuthority('kg:review')")
    public Mono<ResponseEntity<ApiResponse<List<CompanyDuplicateGroupView>>>> listCompanyDuplicates() {
        return listCompanyNearDuplicatesUseCase.execute()
                .map(CompanyDuplicateGroupView::from)
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list, "Company near-duplicate groups")));
    }

    @Operation(summary = "Merge one Company node into another — admin-confirmed, never automatic")
    @PostMapping("/company-duplicates/merge")
    @PreAuthorize("hasAuthority('kg:review')")
    public Mono<ResponseEntity<ApiResponse<Void>>> mergeCompanyDuplicate(@RequestBody MergeCompanyRequest request) {
        return mergeCompanyDuplicateUseCase.execute(request.getDuplicateId(), request.getCanonicalId())
                .then(auditLogService.record("COMPANY_DUPLICATE_MERGE", "company",
                        request.getDuplicateId(), "canonical=" + request.getCanonicalId()))
                .thenReturn(ResponseEntity.ok(ApiResponse.<Void>success(null, "Companies merged")))
                .onErrorResume(IllegalArgumentException.class, ex -> Mono.just(
                        ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), "INVALID_REQUEST"))));
    }

    @Data
    public static class ApproveTechAliasRequest {
        private String canonicalName;
    }

    @Data
    public static class MergeCompanyRequest {
        private String duplicateId;
        private String canonicalId;
    }
}
