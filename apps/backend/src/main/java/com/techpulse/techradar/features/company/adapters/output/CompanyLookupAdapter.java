package com.techpulse.techradar.features.company.adapters.output;

import com.techpulse.techradar.features.company.application.GetCompaniesUseCase;
import com.techpulse.techradar.features.social.ports.CompanyLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Implements social's {@link CompanyLookupPort} by delegating to this feature's own
 * {@link GetCompaniesUseCase} (its Redis-cached company list) — keeps {@code CreatePostUseCase}
 * from depending on the company feature's concrete use-case type directly.
 */
@Component
@RequiredArgsConstructor
public class CompanyLookupAdapter implements CompanyLookupPort {

    private final GetCompaniesUseCase getCompaniesUseCase;

    @Override
    public Mono<CompanySummary> findById(String companyId) {
        return getCompaniesUseCase.all()
                .filter(c -> companyId.equals(c.id()))
                .next()
                .map(c -> new CompanySummary(c.id(), c.name(), c.location()));
    }
}
