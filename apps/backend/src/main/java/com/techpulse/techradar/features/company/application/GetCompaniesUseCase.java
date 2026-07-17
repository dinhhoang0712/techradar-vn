package com.techpulse.techradar.features.company.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.company.ports.CompanyRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Companies with an inferred tech-stack fingerprint, ranked by job count.
 * The Neo4j result is cached in Redis (TTL configurable via app.redis.company-cache-ttl) since it
 * only changes as often as the ingestion pipeline runs, not per request.
 */
@Component
@RequiredArgsConstructor
public class GetCompaniesUseCase {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final TypeReference<List<CompanyProfile>> LIST_TYPE = new TypeReference<>() {};
    static final String CACHE_KEY = "cache:company:all";

    private final CompanyRepository companyRepository;
    private final ReactiveRedisCache redisCache;

    @Value("${app.redis.company-cache-ttl:1800}")
    private long cacheTtlSeconds;

    /** Full cached list, unpaginated — also reused by GetSimilarCompaniesUseCase. */
    public Flux<CompanyProfile> all() {
        return redisCache.getOrLoad(
                CACHE_KEY,
                Duration.ofSeconds(cacheTtlSeconds),
                companyRepository.findAllWithTechStack().map(GetCompaniesUseCase::toProfile),
                LIST_TYPE
        );
    }

    public Flux<CompanyProfile> execute(int page, int size) {
        return execute(null, page, size);
    }

    /** @param q optional case-insensitive name filter, applied to the already-cached list (no new Cypher query). */
    public Flux<CompanyProfile> execute(String q, int page, int size) {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Flux<CompanyProfile> source = (q == null || q.isBlank())
                ? all()
                : all().filter(c -> c.name() != null && c.name().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)));
        return source
                .skip((long) effectivePage * effectiveSize)
                .take(effectiveSize);
    }

    private static CompanyProfile toProfile(CompanyRepository.CompanyRaw raw) {
        return new CompanyProfile(
                raw.id(),
                CompanyNames.clean(raw.name()),
                raw.location(),
                raw.techStack(),
                raw.jobCount()
        );
    }
}
