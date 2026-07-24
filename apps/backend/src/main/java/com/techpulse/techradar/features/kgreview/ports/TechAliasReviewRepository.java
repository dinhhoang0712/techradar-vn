package com.techpulse.techradar.features.kgreview.ports;

import com.techpulse.techradar.features.kgreview.domain.TechAliasReviewItem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TechAliasReviewRepository {

    /** Pending review items, oldest first — same ordering convention as ReportRepository. */
    Flux<TechAliasReviewItem> findPending(int limit, int offset);

    Mono<Long> countPending();

    Mono<TechAliasReviewItem> findById(long id);

    /** @return true if a PENDING row with this id was marked approved. */
    Mono<Boolean> markApproved(long id);

    /** @return true if a PENDING row with this id was marked rejected. */
    Mono<Boolean> markRejected(long id);

    /**
     * Upserts {@code dp_tech_alias_map} so future ingestion auto-canonicalizes this pair without
     * needing another LLM call — same table {@code TechAliasCache.java} refreshes from.
     */
    Mono<Void> saveAlias(String aliasNormalized, String canonicalName);
}
