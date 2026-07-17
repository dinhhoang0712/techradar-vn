package com.techpulse.techradar.features.company.application;

import com.techpulse.techradar.features.company.domain.CompanyMention;
import com.techpulse.techradar.features.company.ports.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Articles mentioning a company (Article-[:MENTIONS]->Company), most recent first. Queried
 * directly (no Redis cache): scoped to a single company and small (limit <= 100), unlike the
 * full company list GetCompaniesUseCase caches.
 */
@Component
@RequiredArgsConstructor
public class GetCompanyMentionsUseCase {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 50;

    private final CompanyRepository companyRepository;

    public Flux<CompanyMention> execute(String companyId, int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return companyRepository.findMentions(companyId, effectiveLimit);
    }
}
