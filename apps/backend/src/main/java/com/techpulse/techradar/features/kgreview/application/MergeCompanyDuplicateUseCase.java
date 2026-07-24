package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.ports.GraphMergePort;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Merges one Company node into another — admin-confirmed only (see
 * {@link com.techpulse.techradar.features.kgreview.domain.CompanyDuplicateGroup} for why this
 * never happens automatically). {@code duplicateId}/{@code canonicalId} are Neo4j
 * {@code Company.id} values (the deterministic slug, e.g. {@code slugify(companyName)}), never
 * display names — two different companies can legitimately share a display name.
 */
@Service
@RequiredArgsConstructor
public class MergeCompanyDuplicateUseCase {

    private final GraphMergePort graphMergePort;

    public Mono<Void> execute(String duplicateId, String canonicalId) {
        if (duplicateId == null || duplicateId.isBlank() || canonicalId == null || canonicalId.isBlank()) {
            return Mono.error(new IllegalArgumentException("duplicateId and canonicalId are required"));
        }
        if (duplicateId.equals(canonicalId)) {
            return Mono.error(new IllegalArgumentException("duplicateId and canonicalId must differ"));
        }
        return graphMergePort.mergeCompany(duplicateId, canonicalId)
                .flatMap(merged -> merged
                        ? Mono.<Void>empty()
                        : Mono.error(new NotFoundException(
                                "One or both companies not found: " + duplicateId + " / " + canonicalId)));
    }
}
