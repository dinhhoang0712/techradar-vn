package com.techpulse.techradar.features.kgreview.application;

import com.techpulse.techradar.features.kgreview.domain.TechAliasReviewItem;
import com.techpulse.techradar.features.kgreview.ports.GraphMergePort;
import com.techpulse.techradar.features.kgreview.ports.TechAliasReviewRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Approves a pending Technology alias pair: merges the duplicate node into the canonical one in
 * Neo4j immediately (so the graph reflects the decision right away, not just after the next
 * scheduled {@code tech_dedup.py} run), then upserts {@code dp_tech_alias_map} so future
 * ingestion auto-canonicalizes this pair, and finally marks the review row resolved.
 * <p>
 * By {@code tech_dedup.py}'s writer convention {@code nameB} is the LLM's suggested canonical —
 * an admin may override that via {@code canonicalNameOverride} (must equal either name in the
 * pair) when the LLM suggestion was backwards.
 */
@Service
@RequiredArgsConstructor
public class ApproveTechAliasUseCase {

    private final TechAliasReviewRepository repository;
    private final GraphMergePort graphMergePort;

    public Mono<Void> execute(long id, String canonicalNameOverride) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Review item not found: " + id)))
                .flatMap(item -> {
                    String canonical = resolveCanonical(item, canonicalNameOverride);
                    String duplicate = canonical.equals(item.nameA()) ? item.nameB() : item.nameA();
                    return graphMergePort.mergeTechnology(duplicate, canonical)
                            .then(repository.saveAlias(duplicate.strip().toLowerCase(), canonical))
                            .then(repository.markApproved(id))
                            .then();
                });
    }

    private String resolveCanonical(TechAliasReviewItem item, String canonicalNameOverride) {
        if (canonicalNameOverride == null || canonicalNameOverride.isBlank()) {
            return item.nameB();
        }
        if (canonicalNameOverride.equals(item.nameA()) || canonicalNameOverride.equals(item.nameB())) {
            return canonicalNameOverride;
        }
        throw new IllegalArgumentException(
                "canonicalNameOverride must be one of the pair's names: " + item.nameA() + " / " + item.nameB());
    }
}
