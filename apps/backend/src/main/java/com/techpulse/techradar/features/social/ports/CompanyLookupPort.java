package com.techpulse.techradar.features.social.ports;

import reactor.core.publisher.Mono;

/**
 * Minimal read-only company lookup that {@code CreatePostUseCase} needs to validate/enrich a
 * tagged company on a post. Owned by social (not company) so social depends on an abstraction it
 * controls instead of the company feature's concrete {@code GetCompaniesUseCase} type (DIP) —
 * implemented by an adapter inside the company feature itself.
 */
public interface CompanyLookupPort {

    /** Empty if no company with this id exists. */
    Mono<CompanySummary> findById(String companyId);

    /** Just the fields a post needs to denormalize a tagged company onto its row. */
    record CompanySummary(String id, String name, String location) {
    }
}
